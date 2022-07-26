/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import akka.actor.Status
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import dsp.constants.SalsahGui
import dsp.errors.BadRequestException
import dsp.valueobjects.Iri._
import dsp.valueobjects.Schema._
import dsp.valueobjects.V2
import org.knora.webapi.ApiV2Complex
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
import zio.ZIO
import zio.prelude.Validation

import java.util.UUID
import javax.validation.Valid
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import dsp.errors.ValidationException
import org.knora.webapi.messages.store.triplestoremessages.BooleanLiteralV2
import dsp.valueobjects.LangString

import dsp.schema.domain.CreatePropertyCommand
import dsp.schema.domain.{SmartIri => NotAnotherSmartIri}

object OntologiesRouteV2 {
  val OntologiesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "ontologies")
}

/**
 * Provides a routing function for API v2 routes that deal with ontologies.
 */
class OntologiesRouteV2(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

  import OntologiesRouteV2._

  private val ALL_LANGUAGES          = "allLanguages"
  private val LAST_MODIFICATION_DATE = "lastModificationDate"

  /**
   * Returns the route.
   */
  override def makeRoute(): Route =
    dereferenceOntologyIri() ~
      getOntologyMetadata() ~
      updateOntologyMetadata() ~
      getOntologyMetadataForProjects() ~
      getOntology() ~
      createClass() ~
      updateClass() ~
      deleteClassComment() ~
      addCardinalities() ~
      canReplaceCardinalities() ~
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
                            requestContext = requestContext
                          )
      } yield OntologyEntitiesGetRequestV2(
        ontologyIri = requestedOntology,
        allLanguages = allLanguages,
        requestingUser = requestingUser
      )

      RouteUtilV2.runRdfRouteWithFuture(
        requestMessageF = requestMessageFuture,
        requestContext = requestContext,
        settings = settings,
        appActor = appActor,
        log = log,
        targetSchema = targetSchema,
        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
      )
    }
  }

  private def getOntologyMetadata(): Route =
    path(OntologiesBasePath / "metadata") {
      get { requestContext =>
        val maybeProjectIri: Option[SmartIri] = RouteUtilV2.getProject(requestContext)

        val requestMessageFuture: Future[OntologyMetadataGetByProjectRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
        } yield OntologyMetadataGetByProjectRequestV2(
          projectIris = maybeProjectIri.toSet,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def updateOntologyMetadata(): Route =
    path(OntologiesBasePath / "metadata") {
      put {
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

            val requestMessageFuture: Future[ChangeOntologyMetadataRequestV2] = for {
              requestingUser <- getUserADM(
                                  requestContext = requestContext
                                )

              requestMessage: ChangeOntologyMetadataRequestV2 <- ChangeOntologyMetadataRequestV2.fromJsonLD(
                                                                   jsonLDDocument = requestDoc,
                                                                   apiRequestID = UUID.randomUUID,
                                                                   requestingUser = requestingUser,
                                                                   appActor = appActor,
                                                                   settings = settings,
                                                                   log = log
                                                                 )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
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
    path(OntologiesBasePath / "metadata" / Segments) { projectIris: List[IRI] =>
      get { requestContext =>
        val requestMessageFuture: Future[OntologyMetadataGetByProjectRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
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
          settings = settings,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def getOntology(): Route =
    path(OntologiesBasePath / "allentities" / Segment) { externalOntologyIriStr: IRI =>
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
                              requestContext = requestContext
                            )
        } yield OntologyEntitiesGetRequestV2(
          ontologyIri = requestedOntologyIri,
          allLanguages = allLanguages,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log,
          targetSchema = targetSchema,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def createClass(): Route = path(OntologiesBasePath / "classes") {
    post {
      // Create a new class.
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestMessageFuture: Future[CreateClassRequestV2] = for {
            requestingUser <- getUserADM(
                                requestContext = requestContext
                              )

            requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

            requestMessage: CreateClassRequestV2 <- CreateClassRequestV2.fromJsonLD(
                                                      jsonLDDocument = requestDoc,
                                                      apiRequestID = UUID.randomUUID,
                                                      requestingUser = requestingUser,
                                                      appActor = appActor,
                                                      settings = settings,
                                                      log = log
                                                    )
          } yield requestMessage

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            settings = settings,
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
    path(OntologiesBasePath / "classes") {
      put {
        // Change the labels or comments of a class.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangeClassLabelsOrCommentsRequestV2] = for {
              requestingUser <- getUserADM(
                                  requestContext = requestContext
                                )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage <- ChangeClassLabelsOrCommentsRequestV2.fromJsonLD(
                                  jsonLDDocument = requestDoc,
                                  apiRequestID = UUID.randomUUID,
                                  requestingUser = requestingUser,
                                  appActor = appActor,
                                  settings = settings,
                                  log = log
                                )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
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
    path(OntologiesBasePath / "classes" / "comment" / Segment) { classIriStr: IRI =>
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
                              requestContext = requestContext
                            )
        } yield DeleteClassCommentRequestV2(
          classIri = classIri,
          lastModificationDate = lastModificationDate,
          apiRequestID = UUID.randomUUID,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def addCardinalities(): Route =
    path(OntologiesBasePath / "cardinalities") {
      post {
        // Add cardinalities to a class.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[AddCardinalitiesToClassRequestV2] = for {
              requestingUser <- getUserADM(
                                  requestContext = requestContext
                                )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: AddCardinalitiesToClassRequestV2 <- AddCardinalitiesToClassRequestV2.fromJsonLD(
                                                                    jsonLDDocument = requestDoc,
                                                                    apiRequestID = UUID.randomUUID,
                                                                    requestingUser = requestingUser,
                                                                    appActor = appActor,
                                                                    settings = settings,
                                                                    log = log
                                                                  )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
              appActor = appActor,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  private def canReplaceCardinalities(): Route =
    path(OntologiesBasePath / "canreplacecardinalities" / Segment) { classIriStr: IRI =>
      get { requestContext =>
        val classIri = classIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(classIri)

        if (!classIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $classIriStr")
        }

        val requestMessageFuture: Future[CanChangeCardinalitiesRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
        } yield CanChangeCardinalitiesRequestV2(
          classIri = classIri,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  // Replaces all cardinalities with what was sent. Deleting means send empty
  // replace request.
  private def replaceCardinalities(): Route =
    path(OntologiesBasePath / "cardinalities") {
      put {
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangeCardinalitiesRequestV2] = for {
              requestingUser <- getUserADM(
                                  requestContext = requestContext
                                )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: ChangeCardinalitiesRequestV2 <- ChangeCardinalitiesRequestV2.fromJsonLD(
                                                                jsonLDDocument = requestDoc,
                                                                apiRequestID = UUID.randomUUID,
                                                                requestingUser = requestingUser,
                                                                appActor = appActor,
                                                                settings = settings,
                                                                log = log
                                                              )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
              appActor = appActor,
              log = log,
              targetSchema = ApiV2Complex,
              schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
            )
          }
        }
      }
    }

  private def canDeleteCardinalitiesFromClass(): Route =
    path(OntologiesBasePath / "candeletecardinalities") {
      post {
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[CanDeleteCardinalitiesFromClassRequestV2] = for {
              requestingUser <- getUserADM(
                                  requestContext = requestContext
                                )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: CanDeleteCardinalitiesFromClassRequestV2 <-
                CanDeleteCardinalitiesFromClassRequestV2.fromJsonLD(
                  jsonLDDocument = requestDoc,
                  apiRequestID = UUID.randomUUID,
                  requestingUser = requestingUser,
                  appActor = appActor,
                  settings = settings,
                  log = log
                )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
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
    path(OntologiesBasePath / "cardinalities") {
      patch {
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[DeleteCardinalitiesFromClassRequestV2] = for {
              requestingUser <- getUserADM(
                                  requestContext = requestContext
                                )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: DeleteCardinalitiesFromClassRequestV2 <- DeleteCardinalitiesFromClassRequestV2.fromJsonLD(
                                                                         jsonLDDocument = requestDoc,
                                                                         apiRequestID = UUID.randomUUID,
                                                                         requestingUser = requestingUser,
                                                                         appActor = appActor,
                                                                         settings = settings,
                                                                         log = log
                                                                       )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
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
    path(OntologiesBasePath / "guiorder") {
      put {
        // Change a class's cardinalities.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangeGuiOrderRequestV2] = for {
              requestingUser <- getUserADM(
                                  requestContext = requestContext
                                )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: ChangeGuiOrderRequestV2 <- ChangeGuiOrderRequestV2.fromJsonLD(
                                                           jsonLDDocument = requestDoc,
                                                           apiRequestID = UUID.randomUUID,
                                                           requestingUser = requestingUser,
                                                           appActor = appActor,
                                                           settings = settings,
                                                           log = log
                                                         )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
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
    path(OntologiesBasePath / "classes" / Segments) { externalResourceClassIris: List[IRI] =>
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
                              requestContext = requestContext
                            )
        } yield ClassesGetRequestV2(
          classIris = classesForResponder,
          allLanguages = allLanguages,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log,
          targetSchema = targetSchema,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def canDeleteClass(): Route =
    path(OntologiesBasePath / "candeleteclass" / Segment) { classIriStr: IRI =>
      get { requestContext =>
        val classIri = classIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(classIri)

        if (!classIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $classIriStr")
        }

        val requestMessageFuture: Future[CanDeleteClassRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
        } yield CanDeleteClassRequestV2(
          classIri = classIri,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def deleteClass(): Route =
    path(OntologiesBasePath / "classes" / Segments) { externalResourceClassIris: List[IRI] =>
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
                              requestContext = requestContext
                            )
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
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def deleteOntologyComment(): Route =
    path(OntologiesBasePath / "comment" / Segment) { ontologyIriStr: IRI =>
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
                              requestContext = requestContext
                            )
        } yield DeleteOntologyCommentRequestV2(
          ontologyIri = ontologyIri,
          lastModificationDate = lastModificationDate,
          apiRequestID = UUID.randomUUID,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def createProperty(): Route =
    path(OntologiesBasePath / "properties") {
      post {
        // Create a new property.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[CreatePropertyRequestV2] = for {
              requestingUser: UserADM <- getUserADM(
                                           requestContext = requestContext
                                         )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: CreatePropertyRequestV2 <- CreatePropertyRequestV2.fromJsonLD(
                                                           jsonLDDocument = requestDoc,
                                                           apiRequestID = UUID.randomUUID,
                                                           requestingUser = requestingUser,
                                                           appActor = appActor,
                                                           settings = settings,
                                                           log = log
                                                         )

              // get gui related values from request and validate them by making value objects from it

              // get ontology info from request
              inputOntology: InputOntologyV2             = InputOntologyV2.fromJsonLD(requestDoc)
              propertyUpdateInfo: PropertyUpdateInfo     = OntologyUpdateHelper.getPropertyDef(inputOntology)
              propertyInfoContent: PropertyInfoContentV2 = propertyUpdateInfo.propertyInfoContent

              // get the (optional) gui element
              maybeGuiElement: Option[SmartIri] =
                propertyInfoContent.predicates
                  .get(SalsahGui.External.GuiElementProp.toSmartIri)
                  .map { predicateInfoV2: PredicateInfoV2 =>
                    predicateInfoV2.objects.head match {
                      case guiElement: SmartIriLiteralV2 => guiElement.value.toOntologySchema(InternalSchema)
                      case other                         => throw BadRequestException(s"Unexpected object for salsah-gui:guiElement: $other")
                    }
                  }

              // validate the gui element by creating value object
              validatedGuiElement = maybeGuiElement match {
                                      case Some(guiElement) => GuiElement.make(guiElement.toString()).map(Some(_))
                                      case None             => Validation.succeed(None)
                                    }

              // get the gui attribute(s)
              maybeGuiAttributes: List[String] =
                propertyInfoContent.predicates
                  .get(SalsahGui.External.GuiAttribute.toSmartIri)
                  .map { predicateInfoV2: PredicateInfoV2 =>
                    predicateInfoV2.objects.map {
                      case guiAttribute: StringLiteralV2 => guiAttribute.value
                      case other                         => throw BadRequestException(s"Unexpected object for salsah-gui:guiAttribute: $other")
                    }.toList
                  }
                  .getOrElse(List())

              // validate the gui attributes by creating value objects
              guiAttributes = maybeGuiAttributes.map(guiAttribute => GuiAttribute.make(guiAttribute)).toList

              validatedGuiAttributes = Validation.validateAll(guiAttributes)

              // validate the combination of gui element and gui attribute by creating a GuiObject value object
              guiObject = Validation
                            .validate(validatedGuiAttributes, validatedGuiElement)
                            .flatMap(values => GuiObject.make(values._1, values._2))

              ontologyIri =
                Validation.succeed(NotAnotherSmartIri(inputOntology.ontologyMetadata.ontologyIri.toString()))
              lastModificationDate = Validation.succeed(propertyUpdateInfo.lastModificationDate)
              propertyIri          = Validation.succeed(NotAnotherSmartIri(propertyInfoContent.propertyIri.toString()))
              subjectType = propertyInfoContent.predicates.get(
                              OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri
                            ) match {
                              case None => Validation.succeed(None)
                              case Some(value) =>
                                value.objects.head match {
                                  case objectType: SmartIriLiteralV2 =>
                                    Validation.succeed(
                                      Some(
                                        NotAnotherSmartIri(objectType.value.toOntologySchema(InternalSchema).toString())
                                      )
                                    )
                                  case other =>
                                    Validation.fail(ValidationException(s"Unexpected subject type for $other"))
                                }
                            }
              objectType =
                propertyInfoContent.predicates.get(OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri) match {
                  case None => Validation.fail(ValidationException(s"Object type cannot be empty."))
                  case Some(value) =>
                    value.objects.head match {
                      case objectType: SmartIriLiteralV2 =>
                        Validation.succeed(
                          NotAnotherSmartIri(objectType.value.toOntologySchema(InternalSchema).toString())
                        )
                      case other => Validation.fail(ValidationException(s"Unexpected object type for $other"))
                    }
                }
              label = propertyInfoContent.predicates.get(OntologyConstants.Rdfs.Label.toSmartIri) match {
                        case None => Validation.fail(ValidationException("Label missing"))
                        case Some(value) =>
                          value.objects.head match {
                            case StringLiteralV2(value, Some(language)) => LangString.makeFromStrings(language, value)
                            case StringLiteralV2(_, None) =>
                              Validation.fail(ValidationException("Label missing the language tag"))
                            case _ => Validation.fail(ValidationException("Unexpected Type for Label"))
                          }
                      }
              comment = propertyInfoContent.predicates.get(OntologyConstants.Rdfs.Comment.toSmartIri) match {
                          case None => Validation.succeed(None)
                          case Some(value) =>
                            value.objects.head match {
                              case StringLiteralV2(value, Some(language)) =>
                                LangString.makeFromStrings(language, value).map(Some(_))
                              case StringLiteralV2(_, None) =>
                                Validation.fail(ValidationException("Comment missing the language tag"))
                              case _ => Validation.fail(ValidationException("Unexpected Type for Comment"))
                            }
                        }
              superProperties =
                propertyInfoContent.subPropertyOf.toList.map(smartIri =>
                  NotAnotherSmartIri(smartIri.toString())
                ) match {
                  case Nil        => Validation.fail(ValidationException("SuperProperties cannot be empty."))
                  case superProps => Validation.succeed(superProps)
                }

              createPropertyCommand: CreatePropertyCommand =
                Validation
                  .validate(
                    ontologyIri,
                    lastModificationDate,
                    propertyIri,
                    subjectType,
                    objectType,
                    label,
                    comment,
                    superProperties,
                    guiObject
                  )
                  .flatMap(values =>
                    CreatePropertyCommand.make(
                      values._1,
                      values._2,
                      values._3,
                      values._4,
                      values._5,
                      values._6,
                      values._7,
                      values._8,
                      values._9
                    )
                  )
                  .fold(e => throw e.head, v => v)
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
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
    path(OntologiesBasePath / "properties") {
      put {
        // Change the labels or comments of a property.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangePropertyLabelsOrCommentsRequestV2] = for {
              requestingUser <- getUserADM(
                                  requestContext = requestContext
                                )

              requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

              requestMessage: ChangePropertyLabelsOrCommentsRequestV2 <- ChangePropertyLabelsOrCommentsRequestV2
                                                                           .fromJsonLD(
                                                                             jsonLDDocument = requestDoc,
                                                                             apiRequestID = UUID.randomUUID,
                                                                             requestingUser = requestingUser,
                                                                             appActor = appActor,
                                                                             settings = settings,
                                                                             log = log
                                                                           )
            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
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
    path(OntologiesBasePath / "properties" / "comment" / Segment) { propertyIriStr: IRI =>
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
                              requestContext = requestContext
                            )
        } yield DeletePropertyCommentRequestV2(
          propertyIri = propertyIri,
          lastModificationDate = lastModificationDate,
          apiRequestID = UUID.randomUUID,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def updatePropertyGuiElement(): Route =
    path(OntologiesBasePath / "properties" / "guielement") {
      put {
        // Change the salsah-gui:guiElement and/or salsah-gui:guiAttribute of a property.
        entity(as[String]) { jsonRequest => requestContext =>
          {
            val requestMessageFuture: Future[ChangePropertyGuiElementRequest] = for {

              requestingUser: UserADM <- getUserADM(
                                           requestContext = requestContext
                                         )

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

              // get the (optional) new gui element
              newGuiElement: Option[SmartIri] =
                propertyInfoContent.predicates
                  .get(SalsahGui.External.GuiElementProp.toSmartIri)
                  .map { predicateInfoV2: PredicateInfoV2 =>
                    predicateInfoV2.objects.head match {
                      case guiElement: SmartIriLiteralV2 => guiElement.value.toOntologySchema(InternalSchema)
                      case other                         => throw BadRequestException(s"Unexpected object for salsah-gui:guiElement: $other")
                    }
                  }

              // validate the new gui element by creating value object
              validatedNewGuiElement = newGuiElement match {
                                         case Some(guiElement) => GuiElement.make(guiElement.toString()).map(Some(_))
                                         case None             => Validation.succeed(None)
                                       }

              // get the new gui attribute(s)
              newGuiAttributes: List[String] =
                propertyInfoContent.predicates
                  .get(SalsahGui.External.GuiAttribute.toSmartIri)
                  .map { predicateInfoV2: PredicateInfoV2 =>
                    predicateInfoV2.objects.map {
                      case guiAttribute: StringLiteralV2 => guiAttribute.value
                      case other                         => throw BadRequestException(s"Unexpected object for salsah-gui:guiAttribute: $other")
                    }.toList
                  }
                  .getOrElse(List())

              // validate the new gui attributes by creating value objects
              guiAttributes = newGuiAttributes.map(guiAttribute => GuiAttribute.make(guiAttribute))

              validatedGuiAttributes = Validation.validateAll(guiAttributes)

              // validate the combination of gui element and gui attribute by creating a GuiObject value object
              guiObject =
                Validation
                  .validate(validatedGuiAttributes, validatedNewGuiElement)
                  .flatMap(values => GuiObject.make(values._1, values._2))
                  .fold(e => throw e.head, v => v)

              // create the request message with the validated gui object
              requestMessage = ChangePropertyGuiElementRequest(
                                 propertyIri = propertyIri.fold(e => throw e.head, v => v),
                                 newGuiObject = guiObject,
                                 lastModificationDate = lastModificationDate,
                                 apiRequestID = UUID.randomUUID,
                                 requestingUser = requestingUser
                               )

            } yield requestMessage

            RouteUtilV2.runRdfRouteWithFuture(
              requestMessageF = requestMessageFuture,
              requestContext = requestContext,
              settings = settings,
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
    path(OntologiesBasePath / "properties" / Segments) { externalPropertyIris: List[IRI] =>
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
                              requestContext = requestContext
                            )
        } yield PropertiesGetRequestV2(
          propertyIris = propsForResponder,
          allLanguages = allLanguages,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log,
          targetSchema = targetSchema,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def canDeleteProperty(): Route =
    path(OntologiesBasePath / "candeleteproperty" / Segment) { propertyIriStr: IRI =>
      get { requestContext =>
        val propertyIri = propertyIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(propertyIri)

        if (!propertyIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid class IRI for request: $propertyIriStr")
        }

        val requestMessageFuture: Future[CanDeletePropertyRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
        } yield CanDeletePropertyRequestV2(
          propertyIri = propertyIri,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def deleteProperty(): Route =
    path(OntologiesBasePath / "properties" / Segments) { externalPropertyIris: List[IRI] =>
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
                              requestContext = requestContext
                            )
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
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def createOntology(): Route = path(OntologiesBasePath) {
    // Create a new, empty ontology.
    post {
      entity(as[String]) { jsonRequest => requestContext =>
        {
          val requestMessageFuture: Future[CreateOntologyRequestV2] = for {
            requestingUser <- getUserADM(
                                requestContext = requestContext
                              )

            requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

            requestMessage: CreateOntologyRequestV2 <- CreateOntologyRequestV2.fromJsonLD(
                                                         jsonLDDocument = requestDoc,
                                                         apiRequestID = UUID.randomUUID,
                                                         requestingUser = requestingUser,
                                                         appActor = appActor,
                                                         settings = settings,
                                                         log = log
                                                       )
          } yield requestMessage

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessageFuture,
            requestContext = requestContext,
            settings = settings,
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
    path(OntologiesBasePath / "candeleteontology" / Segment) { ontologyIriStr: IRI =>
      get { requestContext =>
        val ontologyIri = ontologyIriStr.toSmartIri
        stringFormatter.checkExternalOntologyName(ontologyIri)

        if (!ontologyIri.getOntologySchema.contains(ApiV2Complex)) {
          throw BadRequestException(s"Invalid ontology IRI for request: $ontologyIriStr")
        }

        val requestMessageFuture: Future[CanDeleteOntologyRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
        } yield CanDeleteOntologyRequestV2(
          ontologyIri = ontologyIri,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessageFuture,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
      }
    }

  private def deleteOntology(): Route = path(OntologiesBasePath / Segment) { ontologyIriStr =>
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
                            requestContext = requestContext
                          )
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
        appActor = appActor,
        log = log,
        targetSchema = ApiV2Complex,
        schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
      )
    }
  }
}
