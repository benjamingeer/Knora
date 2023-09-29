/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import org.apache.pekko
import zio.Runtime

import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.storesmessages.ResetTriplestoreContentRequestADM
import org.knora.webapi.messages.admin.responder.storesmessages.StoresADMJsonProtocol
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM.runJsonRoute

import pekko.http.scaladsl.server.Directives._
import pekko.http.scaladsl.server.Route

/**
 * A route used to send requests which can directly affect the data stored inside the triplestore.
 */

final case class StoreRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator with StringFormatter with MessageRelay]
) extends KnoraRoute(routeData, runtime)
    with StoresADMJsonProtocol {
  override def makeRoute: Route = Route {
    path("admin" / "store" / "ResetTriplestoreContent") {
      post {
        entity(as[Seq[RdfDataObject]]) { apiRequest =>
          parameter(Symbol("prependdefaults").as[Boolean] ? true) { prependDefaults => requestContext =>
            runJsonRoute(ResetTriplestoreContentRequestADM(apiRequest, prependDefaults), requestContext)
          }
        }
      }
    }
  }
}
