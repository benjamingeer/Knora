/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util.search.gravsearch

import akka.actor.ActorSystem
import akka.event.{LogSource, LoggingAdapter}
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder.ontologymessages.{EntityInfoGetRequestV2, EntityInfoGetResponseV2, ReadClassInfoV2, ReadPropertyInfoV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.search._
import org.knora.webapi.util.search.gravsearch.GravsearchTypeInspectionUtil.IntermediateTypeInspectionResult
import org.knora.webapi.util.{SmartIri, StringFormatter}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/**
  * A Gravsearch type inspector that infers types, relying on information from the relevant ontologies.
  */
class InferringGravsearchTypeInspector(nextInspector: Option[GravsearchTypeInspector],
                                       system: ActorSystem)
                                      (implicit executionContext: ExecutionContext) extends GravsearchTypeInspector(nextInspector = nextInspector, system = system) {

    import InferringGravsearchTypeInspector._

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val log: LoggingAdapter = akka.event.Logging(system, this)

    // The maximum number of type inference iterations.
    private val MAX_ITERATIONS = 50

    /**
      * Represents an inference rule in a pipeline. Each rule in the pipeline tries to determine type information
      * about a typeable entity. If it cannot do so, it calls the next rule in the pipeline.
      *
      * @param nextRule the next rule in the pipeline.
      */
    private abstract class InferenceRule(protected val nextRule: Option[InferenceRule]) {
        /**
          * Attempts to determine the type of a single entity. Each implementation must end by calling
          * `runNextRule`.
          *
          * @param untypedEntity           the entity whose type needs to be determined.
          * @param previousIterationResult the result of the previous iteration of type inference.
          * @param entityInfo              information about Knora ontology entities mentioned in the Gravsearch query.
          * @param usageIndex              an index of entity usage in the query.
          * @return type information about the entity, or `None` if the type could not be determined.
          */
        def infer(untypedEntity: TypeableEntity,
                  previousIterationResult: IntermediateTypeInspectionResult,
                  entityInfo: EntityInfoGetResponseV2,
                  usageIndex: UsageIndex): Option[GravsearchEntityTypeInfo]

        /**
          * Runs the next rule in the pipeline.
          *
          * @param untypedEntity           the entity whose type needs to be determined.
          * @param maybeInferredType       the type inferred by the previous rule, if any.
          * @param previousIterationResult the result of the previous iteration of type inference.
          * @param entityInfo              information about Knora ontology entities mentioned in the Gravsearch query.
          * @param usageIndex              an index of entity usage in the query.
          * @return type information about the entity, or `None` if the type could not be determined.
          */
        protected def runNextRule(untypedEntity: TypeableEntity,
                                  maybeInferredType: Option[GravsearchEntityTypeInfo],
                                  previousIterationResult: IntermediateTypeInspectionResult,
                                  entityInfo: EntityInfoGetResponseV2,
                                  usageIndex: UsageIndex): Option[GravsearchEntityTypeInfo] = {
            // Did this rule determine the type?
            maybeInferredType match {
                case Some(inferred) =>
                    // Yes. Return it as the result of the pipeline.
                    Some(inferred)

                case None =>
                    // No. Is there another rule in the pipeline?
                    nextRule match {
                        case Some(rule) =>
                            // Yes. Run that rule.
                            rule.infer(
                                untypedEntity = untypedEntity,
                                previousIterationResult = previousIterationResult,
                                entityInfo = entityInfo,
                                usageIndex = usageIndex
                            )

                        case None =>
                            // No. The type could not be determined.
                            None
                    }
            }
        }
    }

    /**
      * Infers that an entity is a `knora-api:Resource` if there is an `rdf:type` statement about it and and the
      * specified type is a Knora resource class.
      */
    private class RdfTypeRule(nextRule: Option[InferenceRule]) extends InferenceRule(nextRule = nextRule) {
        override def infer(untypedEntity: TypeableEntity,
                           previousIterationResult: IntermediateTypeInspectionResult,
                           entityInfo: EntityInfoGetResponseV2,
                           usageIndex: UsageIndex): Option[GravsearchEntityTypeInfo] = {
            // Has this entity been used as a subject?
            val maybeInferredType: Option[NonPropertyTypeInfo] = usageIndex.subjects.get(untypedEntity) match {
                case Some(statements) =>
                    // Yes. If it's been used with the predicate rdf:type with an IRI object, collect those objects.
                    val rdfTypes: Set[SmartIri] = statements.collect {
                        case StatementPattern(_, IriRef(predIri, _), IriRef(objIri, _), _) if predIri.toString == OntologyConstants.Rdf.Type => objIri
                    }

                    // Get any information the ontology responder provided about the classes identified by those IRIs.
                    val knoraClasses: Set[ReadClassInfoV2] = entityInfo.classInfoMap.filterKeys(rdfTypes).values.toSet

                    // Is any of them a resource class?
                    if (knoraClasses.exists(_.isResourceClass)) {
                        // Yes. Return the entity's type as knora-api:Resource.
                        val inferredType = NonPropertyTypeInfo(OntologyConstants.KnoraApiV2Simple.Resource.toSmartIri)
                        log.debug("RdfTypeRule: {} {} .", untypedEntity, inferredType)
                        Some(inferredType)
                    } else {
                        // This entity isn't a resource, so this rule isn't relevant.
                        None
                    }

                case None =>
                    // This entity hasn't been used as a subject, so this rule isn't relevant.
                    None
            }

            runNextRule(
                untypedEntity = untypedEntity,
                maybeInferredType = maybeInferredType,
                previousIterationResult = previousIterationResult,
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )
        }
    }

    /**
      * Infers a property's `knora-api:objectType` if the property's IRI is used as a predicate.
      */
    private class PropertyIriObjectTypeRule(nextRule: Option[InferenceRule]) extends InferenceRule(nextRule = nextRule) {
        override def infer(untypedEntity: TypeableEntity,
                           previousIterationResult: IntermediateTypeInspectionResult,
                           entityInfo: EntityInfoGetResponseV2,
                           usageIndex: UsageIndex): Option[GravsearchEntityTypeInfo] = {
            // Is this entity an IRI?
            val maybeInferredType: Option[PropertyTypeInfo] = untypedEntity match {
                case TypeableIri(iri) =>
                    // Yes. Has it been used as a predicate?
                    usageIndex.predicates.get(untypedEntity) match {
                        case Some(_) =>
                            // Yes. Has the ontology responder provided information about it?
                            entityInfo.propertyInfoMap.get(iri) match {
                                case Some(readPropertyInfo: ReadPropertyInfoV2) =>
                                    // Yes. Try to infer its knora-api:objectType from the provided information.
                                    InferenceRuleUtil.readPropertyInfoToObjectType(readPropertyInfo) match {
                                        case Some(objectTypeIri: SmartIri) =>
                                            val inferredType = PropertyTypeInfo(objectTypeIri = objectTypeIri)
                                            log.debug("PropertyIriObjectTypeRule: {} {} .", untypedEntity, inferredType)
                                            Some(inferredType)

                                        case None =>
                                            // Its knora-api:objectType couldn't be inferred.
                                            None
                                    }

                                case None =>
                                    // The ontology responder hasn't provided a definition of this property. This should have caused
                                    // an error earlier from the ontology responder.
                                    throw AssertionException(s"No information found about property $iri")
                            }

                        case None =>
                            // The IRI hasn't been used as a predicate, so this rule isn't relevant.
                            None
                    }

                case _ =>
                    // This entity isn't an IRI, so this rule isn't relevant.
                    None
            }

            runNextRule(
                untypedEntity = untypedEntity,
                maybeInferredType = maybeInferredType,
                previousIterationResult = previousIterationResult,
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )
        }
    }

    /**
      * Infers an entity's type if the entity is used as the object of a statement and the predicate's
      * knora-api:objectType is known.
      */
    private class TypeOfObjectFromPropertyRule(nextRule: Option[InferenceRule]) extends InferenceRule(nextRule = nextRule) {
        override def infer(untypedEntity: TypeableEntity,
                           previousIterationResult: IntermediateTypeInspectionResult,
                           entityInfo: EntityInfoGetResponseV2,
                           usageIndex: UsageIndex): Option[GravsearchEntityTypeInfo] = {
            // Has this entity been used as the object of one or more statements?
            val maybeInferredType: Option[NonPropertyTypeInfo] = usageIndex.objects.get(untypedEntity) match {
                case Some(statements) =>
                    // Yes. Try to infer type information from the predicate of each of those statements.
                    val typesFromPredicates: Set[NonPropertyTypeInfo] = statements.flatMap {
                        statement =>
                            // Is the predicate typeable?
                            GravsearchTypeInspectionUtil.maybeTypeableEntity(statement.pred) match {
                                case Some(typeablePred: TypeableEntity) =>
                                    // Yes. Do we have its type?
                                    previousIterationResult.typedEntities.get(typeablePred) match {
                                        case Some(propertyTypeInfo: PropertyTypeInfo) =>
                                            // Yes. Use its knora-api:objectType.
                                            Some(NonPropertyTypeInfo(propertyTypeInfo.objectTypeIri))

                                        case _ =>
                                            // We don't have the predicate's type.
                                            None
                                    }

                                case None =>
                                    // The predicate isn't typeable.
                                    None
                            }
                    }

                    if (typesFromPredicates.isEmpty) {
                        None
                    } else if (typesFromPredicates.size == 1) {
                        val inferredType = typesFromPredicates.head
                        log.debug("TypeOfObjectFromPropertyRule: {} {} .", untypedEntity, inferredType)
                        Some(inferredType)
                    } else {
                        throw GravsearchException(s"Incompatible types were inferred for $untypedEntity: ${typesFromPredicates.map(typeFromPred => IriRef(typeFromPred.typeIri).toSparql).mkString(", ")}")
                    }

                case None =>
                    // This entity hasn't been used as a statement object, so this rule isn't relevant.
                    None
            }

            runNextRule(
                untypedEntity = untypedEntity,
                maybeInferredType = maybeInferredType,
                previousIterationResult = previousIterationResult,
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )
        }
    }


    /**
      * Infers an entity's type if the entity is used as the subject of a statement, the predicate is an IRI, and
      * the predicate's knora-api:subjectType is known.
      */
    private class TypeOfSubjectFromPropertyRule(nextRule: Option[InferenceRule]) extends InferenceRule(nextRule = nextRule) {
        override def infer(untypedEntity: TypeableEntity,
                           previousIterationResult: IntermediateTypeInspectionResult,
                           entityInfo: EntityInfoGetResponseV2,
                           usageIndex: UsageIndex): Option[GravsearchEntityTypeInfo] = {
            // Has this entity been used as the subject of one or more statements?
            val maybeInferredType: Option[NonPropertyTypeInfo] = usageIndex.subjects.get(untypedEntity) match {
                case Some(statements) =>
                    // Yes. Try to infer type information from the predicate of each of those statements.
                    val typesFromPredicates: Set[NonPropertyTypeInfo] = statements.flatMap {
                        statement =>
                            // Is the predicate a Knora IRI?
                            statement.pred match {
                                case IriRef(predIri, _) if predIri.isKnoraEntityIri =>
                                    // Yes. Has the ontology responder provided a property definition for it?
                                    entityInfo.propertyInfoMap.get(predIri) match {
                                        case Some(readPropertyInfo: ReadPropertyInfoV2) =>
                                            // Yes. Can we infer the property's knora-api:subjectType from that definition?
                                            InferenceRuleUtil.readPropertyInfoToSubjectType(readPropertyInfo) match {
                                                case Some(subjectTypeIri: SmartIri) =>
                                                    // Yes. Use that type.
                                                    Some(NonPropertyTypeInfo(subjectTypeIri))

                                                case None =>
                                                    // No. This rule can't infer the entity's type.
                                                    None
                                            }


                                        case None =>
                                            // The ontology responder hasn't provided a definition of this property. This should have caused
                                            // an error earlier from the ontology responder.
                                            throw AssertionException(s"No information found about property $predIri")
                                    }

                                case _ =>
                                    // The predicate isn't a Knora IRI, so this rule isn't relevant.
                                    None
                            }
                    }

                    if (typesFromPredicates.isEmpty) {
                        None
                    } else if (typesFromPredicates.size == 1) {
                        val inferredType = typesFromPredicates.head
                        log.debug("TypeOfSubjectFromPropertyRule: {} {} .", untypedEntity, inferredType)
                        Some(inferredType)
                    } else {
                        throw GravsearchException(s"Incompatible types were inferred for $untypedEntity: ${typesFromPredicates.map(typeFromPred => IriRef(typeFromPred.typeIri).toSparql).mkString(", ")}")
                    }

                case None =>
                    // This entity hasn't been used as a subject, so this rule isn't relevant.
                    None
            }

            runNextRule(
                untypedEntity = untypedEntity,
                maybeInferredType = maybeInferredType,
                previousIterationResult = previousIterationResult,
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )
        }
    }

    /**
      * Infers the knora-api:objectType of a property variable if it's used with an object whose type is known.
      */
    private class PropertyVarTypeFromObjectRule(nextRule: Option[InferenceRule]) extends InferenceRule(nextRule = nextRule) {
        override def infer(untypedEntity: TypeableEntity,
                           previousIterationResult: IntermediateTypeInspectionResult,
                           entityInfo: EntityInfoGetResponseV2,
                           usageIndex: UsageIndex): Option[GravsearchEntityTypeInfo] = {
            // Is this entity a variable?
            val maybeInferredType: Option[PropertyTypeInfo] = untypedEntity match {
                case untypedVariable: TypeableVariable =>
                    // Yes. Has it been used as a predicate?
                    usageIndex.predicates.get(untypedVariable) match {
                        case Some(statements) =>
                            // Yes. Try to infer its knora-api:objectType from the object of each of those statements.
                            val typesFromObjects: Set[PropertyTypeInfo] = statements.flatMap {
                                statement =>
                                    // Is the object typeable?
                                    GravsearchTypeInspectionUtil.maybeTypeableEntity(statement.obj) match {
                                        case Some(typeableObj: TypeableEntity) =>
                                            // Yes. Do we have its type?
                                            previousIterationResult.typedEntities.get(typeableObj) match {
                                                case Some(nonPropertyTypeInfo: NonPropertyTypeInfo) =>
                                                    // Yes. Use its type.
                                                    Some(PropertyTypeInfo(nonPropertyTypeInfo.typeIri))

                                                case _ =>
                                                    // We don't have the object's type.
                                                    None
                                            }

                                        case None =>
                                            // The object isn't typeable.
                                            None
                                    }
                            }

                            if (typesFromObjects.isEmpty) {
                                None
                            } else if (typesFromObjects.size == 1) {
                                val inferredType = typesFromObjects.head
                                log.debug("PropertyVarTypeFromObjectRule: {} {} .", untypedVariable, inferredType)
                                Some(inferredType)
                            } else {
                                throw GravsearchException(s"Incompatible values were inferred for the knora-api:objectType of $untypedVariable: ${typesFromObjects.map(typeFromObj => IriRef(typeFromObj.objectTypeIri).toSparql).mkString(", ")}")
                            }

                        case None =>
                            // This entity hasn't been used as a predicate, so this rule isn't relevant.
                            None
                    }

                case _ =>
                    // This entity isn't a variable, so this rule isn't relevant.
                    None
            }

            runNextRule(
                untypedEntity = untypedEntity,
                maybeInferredType = maybeInferredType,
                previousIterationResult = previousIterationResult,
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )
        }
    }

    /**
      * Infers the knora-api:objectType of a property variable if it's compared to a known property IRI in a FILTER.
      */
    private class PropertyVarTypeFromFilterRule(nextRule: Option[InferenceRule]) extends InferenceRule(nextRule = nextRule) {
        override def infer(untypedEntity: TypeableEntity,
                           previousIterationResult: IntermediateTypeInspectionResult,
                           entityInfo: EntityInfoGetResponseV2,
                           usageIndex: UsageIndex): Option[GravsearchEntityTypeInfo] = {
            // Is this entity a variable?
            val maybeInferredType: Option[PropertyTypeInfo] = untypedEntity match {
                case untypedVariable: TypeableVariable =>
                    // Yes. Has it been used as a predicate?
                    usageIndex.predicates.get(untypedVariable) match {
                        case Some(_) =>
                            // Yes. Has it been compared with one or more IRIs in a FILTER?
                            usageIndex.compareExpressionVariables.get(untypedVariable) match {
                                case Some(iris: Set[SmartIri]) =>
                                    // Yes. Interpret those as property IRIs.
                                    val typesFromFilters: Set[PropertyTypeInfo] = iris.flatMap {
                                        propertyIri: SmartIri =>
                                            // Has the ontology responder provided a definition of this property?
                                            entityInfo.propertyInfoMap.get(propertyIri) match {
                                                case Some(readPropertyInfo: ReadPropertyInfoV2) =>
                                                    // Yes. Can we determine the property's knora-api:objectType from that definition?
                                                    InferenceRuleUtil.readPropertyInfoToObjectType(readPropertyInfo) match {
                                                        case Some(objectTypeIri: SmartIri) =>
                                                            // Yes. Use that type.
                                                            Some(PropertyTypeInfo(objectTypeIri = objectTypeIri))

                                                        case None =>
                                                            // No knora-api:objectType could be determined for the property IRI.
                                                            None
                                                    }

                                                case None =>
                                                    // The ontology responder hasn't provided a definition of this property. This should have caused
                                                    // an error earlier from the ontology responder.
                                                    throw AssertionException(s"No information found about property $propertyIri")
                                            }
                                    }

                                    if (typesFromFilters.isEmpty) {
                                        None
                                    } else if (typesFromFilters.size == 1) {
                                        val inferredType = typesFromFilters.head
                                        log.debug("PropertyVarTypeFromFilterRule: {} {} .", untypedVariable, inferredType)
                                        Some(inferredType)
                                    } else {
                                        throw GravsearchException(s"Incompatible values were inferred for the knora-api:objectType of $untypedVariable: ${typesFromFilters.map(typeFromObj => IriRef(typeFromObj.objectTypeIri).toSparql).mkString(", ")}")
                                    }

                                case None =>
                                    // The variable hasn't been compared with an IRI in a FILTER, so this rule isn't relevant.
                                    None
                            }

                        case None =>
                            // The variable hasn't been used as a predicate, so this rule isn't relevant.
                            None
                    }

                case _ =>
                    // The entity isn't a variable, so this rule isn't relevant.
                    None
            }

            runNextRule(
                untypedEntity = untypedEntity,
                maybeInferredType = maybeInferredType,
                previousIterationResult = previousIterationResult,
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )
        }
    }

    /**
      * Utility functions for type inference rules.
      */
    private object InferenceRuleUtil {
        /**
          * Given a [[ReadPropertyInfoV2]], returns the IRI of the inferred knora-api:subjectType of the property, if any.
          *
          * @param readPropertyInfo the property definition.
          * @return the IRI of the inferred knora-api:subjectType of the property, or `None` if it could not inferred.
          */
        def readPropertyInfoToSubjectType(readPropertyInfo: ReadPropertyInfoV2): Option[SmartIri] = {
            // Is this a resource property?
            if (readPropertyInfo.isResourceProp) {
                // Yes. Infer knora-api:subjectType knora-api:Resource.
                Some(OntologyConstants.KnoraApiV2Simple.Resource.toSmartIri)
            } else {
                // It's not a resource property. Use the knora-api:subjectType that the ontology responder provided, if any.
                readPropertyInfo.entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2Simple.SubjectType.toSmartIri).
                    orElse(readPropertyInfo.entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri))
            }
        }

        /**
          * Given a [[ReadPropertyInfoV2]], returns the IRI of the inferred knora-api:objectType of the property, if any.
          *
          * @param readPropertyInfo the property definition.
          * @return the IRI of the inferred knora-api:objectType of the property, or `None` if it could not inferred.
          */
        def readPropertyInfoToObjectType(readPropertyInfo: ReadPropertyInfoV2): Option[SmartIri] = {
            // Is this a link property?
            if (readPropertyInfo.isLinkProp) {
                // Yes. Infer knora-api:objectType knora-api:Resource.
                Some(OntologyConstants.KnoraApiV2Simple.Resource.toSmartIri)
            } else {
                // It's not a link property. Use the knora-api:objectType that the ontology responder provided, if any.
                readPropertyInfo.entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2Simple.ObjectType.toSmartIri).
                    orElse(readPropertyInfo.entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri))
            }
        }
    }

    // The inference rule pipeline.
    private val rulePipeline = new RdfTypeRule(
        Some(new PropertyIriObjectTypeRule(
            Some(new TypeOfObjectFromPropertyRule(
                Some(new TypeOfSubjectFromPropertyRule(
                    Some(new PropertyVarTypeFromObjectRule(
                        Some(new PropertyVarTypeFromFilterRule(None)))))))))))

    /**
      * An index of entity usage in a Gravsearch query.
      *
      * @param knoraClasses               the Knora class IRIs that are used in the query.
      * @param knoraProperties            the Knora property IRIs that are used in the query.
      * @param subjects                   a map of all statement subjects to the statements they occur in.
      * @param predicates                 map of all statement predicates to the statements they occur in.
      * @param objects                    a map of all statement objects to the statements they occur in.
      * @param compareExpressionVariables a map of query variables to Knora entity IRIs (which must be property IRIs)
      *                                   that they are compared to in FILTER expressions.
      */
    private case class UsageIndex(knoraClasses: Set[SmartIri],
                                  knoraProperties: Set[SmartIri],
                                  subjects: Map[TypeableEntity, Set[StatementPattern]],
                                  predicates: Map[TypeableEntity, Set[StatementPattern]],
                                  objects: Map[TypeableEntity, Set[StatementPattern]],
                                  compareExpressionVariables: Map[TypeableVariable, Set[SmartIri]])

    /**
      * A [[WhereTransformer]] that returns statements and filters unchanged.
      */
    private class NoOpWhereTransformer extends WhereTransformer {
        override def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[QueryPattern] = Seq(statementPattern)

        override def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)
    }

    override def inspectTypes(previousResult: IntermediateTypeInspectionResult,
                              whereClause: WhereClause,
                              requestingUser: UserADM): Future[IntermediateTypeInspectionResult] = {
        log.debug("========== Starting type inference ==========")

        for {
            // Make an index of entity usage in the query.
            usageIndex <- Future(makeUsageIndex(whereClause))

            // Ask the ontology responder about all the Knora class and property IRIs mentioned in the query.

            entityInfoRequest = EntityInfoGetRequestV2(
                classIris = usageIndex.knoraClasses,
                propertyIris = usageIndex.knoraProperties,
                requestingUser = requestingUser)

            entityInfo: EntityInfoGetResponseV2 <- (responderManager ? entityInfoRequest).mapTo[EntityInfoGetResponseV2]

            // The ontology responder may return the requested information in the internal schema. Convert each entity
            // definition back to the input schema.

            entityInfoInInputSchemas = EntityInfoGetResponseV2(
                classInfoMap = usageIndex.knoraClasses.flatMap {
                    inputClassIri =>
                        val inputSchema = inputClassIri.getOntologySchema.get match {
                            case apiV2Schema: ApiV2Schema => apiV2Schema
                            case _ => throw GravsearchException(s"Invalid schema in IRI $inputClassIri")
                        }

                        val maybeReadClassInfo: Option[ReadClassInfoV2] = entityInfo.classInfoMap.get(inputClassIri).orElse {
                            entityInfo.classInfoMap.get(inputClassIri.toOntologySchema(InternalSchema))
                        }

                        maybeReadClassInfo.map {
                            readClassInfo => inputClassIri -> readClassInfo.toOntologySchema(inputSchema)
                        }
                }.toMap,
                propertyInfoMap = usageIndex.knoraProperties.flatMap {
                    inputPropertyIri =>
                        val inputSchema = inputPropertyIri.getOntologySchema.get match {
                            case apiV2Schema: ApiV2Schema => apiV2Schema
                            case _ => throw GravsearchException(s"Invalid schema in IRI $inputPropertyIri")
                        }


                        val maybeReadPropertyInfo: Option[ReadPropertyInfoV2] = entityInfo.propertyInfoMap.get(inputPropertyIri).orElse {
                            entityInfo.propertyInfoMap.get(inputPropertyIri.toOntologySchema(InternalSchema))
                        }

                        maybeReadPropertyInfo.map {
                            readPropertyInfo => inputPropertyIri -> readPropertyInfo.toOntologySchema(inputSchema)
                        }
                }.toMap
            )

            // Iterate over the inference rules until no new type information can be inferred.
            intermediateResult = doIterations(
                previousInspectionResult = previousResult,
                entityInfo = entityInfoInInputSchemas,
                usageIndex = usageIndex
            )

            // _ = println(s"Intermediate result from inferring inspector: $intermediateResult")

            // Pass the intermediate result to the next type inspector in the pipeline.
            lastResult <- runNextInspector(
                intermediateResult = intermediateResult,
                whereClause = whereClause,
                requestingUser = requestingUser
            )
        } yield lastResult
    }

    /**
      * Runs all the inference rules repeatedly until no new type information can be found.
      *
      * @param previousInspectionResult the result of the previous type inspection.
      * @param entityInfo               information about Knora ontology entities mentioned in the Gravsearch query.
      * @param usageIndex               an index of entity usage in the query.
      * @return a new intermediate result.
      */
    private def doIterations(previousInspectionResult: IntermediateTypeInspectionResult,
                             entityInfo: EntityInfoGetResponseV2,
                             usageIndex: UsageIndex): IntermediateTypeInspectionResult = {
        var iterationResult = previousInspectionResult
        var iterate = true
        var iterationNumber = 1

        while (iterate) {
            if (iterationNumber > MAX_ITERATIONS) {
                throw GravsearchException(s"Too many type inference iterations")
            }

            // Run an iteration of type inference and get its result.

            log.debug(s"****** Inference iteration $iterationNumber (${iterationResult.untypedEntities.size} untyped entities remaining)")

            val newTypesFound: Map[TypeableEntity, GravsearchEntityTypeInfo] = doIteration(
                previousIterationResult = iterationResult,
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )

            // If no new type information was found, stop.
            if (newTypesFound.isEmpty) {
                log.debug("No new information, stopping iterations")
                iterate = false
            } else {
                // Otherwise, add the new information to the results of the previous iterations.
                iterationResult = IntermediateTypeInspectionResult(
                    typedEntities = iterationResult.typedEntities ++ newTypesFound,
                    untypedEntities = iterationResult.untypedEntities -- newTypesFound.keySet
                )

                // If there are no untyped entities left, stop.
                if (iterationResult.untypedEntities.isEmpty) {
                    log.debug("Type inference complete")
                    iterate = false
                } else {
                    // Otherwise, do another iteration.
                    iterationNumber += 1
                }
            }
        }

        iterationResult
    }

    /**
      * Runs all the type inference rules for each untyped entity.
      *
      * @param previousIterationResult the result of the previous iteration of type inference.
      * @param entityInfo              information about Knora ontology entities mentioned in the Gravsearch query.
      * @param usageIndex              an index of entity usage in the query.
      * @return the new type information that was found in this iteration, or empty map if none was found.
      */
    private def doIteration(previousIterationResult: IntermediateTypeInspectionResult,
                            entityInfo: EntityInfoGetResponseV2,
                            usageIndex: UsageIndex): Map[TypeableEntity, GravsearchEntityTypeInfo] = {
        // Start the iteration with an empty map of new type information.
        val newTypesFound: mutable.Map[TypeableEntity, GravsearchEntityTypeInfo] = mutable.Map.empty

        // Run the inference rule pipeline for each entity that is still untyped.
        for (untypedEntity <- previousIterationResult.untypedEntities) {
            val inferenceRuleResult: Option[GravsearchEntityTypeInfo] = rulePipeline.infer(
                untypedEntity = untypedEntity,
                previousIterationResult = previousIterationResult,
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )

            // If the rule pipeline found type information about the entity, add it to the results of the iteration.
            inferenceRuleResult match {
                case Some(result) => newTypesFound.put(untypedEntity, result)
                case None => ()
            }
        }

        // Return the results of the iteration.
        newTypesFound.toMap
    }

    /**
      * Makes an index of entity usage in the query.
      *
      * @param whereClause the WHERE clause in the query.
      * @return an index of entity usage in the query.
      */
    private def makeUsageIndex(whereClause: WhereClause): UsageIndex = {
        // Flatten the statements and filters in the WHERE clause into a sequence.
        val flattenedPatterns: Seq[QueryPattern] = QueryTraverser.transformWherePatterns(
            patterns = whereClause.patterns,
            inputOrderBy = Seq.empty[OrderCriterion],
            whereTransformer = new NoOpWhereTransformer,
            rebuildStructure = false
        )

        // Make mutable association lists to collect the index in.
        val knoraClasses: mutable.Set[SmartIri] = mutable.Set.empty
        val knoraProperties: mutable.Set[SmartIri] = mutable.Set.empty
        val subjectEntitiesBuffer: mutable.ArrayBuffer[(TypeableEntity, StatementPattern)] = mutable.ArrayBuffer.empty[(TypeableEntity, StatementPattern)]
        val predicateEntitiesBuffer: mutable.ArrayBuffer[(TypeableEntity, StatementPattern)] = mutable.ArrayBuffer.empty[(TypeableEntity, StatementPattern)]
        val objectEntitiesBuffer: mutable.ArrayBuffer[(TypeableEntity, StatementPattern)] = mutable.ArrayBuffer.empty[(TypeableEntity, StatementPattern)]
        val compareExpressionVariablesBuffer: mutable.ArrayBuffer[(TypeableVariable, SmartIri)] = mutable.ArrayBuffer.empty[(TypeableVariable, SmartIri)]

        // Iterate over the sequence of patterns, indexing their contents.
        for (pattern <- flattenedPatterns) {
            pattern match {
                case statement: StatementPattern =>
                    // Make index entries for the statement's subject, predicate, and object, and add them to the buffers.
                    subjectEntitiesBuffer.appendAll(maybeIndexEntry(statement.subj, statement))
                    predicateEntitiesBuffer.appendAll(maybeIndexEntry(statement.pred, statement))
                    objectEntitiesBuffer.appendAll(maybeIndexEntry(statement.obj, statement))

                    statement.pred match {
                        case IriRef(predIri, _) =>
                            // The statement's predicate is an IRI. Is it rdf:type with an IRI as an object?
                            if (predIri.toString == OntologyConstants.Rdf.Type) {
                                statement.obj match {
                                    case IriRef(objIri, _) if objIri.isKnoraEntityIri =>
                                        // Yes. Add it to the set of Knora class IRIs that we'll ask the ontology responder about.
                                        knoraClasses.add(objIri)

                                    case _ => ()
                                }
                            } else {
                                // The predicate is not rdf:type. If it's a Knora property, add it to the set of Knora property IRIs
                                // that we'll ask the ontology responder about.
                                if (predIri.isKnoraEntityIri) {
                                    knoraProperties.add(predIri)
                                }
                            }

                        case _ => ()
                    }

                case filter: FilterPattern =>
                    // Collect the variables and Knora property IRIs that are compared using EQUALS in the filter expression.
                    collectIriCompareExpressions(filter.expression, compareExpressionVariablesBuffer)

                case _ => ()
            }
        }

        // Add the Knora property IRIs collected from filters to the set of Knora property IRIs in the query.
        knoraProperties ++= compareExpressionVariablesBuffer.map(_._2)

        // Construct the index from the contents of the association lists.
        UsageIndex(
            knoraClasses = Set(knoraClasses.toSeq: _*),
            knoraProperties = Set(knoraProperties.toSeq: _*),
            subjects = associationListToMap(subjectEntitiesBuffer),
            predicates = associationListToMap(predicateEntitiesBuffer),
            objects = associationListToMap(objectEntitiesBuffer),
            compareExpressionVariables = associationListToMap(compareExpressionVariablesBuffer)
        )
    }

    /**
      * Given a filter expression, collects the variables and Knora entity IRIs (which must be property IRIs) that are
      * compared using the EQUALS operator.
      *
      * @param filterExpression the filter expression.
      * @param buffer a buffer in which to collect the results.
      */
    private def collectIriCompareExpressions(filterExpression: Expression, buffer: mutable.ArrayBuffer[(TypeableVariable, SmartIri)]): Unit  = {
        filterExpression match {
            case compareExpr: CompareExpression =>
                compareExpr match {
                    case CompareExpression(queryVariable: QueryVariable, operator: CompareExpressionOperator.Value, iriRef: IriRef)
                        if operator == CompareExpressionOperator.EQUALS && iriRef.iri.isKnoraEntityIri =>
                        buffer.append(TypeableVariable(queryVariable.variableName) -> iriRef.iri)

                    case _ =>
                        collectIriCompareExpressions(compareExpr.leftArg, buffer)
                        collectIriCompareExpressions(compareExpr.rightArg, buffer)
                }

            case andExpr: AndExpression =>
                collectIriCompareExpressions(andExpr.leftArg, buffer)
                collectIriCompareExpressions(andExpr.rightArg, buffer)

            case orExpr: OrExpression =>
                collectIriCompareExpressions(orExpr.leftArg, buffer)
                collectIriCompareExpressions(orExpr.rightArg, buffer)

            case _ => ()
        }
    }

    /**
      * Given a statement and an entity in the statement, checks whether the entity is typeable, and makes an index
      * entry if so.
      *
      * @param statementEntity the entity (subject, predicate, or object).
      * @param statement       the statement.
      * @return an index entry for the statement, or `None` if the entity isn't typeable.
      */
    private def maybeIndexEntry(statementEntity: Entity, statement: StatementPattern): Option[(TypeableEntity, StatementPattern)] = {
        GravsearchTypeInspectionUtil.maybeTypeableEntity(statementEntity).map {
            typeableEntity => typeableEntity -> statement
        }
    }

    /**
      * Converts an association list to a map of keys to sets of values.
      *
      * @param seq the association list.
      * @tparam K the type of the keys.
      * @tparam V the type of the values.
      * @return a map of keys to sets of values.
      */
    private def associationListToMap[K, V](seq: Seq[(K, V)]): Map[K, Set[V]] = {
        seq.groupBy {
            case (key, _) => key
        }.map {
            case (key, values) =>
                key -> values.map {
                    case (_, value) => value
                }.toSet
        }
    }
}

object InferringGravsearchTypeInspector {
    /**
      * Provides the string representation of the companion class in log messages.
      *
      * See [[https://doc.akka.io/docs/akka/current/logging.html#translating-log-source-to-string-and-class]].
      */
    implicit val logSource: LogSource[AnyRef] = (o: AnyRef) => o.getClass.getName
}
