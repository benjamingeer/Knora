/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.admin.lists._

/**
 * Provides an akka-http-routing function for API routes that deal with lists.
 */
class ListsRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) {
  private val oldListRoute: OldListsRouteADMFeature    = new OldListsRouteADMFeature(routeData)
  private val deleteNodeRoute: DeleteListItemsRouteADM = new DeleteListItemsRouteADM(routeData)
  private val updateNodeRoute: UpdateListItemsRouteADM = new UpdateListItemsRouteADM(routeData)

  override def makeRoute(): Route =
    oldListRoute.makeRoute() ~
      deleteNodeRoute.makeRoute() ~
      updateNodeRoute.makeRoute()
}
