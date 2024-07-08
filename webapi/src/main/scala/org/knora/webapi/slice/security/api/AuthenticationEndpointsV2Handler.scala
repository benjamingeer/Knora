/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security.api
import sttp.model.headers.CookieValueWithMeta
import zio.ZIO
import zio.ZLayer

import dsp.errors.BadCredentialsException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.Authenticator.BAD_CRED_NOT_VALID
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload.EmailPassword
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload.IriPassword
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload.UsernamePassword
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.TokenResponse

case class AuthenticationEndpointsV2Handler(
  appConfig: AppConfig,
  authenticator: Authenticator,
  endpoints: AuthenticationEndpointsV2,
  mapper: HandlerMapper,
) {
  val postV2Authentication =
    PublicEndpointHandler[
      LoginPayload,
      (
        CookieValueWithMeta,
        TokenResponse,
      ),
    ](
      endpoints.postV2Authentication,
      (login: LoginPayload) => {
        (login match {
          case IriPassword(iri, password)           => authenticator.authenticate(iri, password)
          case UsernamePassword(username, password) => authenticator.authenticate(username, password)
          case EmailPassword(email, password)       => authenticator.authenticate(email, password)
        }).mapBoth(
          _ => BadCredentialsException(BAD_CRED_NOT_VALID),
          token =>
            (
              CookieValueWithMeta.unsafeApply(
                domain = Some(appConfig.cookieDomain),
                httpOnly = true,
                path = Some("/"),
                value = token.jwtString,
              ),
              TokenResponse(token.jwtString),
            ),
        )
      },
    )

  val allHandlers = List(postV2Authentication).map(mapper.mapPublicEndpointHandler(_))
}
object AuthenticationEndpointsV2Handler {
  val layer = ZLayer.derive[AuthenticationEndpointsV2Handler]
}
