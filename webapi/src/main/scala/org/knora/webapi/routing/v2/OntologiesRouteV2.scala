/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.routing.v2

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi._
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.SmartIri
import org.knora.webapi.util.jsonld.{JsonLDDocument, JsonLDUtil}

import scala.concurrent.Future

/**
  * Provides a routing function for API v2 routes that deal with ontologies.
  */
class OntologiesRouteV2(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {
    private val ALL_LANGUAGES = "allLanguages"
    private val LAST_MODIFICATION_DATE = "lastModificationDate"

    def knoraApiPath: Route = {

        path("ontology" / Segments) { _: List[String] =>
            get {
                requestContext => {
                    // This is the route used to dereference an actual ontology IRI. If the URL path looks like it
                    // belongs to a built-in API ontology (which has to contain "knora-api"), prefix it with
                    // http://api.knora.org to get the ontology IRI. Otherwise, if it looks like it belongs to a
                    // project-specific API ontology, prefix it with settings.externalOntologyIriHostAndPort to get the
                    // ontology IRI.

                    val urlPath = requestContext.request.uri.path.toString

                    val requestedOntologyStr: IRI = if (stringFormatter.isBuiltInApiV2OntologyUrlPath(urlPath)) {
                        OntologyConstants.KnoraApi.ApiOntologyHostname + urlPath
                    } else if (stringFormatter.isProjectSpecificApiV2OntologyUrlPath(urlPath)) {
                        "http://" + settings.externalOntologyIriHostAndPort + urlPath
                    } else {
                        throw BadRequestException(s"Invalid or unknown URL path for external ontology: $urlPath")
                    }

                    val requestedOntology = requestedOntologyStr.toSmartIriWithErr(throw BadRequestException(s"Invalid ontology IRI: $requestedOntologyStr"))

                    val targetSchema = requestedOntology.getOntologySchema match {
                        case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                        case _ => throw BadRequestException(s"Invalid ontology IRI: $requestedOntologyStr")
                    }

                    val params: Map[String, String] = requestContext.request.uri.query().toMap
                    val allLanguagesStr = params.get(ALL_LANGUAGES)
                    val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                    val requestMessageFuture: Future[OntologyEntitiesGetRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield OntologyEntitiesGetRequestV2(
                        ontologyIri = requestedOntology,
                        allLanguages = allLanguages,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessageF = requestMessageFuture,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = log,
                        targetSchema = targetSchema,
                        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                    )
                }
            }
        } ~ path("v2" / "ontologies" / "metadata") {
            get {
                requestContext => {
                    val maybeProjectIri: Option[SmartIri] = RouteUtilV2.getProject(requestContext)

                    val requestMessageFuture: Future[OntologyMetadataGetByProjectRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield OntologyMetadataGetByProjectRequestV2(projectIris = maybeProjectIri.toSet, requestingUser = requestingUser)

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessageF = requestMessageFuture,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = log,
                        targetSchema = ApiV2Complex,
                        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                    )
                }
            } ~ put {
                entity(as[String]) { jsonRequest =>
                    requestContext => {

                        val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                        val requestMessageFuture: Future[ChangeOntologyMetadataRequestV2] = for {
                            requestingUser <- getUserADM(requestContext)
                            requestMessage: ChangeOntologyMetadataRequestV2 <- ChangeOntologyMetadataRequestV2.fromJsonLD(
                                jsonLDDocument = requestDoc,
                                apiRequestID = UUID.randomUUID,
                                requestingUser = requestingUser,
                                responderManager = responderManager,
                                storeManager = storeManager,
                                settings = settings,
                                log = log
                            )
                        } yield requestMessage

                        RouteUtilV2.runRdfRouteWithFuture(
                            requestMessageF = requestMessageFuture,
                            requestContext = requestContext,
                            settings = settings,
                            responderManager = responderManager,
                            log = log,
                            targetSchema = ApiV2Complex,
                            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                        )
                    }
                }
            }
        } ~ path("v2" / "ontologies" / "metadata" / Segments) { projectIris: List[IRI] =>
            get {
                requestContext => {

                    val requestMessageFuture: Future[OntologyMetadataGetByProjectRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        validatedProjectIris = projectIris.map(iri => iri.toSmartIriWithErr(throw BadRequestException(s"Invalid project IRI: $iri"))).toSet
                    } yield OntologyMetadataGetByProjectRequestV2(projectIris = validatedProjectIris, requestingUser = requestingUser)

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessageF = requestMessageFuture,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = log,
                        targetSchema = ApiV2Complex,
                        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                    )
                }
            }
        } ~ path("v2" / "ontologies" / "allentities" / Segment) { externalOntologyIriStr: IRI =>
            get {
                requestContext => {
                    val requestedOntologyIri = externalOntologyIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid ontology IRI: $externalOntologyIriStr"))

                    val targetSchema = requestedOntologyIri.getOntologySchema match {
                        case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                        case _ => throw BadRequestException(s"Invalid ontology IRI: $externalOntologyIriStr")
                    }

                    val params: Map[String, String] = requestContext.request.uri.query().toMap
                    val allLanguagesStr = params.get(ALL_LANGUAGES)
                    val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                    val requestMessageFuture: Future[OntologyEntitiesGetRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield OntologyEntitiesGetRequestV2(
                        ontologyIri = requestedOntologyIri,
                        allLanguages = allLanguages,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessageF = requestMessageFuture,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = log,
                        targetSchema = targetSchema,
                        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                    )
                }
            }
        } ~ path("v2" / "ontologies" / "classes") {
            post {
                // Create a new class.
                entity(as[String]) { jsonRequest =>
                    requestContext => {

                        val requestMessageFuture: Future[CreateClassRequestV2] = for {
                            requestingUser <- getUserADM(requestContext)
                            requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
                            requestMessage: CreateClassRequestV2 <- CreateClassRequestV2.fromJsonLD(
                                jsonLDDocument = requestDoc,
                                apiRequestID = UUID.randomUUID,
                                requestingUser = requestingUser,
                                responderManager = responderManager,
                                storeManager = storeManager,
                                settings = settings,
                                log = log
                            )
                        } yield requestMessage

                        RouteUtilV2.runRdfRouteWithFuture(
                            requestMessageF = requestMessageFuture,
                            requestContext = requestContext,
                            settings = settings,
                            responderManager = responderManager,
                            log = log,
                            targetSchema = ApiV2Complex,
                            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                        )
                    }
                }
            } ~ put {
                // Change the labels or comments of a class.
                entity(as[String]) { jsonRequest =>
                    requestContext => {

                        val requestMessageFuture: Future[ChangeClassLabelsOrCommentsRequestV2] = for {
                            requestingUser <- getUserADM(requestContext)
                            requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
                            requestMessage <- ChangeClassLabelsOrCommentsRequestV2.fromJsonLD(
                                jsonLDDocument = requestDoc,
                                apiRequestID = UUID.randomUUID,
                                requestingUser = requestingUser,
                                responderManager = responderManager,
                                storeManager = storeManager,
                                settings = settings,
                                log = log
                            )
                        } yield requestMessage

                        RouteUtilV2.runRdfRouteWithFuture(
                            requestMessageF = requestMessageFuture,
                            requestContext = requestContext,
                            settings = settings,
                            responderManager = responderManager,
                            log = log,
                            targetSchema = ApiV2Complex,
                            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                        )
                    }
                }
            }
        } ~ path("v2" / "ontologies" / "cardinalities") {
            post {
                // Add cardinalities to a class.
                entity(as[String]) { jsonRequest =>
                    requestContext => {

                        val requestMessageFuture: Future[AddCardinalitiesToClassRequestV2] = for {
                            requestingUser <- getUserADM(requestContext)
                            requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
                            requestMessage: AddCardinalitiesToClassRequestV2 <- AddCardinalitiesToClassRequestV2.fromJsonLD(
                                jsonLDDocument = requestDoc,
                                apiRequestID = UUID.randomUUID,
                                requestingUser = requestingUser,
                                responderManager = responderManager,
                                storeManager = storeManager,
                                settings = settings,
                                log = log
                            )
                        } yield requestMessage

                        RouteUtilV2.runRdfRouteWithFuture(
                            requestMessageF = requestMessageFuture,
                            requestContext = requestContext,
                            settings = settings,
                            responderManager = responderManager,
                            log = log,
                            targetSchema = ApiV2Complex,
                            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                        )
                    }
                }
            } ~ put {
                // Change a class's cardinalities.
                entity(as[String]) { jsonRequest =>
                    requestContext => {

                        val requestMessageFuture: Future[ChangeCardinalitiesRequestV2] = for {
                            requestingUser <- getUserADM(requestContext)
                            requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
                            requestMessage: ChangeCardinalitiesRequestV2 <- ChangeCardinalitiesRequestV2.fromJsonLD(
                                jsonLDDocument = requestDoc,
                                apiRequestID = UUID.randomUUID,
                                requestingUser = requestingUser,
                                responderManager = responderManager,
                                storeManager = storeManager,
                                settings = settings,
                                log = log
                            )
                        } yield requestMessage

                        RouteUtilV2.runRdfRouteWithFuture(
                            requestMessageF = requestMessageFuture,
                            requestContext = requestContext,
                            settings = settings,
                            responderManager = responderManager,
                            log = log,
                            targetSchema = ApiV2Complex,
                            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                        )
                    }
                }
            }
        } ~ path("v2" / "ontologies" / "classes" / Segments) { externalResourceClassIris: List[IRI] =>
            get {
                requestContext => {

                    val classesAndSchemas: Set[(SmartIri, ApiV2Schema)] = externalResourceClassIris.map {
                        classIriStr: IRI =>
                            val requestedClassIri: SmartIri = classIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid class IRI: $classIriStr"))

                            if (!requestedClassIri.isKnoraApiV2EntityIri) {
                                throw BadRequestException(s"Invalid class IRI: $classIriStr")
                            }

                            val schema = requestedClassIri.getOntologySchema match {
                                case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                                case _ => throw BadRequestException(s"Invalid class IRI: $classIriStr")
                            }

                            (requestedClassIri, schema)
                    }.toSet

                    val (classesForResponder: Set[SmartIri], schemas: Set[ApiV2Schema]) = classesAndSchemas.unzip

                    if (classesForResponder.map(_.getOntologyFromEntity).size != 1) {
                        throw BadRequestException(s"Only one ontology may be queried per request")
                    }

                    // Decide which API schema to use for the response.
                    val targetSchema = if (schemas.size == 1) {
                        schemas.head
                    } else {
                        // The client requested different schemas.
                        throw BadRequestException("The request refers to multiple API schemas")
                    }

                    val params: Map[String, String] = requestContext.request.uri.query().toMap
                    val allLanguagesStr = params.get(ALL_LANGUAGES)
                    val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                    val requestMessageFuture: Future[ClassesGetRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ClassesGetRequestV2(
                        classIris = classesForResponder,
                        allLanguages = allLanguages,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessageF = requestMessageFuture,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = log,
                        targetSchema = targetSchema,
                        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                    )
                }
            } ~ delete {
                requestContext => {

                    val classIriStr = externalResourceClassIris match {
                        case List(str) => str
                        case _ => throw BadRequestException(s"Only one class can be deleted at a time")
                    }

                    val classIri = classIriStr.toSmartIri

                    if (!classIri.getOntologySchema.contains(ApiV2Complex)) {
                        throw BadRequestException(s"Invalid class IRI for request: $classIriStr")
                    }

                    val lastModificationDateStr = requestContext.request.uri.query().toMap.getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))
                    val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(lastModificationDateStr, throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr"))

                    val requestMessageFuture: Future[DeleteClassRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield DeleteClassRequestV2(
                        classIri = classIri,
                        lastModificationDate = lastModificationDate,
                        apiRequestID = UUID.randomUUID,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessageF = requestMessageFuture,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = log,
                        targetSchema = ApiV2Complex,
                        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                    )
                }
            }
        } ~ path("v2" / "ontologies" / "properties") {
            post {
                // Create a new property.
                entity(as[String]) { jsonRequest =>
                    requestContext => {

                        val requestMessageFuture: Future[CreatePropertyRequestV2] = for {
                            requestingUser <- getUserADM(requestContext)
                            requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
                            requestMessage: CreatePropertyRequestV2 <- CreatePropertyRequestV2.fromJsonLD(
                                jsonLDDocument = requestDoc,
                                apiRequestID = UUID.randomUUID,
                                requestingUser = requestingUser,
                                responderManager = responderManager,
                                storeManager = storeManager,
                                settings = settings,
                                log = log
                            )
                        } yield requestMessage

                        RouteUtilV2.runRdfRouteWithFuture(
                            requestMessageF = requestMessageFuture,
                            requestContext = requestContext,
                            settings = settings,
                            responderManager = responderManager,
                            log = log,
                            targetSchema = ApiV2Complex,
                            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                        )
                    }
                }
            } ~ put {
                // Change the labels or comments of a property.
                entity(as[String]) { jsonRequest =>
                    requestContext => {

                        val requestMessageFuture: Future[ChangePropertyLabelsOrCommentsRequestV2] = for {
                            requestingUser <- getUserADM(requestContext)
                            requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
                            requestMessage: ChangePropertyLabelsOrCommentsRequestV2 <- ChangePropertyLabelsOrCommentsRequestV2.fromJsonLD(
                                jsonLDDocument = requestDoc,
                                apiRequestID = UUID.randomUUID,
                                requestingUser = requestingUser,
                                responderManager = responderManager,
                                storeManager = storeManager,
                                settings = settings,
                                log = log
                            )
                        } yield requestMessage

                        RouteUtilV2.runRdfRouteWithFuture(
                            requestMessageF = requestMessageFuture,
                            requestContext = requestContext,
                            settings = settings,
                            responderManager = responderManager,
                            log = log,
                            targetSchema = ApiV2Complex,
                            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                        )
                    }
                }
            }
        } ~ path("v2" / "ontologies" / "properties" / Segments) { externalPropertyIris: List[IRI] =>
            get {
                requestContext => {

                    val propsAndSchemas: Set[(SmartIri, ApiV2Schema)] = externalPropertyIris.map {
                        (propIriStr: IRI) =>
                            val requestedPropIri: SmartIri = propIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid property IRI: $propIriStr"))

                            if (!requestedPropIri.isKnoraApiV2EntityIri) {
                                throw BadRequestException(s"Invalid property IRI: $propIriStr")
                            }

                            val schema = requestedPropIri.getOntologySchema match {
                                case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                                case _ => throw BadRequestException(s"Invalid property IRI: $propIriStr")
                            }

                            (requestedPropIri, schema)
                    }.toSet

                    val (propsForResponder: Set[SmartIri], schemas: Set[ApiV2Schema]) = propsAndSchemas.unzip

                    if (propsForResponder.map(_.getOntologyFromEntity).size != 1) {
                        throw BadRequestException(s"Only one ontology may be queried per request")
                    }

                    // Decide which API schema to use for the response.
                    val targetSchema = if (schemas.size == 1) {
                        schemas.head
                    } else {
                        // The client requested different schemas.
                        throw BadRequestException("The request refers to multiple API schemas")
                    }

                    val params: Map[String, String] = requestContext.request.uri.query().toMap
                    val allLanguagesStr = params.get(ALL_LANGUAGES)
                    val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                    val requestMessageFuture: Future[PropertiesGetRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield PropertiesGetRequestV2(
                        propertyIris = propsForResponder,
                        allLanguages = allLanguages,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessageF = requestMessageFuture,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = log,
                        targetSchema = targetSchema,
                        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                    )
                }
            } ~ delete {
                requestContext => {

                    val propertyIriStr = externalPropertyIris match {
                        case List(str) => str
                        case _ => throw BadRequestException(s"Only one property can be deleted at a time")
                    }

                    val propertyIri = propertyIriStr.toSmartIri

                    if (!propertyIri.getOntologySchema.contains(ApiV2Complex)) {
                        throw BadRequestException(s"Invalid property IRI for request: $propertyIri")
                    }

                    val lastModificationDateStr = requestContext.request.uri.query().toMap.getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))
                    val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(lastModificationDateStr, throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr"))

                    val requestMessageFuture: Future[DeletePropertyRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield DeletePropertyRequestV2(
                        propertyIri = propertyIri,
                        lastModificationDate = lastModificationDate,
                        apiRequestID = UUID.randomUUID,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessageF = requestMessageFuture,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = log,
                        targetSchema = ApiV2Complex,
                        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                    )
                }
            }
        } ~ path("v2" / "ontologies") {
            // Create a new, empty ontology.
            post {
                entity(as[String]) { jsonRequest =>
                    requestContext => {

                        val requestMessageFuture: Future[CreateOntologyRequestV2] = for {
                            requestingUser <- getUserADM(requestContext)
                            requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
                            requestMessage: CreateOntologyRequestV2 <- CreateOntologyRequestV2.fromJsonLD(
                                jsonLDDocument = requestDoc,
                                apiRequestID = UUID.randomUUID,
                                requestingUser = requestingUser,
                                responderManager = responderManager,
                                storeManager = storeManager,
                                settings = settings,
                                log = log
                            )
                        } yield requestMessage

                        RouteUtilV2.runRdfRouteWithFuture(
                            requestMessageF = requestMessageFuture,
                            requestContext = requestContext,
                            settings = settings,
                            responderManager = responderManager,
                            log = log,
                            targetSchema = ApiV2Complex,
                            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                        )
                    }
                }
            }
        } ~ path ("v2" / "ontologies" / Segment) { ontologyIriStr =>
            delete {
                requestContext => {

                    val ontologyIri = ontologyIriStr.toSmartIri

                    if (!ontologyIri.isKnoraOntologyIri || ontologyIri.isKnoraBuiltInDefinitionIri || !ontologyIri.getOntologySchema.contains(ApiV2Complex)) {
                        throw BadRequestException(s"Invalid ontology IRI for request: $ontologyIri")
                    }

                    val lastModificationDateStr = requestContext.request.uri.query().toMap.getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))
                    val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(lastModificationDateStr, throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr"))

                    val requestMessageFuture: Future[DeleteOntologyRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield DeleteOntologyRequestV2(
                        ontologyIri = ontologyIri,
                        lastModificationDate = lastModificationDate,
                        apiRequestID = UUID.randomUUID,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessageF = requestMessageFuture,
                        requestContext = requestContext,
                        settings = settings,
                        responderManager = responderManager,
                        log = log,
                        targetSchema = ApiV2Complex,
                        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                    )
                }
            }
        }
    }
}