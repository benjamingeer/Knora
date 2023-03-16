/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import zio.prelude.Validation

import java.util.UUID.randomUUID
import scala.concurrent.Future

import dsp.constants.SalsahGui
import dsp.errors.BadRequestException
import dsp.errors.ValidationException
import dsp.valueobjects.Iri._
import dsp.valueobjects.LangString
import dsp.valueobjects.Schema._
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.RouteUtilV2.completeZioApiV2ComplexResponse
import org.knora.webapi.routing.RouteUtilV2.getStringQueryParam
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService

/**
 * Provides a routing function for API v2 routes that deal with ontologies.
 */
class OntologiesRouteV2(routeData: KnoraRouteData, implicit val runtime: zio.Runtime[RestCardinalityService])
    extends KnoraRoute(routeData)
    with Authenticator {

  val ontologiesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "ontologies")

  private val ALL_LANGUAGES          = "allLanguages"
  private val LAST_MODIFICATION_DATE = "lastModificationDate"

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    dereferenceOntologyIri() ~
      getOntologyMetadata() ~
      updateOntologyMetadata() ~
      getOntologyMetadataForProjects() ~
      getOntology() ~
      createClass() ~
      updateClass() ~
      deleteClassComment() ~
      addCardinalities() ~
      canReplaceCardinalities ~
      replaceCardinalities() ~
      canDeleteCardinalitiesFromClass() ~
      deleteCardinalitiesFromClass() ~
      changeGuiOrder() ~
      getClasses() ~
      canDeleteClass() ~
      deleteClass() ~
      deleteOntologyComment() ~
      createProperty() ~
      updatePropertyLabelsOrComments() ~
      deletePropertyComment() ~
      updatePropertyGuiElement() ~
      getProperties() ~
      canDeleteProperty() ~
      deleteProperty() ~
      createOntology() ~
      canDeleteOntology() ~
      deleteOntology()

  private def dereferenceOntologyIri(): Route = path("ontology" / Segments) { _: List[String] =>
    get { requestContext =>
      // This is the route used to dereference an actual ontology IRI. If the URL path looks like it
      // belongs to a built-in API ontology (which has to contain "knora-api"), prefix it with
      // http://api.knora.org to get the ontology IRI. Otherwise, if it looks like it belongs to a
      // project-specific API ontology, prefix it with routeData.appConfig.externalOntologyIriHostAndPort to get the
      // ontology IRI.

      val urlPath = requestContext.request.uri.path.toString

      val requestedOntologyStr: IRI = if (stringFormatter.isBuiltInApiV2OntologyUrlPath(urlPath)) {
        OntologyConstants.KnoraApi.ApiOntologyHostname + urlPath
      } else if (stringFormatter.isProjectSpecificApiV2OntologyUrlPath(urlPath)) {
        "http://" + routeData.appConfig.knoraApi.externalOntologyIriHostAndPort + urlPath
      } else {
        throw BadRequestException(s"Invalid or unknown URL path for external ontology: $urlPath")
      }

      val requestedOntology = requestedOntologyStr.toSmartIriWithErr(
        throw BadRequestException(s"Invalid ontology IRI: $requestedOntologyStr")
      )

      stringFormatter.checkExternalOntologyName(requestedOntology)

      val targetSchema = requestedOntology.getOntologySchema match {
        case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
        case _                              => throw BadRequestException(s"Invalid ontology IRI: $requestedOntologyStr")
      }

      val params: Map[String, String] = requestContext.request.uri.query().toMap
      val allLanguagesStr             = params.get(ALL_LANGUAGES)
      val allLanguages = stringFormatter.optionStringToBoolean(
        allLanguagesStr,
        throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr")
      )

      val requestMessageFuture: Future[OntologyEntitiesGetRequestV2] = for {
        requestingUser <- getUserADM(
                            requestContext = requestContext,
                            routeData.appConfig
                          )
      } yield OntologyEntitiesGetRequestV2(
        ontologyIri = requestedOntology,
        allLanguages = allLanguages,
        requestingUser = requestingUser
      )

      RouteUtilV2.runRdfRouteWithFuture(
        requestMessageF = requestMessageFuture,
        requestContext = requestContext,
        appConfig = routeData.appConfig,
        appActor = appActor,
        log = log,
        targetSchema = targetSchema,
        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
      )
    }
  }

  private def getOntologyMetadata(): Route =
    path(ontologiesBasePath / "metadata") {
      get { requestContext =>
        val maybeProjectIri: Option[SmartIri] = RouteUtilV2.getProject(requestContext)

        val requestMessageFuture: Future[OntologyMetadataGetByProjectRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield OntologyMetadataGetByProjectRequestV2(
          projectIris = maybeProjectIri.toSet,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          appConfig = routeData.appConfig,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def updateOntologyMetadata(): Route =
    path(ontologiesBasePath / "metadata") {
      put {
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

            val requestMessageFuture: Future[ChangeOntologyMetadataRequestV2] = for {
              requestingUser <- getUserADM(
                                  requestContext = requestContext,
                                  routeData.appConfig
                                )

              requestMessage: ChangeOntologyMetadataRequestV2 <- ChangeOntologyMetadataRequestV2.fromJsonLD(
                                                                   jsonLDDocument = requestDoc,
                                                                   apiRequestID = randomUUID,
                                                                   requestingUser = requestingUser,
                                                                   appActor = appActor,
                                                                   log = log
                                                                 )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              appConfig = routeData.appConfig,
              appActor = appActor,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  private def getOntologyMetadataForProjects(): Route =
    path(ontologiesBasePath / "metadata" / Segments) { projectIris: List[IRI] =>
      get { requestContext =>
        val requestMessageFuture: Future[OntologyMetadataGetByProjectRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )

          validatedProjectIris =
            projectIris
              .map(iri => iri.toSmartIriWithErr(throw BadRequestException(s"Invalid project IRI: $iri")))
              .toSet
        } yield OntologyMetadataGetByProjectRequestV2(
          projectIris = validatedProjectIris,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          appConfig = routeData.appConfig,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def getOntology(): Route =
    path(ontologiesBasePath / "allentities" / Segment) { externalOntologyIriStr: IRI =>
      get { requestContext =>
        val requestedOntologyIri = externalOntologyIriStr.toSmartIriWithErr(
          throw BadRequestException(s"Invalid ontology IRI: $externalOntologyIriStr")
        )

        stringFormatter.checkExternalOntologyName(requestedOntologyIri)

        val targetSchema = requestedOntologyIri.getOntologySchema match {
          case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
          case _                              => throw BadRequestException(s"Invalid ontology IRI: $externalOntologyIriStr")
        }

        val params: Map[String, String] = requestContext.request.uri.query().toMap
        val allLanguagesStr             = params.get(ALL_LANGUAGES)
        val allLanguages = stringFormatter.optionStringToBoolean(
          params.get(ALL_LANGUAGES),
          throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr")
        )

        val requestMessageFuture: Future[OntologyEntitiesGetRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield OntologyEntitiesGetRequestV2(
          ontologyIri = requestedOntologyIri,
          allLanguages = allLanguages,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          appConfig = routeData.appConfig,
          appActor = appActor,
          log = log,
          targetSchema = targetSchema,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def createClass(): Route = path(ontologiesBasePath / "classes") {
    post {
      // Create a new class.
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestMessageFuture: Future[CreateClassRequestV2] = for {
            requestingUser <- getUserADM(
                                requestContext = requestContext,
                                routeData.appConfig
                              )

            requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

            requestMessage: CreateClassRequestV2 <- CreateClassRequestV2.fromJsonLD(
                                                      jsonLDDocument = requestDoc,
                                                      apiRequestID = randomUUID,
                                                      requestingUser = requestingUser,
                                                      appActor = appActor,
                                                      log = log
                                                    )
          } yield requestMessage

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            appConfig = routeData.appConfig,
            appActor = appActor,
            log = log,
            targetSchema = ApiV2Complex,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
      }
    }
  }

  private def updateClass(): Route =
    path(ontologiesBasePath / "classes") {
      put {
        // Change the labels or comments of a class.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangeClassLabelsOrCommentsRequestV2] = for {
              requestingUser <- getUserADM(
                                  requestContext = requestContext,
                                  routeData.appConfig
                                )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage <- ChangeClassLabelsOrCommentsRequestV2.fromJsonLD(
                                  jsonLDDocument = requestDoc,
                                  apiRequestID = randomUUID,
                                  requestingUser = requestingUser,
                                  appActor = appActor,
                                  log = log
                                )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              appConfig = routeData.appConfig,
              appActor = appActor,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  // delete the comment of a class definition
  private def deleteClassComment(): Route =
    path(ontologiesBasePath / "classes" / "comment" / Segment) { classIriStr: IRI =>
      delete { requestContext =>
        val classIri = classIriStr.toSmartIri

        if (!classIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $classIriStr")
        }

        val lastModificationDateStr = requestContext.request.uri
          .query()
          .toMap
          .getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))

        val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(
          lastModificationDateStr,
          throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr")
        )

        val requestMessageFuture: Future[DeleteClassCommentRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield DeleteClassCommentRequestV2(
          classIri = classIri,
          lastModificationDate = lastModificationDate,
          apiRequestID = randomUUID,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          appConfig = routeData.appConfig,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def addCardinalities(): Route =
    path(ontologiesBasePath / "cardinalities") {
      post {
        // Add cardinalities to a class.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[AddCardinalitiesToClassRequestV2] = for {
              requestingUser <- getUserADM(
                                  requestContext = requestContext,
                                  routeData.appConfig
                                )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: AddCardinalitiesToClassRequestV2 <- AddCardinalitiesToClassRequestV2.fromJsonLD(
                                                                    jsonLDDocument = requestDoc,
                                                                    apiRequestID = randomUUID,
                                                                    requestingUser = requestingUser,
                                                                    appActor = appActor,
                                                                    log = log
                                                                  )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              appConfig = routeData.appConfig,
              appActor = appActor,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  private def canReplaceCardinalities: Route =
    // GET basePath/{iriEncode} or
    // GET basePath/{iriEncode}?propertyIri={iriEncode}&newCardinality=[0-1|1|1-n|0-n]
    path(ontologiesBasePath / "canreplacecardinalities" / Segment) { classIri: IRI =>
      get { requestContext =>
        val appConfig = routeData.appConfig
        val responseZio = getUserADMZ(requestContext, appConfig)
          .flatMap(user =>
            RestCardinalityService.canChangeCardinality(
              classIri,
              user,
              propertyIri = getStringQueryParam(requestContext, RestCardinalityService.propertyIriKey),
              newCardinality = getStringQueryParam(requestContext, RestCardinalityService.newCardinalityKey)
            )
          )
        completeZioApiV2ComplexResponse(responseZio, requestContext, appConfig)
      }
    }

  private def replaceCardinalities(): Route =
    path(ontologiesBasePath / "cardinalities") {
      put {
        entity(as[String]) { reqBody => requestContext =>
          {
            val messageF = for {
              user    <- getUserADM(requestContext, appConfig)
              document = JsonLDUtil.parseJsonLD(reqBody)
              msg     <- ReplaceClassCardinalitiesRequestV2.fromJsonLD(document, randomUUID, user, appActor, log)
            } yield msg
            val options = RouteUtilV2.getSchemaOptions(requestContext)
            RouteUtilV2.runRdfRouteWithFuture(messageF, requestContext, appConfig, appActor, log, ApiV2Complex, options)
          }
        }
      }
    }

  private def canDeleteCardinalitiesFromClass(): Route =
    path(ontologiesBasePath / "candeletecardinalities") {
      post {
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[CanDeleteCardinalitiesFromClassRequestV2] = for {
              requestingUser <- getUserADM(
                                  requestContext = requestContext,
                                  routeData.appConfig
                                )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: CanDeleteCardinalitiesFromClassRequestV2 <-
                CanDeleteCardinalitiesFromClassRequestV2.fromJsonLD(
                  jsonLDDocument = requestDoc,
                  apiRequestID = randomUUID,
                  requestingUser = requestingUser,
                  appActor = appActor,
                  log = log
                )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              appConfig = routeData.appConfig,
              appActor = appActor,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  // delete a single cardinality from the specified class if the property is
  // not used in resources.
  private def deleteCardinalitiesFromClass(): Route =
    path(ontologiesBasePath / "cardinalities") {
      patch {
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[DeleteCardinalitiesFromClassRequestV2] = for {
              requestingUser <- getUserADM(
                                  requestContext = requestContext,
                                  routeData.appConfig
                                )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: DeleteCardinalitiesFromClassRequestV2 <- DeleteCardinalitiesFromClassRequestV2.fromJsonLD(
                                                                         jsonLDDocument = requestDoc,
                                                                         apiRequestID = randomUUID,
                                                                         requestingUser = requestingUser,
                                                                         appActor = appActor,
                                                                         log = log
                                                                       )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              appConfig = routeData.appConfig,
              appActor = appActor,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  private def changeGuiOrder(): Route =
    path(ontologiesBasePath / "guiorder") {
      put {
        // Change a class's cardinalities.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangeGuiOrderRequestV2] = for {
              requestingUser <- getUserADM(
                                  requestContext = requestContext,
                                  routeData.appConfig
                                )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: ChangeGuiOrderRequestV2 <- ChangeGuiOrderRequestV2.fromJsonLD(
                                                           jsonLDDocument = requestDoc,
                                                           apiRequestID = randomUUID,
                                                           requestingUser = requestingUser,
                                                           appActor = appActor,
                                                           log = log
                                                         )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              appConfig = routeData.appConfig,
              appActor = appActor,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  private def getClasses(): Route =
    path(ontologiesBasePath / "classes" / Segments) { externalResourceClassIris: List[IRI] =>
      get { requestContext =>
        val classesAndSchemas: Set[(SmartIri, ApiV2Schema)] = externalResourceClassIris.map { classIriStr: IRI =>
          val requestedClassIri: SmartIri =
            classIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid class IRI: $classIriStr"))

          stringFormatter.checkExternalOntologyName(requestedClassIri)

          if (!requestedClassIri.isKnoraApiV2EntityIri) {
            throw BadRequestException(s"Invalid class IRI: $classIriStr")
          }

          val schema = requestedClassIri.getOntologySchema match {
            case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
            case _                              => throw BadRequestException(s"Invalid class IRI: $classIriStr")
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
        val allLanguagesStr             = params.get(ALL_LANGUAGES)
        val allLanguages = stringFormatter.optionStringToBoolean(
          params.get(ALL_LANGUAGES),
          throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr")
        )

        val requestMessageFuture: Future[ClassesGetRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield ClassesGetRequestV2(
          classIris = classesForResponder,
          allLanguages = allLanguages,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          appConfig = routeData.appConfig,
          appActor = appActor,
          log = log,
          targetSchema = targetSchema,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def canDeleteClass(): Route =
    path(ontologiesBasePath / "candeleteclass" / Segment) { classIriStr: IRI =>
      get { requestContext =>
        val classIri = classIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(classIri)

        if (!classIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $classIriStr")
        }

        val requestMessageFuture: Future[CanDeleteClassRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield CanDeleteClassRequestV2(
          classIri = classIri,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          appConfig = routeData.appConfig,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def deleteClass(): Route =
    path(ontologiesBasePath / "classes" / Segments) { externalResourceClassIris: List[IRI] =>
      delete { requestContext =>
        val classIriStr = externalResourceClassIris match {
          case List(str) => str
          case _         => throw BadRequestException(s"Only one class can be deleted at a time")
        }

        val classIri = classIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(classIri)

        if (!classIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $classIriStr")
        }

        val lastModificationDateStr = requestContext.request.uri
          .query()
          .toMap
          .getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))
        val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(
          lastModificationDateStr,
          throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr")
        )

        val requestMessageFuture: Future[DeleteClassRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield DeleteClassRequestV2(
          classIri = classIri,
          lastModificationDate = lastModificationDate,
          apiRequestID = randomUUID,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          appConfig = routeData.appConfig,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def deleteOntologyComment(): Route =
    path(ontologiesBasePath / "comment" / Segment) { ontologyIriStr: IRI =>
      delete { requestContext =>
        val ontologyIri = ontologyIriStr.toSmartIri

        if (!ontologyIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $ontologyIriStr")
        }

        val lastModificationDateStr = requestContext.request.uri
          .query()
          .toMap
          .getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))

        val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(
          lastModificationDateStr,
          throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr")
        )

        val requestMessageFuture: Future[DeleteOntologyCommentRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield DeleteOntologyCommentRequestV2(
          ontologyIri = ontologyIri,
          lastModificationDate = lastModificationDate,
          apiRequestID = randomUUID,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          appConfig = routeData.appConfig,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def createProperty(): Route =
    path(ontologiesBasePath / "properties") {
      post {
        // Create a new property.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[CreatePropertyRequestV2] = for {

              requestingUser: UserADM <- getUserADM(requestContext = requestContext, routeData.appConfig)

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              // get ontology info from request
              inputOntology: InputOntologyV2 = InputOntologyV2.fromJsonLD(requestDoc)

              // get property info from request - in OntologyUpdateHelper.getPropertyDef a lot of validation of the property iri is done
              propertyUpdateInfo: PropertyUpdateInfo = OntologyUpdateHelper.getPropertyDef(inputOntology)

              propertyInfoContent: PropertyInfoContentV2 = propertyUpdateInfo.propertyInfoContent

              // validate property IRI
              _ = PropertyIri.make(propertyInfoContent.propertyIri.toString)

              requestMessage: CreatePropertyRequestV2 <- CreatePropertyRequestV2.fromJsonLD(
                                                           jsonLDDocument = requestDoc,
                                                           apiRequestID = randomUUID,
                                                           requestingUser = requestingUser,
                                                           appActor = appActor,
                                                           log = log
                                                         )
            } yield requestMessage
            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              appConfig = routeData.appConfig,
              appActor = appActor,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  private def updatePropertyLabelsOrComments(): Route =
    path(ontologiesBasePath / "properties") {
      put {
        // Change the labels or comments of a property.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangePropertyLabelsOrCommentsRequestV2] = for {
              requestingUser <- getUserADM(
                                  requestContext = requestContext,
                                  routeData.appConfig
                                )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: ChangePropertyLabelsOrCommentsRequestV2 <- ChangePropertyLabelsOrCommentsRequestV2
                                                                           .fromJsonLD(
                                                                             jsonLDDocument = requestDoc,
                                                                             apiRequestID = randomUUID,
                                                                             requestingUser = requestingUser,
                                                                             appActor = appActor,
                                                                             log = log
                                                                           )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              appConfig = routeData.appConfig,
              appActor = appActor,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  // delete the comment of a property definition
  private def deletePropertyComment(): Route =
    path(ontologiesBasePath / "properties" / "comment" / Segment) { propertyIriStr: IRI =>
      delete { requestContext =>
        val propertyIri = propertyIriStr.toSmartIri

        if (!propertyIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid property IRI for request: $propertyIriStr")
        }

        val lastModificationDateStr = requestContext.request.uri
          .query()
          .toMap
          .getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))

        val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(
          lastModificationDateStr,
          throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr")
        )

        val requestMessageFuture: Future[DeletePropertyCommentRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield DeletePropertyCommentRequestV2(
          propertyIri = propertyIri,
          lastModificationDate = lastModificationDate,
          apiRequestID = randomUUID,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          appConfig = routeData.appConfig,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def updatePropertyGuiElement(): Route =
    path(ontologiesBasePath / "properties" / "guielement") {
      put {
        // Change the salsah-gui:guiElement and/or salsah-gui:guiAttribute of a property.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangePropertyGuiElementRequest] = for {

              requestingUser: UserADM <- getUserADM(requestContext = requestContext, routeData.appConfig)

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              // get ontology info from request
              inputOntology: InputOntologyV2 = InputOntologyV2.fromJsonLD(requestDoc)

              // get property info from request - in OntologyUpdateHelper.getPropertyDef a lot of validation of the property iri is done
              propertyUpdateInfo: PropertyUpdateInfo = OntologyUpdateHelper.getPropertyDef(inputOntology)

              propertyInfoContent: PropertyInfoContentV2 = propertyUpdateInfo.propertyInfoContent
              lastModificationDate                       = propertyUpdateInfo.lastModificationDate

              // get all values from request and validate them by making value objects from it

              // get and validate property IRI
              propertyIri = PropertyIri.make(propertyInfoContent.propertyIri.toString)

              // get the (optional) new gui element from the request
              newGuiElement =
                propertyInfoContent.predicates
                  .get(SalsahGui.External.GuiElementProp.toSmartIri)
                  .map { predicateInfoV2: PredicateInfoV2 =>
                    predicateInfoV2.objects.head match {
                      case guiElement: SmartIriLiteralV2 => guiElement.value.toOntologySchema(InternalSchema).toString()
                      case other                         => throw BadRequestException(s"Unexpected object for salsah-gui:guiElement: $other")
                    }
                  }

              // get the new gui attribute(s) from the request
              newGuiAttributes =
                propertyInfoContent.predicates
                  .get(SalsahGui.External.GuiAttribute.toSmartIri)
                  .map { predicateInfoV2: PredicateInfoV2 =>
                    predicateInfoV2.objects.map {
                      case guiAttribute: StringLiteralV2 => guiAttribute.value
                      case other                         => throw BadRequestException(s"Unexpected object for salsah-gui:guiAttribute: $other")
                    }.toSet
                  }
                  .getOrElse(Set())

              guiObject =
                GuiObject
                  .makeFromStrings(newGuiAttributes, newGuiElement)
                  .fold(
                    e => throw BadRequestException(e.map(error => error.msg).mkString(sys.props("line.separator"))),
                    v => v
                  )

              // create the request message with the validated gui object
              requestMessage = ChangePropertyGuiElementRequest(
                                 propertyIri = propertyIri.fold(e => throw e.head, v => v),
                                 newGuiObject = guiObject,
                                 lastModificationDate = lastModificationDate,
                                 apiRequestID = randomUUID,
                                 requestingUser = requestingUser
                               )

            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              appConfig = routeData.appConfig,
              appActor = appActor,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  private def getProperties(): Route =
    path(ontologiesBasePath / "properties" / Segments) { externalPropertyIris: List[IRI] =>
      get { requestContext =>
        val propsAndSchemas: Set[(SmartIri, ApiV2Schema)] = externalPropertyIris.map { propIriStr: IRI =>
          val requestedPropIri: SmartIri =
            propIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid property IRI: $propIriStr"))

          stringFormatter.checkExternalOntologyName(requestedPropIri)

          if (!requestedPropIri.isKnoraApiV2EntityIri) {
            throw BadRequestException(s"Invalid property IRI: $propIriStr")
          }

          val schema = requestedPropIri.getOntologySchema match {
            case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
            case _                              => throw BadRequestException(s"Invalid property IRI: $propIriStr")
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
        val allLanguagesStr             = params.get(ALL_LANGUAGES)
        val allLanguages = stringFormatter.optionStringToBoolean(
          params.get(ALL_LANGUAGES),
          throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr")
        )

        val requestMessageFuture: Future[PropertiesGetRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield PropertiesGetRequestV2(
          propertyIris = propsForResponder,
          allLanguages = allLanguages,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          appConfig = routeData.appConfig,
          appActor = appActor,
          log = log,
          targetSchema = targetSchema,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def canDeleteProperty(): Route =
    path(ontologiesBasePath / "candeleteproperty" / Segment) { propertyIriStr: IRI =>
      get { requestContext =>
        val propertyIri = propertyIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(propertyIri)

        if (!propertyIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $propertyIriStr")
        }

        val requestMessageFuture: Future[CanDeletePropertyRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield CanDeletePropertyRequestV2(
          propertyIri = propertyIri,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          appConfig = routeData.appConfig,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def deleteProperty(): Route =
    path(ontologiesBasePath / "properties" / Segments) { externalPropertyIris: List[IRI] =>
      delete { requestContext =>
        val propertyIriStr = externalPropertyIris match {
          case List(str) => str
          case _         => throw BadRequestException(s"Only one property can be deleted at a time")
        }

        val propertyIri = propertyIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(propertyIri)

        if (!propertyIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid property IRI for request: $propertyIri")
        }

        val lastModificationDateStr = requestContext.request.uri
          .query()
          .toMap
          .getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))
        val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(
          lastModificationDateStr,
          throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr")
        )

        val requestMessageFuture: Future[DeletePropertyRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield DeletePropertyRequestV2(
          propertyIri = propertyIri,
          lastModificationDate = lastModificationDate,
          apiRequestID = randomUUID,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          appConfig = routeData.appConfig,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def createOntology(): Route = path(ontologiesBasePath) {
    // Create a new, empty ontology.
    post {
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestMessageFuture: Future[CreateOntologyRequestV2] = for {
            requestingUser <- getUserADM(
                                requestContext = requestContext,
                                routeData.appConfig
                              )

            requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

            requestMessage: CreateOntologyRequestV2 <- CreateOntologyRequestV2.fromJsonLD(
                                                         jsonLDDocument = requestDoc,
                                                         apiRequestID = randomUUID,
                                                         requestingUser = requestingUser,
                                                         appActor = appActor,
                                                         log = log
                                                       )
          } yield requestMessage

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            appConfig = routeData.appConfig,
            appActor = appActor,
            log = log,
            targetSchema = ApiV2Complex,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
        }
      }
    }
  }

  private def canDeleteOntology(): Route =
    path(ontologiesBasePath / "candeleteontology" / Segment) { ontologyIriStr: IRI =>
      get { requestContext =>
        val ontologyIri = ontologyIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(ontologyIri)

        if (!ontologyIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid ontology IRI for request: $ontologyIriStr")
        }

        val requestMessageFuture: Future[CanDeleteOntologyRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield CanDeleteOntologyRequestV2(
          ontologyIri = ontologyIri,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          appConfig = routeData.appConfig,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def deleteOntology(): Route = path(ontologiesBasePath / Segment) { ontologyIriStr =>
    delete { requestContext =>
      val ontologyIri = ontologyIriStr.toSmartIri
      stringFormatter.checkExternalOntologyName(ontologyIri)

      if (
        !ontologyIri.isKnoraOntologyIri || ontologyIri.isKnoraBuiltInDefinitionIri || !ontologyIri.getOntologySchema
          .contains(ApiV2Complex)
      ) {
        throw BadRequestException(s"Invalid ontology IRI for request: $ontologyIri")
      }

      val lastModificationDateStr = requestContext.request.uri
        .query()
        .toMap
        .getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))
      val lastModificationDate = stringFormatter.xsdDateTimeStampToInstant(
        lastModificationDateStr,
        throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr")
      )

      val requestMessageFuture: Future[DeleteOntologyRequestV2] = for {
        requestingUser <- getUserADM(
                            requestContext = requestContext,
                            routeData.appConfig
                          )
      } yield DeleteOntologyRequestV2(
        ontologyIri = ontologyIri,
        lastModificationDate = lastModificationDate,
        apiRequestID = randomUUID,
        requestingUser = requestingUser
      )

      RouteUtilV2.runRdfRouteWithFuture(
        requestMessageF = requestMessageFuture,
        requestContext = requestContext,
        appConfig = routeData.appConfig,
        appActor = appActor,
        log = log,
        targetSchema = ApiV2Complex,
        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
      )
    }
  }
}
