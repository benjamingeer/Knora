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
import org.apache.commons.lang3.builder.HashCodeBuilder
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
    private val knoraIriUtil = new KnoraIriUtil
    private val valueUtilV1 = new ValueUtilV1(settings)

    /**
      * This was used to query the named graphs in SPARQL. In the future, we may need it again.
      * Currently, we se a workaround (`getNamedGraph`).
      * // get a list of the named graphs for ontology information
      * private val namedGraphs: Seq[IRI] = settings.projectNamedGraphs.map {
      * case (project: IRI, namedGraph: ProjectNamedGraphs) => namedGraph.ontology
      * }.toList
      */

    /**
      * Keys for the ontology cache.
      */
    private object OntologyCacheKeys {

        import scala.math.Ordered.orderingToOrdered

        /**
          * The type of ontology cache keys for instances of [[ResourceEntityInfoV1]].
          *
          * @param resourceClassIri  the IRI of the resource class described.
          * @param preferredLanguage the user's preferred language, which the key's value will use if it was available.
          */
        class ResourceEntityInfoKey(val resourceClassIri: IRI, val preferredLanguage: String) extends Ordered[ResourceEntityInfoKey] {
            def compare(that: ResourceEntityInfoKey): Int = {
                (this.resourceClassIri, this.preferredLanguage) compare(that.resourceClassIri, that.preferredLanguage)
            }

            override def equals(that: Any): Boolean = {
                that match {
                    case otherEntityInfoKey: ResourceEntityInfoKey =>
                        this.resourceClassIri == otherEntityInfoKey.resourceClassIri && this.preferredLanguage == otherEntityInfoKey.preferredLanguage
                    case _ => false
                }
            }

            override def hashCode(): Int = {
                new HashCodeBuilder(17, 37).append(resourceClassIri).append(preferredLanguage).toHashCode
            }

            override def toString: String = s"ResourceEntityInfoKey($resourceClassIri, $preferredLanguage)"
        }

        /**
          * The type of ontology cache keys for instances of [[PropertyEntityInfoV1]].
          *
          * @param propertyIri       the IRI of the property described.
          * @param preferredLanguage the user's preferred language, which the key's value will use if it was available.
          */
        class PropertyEntityInfoKey(val propertyIri: IRI, val preferredLanguage: String) extends Ordered[PropertyEntityInfoKey] {
            def compare(that: PropertyEntityInfoKey): Int = {
                (this.propertyIri, this.preferredLanguage) compare(that.propertyIri, that.preferredLanguage)
            }

            override def equals(that: Any): Boolean = {
                that match {
                    case otherEntityInfoKey: PropertyEntityInfoKey =>
                        this.propertyIri == otherEntityInfoKey.propertyIri && this.preferredLanguage == otherEntityInfoKey.preferredLanguage
                    case _ => false
                }
            }

            override def hashCode(): Int = {
                new HashCodeBuilder(19, 39).append(propertyIri).append(preferredLanguage).toHashCode
            }

            override def toString: String = s"PropertyEntityInfoKey($propertyIri, $preferredLanguage)"
        }

        /**
          * The type of ontology cache keys for instances of [[NamedGraphEntityInfoV1]].
          *
          * @param namedGraphIri the Iri of the named graph.
          */
        class NamedGraphInfoKey(val namedGraphIri: IRI) extends Ordered[NamedGraphInfoKey] {
            def compare(that: NamedGraphInfoKey): Int = {
                (this.namedGraphIri) compare (that.namedGraphIri)
            }

            override def equals(that: Any): Boolean = {
                that match {
                    case otherEntityInfoKey: NamedGraphInfoKey =>
                        this.namedGraphIri == otherEntityInfoKey.namedGraphIri
                    case _ => false
                }
            }

            override def hashCode(): Int = {
                new HashCodeBuilder(21, 41).append(namedGraphIri).toHashCode
            }

            override def toString: String = s"NamedGraphInfoKey($namedGraphIri)"
        }

    }

    /**
      * Receives a message extending [[OntologyResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
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
      * Given a list of resource IRIs and a list of property IRIs (ontology entities), returns an [[EntityInfoGetResponseV1]] describing both resource and property entities.
      *
      * @param resourceIris the IRIs of the resource entities to be queried.
      * @param propertyIris the IRIs of the property entities to be queried.
      * @param userProfile  the profile of the user making the request.
      * @return an [[EntityInfoGetResponseV1]].
      */
    private def getEntityInfoResponseV1(resourceIris: Set[IRI] = Set.empty[IRI], propertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserProfileV1): Future[EntityInfoGetResponseV1] = {

        /**
          * Gets the IRI of the ontology that an entity belongs to, assuming that the entity IRI has the form `http://something.something#entityName`.
          * The ontology IRI is assumed to be the part before the `#`. TODO: is this adequate?
          *
          * @param entityIri the entity IRI (e.g. `http://www.knora.org/ontology/incunabula#publisher`).
          * @return the part before the `#`.
          */
        def getOntologyIri(entityIri: IRI): String = {
            entityIri.split("#")(0)
        }

        /**
          * Queries the ontology for the given resource entity Iris and returns a Map of [[ResourceEntityInfoV1]] to be cached.
          *
          * @param resourceIris the resource entity Iris to be queried.
          * @return a Map of [[ResourceEntityInfoV1]] to be cached.
          */
        def queryResourceEntityInfoResponse(resourceIris: Set[OntologyCacheKeys.ResourceEntityInfoKey]): Future[Map[OntologyCacheKeys.ResourceEntityInfoKey, ResourceEntityInfoV1]] = {

            case class OwlCardinality(propertyIri: IRI, cardinalityIri: IRI, cardinalityValue: Int, isLinkProp: Boolean, isLinkValueProp: Boolean, isFileValueProp: Boolean)

            val resourceClassIris = resourceIris.map(_.resourceClassIri).toVector

            for {
            // Get information about resource classes.
                sparqlQueryStringForResourceClasses <- Future(queries.sparql.v1.txt.getResourceClassInfo(
                    triplestore = settings.triplestoreType,
                    entityIris = resourceClassIris
                ).toString())
                // _ = println(sparqlQueryStringForResourceClasses)
                resourceClassesResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryStringForResourceClasses)).mapTo[SparqlSelectResponse]

                // Filter the query results to get text in the user's preferred language (or the application's default language),
                // if possible.
                resourceClassesFilteredByLanguage = SparqlUtil.filterByLanguage(response = resourceClassesResponse,
                    langSpecificColumnName = "o",
                    preferredLanguage = userProfile.userData.lang,
                    settings = settings
                )

                // Group the resource query results by subject (resource class IRI).
                resourceClassesGroupedBySubject: Map[IRI, Seq[VariableResultsRow]] = resourceClassesFilteredByLanguage.groupBy(_.rowMap("s"))

                // Ensure that all the queried IRIs actually refer to resource classes.
                incorrectIris = resourceClassIris.toSet -- resourceClassesGroupedBySubject.keySet
                _ = if (incorrectIris.nonEmpty) {
                    val incorrectIrisStr = incorrectIris.mkString(", ")
                    throw BadRequestException(s"One or more requested IRIs do not refer to subclasses of knora-base:Resource: $incorrectIrisStr")
                }

                // Get information about resource class cardinalities.
                sparqlQueryStringForCardinalities = queries.sparql.v1.txt.getResourceClassCardinalities(
                    triplestore = settings.triplestoreType,
                    entityIris = resourceClassIris
                ).toString()
                // _ = println(sparqlQueryStringForCardinalities)
                cardinalitiesResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryStringForCardinalities)).mapTo[SparqlSelectResponse]

                // Group the resource query results by subject (resource class IRI).
                cardinalitiesGroupedBySubject: Map[IRI, Seq[VariableResultsRow]] = cardinalitiesResponse.results.bindings.groupBy(_.rowMap("s"))

                // Convert the results for each subject into an ResourceEntityInfoV1.
                resourceEntities: Map[OntologyCacheKeys.ResourceEntityInfoKey, ResourceEntityInfoV1] = resourceClassesGroupedBySubject.map {
                    case (resourceClass: IRI, rows: Seq[VariableResultsRow]) =>

                        // Group resource class info by predicates (e.g. rdf:type)
                        val groupedByPredicate: Map[IRI, Seq[VariableResultsRow]] = rows.groupBy(_.rowMap("p"))

                        // Get the cardinality information for each subject.
                        val owlCardinalities = cardinalitiesGroupedBySubject.get(resourceClass) match {
                            case Some(cardinalityRows) =>
                                cardinalityRows.filter {
                                    cardinalityRow =>
                                        // Only return cardinalities for Knora value properties or Knora link properties (not for Knora system
                                        // properties).
                                        val cardinalityRowMap = cardinalityRow.rowMap
                                        InputValidation.optionStringToBoolean(cardinalityRowMap.get("isKnoraValueProp")) || InputValidation.optionStringToBoolean(cardinalityRowMap.get("isLinkProp"))
                                }.map {
                                    cardinalityRow =>
                                        val cardinalityRowMap = cardinalityRow.rowMap
                                        OwlCardinality(
                                            propertyIri = cardinalityRowMap("cardinalityProp"),
                                            cardinalityIri = cardinalityRowMap("cardinality"),
                                            cardinalityValue = cardinalityRowMap("cardinalityVal").toInt,
                                            isLinkProp = InputValidation.optionStringToBoolean(cardinalityRowMap.get("isLinkProp")),
                                            isLinkValueProp = InputValidation.optionStringToBoolean(cardinalityRowMap.get("isLinkValueProp")),
                                            isFileValueProp = InputValidation.optionStringToBoolean(cardinalityRowMap.get("isFileValueProp"))
                                        )
                                }

                            case None =>
                                // TODO: can there be a resource class without cardinalities?
                                Nil
                        }

                        // Identify the link properties, like value properties, and file value properties in the cardinalities.
                        val linkProps = owlCardinalities.filter(_.isLinkProp).map(_.propertyIri).toSet
                        val linkValueProps = owlCardinalities.filter(_.isLinkValueProp).map(_.propertyIri).toSet
                        val fileValueProps = owlCardinalities.filter(_.isFileValueProp).map(_.propertyIri).toSet

                        // Make sure there is a link value property for each link property.
                        val missingLinkValueProps = linkProps.map(linkProp => knoraIriUtil.linkPropertyIriToLinkValuePropertyIri(linkProp)) -- linkValueProps
                        if (missingLinkValueProps.nonEmpty) {
                            throw InconsistentTriplestoreDataException(s"Resource class $resourceClass has cardinalities for one or more link properties without corresponding link value properties. The missing link value property or properties: ${missingLinkValueProps.mkString(", ")}")
                        }

                        // Make sure there is a link property for each link value property.
                        val missingLinkProps = linkValueProps.map(linkValueProp => knoraIriUtil.linkValuePropertyIri2LinkPropertyIri(linkValueProp)) -- linkProps
                        if (missingLinkProps.nonEmpty) {
                            throw InconsistentTriplestoreDataException(s"Resource class $resourceClass has cardinalities for one or more link value properties without corresponding link properties. The missing link property or properties: ${missingLinkProps.mkString(", ")}")
                        }
                        // Make a PredicateInfoV1 for each of the subject's predicates.
                        val predicates: Map[IRI, PredicateInfoV1] = groupedByPredicate.map {
                            case (predicateIri, predicateRowList) =>
                                predicateIri -> PredicateInfoV1(
                                    predicateIri = predicateIri,
                                    ontologyIri = getOntologyIri(predicateIri),
                                    // Don't include cardinalities among the predicates, because we return them separately.
                                    objects = predicateRowList.map(row => row.rowMap("o")).toSet
                                )
                        }

                        new OntologyCacheKeys.ResourceEntityInfoKey(resourceClass, userProfile.userData.lang) -> ResourceEntityInfoV1(
                            resourceIri = resourceClass,
                            predicates = new ErrorHandlingMap(predicates, { key: IRI => s"Predicate $key not found for resource class $resourceClass" }),
                            cardinalities = owlCardinalities.map {
                                owlCardinality =>
                                    // Convert the OWL cardinality to a Knora Cardinality enum value.
                                    val propertyIri = owlCardinality.propertyIri
                                    val owlCardinalityIri = owlCardinality.cardinalityIri
                                    val owlCardinalityValue = owlCardinality.cardinalityValue
                                    val card = Cardinality.owlCardinality2KnoraCardinality(propertyIri, owlCardinalityIri, owlCardinalityValue)
                                    propertyIri -> card
                            }.toMap,
                            linkProperties = linkProps,
                            linkValueProperties = linkValueProps,
                            fileValueProperties = fileValueProps
                        )


                }(breakOut) // http://stackoverflow.com/questions/1715681/scala-2-8-breakout

            } yield new ErrorHandlingMap(resourceEntities, { key: OntologyCacheKeys.ResourceEntityInfoKey => s"Ontology entity $key not found" })
        }

        /**
          * Queries the ontology for the given property entity Iris and returns a Map of [[PropertyEntityInfoV1]] to be cached.
          *
          * @param propertyIris the property entity Iris to be queried.
          * @return a Map of [[PropertyEntityInfoV1]] to be cached.
          */
        def queryPropertyEntityInfoResponse(propertyIris: Set[OntologyCacheKeys.PropertyEntityInfoKey]): Future[Map[OntologyCacheKeys.PropertyEntityInfoKey, PropertyEntityInfoV1]] = {

            for {
            // get information about property entities
                sparqlQueryStringForProps <- Future(queries.sparql.v1.txt.getEntityInfoForProps(
                    triplestore = settings.triplestoreType,
                    entityIris = propertyIris.map(_.propertyIri).toVector
                ).toString())
                propertiesResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryStringForProps)).mapTo[SparqlSelectResponse]

                // Filter the query results to get text in the user's preferred language (or the application's default language),
                // if possible.
                propertiesFilteredByLanguage = SparqlUtil.filterByLanguage(response = propertiesResponse,
                    langSpecificColumnName = "o",
                    preferredLanguage = userProfile.userData.lang,
                    settings = settings
                )

                // Group the property query results by subject.
                propertiesGroupedBySubject: Map[IRI, Seq[VariableResultsRow]] = propertiesFilteredByLanguage.groupBy(_.rowMap("s"))

                // Convert the results for each subject into an ResourceEntityInfoV1.
                propertyEntities: Map[OntologyCacheKeys.PropertyEntityInfoKey, PropertyEntityInfoV1] = propertiesGroupedBySubject.map {
                    case (propertyIri: IRI, rows: Seq[VariableResultsRow]) =>

                        val isLinkProp = rows.head.rowMap.get("isLinkProp").exists(_.toBoolean)

                        // group by predicates (e.g. rdfs:subPropertyOf, rdf:type)
                        val groupedByPredicate: Map[IRI, Seq[VariableResultsRow]] = rows.groupBy(_.rowMap("p"))

                        // Make a PredicateInfoV1 for each of the subject's predicates.
                        val predicates: Map[IRI, PredicateInfoV1] = groupedByPredicate.map {
                            case (predicateIri, predicateRowList) =>
                                predicateIri -> PredicateInfoV1(
                                    predicateIri = predicateIri,
                                    ontologyIri = getOntologyIri(propertyIri),
                                    objects = predicateRowList.map(row => row.rowMap("o")).toSet
                                )
                        }

                        new OntologyCacheKeys.PropertyEntityInfoKey(propertyIri, userProfile.userData.lang) -> PropertyEntityInfoV1(
                            propertyIri = propertyIri,
                            isLinkProp = isLinkProp,
                            predicates = new ErrorHandlingMap(predicates, { key: IRI => s"Predicate $key not found for ontology entity $propertyIri" })
                        )

                }(breakOut) // http://stackoverflow.com/questions/1715681/scala-2-8-breakout

            } yield new ErrorHandlingMap(propertyEntities, { key: OntologyCacheKeys.PropertyEntityInfoKey => s"Ontology entity $key not found" })

        }

        for {

        // if no resource Iris are given, just return an empty Map
            resourceEntityInfoMap: Map[IRI, ResourceEntityInfoV1] <- if (resourceIris.nonEmpty) {
                for {
                // Get the resource entity info from the cache, or query the triplestore and add the resource entity info to the cache.
                    resourceCacheResultMap <- CacheUtil.getOrCacheItems(
                        cacheName = OntologyCacheName,
                        cacheKeys = resourceIris.map(iri => new OntologyCacheKeys.ResourceEntityInfoKey(iri, userProfile.userData.lang)),
                        queryFun = queryResourceEntityInfoResponse
                    )

                    resourceEntityInfoMapToWrap = resourceCacheResultMap.map {
                        case (entityInfoKey, entityInfo) => entityInfoKey.resourceClassIri -> entityInfo
                    }

                    resInfo = new ErrorHandlingMap(resourceEntityInfoMapToWrap, { key: IRI => s"Ontology entity $key not found" })

                } yield resInfo
            } else {
                Future(Map.empty[IRI, ResourceEntityInfoV1])
            }

            // if no property Iris are given, just return an empty Map
            propertyEntityInfoMap: Map[IRI, PropertyEntityInfoV1] <- if (propertyIris.nonEmpty) {
                for {
                // Get the property entity info from the cache, or query the triplestore and add the property entity info to the cache.
                    propertyCacheResultMap: Map[OntologyCacheKeys.PropertyEntityInfoKey, PropertyEntityInfoV1] <- CacheUtil.getOrCacheItems(
                        cacheName = OntologyCacheName,
                        cacheKeys = propertyIris.map(iri => new OntologyCacheKeys.PropertyEntityInfoKey(iri, userProfile.userData.lang)),
                        queryFun = queryPropertyEntityInfoResponse
                    )

                    propertyEntityInfoMapToWrap: Map[IRI, PropertyEntityInfoV1] = propertyCacheResultMap.map {
                        case (entityInfoKey: OntologyCacheKeys.PropertyEntityInfoKey, entityInfo: PropertyEntityInfoV1) => entityInfoKey.propertyIri -> entityInfo
                    }

                    propInfo = new ErrorHandlingMap(propertyEntityInfoMapToWrap, { key: IRI => s"Ontology entity $key not found" })

                } yield propInfo
            } else {
                Future(Map.empty[IRI, PropertyEntityInfoV1])
            }

        } yield EntityInfoGetResponseV1(resourceEntityInfoMap = resourceEntityInfoMap, propertyEntityInfoMap = propertyEntityInfoMap)
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
            resourceInfoResponse: EntityInfoGetResponseV1 <- getEntityInfoResponseV1(resourceIris = Set(resourceTypeIri), userProfile = userProfile)
            resourceInfo: ResourceEntityInfoV1 = resourceInfoResponse.resourceEntityInfoMap(resourceTypeIri)

            // Get all information about those properties.
            propertyInfo: EntityInfoGetResponseV1 <- getEntityInfoResponseV1(propertyIris = resourceInfo.cardinalities.keySet, userProfile = userProfile)

            // Build the property definitions.
            propertyDefinitions: Set[PropertyDefinitionV1] = resourceInfo.cardinalities.filterNot {
                // filter out the properties that point to LinkValue objects
                case (propertyIri, cardinality) =>
                    resourceInfo.linkValueProperties(propertyIri)
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
                                    label = entityInfo.getPredicateObject(OntologyConstants.Rdfs.Label),
                                    description = entityInfo.getPredicateObject(OntologyConstants.Rdfs.Comment),
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
                                    label = entityInfo.getPredicateObject(OntologyConstants.Rdfs.Label),
                                    description = entityInfo.getPredicateObject(OntologyConstants.Rdfs.Comment),
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
                    label = resourceInfo.getPredicateObject(OntologyConstants.Rdfs.Label),
                    description = resourceInfo.getPredicateObject(OntologyConstants.Rdfs.Comment),
                    iconsrc = resourceInfo.getPredicateObject(OntologyConstants.KnoraBase.ResourceIcon),
                    properties = propertyDefinitions
                ),
                userProfile.userData
            )
        } yield resourceTypeResponse
    }

    /**
      * Checks whether a certain OWL class is a subclass of another OWL class.
      *
      * @param checkSubClassRequest a [[CheckSubClassRequestV1]]
      * @return a [[CheckSubClassResponseV1]].
      */
    private def checkSubClass(checkSubClassRequest: CheckSubClassRequestV1): Future[CheckSubClassResponseV1] = {
        // TODO: cache this data.

        for {
            sparqlQuery <- Future(
                queries.sparql.v1.txt.checkSubClass(
                    triplestore = settings.triplestoreType,
                    subClassIri = checkSubClassRequest.subClassIri,
                    superClassIri = checkSubClassRequest.superClassIri
                ).toString()
            )

            queryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

        } yield CheckSubClassResponseV1(isSubClass = queryResponse.results.bindings.nonEmpty)
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

        def queryNamedGraphEntityV1(namedGraphInfoKey: OntologyCacheKeys.NamedGraphInfoKey): Future[NamedGraphEntityInfoV1] = {
            val namedGraphIriToQuery = namedGraphInfoKey.namedGraphIri

            for {
                resourceTypesInNamedGraphSparql <- Future(queries.sparql.v1.txt.getResourceTypesForNamedGraph(
                    namedGraph = namedGraphIriToQuery,
                    triplestore = settings.triplestoreType
                ).toString())
                resourceTypesInNamedGraphResponse <- (storeManager ? SparqlSelectRequest(resourceTypesInNamedGraphSparql)).mapTo[SparqlSelectResponse]

                resourceClassIrisInNamedGraph = resourceTypesInNamedGraphResponse.results.bindings.map {
                    (row) => row.rowMap("class")
                }.toVector

                propertyTypesInNamedGraphSparql <- Future(queries.sparql.v1.txt.getPropertyTypesForNamedGraph(
                    namedGraph = namedGraphIriToQuery,
                    triplestore = settings.triplestoreType
                ).toString())
                propertyTypesInNamedGraphResponse <- (storeManager ? SparqlSelectRequest(propertyTypesInNamedGraphSparql)).mapTo[SparqlSelectResponse]

                propertyTypeIrisInNamedGraph = propertyTypesInNamedGraphResponse.results.bindings.map {
                    (row) => row.rowMap("property")
                }.toVector


                // cache these results
                namedGraphEntity = NamedGraphEntityInfoV1(
                    namedGraphIri = namedGraphIriToQuery,
                    propertyIris = propertyTypeIrisInNamedGraph,
                    resourceClasses = resourceClassIrisInNamedGraph
                )
            } yield namedGraphEntity
        }

        for {

            cacheKeyForNamedGraph <- Future(new OntologyCacheKeys.NamedGraphInfoKey(namedGraphIri))

            namedGraphCacheResult <- CacheUtil.getOrCacheItem(
                cacheName = OntologyCacheName,
                cacheKey = cacheKeyForNamedGraph,
                queryFun = queryNamedGraphEntityV1
            )
        } yield namedGraphCacheResult


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
                resInfosForNamedGraphFuture: Seq[Future[(String, ResourceTypeResponseV1)]] = namedGraphEntityInfo.resourceClasses.map {
                    (resClassIri) =>
                        for {
                            resInfo <- getResourceTypeResponseV1(resClassIri, userProfile)
                        } yield (resClassIri, resInfo)
                }

                resInfosForNamedGraph <- Future.sequence(resInfosForNamedGraphFuture)

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
                            label = resInfo.restype_info.label.getOrElse(throw InconsistentTriplestoreDataException(s"No label given for ${resClassIri}")),
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
                propertyIris: Vector[IRI] = namedGraphEntityInfo.propertyIris
                entities: EntityInfoGetResponseV1 <- getEntityInfoResponseV1(propertyIris = propertyIris.toSet, userProfile = userProfile)
                propertyEntityInfoMap: Map[IRI, PropertyEntityInfoV1] = entities.propertyEntityInfoMap

                propertyDefinitions: Vector[PropertyDefinitionInNamedGraphV1] = propertyEntityInfoMap.map {
                    case (propertyIri: IRI, entityInfo: PropertyEntityInfoV1) =>

                        if (entityInfo.isLinkProp) {
                            // It is a linking prop: its valuetype_id is knora-base:LinkValue.
                            // It is restricted to the resource class that is given for knora-base:objectClassConstraint
                            // for the given property which goes in the attributes that will be read by the GUI.

                            PropertyDefinitionInNamedGraphV1(
                                id = propertyIri,
                                name = propertyIri,
                                label = entityInfo.getPredicateObject(OntologyConstants.Rdfs.Label),
                                description = entityInfo.getPredicateObject(OntologyConstants.Rdfs.Comment),
                                vocabulary = entityInfo.predicates.values.head.ontologyIri,
                                valuetype_id = OntologyConstants.KnoraBase.LinkValue,
                                attributes = valueUtilV1.makeAttributeString(entityInfo.getPredicateObjects(OntologyConstants.SalsahGui.GuiAttribute) + valueUtilV1.makeAttributeRestype(entityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse(throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")))),
                                gui_name = entityInfo.getPredicateObject(OntologyConstants.SalsahGui.GuiElement).map(iri => SalsahGuiConversions.iri2SalsahGuiElement(iri))
                            )

                        } else {
                            PropertyDefinitionInNamedGraphV1(
                                id = propertyIri,
                                name = propertyIri,
                                label = entityInfo.getPredicateObject(OntologyConstants.Rdfs.Label),
                                description = entityInfo.getPredicateObject(OntologyConstants.Rdfs.Comment),
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
