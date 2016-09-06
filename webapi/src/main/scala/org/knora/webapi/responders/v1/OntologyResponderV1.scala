/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.responders.v1

import akka.actor.Status
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.v1.responder.resourcemessages.SalsahGuiConversions
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util._

import scala.collection.breakOut
import scala.concurrent.Future

/**
  * Handles requests for information about ontology entities.
  *
  * The results of these requests are cached to improve performance. To clear the cache, you currently have to restart
  * the application. When updates are implemented in this responder, it will clear its cache on each update.
  */
class OntologyResponderV1 extends ResponderV1 {

    private val OntologyCacheName = "ontologyCache"
    private val knoraIdUtil = new KnoraIdUtil
    private val valueUtilV1 = new ValueUtilV1(settings)

    /**
      * Receives a message extending [[OntologyResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case LoadOntologiesRequest() => future2Message(sender(), loadOntologies(), log)
        case EntityInfoGetRequestV1(resourceIris, propertyIris, userProfile) => future2Message(sender(), getEntityInfoResponseV1(resourceIris, propertyIris, userProfile), log)
        case ResourceTypeGetRequestV1(resourceTypeIri, userProfile) => future2Message(sender(), getResourceTypeResponseV1(resourceTypeIri, userProfile), log)
        case checkSubClassRequest: CheckSubClassRequestV1 => future2Message(sender(), checkSubClass(checkSubClassRequest), log)
        case NamedGraphsGetRequestV1(userProfile) => future2Message(sender(), getNamedGraphs(userProfile), log)
        case ResourceTypesForNamedGraphGetRequestV1(namedGraphIri, userProfile) => future2Message(sender(), getResourceTypesForNamedGraph(namedGraphIri, userProfile), log)
        case PropertyTypesForNamedGraphGetRequestV1(namedGraphIri, userProfile) => future2Message(sender(), getPropertyTypesForNamedGraph(namedGraphIri, userProfile), log)
        case PropertyTypesForResourceTypeGetRequestV1(restypeId, userProfile) => future2Message(sender(), getPropertyTypesForResourceType(restypeId, userProfile), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Loads and caches all ontology information.
      * @return a [[LoadOntologiesResponse]].
      */
    private def loadOntologies(): Future[LoadOntologiesResponse] = {
        case class OwlCardinality(propertyIri: IRI, cardinalityIri: IRI, cardinalityValue: Int, isLinkProp: Boolean, isLinkValueProp: Boolean, isFileValueProp: Boolean)

        /**
          * Recursively walks up an entity hierarchy, collecting the IRIs of all base entities.
          *
          * @param iri             the IRI of an entity.
          * @param directRelations a map of entities to their direct base entities.
          * @return all the base entities of the specified entity.
          */
        def getAllBaseDefs(iri: IRI, directRelations: Map[IRI, Set[IRI]]): Set[IRI] = {
            directRelations.get(iri) match {
                case Some(baseDefs) =>
                    baseDefs ++ baseDefs.flatMap(baseDef => getAllBaseDefs(baseDef, directRelations))

                case None => Set.empty[IRI]
            }
        }

        /**
          * Given a resource class, recursively adds its inherited cardinalities to the cardinalities it defines
          * directly. A cardinality for a subproperty in a subclass overrides a cardinality for a base property in
          * a base class.
          *
          * @param resourceClassIri                 the IRI of the resource class whose properties are to be computed.
          * @param directSubClassRelations          a map of the cardinalities defined directly on each resource class.
          * @param allSubPropertyRelations          a map in which each property IRI points to the full set of its base properties.
          * @param directResourceClassCardinalities a map of the cardinalities defined directly on each resource class.
          * @return the IRIs of the properties that have cardinalities on the resource class, or that it inherits
          *         from its base classes.
          */
        def inheritCardinalities(resourceClassIri: IRI,
                                 directSubClassRelations: Map[IRI, Set[IRI]],
                                 allSubPropertyRelations: Map[IRI, Set[IRI]],
                                 directResourceClassCardinalities: Map[IRI, Map[IRI, OwlCardinality]]): Map[IRI, OwlCardinality] = {
            // Recursively get properties that are available to inherit from base classes.
            val cardinalitiesAvailableToInherit: Map[IRI, OwlCardinality] = directSubClassRelations(resourceClassIri).foldLeft(Map.empty[IRI, OwlCardinality]) {
                case (acc, baseClass) =>
                    acc ++ inheritCardinalities(
                        resourceClassIri = baseClass,
                        directSubClassRelations = directSubClassRelations,
                        allSubPropertyRelations = allSubPropertyRelations,
                        directResourceClassCardinalities = directResourceClassCardinalities
                    )
            }

            // Get the properties that have cardinalities defined directly on this class.
            val thisClassCardinalities: Map[IRI, OwlCardinality] = directResourceClassCardinalities(resourceClassIri)

            // From the properties that are available to inherit, filter out the ones that are overridden by properties
            // with cardinalities defined directly on this class.
            val inheritedCardinalities: Map[IRI, OwlCardinality] = cardinalitiesAvailableToInherit.filterNot {
                case (baseClassProp, baseClassCardinality) => thisClassCardinalities.exists {
                    case (thisClassProp, cardinality) => allSubPropertyRelations(thisClassProp).contains(baseClassProp)
                }
            }

            // Add the inherited properties to the ones with directly defined cardinalities.
            thisClassCardinalities ++ inheritedCardinalities
        }

        for {
        // Get all resource class definitions.
            resourceDefsSparql <- Future(queries.sparql.v1.txt.getResourceClassDefinitions(triplestore = settings.triplestoreType).toString())
            resourceDefsResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(resourceDefsSparql)).mapTo[SparqlSelectResponse]
            resourceDefsRows: Seq[VariableResultsRow] = resourceDefsResponse.results.bindings

            // Get the value class hierarchy.
            valueClassesSparql = queries.sparql.v1.txt.getValueClassHierarchy(triplestore = settings.triplestoreType).toString()
            valueClassesResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(valueClassesSparql)).mapTo[SparqlSelectResponse]
            valueClassesRows: Seq[VariableResultsRow] = valueClassesResponse.results.bindings

