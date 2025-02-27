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
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.Sipi
import org.knora.webapi.core.MessageRelay
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

  private val jsonLdRequestParser                  = ZIO.serviceWithZIO[ApiComplexV2JsonLdRequestParser]
  private val sipiConfig: Sipi                     = appConfig.sipi
  private val resourcesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "resources")

  def makeRoute: Route = createResource()

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
