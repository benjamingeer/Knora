/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.v2.routing.authenticationmessages.AuthenticationV2JsonProtocol
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.LoginApiRequestPayloadV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData

/**
 * A route providing API v2 authentication support. It allows the creation of "sessions", which are used in the SALSAH app.
 */
class AuthenticationRouteV2(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Authenticator
    with AuthenticationV2JsonProtocol {

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    path("v2" / "authentication") {
      get { // authenticate credentials
        requestContext =>
          requestContext.complete {
            doAuthenticateV2(
              requestContext = requestContext,
              routeData.appConfig
            )
          }
      } ~
        post { // login
          /* send iri, email, or username, and password in body as:
           * {
           *   "iri|username|email": "value_of_iri_username_or_email",
           *   "password": "userspassword"
           * }, e.g., for email:
           * {
           *   "email": "email@example.com",
           *   "password": "userspassword"
           * }
           *
           * Returns a JWT token (and session cookie), which can be supplied with every request thereafter in
           * the authorization header with the bearer scheme: 'Authorization: Bearer abc.def.ghi'
           */
          entity(as[LoginApiRequestPayloadV2]) { apiRequest => requestContext =>
            requestContext.complete {
              doLoginV2(
                credentials = KnoraPasswordCredentialsV2(
                  UserIdentifierADM(
                    maybeIri = apiRequest.iri,
                    maybeEmail = apiRequest.email,
                    maybeUsername = apiRequest.username
                  ),
                  password = apiRequest.password
                ),
                routeData.appConfig
              )
            }
          }
        } ~
        delete { // logout
          requestContext =>
            requestContext.complete {
              doLogoutV2(requestContext, routeData.appConfig)
            }
        }
    } ~
      path("v2" / "login") {
        get { // html login interface (necessary for IIIF Authentication API support)
          requestContext =>
            requestContext.complete {
              presentLoginFormV2(requestContext, routeData.appConfig)
            }
        } ~
          post { // called by html login interface (necessary for IIIF Authentication API support)
            formFields(Symbol("username"), Symbol("password")) { (username, password) => requestContext =>
              {
                requestContext.complete {
                  doLoginV2(
                    credentials = KnoraPasswordCredentialsV2(
                      UserIdentifierADM(
                        maybeUsername = Some(username)
                      ),
                      password = password
                    ),
                    routeData.appConfig
                  )
                }
              }
            }
          }
      }
}