            // Get all property definitions.
            propertyDefsSparql = queries.sparql.v1.txt.getPropertyDefinitions(triplestore = settings.triplestoreType).toString()
            propertyDefsResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(propertyDefsSparql)).mapTo[SparqlSelectResponse]
            propertyDefsRows: Seq[VariableResultsRow] = propertyDefsResponse.results.bindings

            // Make a map of IRIs of named graphs to IRIs of resource classes defined in each one.
            graphClassMap: Map[IRI, Set[IRI]] = resourceDefsRows.groupBy(_.rowMap("graph")).map {
                case (graphIri: IRI, graphRows: Seq[VariableResultsRow]) =>
                    graphIri -> graphRows.map(_.rowMap("resourceClass")).toSet
            }

            // Make a map of IRIs of named graphs to IRIs of properties defined in each one.
            graphPropMap: Map[IRI, Set[IRI]] = propertyDefsRows.groupBy(_.rowMap("graph")).map {
                case (graphIri, graphRows) =>
                    graphIri -> graphRows.map(_.rowMap("prop")).toSet
            }

            // Group the rows representing resource class definitions by resource class IRI.
            resourceDefsGrouped: Map[IRI, Seq[VariableResultsRow]] = resourceDefsRows.groupBy(_.rowMap("resourceClass"))
            resourceClassIris = resourceDefsGrouped.keySet

            // Group the rows representing property definitions by property IRI.
            propertyDefsGrouped: Map[IRI, Seq[VariableResultsRow]] = propertyDefsRows.groupBy(_.rowMap("prop"))
            propertyIris = propertyDefsGrouped.keySet

            // Group the rows representing value class relations by value class IRI.
            valueClassesGrouped: Map[IRI, Seq[VariableResultsRow]] = valueClassesRows.groupBy(_.rowMap("valueClass"))

            // Make a map of resource class IRIs to their immediate base classes.
            directResourceSubClassRelations: Map[IRI, Set[IRI]] = resourceDefsGrouped.map {
                case (resourceClassIri, rows) =>
                    val baseClasses = rows.filter(_.rowMap.get("resourceClassPred").contains(OntologyConstants.Rdfs.SubClassOf)).map(_.rowMap("resourceClassObj")).toSet
                    (resourceClassIri, baseClasses)
            }

            // Make a map of property IRIs to their immediate base properties.
            directSubPropertyRelations: Map[IRI, Set[IRI]] = propertyDefsGrouped.map {
                case (propertyIri, rows) =>
                    val baseProperties = rows.filter(_.rowMap.get("propPred").contains(OntologyConstants.Rdfs.SubPropertyOf)).map(_.rowMap("propObj")).toSet
                    (propertyIri, baseProperties)
            }

            // Make a map in which each resource class IRI points to the full set of its base classes. A class is also
            // a subclass of itself.
            allResourceSubClassRelations: Map[IRI, Set[IRI]] = resourceClassIris.map {
                resourceClassIri => (resourceClassIri, getAllBaseDefs(resourceClassIri, directResourceSubClassRelations) + resourceClassIri)
            }.toMap

            // Make a map in which each property IRI points to the full set of its base properties. A property is also
            // a subproperty of itself.
            allSubPropertyRelations: Map[IRI, Set[IRI]] = propertyIris.map {
                propertyIri => (propertyIri, getAllBaseDefs(propertyIri, directSubPropertyRelations) + propertyIri)
            }.toMap

            // Make a map in which each value class IRI points to the full set of its base classes (excluding the ones
            // whose names end in "Base", since they aren't used directly). A class is also a subclass of itself (this
            // is handled by the SPARQL query).
            allValueSubClassRelations: Map[IRI, Set[IRI]] = valueClassesGrouped.map {
                case (valueClassIri, baseClassRows) =>
                    valueClassIri -> baseClassRows.map(_.rowMap("baseClass")).filterNot(_.endsWith("Base")).toSet
            }

            // Make a set of all subproperties of knora-base:hasLinkTo.
            linkProps: Set[IRI] = propertyIris.filter(prop => allSubPropertyRelations(prop).contains(OntologyConstants.KnoraBase.HasLinkTo))

            // Make a set of all subproperties of knora-base:hasLinkToValue.
            linkValueProps: Set[IRI] = propertyIris.filter(prop => allSubPropertyRelations(prop).contains(OntologyConstants.KnoraBase.HasLinkToValue))

            // Make a set of all subproperties of knora-base:hasFileValue.
            fileValueProps: Set[IRI] = propertyIris.filter(prop => allSubPropertyRelations(prop).contains(OntologyConstants.KnoraBase.HasFileValue))

            // Make a map of the cardinalities defined directly on each resource class. Each resource class IRI points to a map of
            // property IRIs to OwlCardinality objects.
            directResourceClassCardinalities: Map[IRI, Map[IRI, OwlCardinality]] = resourceDefsGrouped.map {
                case (resourceClassIri, rows) =>
                    val resourceClassCardinalities: Map[IRI, OwlCardinality] = rows.filter(_.rowMap.contains("cardinalityProp")).map {
                        cardinalityRow =>
                            val cardinalityRowMap = cardinalityRow.rowMap
                            val propertyIri = cardinalityRowMap("cardinalityProp")

                            val owlCardinality = OwlCardinality(
                                propertyIri = propertyIri,
                                cardinalityIri = cardinalityRowMap("cardinality"),
                                cardinalityValue = cardinalityRowMap("cardinalityVal").toInt,
                                isLinkProp = linkProps.contains(propertyIri),
                                isLinkValueProp = linkValueProps.contains(propertyIri),
                                isFileValueProp = fileValueProps.contains(propertyIri)
                            )

                            propertyIri -> owlCardinality
                    }.toMap

                    resourceClassIri -> resourceClassCardinalities
            }

            // Allow each resource class to inherit cardinalities from its base classes.
            resourceCardinalitiesWithInheritance: Map[IRI, Set[OwlCardinality]] = resourceClassIris.map {
                resourceClassIri =>
                    val resourceClassCardinalities: Set[OwlCardinality] = inheritCardinalities(
                        resourceClassIri = resourceClassIri,
                        directSubClassRelations = directResourceSubClassRelations,
                        allSubPropertyRelations = allSubPropertyRelations,
                        directResourceClassCardinalities = directResourceClassCardinalities
                    ).values.toSet

                    resourceClassIri -> resourceClassCardinalities
            }.toMap

            // Construct a ResourceEntityInfoV1 for each resource class.
            resourceEntityInfos: Map[IRI, ResourceEntityInfoV1] = resourceDefsGrouped.map {
                case (resourceClassIri, resourceClassRows) =>
                    // Group the rows for each resource class by predicate IRI.
                    val groupedByPredicate: Map[IRI, Seq[VariableResultsRow]] = resourceClassRows.filter(_.rowMap.contains("resourceClassPred")).groupBy(_.rowMap("resourceClassPred")) - OntologyConstants.Rdfs.SubClassOf

                    val predicates: Map[IRI, PredicateInfoV1] = groupedByPredicate.map {
                        case (predicateIri, predicateRows) =>
                            val (predicateRowsWithLang, predicateRowsWithoutLang) = predicateRows.partition(_.rowMap.contains("resourceClassObjLang"))
                            val objects = predicateRowsWithoutLang.map(_.rowMap("resourceClassObj")).toSet
                            val objectsWithLang = predicateRowsWithLang.map {
                                predicateRow => predicateRow.rowMap("resourceClassObjLang") -> predicateRow.rowMap("resourceClassObj")
                            }.toMap

                            predicateIri -> PredicateInfoV1(
                                predicateIri = predicateIri,
                                ontologyIri = getOntologyIri(resourceClassIri),
                                objects = objects,
                                objectsWithLang = objectsWithLang
                            )
                    }

                    // Get the OWL cardinalities for the class.
                    val owlCardinalities = resourceCardinalitiesWithInheritance(resourceClassIri)

                    // Identify the link properties, like value properties, and file value properties in the cardinalities.
                    val linkProps = owlCardinalities.filter(_.isLinkProp).map(_.propertyIri)
                    val linkValueProps = owlCardinalities.filter(_.isLinkValueProp).map(_.propertyIri)
                    val fileValueProps = owlCardinalities.filter(_.isFileValueProp).map(_.propertyIri)

                    // Make sure there is a link value property for each link property.
                    val missingLinkValueProps = linkProps.map(linkProp => knoraIdUtil.linkPropertyIriToLinkValuePropertyIri(linkProp)) -- linkValueProps
                    if (missingLinkValueProps.nonEmpty) {
                        throw InconsistentTriplestoreDataException(s"Resource class $resourceClassIri has cardinalities for one or more link properties without corresponding link value properties. The missing link value property or properties: ${missingLinkValueProps.mkString(", ")}")
                    }

                    // Make sure there is a link property for each link value property.
                    val missingLinkProps = linkValueProps.map(linkValueProp => knoraIdUtil.linkValuePropertyIri2LinkPropertyIri(linkValueProp)) -- linkProps
                    if (missingLinkProps.nonEmpty) {
                        throw InconsistentTriplestoreDataException(s"Resource class $resourceClassIri has cardinalities for one or more link value properties without corresponding link properties. The missing link property or properties: ${missingLinkProps.mkString(", ")}")
                    }

                    val resourceEntityInfo = ResourceEntityInfoV1(
                        resourceClassIri = resourceClassIri,
                        predicates = new ErrorHandlingMap(predicates, { key: IRI => s"Predicate $key not found for resource class $resourceClassIri" }),
                        cardinalities = owlCardinalities.map {
                            owlCardinality =>
                                // Convert the OWL cardinality to a Knora Cardinality enum value.
                                owlCardinality.propertyIri -> Cardinality.owlCardinality2KnoraCardinality(
                                    propertyIri = owlCardinality.propertyIri,
                                    owlCardinalityIri = owlCardinality.cardinalityIri,
                                    owlCardinalityValue = owlCardinality.cardinalityValue
                                )
                        }.toMap,
                        linkProperties = linkProps,
                        linkValueProperties = linkValueProps,
                        fileValueProperties = fileValueProps
                    )

                    resourceClassIri -> resourceEntityInfo
            }

            // Construct a PropertyEntityInfoV1 for each property definition.
            propertyEntityInfos: Map[String, PropertyEntityInfoV1] = propertyDefsGrouped.map {
                case (propertyIri, propertyRows) =>
                    // Group the rows for each resource class by predicate IRI.
                    val groupedByPredicate: Map[IRI, Seq[VariableResultsRow]] = propertyRows.groupBy(_.rowMap("propPred")) - OntologyConstants.Rdfs.SubPropertyOf

                    val predicates: Map[IRI, PredicateInfoV1] = groupedByPredicate.map {
                        case (predicateIri, predicateRows) =>
                            val (predicateRowsWithLang, predicateRowsWithoutLang) = predicateRows.partition(_.rowMap.contains("propObjLang"))
                            val objects = predicateRowsWithoutLang.map(_.rowMap("propObj")).toSet
                            val objectsWithLang = predicateRowsWithLang.map {
                                predicateRow => predicateRow.rowMap("propObjLang") -> predicateRow.rowMap("propObj")
                            }.toMap

                            predicateIri -> PredicateInfoV1(
                                predicateIri = predicateIri,
                                ontologyIri = getOntologyIri(propertyIri),
                                objects = objects,
                                objectsWithLang = objectsWithLang
                            )
                    }

                    val propertyEntityInfo = PropertyEntityInfoV1(
                        propertyIri = propertyIri,
                        isLinkProp = linkProps.contains(propertyIri),
                        isLinkValueProp = linkValueProps.contains(propertyIri),
                        isFileValueProp = fileValueProps.contains(propertyIri),
                        predicates = predicates
                    )

                    propertyIri -> propertyEntityInfo
            }

            // Populate the ontology cache. TODO: Use a proper cache.
            _ = OntologyCache.populateCache(
                namedGraphResourceClasses = graphClassMap,
                namedGraphProperties = graphPropMap,
                resourceClassDefs = resourceEntityInfos,
                subClassRelations = allResourceSubClassRelations ++ allValueSubClassRelations,
                propertyDefs = propertyEntityInfos
            )
        } yield LoadOntologiesResponse()
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
      * Gets the IRI of the ontology that an entity belongs to, assuming that the entity IRI has the form `http://something.something#entityName`.
      * The ontology IRI is assumed to be the part before the `#`. TODO: is this adequate?
      *
      * @param entityIri the entity IRI (e.g. `http://www.knora.org/ontology/incunabula#publisher`).
      * @return the part before the `#`.
      */
    private def getOntologyIri(entityIri: IRI): String = {
        entityIri.split("#")(0)
    }

    /**
      * Given a list of resource IRIs and a list of property IRIs (ontology entities), returns an [[EntityInfoGetResponseV1]] describing both resource and property entities.
      *
      * @param resourceIris the IRIs of the resource entities to be queried.
      * @param propertyIris the IRIs of the property entities to be queried.
      * @param userProfile  the profile of the user making the request.
      * @return an [[EntityInfoGetResponseV1]].
      */
    private def getEntityInfoResponseV1(resourceIris: Set[IRI] = Set.empty[IRI], propertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserProfileV1): Future[EntityInfoGetResponseV1] = {
        Future(
            EntityInfoGetResponseV1(
                resourceEntityInfoMap = OntologyCache.resourceClassDefs.filterKeys(resourceIris),
                propertyEntityInfoMap = OntologyCache.propertyDefs.filterKeys(propertyIris)
            )
        )
    }


    /**
      * Given the IRI of a resource type, returns a [[ResourceTypeResponseV1]] describing the resource type and its possible
      * properties.
      *
      * @param resourceTypeIri the IRI of the resource type to be queried.
      * @param userProfile     the profile of the user making the request.
      * @return a [[ResourceTypeResponseV1]].
      */
    private def getResourceTypeResponseV1(resourceTypeIri: String, userProfile: UserProfileV1): Future[ResourceTypeResponseV1] = {

        for {
        // Get all information about the resource type, including its property cardinalities.
            resourceClassInfoResponse: EntityInfoGetResponseV1 <- getEntityInfoResponseV1(resourceIris = Set(resourceTypeIri), userProfile = userProfile)
            resourceClassInfo: ResourceEntityInfoV1 = resourceClassInfoResponse.resourceEntityInfoMap(resourceTypeIri)

            // Get all information about those properties.
            propertyInfo: EntityInfoGetResponseV1 <- getEntityInfoResponseV1(propertyIris = resourceClassInfo.cardinalities.keySet, userProfile = userProfile)

            // Build the property definitions.
            propertyDefinitions: Set[PropertyDefinitionV1] = resourceClassInfo.cardinalities.filterNot {
                // filter out the properties that point to LinkValue objects
                case (propertyIri, cardinality) =>
                    resourceClassInfo.linkValueProperties(propertyIri)
            }.map {
                case (propertyIri: IRI, cardinality: Cardinality.Value) =>
                    propertyInfo.propertyEntityInfoMap.get(propertyIri) match {
                        case Some(entityInfo: PropertyEntityInfoV1) =>

                            if (entityInfo.isLinkProp) {
                                // It is a linking prop: its valuetype_id is knora-base:LinkValue.
                                // It is restricted to the resource class that is given for knora-base:objectClassConstraint
                                // for the given property which goes in the attributes that will be read by the GUI.

                                PropertyDefinitionV1(
                                    id = propertyIri,
                                    name = propertyIri,
                                    label = entityInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(userProfile.userData.lang, settings.fallbackLanguage)),
                                    description = entityInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Comment, preferredLangs = Some(userProfile.userData.lang, settings.fallbackLanguage)),
                                    vocabulary = entityInfo.predicates.values.head.ontologyIri,
                                    occurrence = cardinality.toString,
                                    valuetype_id = OntologyConstants.KnoraBase.LinkValue,
                                    attributes = valueUtilV1.makeAttributeString(entityInfo.getPredicateObjects(OntologyConstants.SalsahGui.GuiAttribute) + valueUtilV1.makeAttributeRestype(entityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse(throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")))),
                                    gui_name = entityInfo.getPredicateObject(OntologyConstants.SalsahGui.GuiElement).map(iri => SalsahGuiConversions.iri2SalsahGuiElement(iri))
                                )

                            } else {

                                PropertyDefinitionV1(
                                    id = propertyIri,
                                    name = propertyIri,
                                    label = entityInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(userProfile.userData.lang, settings.fallbackLanguage)),
                                    description = entityInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Comment, preferredLangs = Some(userProfile.userData.lang, settings.fallbackLanguage)),
                                    vocabulary = entityInfo.predicates.values.head.ontologyIri,
                                    occurrence = cardinality.toString,
                                    valuetype_id = entityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse(throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")),
                                    attributes = valueUtilV1.makeAttributeString(entityInfo.getPredicateObjects(OntologyConstants.SalsahGui.GuiAttribute)),
                                    gui_name = entityInfo.getPredicateObject(OntologyConstants.SalsahGui.GuiElement).map(iri => SalsahGuiConversions.iri2SalsahGuiElement(iri))
                                )
                            }
                        case None =>
                            throw new InconsistentTriplestoreDataException(s"Resource type $resourceTypeIri is defined as having property $propertyIri, which doesn't exist")
                    }
            }(breakOut)

            // Build the API response.
            resourceTypeResponse = ResourceTypeResponseV1(
                restype_info = ResTypeInfoV1(
                    name = resourceTypeIri,
                    label = resourceClassInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(userProfile.userData.lang, settings.fallbackLanguage)),
                    description = resourceClassInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Comment, preferredLangs = Some(userProfile.userData.lang, settings.fallbackLanguage)),
                    iconsrc = resourceClassInfo.getPredicateObject(OntologyConstants.KnoraBase.ResourceIcon),
                    properties = propertyDefinitions
                ),
                userProfile.userData
            )
        } yield resourceTypeResponse
    }

    /**
      * Checks whether a certain Knora resource or value class is a subclass of another class.
      *
      * @param checkSubClassRequest a [[CheckSubClassRequestV1]]
      * @return a [[CheckSubClassResponseV1]].
      */
    private def checkSubClass(checkSubClassRequest: CheckSubClassRequestV1): Future[CheckSubClassResponseV1] = {
        Future(
            CheckSubClassResponseV1(
                isSubClass = OntologyCache.subClassRelations(checkSubClassRequest.subClassIri).contains(checkSubClassRequest.superClassIri)
            )
        )
    }

    /**
      * Returns all the existing named graphs as a [[NamedGraphsResponseV1]].
      *
      * @param userProfile the profile of the user making the request.
      * @return a [[NamedGraphsResponseV1]].
      */
    private def getNamedGraphs(userProfile: UserProfileV1): Future[NamedGraphsResponseV1] = {
        val namedGraphs: Vector[NamedGraphV1] = settings.namedGraphs.filter(_.visibleInGUI).map {
            (namedGraph: ProjectNamedGraphs) =>
                NamedGraphV1(
                    id = namedGraph.ontology,
                    shortname = namedGraph.name,
                    longname = namedGraph.name,
                    description = namedGraph.name,
                    project_id = namedGraph.project,
                    uri = namedGraph.ontology,
                    active = false
                )
        }

        Future(NamedGraphsResponseV1(
            vocabularies = namedGraphs,
            userdata = userProfile.userData
        ))
    }

    /**
      * Gets the [[NamedGraphEntityInfoV1]] for a named graph
      *
      * @param namedGraphIri the Iri of the named graph to query
      * @param userProfile   the profile of the user making the request.
      * @return a [[NamedGraphEntityInfoV1]].
      */
    private def getNamedGraphEntityInfoV1ForNamedGraph(namedGraphIri: IRI, userProfile: UserProfileV1): Future[NamedGraphEntityInfoV1] = {
        Future(
            NamedGraphEntityInfoV1(
                namedGraphIri = namedGraphIri,
                propertyIris = OntologyCache.namedGraphProperties(namedGraphIri),
                resourceClasses = OntologyCache.namedGraphResourceClasses(namedGraphIri)
            )
        )
    }

    /**
      * Gets all the resource classes and their properties for a named graph.
      *
      * @param namedGraphIriOption the Iri of the named graph or None if all the named graphs should be queried.
      * @param userProfile         the profile of the user making the request.
      * @return [[ResourceTypesForNamedGraphResponseV1]].
      */
    private def getResourceTypesForNamedGraph(namedGraphIriOption: Option[IRI], userProfile: UserProfileV1): Future[ResourceTypesForNamedGraphResponseV1] = {

        // get the resource types for a named graph
        def getResourceTypes(namedGraphIri: IRI): Future[Vector[ResourceTypeV1]] = {
            for {

            // get NamedGraphEntityInfoV1 for the given named graph
                namedGraphEntityInfo: NamedGraphEntityInfoV1 <- getNamedGraphEntityInfoV1ForNamedGraph(namedGraphIri, userProfile)

                // get resinfo for each resource class in namedGraphEntityInfo
                resInfosForNamedGraphFuture: Set[Future[(String, ResourceTypeResponseV1)]] = namedGraphEntityInfo.resourceClasses.map {
                    (resClassIri) =>
                        for {
                            resInfo <- getResourceTypeResponseV1(resClassIri, userProfile)
                        } yield (resClassIri, resInfo)
                }

                resInfosForNamedGraph: Set[(IRI, ResourceTypeResponseV1)] <- Future.sequence(resInfosForNamedGraphFuture)

                resourceTypes: Vector[ResourceTypeV1] = resInfosForNamedGraph.map {
                    case (resClassIri, resInfo) =>

                        val properties = resInfo.restype_info.properties.map {
                            (prop) => PropertyTypeV1(
                                id = prop.id,
                                label = prop.label.getOrElse(throw InconsistentTriplestoreDataException(s"No label given for ${prop.id}"))
                            )
                        }.toVector

                        ResourceTypeV1(
                            id = resClassIri,
                            label = resInfo.restype_info.label.getOrElse(throw InconsistentTriplestoreDataException(s"No label given for $resClassIri")),
                            properties = properties
                        )
                }.toVector

            } yield resourceTypes
        }

        // get resource types for named graph depending on given Iri-Option
        namedGraphIriOption match {
            case Some(namedGraphIri) => // get the resource types for the given named graph
                for {
                    resourceTypes <- getResourceTypes(namedGraphIri)
                } yield ResourceTypesForNamedGraphResponseV1(resourcetypes = resourceTypes, userdata = userProfile.userData)

            case None => // map over all named graphs and collect the resource types
                val resourceTypesFuture: Vector[Future[Vector[ResourceTypeV1]]] = settings.namedGraphs.filter(_.visibleInGUI).map {
                    namedGraphs: ProjectNamedGraphs => getResourceTypes(namedGraphs.ontology)
                }

                val sequencedFuture = Future.sequence(resourceTypesFuture)

                for {
                    resourceTypes <- sequencedFuture
                } yield ResourceTypesForNamedGraphResponseV1(resourcetypes = resourceTypes.flatten, userdata = userProfile.userData)
        }

    }

    /**
      * Gets the property types defined in the given named graph. If there is no named graph defined, get property types for all existing named graphs.
      *
      * @param namedGraphIriOption the Iri of the named graph or None if all the named graphs should be queried.
      * @param userProfile         the profile of the user making the request.
      * @return a [[PropertyTypesForNamedGraphResponseV1]].
      */
    private def getPropertyTypesForNamedGraph(namedGraphIriOption: Option[IRI], userProfile: UserProfileV1): Future[PropertyTypesForNamedGraphResponseV1] = {

        def getPropertiesForNamedGraph(namedGraphIri: IRI, userProfile: UserProfileV1): Future[Vector[PropertyDefinitionInNamedGraphV1]] = {
            for {
                namedGraphEntityInfo <- getNamedGraphEntityInfoV1ForNamedGraph(namedGraphIri, userProfile)
                propertyIris: Set[IRI] = namedGraphEntityInfo.propertyIris
                entities: EntityInfoGetResponseV1 <- getEntityInfoResponseV1(propertyIris = propertyIris, userProfile = userProfile)
                propertyEntityInfoMap: Map[IRI, PropertyEntityInfoV1] = entities.propertyEntityInfoMap.filterNot {
                    case (propertyIri, propertyEntityInfo) => propertyEntityInfo.isLinkValueProp
                }

                propertyDefinitions: Vector[PropertyDefinitionInNamedGraphV1] = propertyEntityInfoMap.map {
                    case (propertyIri: IRI, entityInfo: PropertyEntityInfoV1) =>

                        if (entityInfo.isLinkProp) {
                            // It is a linking prop: its valuetype_id is knora-base:LinkValue.
                            // It is restricted to the resource class that is given for knora-base:objectClassConstraint
                            // for the given property which goes in the attributes that will be read by the GUI.

                            PropertyDefinitionInNamedGraphV1(
                                id = propertyIri,
                                name = propertyIri,
                                label = entityInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(userProfile.userData.lang, settings.fallbackLanguage)),
                                description = entityInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Comment, preferredLangs = Some(userProfile.userData.lang, settings.fallbackLanguage)),
                                vocabulary = entityInfo.predicates.values.head.ontologyIri,
                                valuetype_id = OntologyConstants.KnoraBase.LinkValue,
                                attributes = valueUtilV1.makeAttributeString(entityInfo.getPredicateObjects(OntologyConstants.SalsahGui.GuiAttribute) + valueUtilV1.makeAttributeRestype(entityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse(throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")))),
                                gui_name = entityInfo.getPredicateObject(OntologyConstants.SalsahGui.GuiElement).map(iri => SalsahGuiConversions.iri2SalsahGuiElement(iri))
                            )

                        } else {
                            PropertyDefinitionInNamedGraphV1(
                                id = propertyIri,
                                name = propertyIri,
                                label = entityInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(userProfile.userData.lang, settings.fallbackLanguage)),
                                description = entityInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Comment, preferredLangs = Some(userProfile.userData.lang, settings.fallbackLanguage)),
                                vocabulary = entityInfo.predicates.values.head.ontologyIri,
                                valuetype_id = entityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse(throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")),
                                attributes = valueUtilV1.makeAttributeString(entityInfo.getPredicateObjects(OntologyConstants.SalsahGui.GuiAttribute)),
                                gui_name = entityInfo.getPredicateObject(OntologyConstants.SalsahGui.GuiElement).map(iri => SalsahGuiConversions.iri2SalsahGuiElement(iri))
                            )

                        }

                }.toVector
            } yield propertyDefinitions
        }

        namedGraphIriOption match {
            case Some(namedGraphIri) => // get all the property types for the given named graph
                for {
                    propertyTypes <- getPropertiesForNamedGraph(namedGraphIri, userProfile)

                } yield PropertyTypesForNamedGraphResponseV1(properties = propertyTypes, userdata = userProfile.userData)
            case None => // get the property types for all named graphs (collect them by mapping over all named graphs)
                val propertyTypesFutures: Vector[Future[Vector[PropertyDefinitionInNamedGraphV1]]] = settings.namedGraphs.filter(_.visibleInGUI).map {
                    namedGraphs: ProjectNamedGraphs => getPropertiesForNamedGraph(namedGraphs.ontology, userProfile)
                }

                val sequencedFuture: Future[Vector[Vector[PropertyDefinitionInNamedGraphV1]]] = Future.sequence(propertyTypesFutures)

                for {
                    propertyTypes <- sequencedFuture
                } yield PropertyTypesForNamedGraphResponseV1(properties = propertyTypes.flatten, userdata = userProfile.userData)
        }

    }

    /**
      * Gets the property types defined for the given resource class.
      *
      * @param resourceClassIri the Iri of the resource class to query for.
      * @param userProfile      the profile of the user making the request.
      * @return a [[PropertyTypesForResourceTypeResponseV1]].
      */
    private def getPropertyTypesForResourceType(resourceClassIri: IRI, userProfile: UserProfileV1): Future[PropertyTypesForResourceTypeResponseV1] = {
        for {
            resInfo: ResourceTypeResponseV1 <- getResourceTypeResponseV1(resourceClassIri, userProfile)
            propertyTypes = resInfo.restype_info.properties.toVector

        } yield PropertyTypesForResourceTypeResponseV1(properties = propertyTypes, userdata = userProfile.userData)

    }

}

