/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import org.apache.pekko
import zio._

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.sipimessages.SipiFileInfoGetRequestADM
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM

import pekko.http.scaladsl.server.Directives._
import pekko.http.scaladsl.server.Route

/**
 * Provides a routing function for the API that Sipi connects to.
 */
final case class FilesRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator with StringFormatter with MessageRelay]
) extends KnoraRoute(routeData, runtime) {

  /**
   * A routing function for the API that Sipi connects to.
   */
  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    path("admin" / "files" / Segments(2)) { projectIDAndFile: Seq[String] =>
      get { requestContext =>
        val requestMessage = for {
          requestingUser <- Authenticator.getUserADM(requestContext)
          projectID <- ZIO
                         .fromOption(stringFormatter.validateProjectShortcode(projectIDAndFile.head))
                         .orElseFail(BadRequestException(s"Invalid project ID: '${projectIDAndFile.head}'"))
          filename <- ZIO
                        .fromOption(Iri.toSparqlEncodedString(projectIDAndFile(1)))
                        .orElseFail(BadRequestException(s"Invalid filename: '${projectIDAndFile(1)}'"))
          _ = log.info(s"/admin/files route called for filename $filename by user: ${requestingUser.id}")
        } yield SipiFileInfoGetRequestADM(projectID, filename, requestingUser)
        RouteUtilADM.runJsonRouteZ(requestMessage, requestContext)
      }
    }
}
