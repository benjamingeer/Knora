/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security.api

import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.*
import zio.ZLayer
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.json.JsonDecoder
import zio.json.JsonEncoder
import zio.json.internal.Write

import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.CheckResponse
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginForm
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LogoutResponse
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.TokenResponse

case class AuthenticationEndpointsV2(
  private val baseEndpoints: BaseEndpoints,
  private val authenticator: Authenticator,
) {

  private val basePath: EndpointInput[Unit] = "v2" / "authentication"
  private val cookieName                    = authenticator.calculateCookieName()

  val getV2Authentication = baseEndpoints.securedEndpoint.get
    .in(basePath)
    .out(jsonBody[CheckResponse])

  val postV2Authentication = baseEndpoints.publicEndpoint.post
    .in(basePath)
    .in(jsonBody[LoginPayload])
    .out(setCookie(cookieName))
    .out(jsonBody[TokenResponse])

  val deleteV2Authentication = baseEndpoints.publicEndpoint.delete
    .in(basePath)
    .in(auth.bearer[Option[String]](WWWAuthenticateChallenge.bearer))
    .in(cookie[Option[String]](cookieName))
    .out(setCookie(cookieName))
    .out(jsonBody[LogoutResponse])

  val getV2Login = baseEndpoints.publicEndpoint.get
    .in("v2" / "login")
    .out(htmlBodyUtf8)

  val postV2Login = baseEndpoints.publicEndpoint.post
    .in("v2" / "login")
    .in(formBody[LoginForm])
    .out(setCookie(cookieName))
    .out(jsonBody[TokenResponse])

  val endpoints: Seq[AnyEndpoint] =
    Seq(getV2Authentication.endpoint, postV2Authentication, deleteV2Authentication, getV2Login, postV2Login)
      .map(_.tag("V2 Authentication"))
}

object AuthenticationEndpointsV2 {

  val layer = ZLayer.derive[AuthenticationEndpointsV2]

  final case class CheckResponse(message: String)
  object CheckResponse {
    given JsonCodec[CheckResponse] = DeriveJsonCodec.gen[CheckResponse]
  }

  final case class TokenResponse(token: String)
  object TokenResponse {
    given JsonCodec[TokenResponse] = DeriveJsonCodec.gen[TokenResponse]
  }

  final case class LogoutResponse(status: Int, message: String)
  object LogoutResponse {
    given JsonCodec[LogoutResponse] = DeriveJsonCodec.gen[LogoutResponse]
  }

  trait WithPassword { def password: String }
  enum LoginPayload extends WithPassword {
    case IriPassword(iri: UserIri, password: String)            extends LoginPayload
    case EmailPassword(email: Email, password: String)          extends LoginPayload
    case UsernamePassword(username: Username, password: String) extends LoginPayload
  }
  object LoginPayload {
    import org.knora.webapi.slice.admin.api.Codecs.ZioJsonCodec.*

    private val iriPasswordCodec: JsonCodec[IriPassword]           = DeriveJsonCodec.gen[IriPassword]
    private val emailPasswordCodec: JsonCodec[EmailPassword]       = DeriveJsonCodec.gen[EmailPassword]
    private val usernamePasswordCodec: JsonCodec[UsernamePassword] = DeriveJsonCodec.gen[UsernamePassword]

    private val loginPayloadEncoder: JsonEncoder[LoginPayload] =
      (a: LoginPayload, indent: Option[RuntimeFlags], out: Write) =>
        a match {
          case e: LoginPayload.EmailPassword    => emailPasswordCodec.encoder.unsafeEncode(e, indent, out)
          case i: LoginPayload.IriPassword      => iriPasswordCodec.encoder.unsafeEncode(i, indent, out)
          case u: LoginPayload.UsernamePassword => usernamePasswordCodec.encoder.unsafeEncode(u, indent, out)
        }

    private val loginPayloadDecoder: JsonDecoder[LoginPayload] =
      iriPasswordCodec.decoder
        .asInstanceOf[JsonDecoder[LoginPayload]]
        .orElse(usernamePasswordCodec.decoder.asInstanceOf[JsonDecoder[LoginPayload]])
        .orElse(emailPasswordCodec.decoder.asInstanceOf[JsonDecoder[LoginPayload]])

    given loginPayloadCodec: JsonCodec[LoginPayload] = JsonCodec(loginPayloadEncoder, loginPayloadDecoder)
  }

  final case class LoginForm(username: String, password: String)
}