object OntologyCache {
    var propertyDefs: Map[IRI, PropertyEntityInfoV1] = Map.empty[IRI, PropertyEntityInfoV1]
    var resourceClassDefs: Map[IRI, ResourceEntityInfoV1] = Map.empty[IRI, ResourceEntityInfoV1]
    var namedGraphResourceClasses: Map[IRI, Set[IRI]] = Map.empty[IRI, Set[IRI]]
    var namedGraphProperties: Map[IRI, Set[IRI]] = Map.empty[IRI, Set[IRI]]
    var subClassRelations: Map[IRI, Set[IRI]] = Map.empty[IRI, Set[IRI]]

    def populateCache(namedGraphResourceClasses: Map[IRI, Set[IRI]],
                      namedGraphProperties: Map[IRI, Set[IRI]],
                      resourceClassDefs: Map[IRI, ResourceEntityInfoV1],
                      subClassRelations: Map[IRI, Set[IRI]],
                      propertyDefs: Map[IRI, PropertyEntityInfoV1]): Unit = {
        OntologyCache.namedGraphResourceClasses = new ErrorHandlingMap(namedGraphResourceClasses, { key => s"Named graph not found: $key" })
        OntologyCache.namedGraphProperties = new ErrorHandlingMap(namedGraphProperties, { key => s"Named graph not found: $key" })
        OntologyCache.resourceClassDefs = new ErrorHandlingMap(resourceClassDefs, { key => s"Resource class not found: $key" })
        OntologyCache.subClassRelations = new ErrorHandlingMap(subClassRelations, { key => s"Class not found: $key" })
        OntologyCache.propertyDefs = new ErrorHandlingMap(propertyDefs, { key => s"Property not found: $key" })
    }
}
