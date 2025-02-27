/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.PathMatcher
import org.apache.pekko.http.scaladsl.server.Route
import zio.*
import zio.ZIO

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.GraphRoute
import org.knora.webapi.config.Sipi
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.api.ApiV2.Headers.xKnoraAcceptProject
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.store.iiif.api.SipiService

/**
 * Provides a routing function for API v2 routes that deal with resources.
 */
final case class ResourcesRouteV2(appConfig: AppConfig)(
  private implicit val runtime: Runtime[
    ApiComplexV2JsonLdRequestParser & AppConfig & Authenticator & IriConverter & ProjectService & MessageRelay &
      SearchResponderV2 & SipiService & StringFormatter & UserService,
  ],
) extends LazyLogging {

  private val jsonLdRequestParser = ZIO.serviceWithZIO[ApiComplexV2JsonLdRequestParser]

  private val sipiConfig: Sipi             = appConfig.sipi
  private val resultsPerPage: Int          = appConfig.v2.resourcesSequence.resultsPerPage
  private val graphRouteConfig: GraphRoute = appConfig.v2.graphRoute

  private val resourcesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "resources")

  private val Text_Property          = "textProperty"
  private val Mapping_Iri            = "mappingIri"
  private val GravsearchTemplate_Iri = "gravsearchTemplateIri"
  private val TEIHeader_XSLT_IRI     = "teiHeaderXSLTIri"
  private val Depth                  = "depth"
  private val ExcludeProperty        = "excludeProperty"
  private val Direction              = "direction"
  private val Inbound                = "inbound"
  private val Outbound               = "outbound"
  private val Both                   = "both"

  def makeRoute: Route =
    getIIIFManifest() ~
      createResource() ~
      updateResourceMetadata() ~
      getResourcesInProject() ~
      getResourcesPreview() ~
      getResourcesTei() ~
      getResourcesGraph() ~
      deleteResource() ~
      eraseResource()

  private def getIIIFManifest(): Route =
    path(resourcesBasePath / "iiifmanifest" / Segment) { (resourceIriStr: IRI) =>
      get { requestContext =>
        val requestTask = for {
          resourceIri <- Iri
                           .validateAndEscapeIri(resourceIriStr)
                           .toZIO
                           .orElseFail(BadRequestException(s"Invalid resource IRI: $resourceIriStr"))
          user <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
        } yield ResourceIIIFManifestGetRequestV2(resourceIri, user)
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }

  private def createResource(): Route = path(resourcesBasePath) {
    post {
      entity(as[String]) { jsonRequest => requestContext =>
        val requestTask = for {
          requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
          apiRequestId   <- RouteUtilZ.randomUuid()
          requestMessage <- jsonLdRequestParser(
                              _.createResourceRequestV2(jsonRequest, requestingUser, apiRequestId),
                            ).mapError(BadRequestException.apply)
          // check for each value which represents a file value if the file's MIME type is allowed
          _ <- checkMimeTypesForFileValueContents(requestMessage.createResource.flatValues)
        } yield requestMessage
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }
  }

  private def updateResourceMetadata(): Route = path(resourcesBasePath) {
    put {
      entity(as[String]) { jsonRequest => requestContext =>
        val requestMessageFuture = for {
          requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
          apiRequestId   <- RouteUtilZ.randomUuid()
          requestMessage <-
            jsonLdRequestParser(_.updateResourceMetadataRequestV2(jsonRequest, requestingUser, apiRequestId))
              .mapError(BadRequestException.apply)
        } yield requestMessage
        RouteUtilV2.runRdfRouteZ(requestMessageFuture, requestContext)
      }
    }
  }

  private def getResourcesInProject(): Route = path(resourcesBasePath) {
    get { requestContext =>
      val params: Map[String, String] = requestContext.request.uri.query().toMap

      val getResourceClass = ZIO
        .fromOption(params.get("resourceClass"))
        .orElseFail(BadRequestException(s"This route requires the parameter 'resourceClass'"))
        .flatMap(iri =>
          ZIO
            .serviceWithZIO[IriConverter](_.asSmartIri(iri))
            .orElseFail(BadRequestException(s"Invalid resource class IRI: $iri")),
        )
        .filterOrElseWith(it => it.isKnoraApiV2EntityIri && it.isApiV2ComplexSchema)(it =>
          ZIO.fail(BadRequestException(s"Invalid resource class IRI: $it")),
        )
        .flatMap(it => ZIO.serviceWithZIO[IriConverter](_.asInternalSmartIri(it)))

      val getOrderByProperty: ZIO[IriConverter, Throwable, Option[SmartIri]] =
        ZIO.foreach(params.get("orderByProperty")) { orderByPropertyStr =>
          ZIO
            .serviceWithZIO[IriConverter](_.asSmartIri(orderByPropertyStr))
            .orElseFail(BadRequestException(s"Invalid property IRI: $orderByPropertyStr"))
            .filterOrFail(iri => iri.isKnoraApiV2EntityIri && iri.isApiV2ComplexSchema)(
              BadRequestException(s"Invalid property IRI: $orderByPropertyStr"),
            )
            .flatMap(it => ZIO.serviceWithZIO[IriConverter](_.asInternalSmartIri(it)))
        }

      val getPage = ZIO
        .fromOption(params.get("page"))
        .orElseFail(BadRequestException(s"This route requires the parameter 'page'"))
        .flatMap(pageStr =>
          ZIO
            .fromOption(ValuesValidator.validateInt(pageStr))
            .orElseFail(BadRequestException(s"Invalid page number: $pageStr")),
        )

      val getProjectIri = RouteUtilV2
        .getProjectIri(requestContext)
        .some
        .orElseFail(BadRequestException(s"This route requires the request header $xKnoraAcceptProject"))

      val targetSchemaTask = RouteUtilV2.getOntologySchema(requestContext)
      val response = for {
        maybeOrderByProperty <- getOrderByProperty
        resourceClass        <- getResourceClass
        projectIri           <- getProjectIri
        page                 <- getPage
        targetSchema <- targetSchemaTask.zip(RouteUtilV2.getSchemaOptions(requestContext)).map {
                          case (schema, options) => SchemaRendering(schema, options)
                        }
        requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
        response <- ZIO.serviceWithZIO[SearchResponderV2](
                      _.searchResourcesByProjectAndClassV2(
                        projectIri,
                        resourceClass,
                        maybeOrderByProperty,
                        page,
                        targetSchema,
                        requestingUser,
                      ),
                    )
      } yield response

      RouteUtilV2.completeResponse(response, requestContext, targetSchemaTask)
    }
  }

  private def getResourceIris(resIris: Seq[IRI]): IO[BadRequestException, Seq[IRI]] =
    ZIO
      .fail(BadRequestException(s"List of provided resource Iris exceeds limit of $resultsPerPage"))
      .when(resIris.size > resultsPerPage) *>
      ZIO.foreach(resIris) { (resIri: IRI) =>
        Iri
          .validateAndEscapeIri(resIri)
          .toZIO
          .orElseFail(BadRequestException(s"Invalid resource IRI: <$resIri>"))
      }

  private def getResourcesPreview(): Route =
    path("v2" / "resourcespreview" / Segments) { (resIris: Seq[String]) =>
      get { requestContext =>
        val targetSchemaTask = RouteUtilV2.getOntologySchema(requestContext)
        val requestTask = for {
          resourceIris <- getResourceIris(resIris)
          targetSchema <- targetSchemaTask
          user         <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
        } yield ResourcesPreviewGetRequestV2(resourceIris, withDeletedResource = true, targetSchema, user)
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext, targetSchemaTask)
      }
    }

  private def getResourcesTei(): Route = path("v2" / "tei" / Segment) { (resIri: String) =>
    get { requestContext =>
      val params: Map[String, String] = requestContext.request.uri.query().toMap
      val getResourceIri =
        Iri
          .validateAndEscapeIri(resIri)
          .toZIO
          .orElseFail(BadRequestException(s"Invalid resource IRI: <$resIri>"))
      val requestTask = for {
        resourceIri           <- getResourceIri
        mappingIri            <- getMappingIriFromParams(params)
        textProperty          <- getTextPropertyFromParams(params)
        gravsearchTemplateIri <- getGravsearchTemplateIriFromParams(params)
        headerXSLTIri         <- getHeaderXSLTIriFromParams(params)
        user                  <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
      } yield ResourceTEIGetRequestV2(resourceIri, textProperty, mappingIri, gravsearchTemplateIri, headerXSLTIri, user)
      RouteUtilV2.runTEIXMLRoute(requestTask, requestContext)
    }
  }

  private def getResourcesGraph(): Route = path("v2" / "graph" / Segment) { (resIriStr: String) =>
    get { requestContext =>
      val getResourceIri =
        Iri
          .validateAndEscapeIri(resIriStr)
          .toZIO
          .orElseFail(BadRequestException(s"Invalid resource IRI: <$resIriStr>"))
      val params: Map[String, String] = requestContext.request.uri.query().toMap
      val getDepth: IO[BadRequestException, Int] =
        ZIO
          .succeed(params.get(Depth).flatMap(ValuesValidator.validateInt).getOrElse(graphRouteConfig.defaultGraphDepth))
          .filterOrFail(_ >= 1)(BadRequestException(s"$Depth must be at least 1"))
          .filterOrFail(_ <= graphRouteConfig.maxGraphDepth)(
            BadRequestException(s"$Depth cannot be greater than ${graphRouteConfig.maxGraphDepth}"),
          )

      val getExcludeProperty: ZIO[IriConverter, BadRequestException, Option[SmartIri]] = params
        .get(ExcludeProperty)
        .map(propIriStr =>
          ZIO
            .serviceWithZIO[IriConverter](_.asSmartIri(propIriStr))
            .mapBoth(_ => BadRequestException(s"Invalid property IRI: <$propIriStr>"), Some(_)),
        )
        .getOrElse(ZIO.none)

      val getInboundOutbound: IO[BadRequestException, (Boolean, Boolean)] =
        params.getOrElse(Direction, Outbound) match {
          case Inbound  => ZIO.succeed((true, false))
          case Outbound => ZIO.succeed((false, true))
          case Both     => ZIO.succeed((true, true))
          case other    => ZIO.fail(BadRequestException(s"Invalid direction: $other"))
        }

      val requestTask = for {
        resourceIri        <- getResourceIri
        depth              <- getDepth
        excludeProperty    <- getExcludeProperty
        t                  <- getInboundOutbound
        (inbound, outbound) = t
        requestingUser     <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
      } yield GraphDataGetRequestV2(resourceIri, depth, inbound, outbound, excludeProperty, requestingUser)
      RouteUtilV2.runRdfRouteZ(requestTask, requestContext, RouteUtilV2.getOntologySchema(requestContext))
    }
  }

  private def deleteResource(): Route = path(resourcesBasePath / "delete") {
    post {
      entity(as[String]) { jsonRequest => requestContext =>
        val requestTask = for {
          apiRequestId   <- RouteUtilZ.randomUuid()
          requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
          msg <- jsonLdRequestParser(_.deleteOrEraseResourceRequestV2(jsonRequest, requestingUser, apiRequestId))
                   .mapError(BadRequestException.apply)
        } yield msg
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }
  }

  private def eraseResource(): Route = path(resourcesBasePath / "erase") {
    post {
      entity(as[String]) { jsonRequest => requestContext =>
        val requestTask = for {
          apiRequestId   <- RouteUtilZ.randomUuid()
          requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
          requestMessage <-
            jsonLdRequestParser(_.deleteOrEraseResourceRequestV2(jsonRequest, requestingUser, apiRequestId))
              .mapError(BadRequestException.apply)
        } yield requestMessage.copy(erase = true)
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }
  }

  /**
   * Gets the Iri of the property that represents the text of the resource.
   *
   * @param params the GET parameters.
   * @return the internal resource class, if any.
   */
  private def getTextPropertyFromParams(params: Map[String, String]): ZIO[IriConverter, Throwable, SmartIri] =
    ZIO
      .fromOption(params.get(Text_Property))
      .orElseFail(BadRequestException(s"param $Text_Property not set"))
      .flatMap { textPropIriStr =>
        ZIO
          .serviceWithZIO[IriConverter](_.asSmartIri(textPropIriStr))
          .orElseFail(BadRequestException(s"Invalid property IRI: <$textPropIriStr>"))
          .filterOrFail(_.isKnoraApiV2EntityIri)(
            BadRequestException(s"<$textPropIriStr> is not a valid knora-api property IRI"),
          )
          .mapAttempt(_.toOntologySchema(InternalSchema))
      }

  /**
   * Gets the Iri of the mapping to be used to convert standoff to XML.
   *
   * @param params the GET parameters.
   * @return the internal resource class, if any.
   */
  private def getMappingIriFromParams(params: Map[String, String]): IO[BadRequestException, Option[IRI]] =
    params
      .get(Mapping_Iri)
      .map { mapping =>
        Iri
          .validateAndEscapeIri(mapping)
          .toZIO
          .mapBoth(_ => BadRequestException(s"Invalid mapping IRI: <$mapping>"), Some(_))
      }
      .getOrElse(ZIO.none)

  /**
   * Gets the Iri of Gravsearch template to be used to query for the resource's metadata.
   *
   * @param params the GET parameters.
   * @return the internal resource class, if any.
   */
  private def getGravsearchTemplateIriFromParams(params: Map[String, String]): IO[BadRequestException, Option[IRI]] =
    params
      .get(GravsearchTemplate_Iri)
      .map { gravsearch =>
        Iri
          .validateAndEscapeIri(gravsearch)
          .toZIO
          .mapBoth(_ => BadRequestException(s"Invalid template IRI: <$gravsearch>"), Some(_))
      }
      .getOrElse(ZIO.none)

  /**
   * Gets the Iri of the XSL transformation to be used to convert the TEI header's metadata.
   *
   * @param params the GET parameters.
   * @return the internal resource class, if any.
   */
  private def getHeaderXSLTIriFromParams(params: Map[String, String]): IO[BadRequestException, Option[IRI]] =
    params
      .get(TEIHeader_XSLT_IRI)
      .map { xslt =>
        Iri
          .validateAndEscapeIri(xslt)
          .toZIO
          .mapBoth(_ => BadRequestException(s"Invalid XSLT IRI: <$xslt>"), Some(_))
      }
      .getOrElse(ZIO.none)

  /**
   * Checks if the MIME types of the given values are allowed by the configuration
   *
   * @param values the values to be checked.
   */
  private def checkMimeTypesForFileValueContents(
    values: Iterable[CreateValueInNewResourceV2],
  ): Task[Unit] = {
    def failBadRequest(fileValueContent: FileValueContentV2): IO[BadRequestException, Unit] = {
      val msg =
        s"File ${fileValueContent.fileValue.internalFilename} has MIME type ${fileValueContent.fileValue.internalMimeType}, which is not supported for still image files"
      ZIO.fail(BadRequestException(msg))
    }
    ZIO
      .foreach(values) { value =>
        value.valueContent match {
          case fileValueContent: StillImageFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.imageMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case fileValueContent: DocumentFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.documentMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case fileValueContent: ArchiveFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.archiveMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case fileValueContent: TextFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.textMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case fileValueContent: AudioFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.audioMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case fileValueContent: MovingImageFileValueContentV2 =>
            failBadRequest(fileValueContent)
              .when(!sipiConfig.videoMimeTypes.contains(fileValueContent.fileValue.internalMimeType))
          case _ => ZIO.unit
        }
      }
      .unit
  }
}
