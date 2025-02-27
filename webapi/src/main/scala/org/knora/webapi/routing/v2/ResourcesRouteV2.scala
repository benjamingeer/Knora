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
import org.knora.webapi.config.Sipi
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
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

  private val sipiConfig: Sipi = appConfig.sipi

  private val resourcesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "resources")

  private val Text_Property          = "textProperty"
  private val Mapping_Iri            = "mappingIri"
  private val GravsearchTemplate_Iri = "gravsearchTemplateIri"
  private val TEIHeader_XSLT_IRI     = "teiHeaderXSLTIri"

  def makeRoute: Route = createResource() ~ getResourcesTei()

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
