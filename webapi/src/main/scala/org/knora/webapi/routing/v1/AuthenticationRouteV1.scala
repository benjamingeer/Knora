/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData}

/**
 * A route providing authentication support. It allows the creation of "sessions", which is used in the SALSAH app.
 */
class AuthenticationRouteV1(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

  /**
   * Returns the route.
   */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    path("v1" / "authenticate") {
      get { requestContext =>
        requestContext.complete {
          doAuthenticateV1(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        }
      }
    } ~ path("v1" / "session") {
      get { requestContext =>
        requestContext.complete {
          val params = requestContext.request.uri.query().toMap
          if (params.contains("logout")) {
            doLogoutV2(requestContext)
          } else if (params.contains("login")) {
            doLoginV1(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } else {
            doAuthenticateV1(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          }
        }
      } ~ post { requestContext =>
        requestContext.complete {
          doLoginV1(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        }
      } ~ delete { requestContext =>
        requestContext.complete {
          doLogoutV2(requestContext)
        }
      }
    }
}
