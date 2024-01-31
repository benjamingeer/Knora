/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.PathMatcher
import org.apache.pekko.http.scaladsl.server.Route
import zio.*

import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.*
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM

/**
 * Provides routes to delete list items.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the route.
 */
final case class DeleteListItemsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator & StringFormatter & MessageRelay]
) extends KnoraRoute(routeData, runtime)
    with ListADMJsonProtocol {

  val listsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "lists")

  def makeRoute: Route = deleteListNodeComments()

  /**
   * Deletes all comments from requested list node (only child).
   */
  private def deleteListNodeComments(): Route =
    path(listsBasePath / "comments" / Segment) { iri =>
      delete { requestContext =>
        val requestTask =
          RouteUtilADM.getIriUser(iri, requestContext).map(r => ListNodeCommentsDeleteRequestADM(r.iri, r.user))
        RouteUtilADM.runJsonRouteZ(requestTask, requestContext)
      }
    }
}
