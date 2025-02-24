/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko
import zio.Runtime
import zio.ZIO

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*

import dsp.errors.BadRequestException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.standoffmessages.CreateMappingRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.CreateMappingRequestXMLV2
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.security.Authenticator

import pekko.actor.ActorSystem
import pekko.http.scaladsl.model.Multipart
import pekko.http.scaladsl.model.Multipart.BodyPart
import pekko.http.scaladsl.server.Directives.*
import pekko.http.scaladsl.server.Route

final case class StandoffRouteV2()(
  private implicit val runtime: Runtime[
    ApiComplexV2JsonLdRequestParser & AppConfig & Authenticator & IriConverter & StringFormatter & MessageRelay,
  ],
  private implicit val system: ActorSystem,
) extends LazyLogging {
  private implicit val ec: ExecutionContext = system.dispatcher

  def makeRoute: Route =
    path("v2" / "mapping") {
      post {
        entity(as[Multipart.FormData]) { (formData: Multipart.FormData) => requestContext =>
          val jsonPartKey = "json"
          val xmlPartKey  = "xml"
          type Name = String

          // collect all parts of the multipart as it arrives into a map
          val allPartsFuture: Future[Map[Name, String]] = formData.parts
            .mapAsync[(Name, String)](1) {
              case b: BodyPart if b.name == jsonPartKey =>
                b.toStrict(2.seconds).map { strict =>
                  (b.name, strict.entity.data.utf8String)
                }

              case b: BodyPart if b.name == xmlPartKey =>
                b.toStrict(2.seconds).map { strict =>
                  (b.name, strict.entity.data.utf8String)
                }

              case b: BodyPart if b.name.isEmpty =>
                throw BadRequestException("part of HTTP multipart request has no name")

              case b: BodyPart => throw BadRequestException(s"multipart contains invalid name: ${b.name}")
            }
            .runFold(Map.empty[Name, String])((map, tuple) => map + tuple)

          val requestMessageTask = for {
            requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
            allParts       <- ZIO.fromFuture(_ => allPartsFuture)
            metadata <-
              ZIO
                .fromOption(allParts.get(jsonPartKey))
                .orElseFail(
                  BadRequestException(s"MultiPart POST request was sent without required '$jsonPartKey' part!"),
                )
                .flatMap(jsonLd =>
                  ZIO
                    .serviceWithZIO[ApiComplexV2JsonLdRequestParser](_.createMappingRequestMetadataV2(jsonLd))
                    .mapError(BadRequestException.apply),
                )
            xml <-
              ZIO
                .fromOption(allParts.get(xmlPartKey))
                .mapBoth(
                  _ => BadRequestException(s"MultiPart POST request was sent without required '$xmlPartKey' part!"),
                  CreateMappingRequestXMLV2.apply,
                )
            apiRequestID <- RouteUtilZ.randomUuid()
          } yield CreateMappingRequestV2(metadata, xml, requestingUser, apiRequestID)
          RouteUtilV2.runRdfRouteZ(requestMessageTask, requestContext)
        }
      }
    }
}
