/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security.api

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
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.TokenResponse

case class AuthenticationEndpointsV2(
  private val baseEndpoints: BaseEndpoints,
  private val authenticator: Authenticator,
) {

  private val basePath: EndpointInput[Unit] = "v2" / "authentication"
  private val cookieName                    = authenticator.calculateCookieName()

  val postV2Authentication = baseEndpoints.publicEndpoint.post
    .in(basePath)
    .in(jsonBody[LoginPayload])
    .out(setCookie(cookieName))
    .out(jsonBody[TokenResponse])

  val endpoints: Seq[AnyEndpoint] = Seq(postV2Authentication)
}

object AuthenticationEndpointsV2 {

  val layer = ZLayer.derive[AuthenticationEndpointsV2]

  final case class TokenResponse(token: String)
  object TokenResponse {
    given JsonCodec[TokenResponse] = DeriveJsonCodec.gen[TokenResponse]
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
}
