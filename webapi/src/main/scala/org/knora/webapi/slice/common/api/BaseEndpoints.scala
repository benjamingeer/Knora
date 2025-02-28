/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.model.StatusCode
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.Endpoint
import sttp.tapir.EndpointOutput
import sttp.tapir.auth
import sttp.tapir.cookie
import sttp.tapir.endpoint
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.model.UsernamePassword
import sttp.tapir.oneOf
import sttp.tapir.oneOfVariant
import sttp.tapir.statusCode
import zio.ZIO
import zio.ZLayer

import scala.concurrent.Future

import dsp.errors.*
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.AnonymousUser
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.security.Authenticator

final case class BaseEndpoints(authenticator: Authenticator)(implicit val r: zio.Runtime[Any]) {

  private val errorOutputs: EndpointOutput.OneOf[RequestRejectedException, RequestRejectedException] =
    oneOf[RequestRejectedException](
      // default
      oneOfVariant[NotFoundException](statusCode(StatusCode.NotFound).and(jsonBody[NotFoundException])),
      oneOfVariant[BadRequestException](statusCode(StatusCode.BadRequest).and(jsonBody[BadRequestException])),
      oneOfVariant[OntologyConstraintException](
        statusCode(StatusCode.BadRequest).and(jsonBody[OntologyConstraintException]),
      ),
      oneOfVariant[ValidationException](statusCode(StatusCode.BadRequest).and(jsonBody[ValidationException])),
      oneOfVariant[DuplicateValueException](statusCode(StatusCode.BadRequest).and(jsonBody[DuplicateValueException])),
      oneOfVariant[GravsearchException](statusCode(StatusCode.BadRequest).and(jsonBody[GravsearchException])),
      // plus security
      oneOfVariant[BadCredentialsException](statusCode(StatusCode.Unauthorized).and(jsonBody[BadCredentialsException])),
      oneOfVariant[ForbiddenException](statusCode(StatusCode.Forbidden).and(jsonBody[ForbiddenException])),
    )

  val publicEndpoint = endpoint.errorOut(errorOutputs)

  private val endpointWithBearerCookieBasicAuthOptional
    : Endpoint[(Option[String], Option[String], Option[UsernamePassword]), Unit, RequestRejectedException, Unit, Any] =
    endpoint
      .errorOut(errorOutputs)
      .securityIn(auth.bearer[Option[String]](WWWAuthenticateChallenge.bearer))
      .securityIn(cookie[Option[String]](authenticator.calculateCookieName()))
      .securityIn(auth.basic[Option[UsernamePassword]](WWWAuthenticateChallenge.basic("realm")))

  val securedEndpoint = endpointWithBearerCookieBasicAuthOptional.serverSecurityLogic {
    case (Some(jwtToken), _, _) => authenticateJwt(jwtToken)
    case (_, Some(cookie), _)   => authenticateJwt(cookie)
    case (_, _, Some(basic))    => authenticateBasic(basic)
    case _                      => Future.successful(Left(BadCredentialsException("No credentials provided.")))
  }

  val withUserEndpoint = endpointWithBearerCookieBasicAuthOptional.serverSecurityLogic {
    case (Some(jwtToken), _, _) => authenticateJwt(jwtToken)
    case (_, Some(cookie), _)   => authenticateJwt(cookie)
    case (_, _, Some(basic))    => authenticateBasic(basic)

    case _ => Future.successful(Right(AnonymousUser))
  }

  private def authenticateJwt(jwtToken: String): Future[Either[RequestRejectedException, User]] =
    UnsafeZioRun.runToFuture(
      authenticator.authenticate(jwtToken).orElseFail(BadCredentialsException("Invalid credentials.")).either,
    )

  private def authenticateBasic(basic: UsernamePassword): Future[Either[RequestRejectedException, User]] =
    UnsafeZioRun.runToFuture(
      (for {
        email <- ZIO
                   .fromEither(Email.from(basic.username))
                   .orElseFail(BadCredentialsException("Invalid credentials, email address expected."))
        password <- ZIO
                      .fromOption(basic.password)
                      .orElseFail(BadCredentialsException("Invalid credentials, missing password."))
        userAndJwt <- authenticator
                        .authenticate(email, password)
                        .orElseFail(BadCredentialsException("Invalid credentials."))
        (user, _) = userAndJwt
      } yield user).either,
    )
}

object BaseEndpoints {
  val layer = ZLayer.fromZIO(
    for {
      auth <- ZIO.service[Authenticator]
      r    <- ZIO.runtime[Any]
    } yield BaseEndpoints(auth)(r),
  )
}
