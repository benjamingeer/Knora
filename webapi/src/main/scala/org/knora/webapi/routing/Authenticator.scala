/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers
import akka.http.scaladsl.model.headers.HttpCookie
import akka.http.scaladsl.model.headers.HttpCookiePair
import akka.http.scaladsl.server.RequestContext
import akka.util.ByteString
import com.typesafe.scalalogging.Logger
import org.apache.commons.codec.binary.Base32
import org.slf4j.LoggerFactory
import pdi.jwt.JwtAlgorithm
import pdi.jwt.JwtClaim
import pdi.jwt.JwtHeader
import pdi.jwt.JwtSprayJson
import spray.json._
import zio._
import zio.macros.accessible

import java.util.Base64
import scala.util.Failure
import scala.util.Success

import dsp.errors.AuthenticationException
import dsp.errors.BadCredentialsException
import dsp.valueobjects.Iri
import dsp.valueobjects.UuidUtil
import org.knora.webapi.IRI
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.JwtConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraJWTTokenCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraSessionCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages._
import org.knora.webapi.routing.Authenticator.AUTHENTICATION_INVALIDATION_CACHE_NAME
import org.knora.webapi.routing.Authenticator.BAD_CRED_NONE_SUPPLIED
import org.knora.webapi.routing.Authenticator.BAD_CRED_NOT_VALID
import org.knora.webapi.routing.Authenticator.BAD_CRED_USER_INACTIVE
import org.knora.webapi.routing.Authenticator.BAD_CRED_USER_NOT_FOUND
import org.knora.webapi.util.cache.CacheUtil

/**
 * This trait is used in routes that need authentication support. It provides methods that use the [[RequestContext]]
 * to extract credentials, authenticate provided credentials, and look up cached credentials through the use of the
 * session id. All private methods used in this trait can be found in the companion object.
 */
@accessible
trait Authenticator {

  /**
   * Returns a User that match the credentials found in the [[RequestContext]].
   * The credentials can be email/password as parameters or auth headers, or session token in a cookie header. If no
   * credentials are found, then a default UserProfile is returned. If the credentials are not correct, then the
   * corresponding error is returned.
   *
   * @param requestContext       a [[RequestContext]] containing the http request
   * @return a [[UserADM]]
   */
  def getUserADM(requestContext: RequestContext): Task[UserADM]

  /**
   * Calculates the cookie name, where the external host and port are encoded as a base32 string
   * to make the name of the cookie unique between environments.
   *
   * The default padding needs to be changed from '=' to '9' because '=' is not allowed inside the cookie!!!
   * This also needs to be changed in all the places that base32 is used to calculate the cookie name, e.g., sipi.
   *
   * @return the calculated cookie name as [[String]]
   */
  def calculateCookieName(): String

  /**
   * Tries to retrieve a [[UserADM]] based on the supplied credentials. If both email/password and session
   * token are supplied, then the user profile for the session token is returned. This method should only be used
   * with authenticated credentials.
   *
   * @param credentials          the user supplied credentials.
   * @return a [[UserADM]]
   *
   *         [[AuthenticationException]] when the IRI can not be found inside the token, which is probably a bug.
   */
  def getUserADMThroughCredentialsV2(credentials: Option[KnoraCredentialsV2]): Task[UserADM]

  /**
   * Used to logout the user, i.e. returns a header deleting the cookie and puts the token on the 'invalidated' list.
   *
   * @param requestContext a [[RequestContext]] containing the http request
   * @return a [[HttpResponse]]
   */
  def doLogoutV2(requestContext: RequestContext): Task[HttpResponse]

  /**
   * Checks if the provided credentials are valid, and if so returns a JWT token for the client to save.
   *
   * @param credentials          the user supplied [[KnoraPasswordCredentialsV2]] containing the user's login information.
   * @return a [[HttpResponse]] containing either a failure message or a message with a cookie header containing
   *         the generated session id.
   */
  def doLoginV2(credentials: KnoraPasswordCredentialsV2): Task[HttpResponse]

  /**
   * Checks if the credentials provided in [[RequestContext]] are valid.
   *
   * @param requestContext a [[RequestContext]] containing the http request
   * @return a [[HttpResponse]]
   */
  def doAuthenticateV2(requestContext: RequestContext): Task[HttpResponse]

  /**
   * Returns a simple login form for testing purposes
   *
   * @param requestContext    a [[RequestContext]] containing the http request
   * @return                  a [[HttpResponse]] with an html login form
   */
  def presentLoginFormV2(requestContext: RequestContext): Task[HttpResponse]

  /**
   * Tries to authenticate the supplied credentials (email/password or token). In the case of email/password,
   * authentication is performed checking if the supplied email/password combination is valid by retrieving the
   * user's profile. In the case of the token, the token itself is validated. If both are supplied, then both need
   * to be valid.
   *
   * @param credentials          the user supplied and extracted credentials.
   * @return true if the credentials are valid. If the credentials are invalid, then the corresponding exception
   *         will be returned.
   *
   *         [[BadCredentialsException]] when no credentials are supplied; when user is not active;
   *         when the password does not match; when the supplied token is not valid.
   */
  def authenticateCredentialsV2(credentials: Option[KnoraCredentialsV2]): Task[Boolean]

  /**
   * Tries to get a [[UserADM]].
   *
   * @param identifier           the IRI, email, or username of the user to be queried
   * @return a [[UserADM]]
   *
   *         [[BadCredentialsException]] when either the supplied email is empty or no user with such an email could be found.
   */
  def getUserByIdentifier(identifier: UserIdentifierADM): Task[UserADM]
}

object Authenticator {

  val BAD_CRED_USER_NOT_FOUND = "bad credentials: user not found"
  val BAD_CRED_NONE_SUPPLIED  = "bad credentials: none found"
  val BAD_CRED_USER_INACTIVE  = "bad credentials: user inactive"
  val BAD_CRED_NOT_VALID      = "bad credentials: not valid"

  val AUTHENTICATION_INVALIDATION_CACHE_NAME = "authenticationInvalidationCache"
}

final case class AuthenticatorLive(
  private val appConfig: AppConfig,
  private val messageRelay: MessageRelay,
  private val jwtService: JwtService,
  private implicit val stringFormatter: StringFormatter
) extends Authenticator {

  private val logger = Logger(LoggerFactory.getLogger(this.getClass))

  /**
   * Checks if the provided credentials are valid, and if so returns a JWT token for the client to save.
   *
   * @param credentials          the user supplied [[KnoraPasswordCredentialsV2]] containing the user's login information.
   * @return a [[HttpResponse]] containing either a failure message or a message with a cookie header containing
   *         the generated session id.
   */
  override def doLoginV2(credentials: KnoraPasswordCredentialsV2): Task[HttpResponse] =
    for {
      _           <- authenticateCredentialsV2(credentials = Some(credentials))
      userADM     <- getUserByIdentifier(credentials.identifier)
      cookieDomain = Some(appConfig.cookieDomain)
      jwtString   <- jwtService.createJwt(userADM).map(_.jwtString)

      httpResponse = HttpResponse(
                       headers = List(
                         headers.`Set-Cookie`(
                           HttpCookie(
                             calculateCookieName(),
                             jwtString,
                             domain = cookieDomain,
                             path = Some("/"),
                             httpOnly = true
                           )
                         )
                       ), // set path to "/" to make the cookie valid for the whole domain (and not just a segment like v1 etc.)
                       status = StatusCodes.OK,
                       entity = HttpEntity(
                         ContentTypes.`application/json`,
                         JsObject(
                           "token" -> JsString(jwtString)
                         ).compactPrint
                       )
                     )

    } yield httpResponse

  /**
   * Returns a simple login form for testing purposes
   *
   * @param requestContext    a [[RequestContext]] containing the http request
   * @return                  a [[HttpResponse]] with an html login form
   */
  override def presentLoginFormV2(requestContext: RequestContext): Task[HttpResponse] = {
    val apiUrl = appConfig.knoraApi.externalKnoraApiBaseUrl
    val form =
      s"""
         |<div align="center">
         |    <section class="container">
         |        <div class="login">
         |            <h1>DSP-API Login</h1>
         |            <form name="myform" action="$apiUrl/v2/login" method="post">
         |                <p>
         |                    <input type="text" name="username" value="" placeholder="Username">
         |                </p>
         |                <p>
         |                    <input type="password" name="password" value="" placeholder="Password">
         |                </p>
         |                <p class="submit">
         |                    <input type="submit" name="submit" value="Login">
         |                </p>
         |            </form>
         |        </div>
         |
         |    </section>
         |
         |    <section class="about">
         |        <p class="about-author">
         |            &copy; 2015&ndash;2022 <a href="https://dasch.swiss" target="_blank">dasch.swiss</a>
         |    </section>
         |</div>
        """.stripMargin

    val httpResponse = HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        form
      )
    )

    ZIO.succeed(httpResponse)
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Authentication ENTRY POINT
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Checks if the credentials provided in [[RequestContext]] are valid.
   *
   * @param requestContext a [[RequestContext]] containing the http request
   * @return a [[HttpResponse]]
   */
  override def doAuthenticateV2(requestContext: RequestContext): Task[HttpResponse] =
    authenticateCredentialsV2(extractCredentialsV2(requestContext)).as {
      HttpResponse(
        status = StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`application/json`,
          JsObject(
            "message" -> JsString("credentials are OK")
          ).compactPrint
        )
      )
    }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // LOGOUT ENTRY POINT
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Used to logout the user, i.e. returns a header deleting the cookie and puts the token on the 'invalidated' list.
   *
   * @param requestContext a [[RequestContext]] containing the http request
   * @return a [[HttpResponse]]
   */
  override def doLogoutV2(requestContext: RequestContext): Task[HttpResponse] = ZIO.attempt {

    val credentials  = extractCredentialsV2(requestContext)
    val cookieDomain = Some(appConfig.cookieDomain)

    credentials match {
      case Some(KnoraSessionCredentialsV2(sessionToken)) =>
        CacheUtil.put(AUTHENTICATION_INVALIDATION_CACHE_NAME, sessionToken, sessionToken)

        HttpResponse(
          headers = List(
            headers.`Set-Cookie`(
              HttpCookie(
                calculateCookieName(),
                "",
                domain = cookieDomain,
                path = Some("/"),
                httpOnly = true,
                expires = Some(DateTime(1970, 1, 1, 0, 0, 0)),
                maxAge = Some(0)
              )
            )
          ),
          status = StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            JsObject(
              "status"  -> JsNumber(0),
              "message" -> JsString("Logout OK")
            ).compactPrint
          )
        )
      case Some(KnoraJWTTokenCredentialsV2(jwtToken)) =>
        CacheUtil.put(AUTHENTICATION_INVALIDATION_CACHE_NAME, jwtToken, jwtToken)

        HttpResponse(
          headers = List(
            headers.`Set-Cookie`(
              HttpCookie(
                calculateCookieName(),
                "",
                domain = cookieDomain,
                path = Some("/"),
                httpOnly = true,
                expires = Some(DateTime(1970, 1, 1, 0, 0, 0))
              )
            )
          ),
          status = StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            JsObject(
              "status"  -> JsNumber(0),
              "message" -> JsString("Logout OK")
            ).compactPrint
          )
        )
      case _ =>
        // nothing to do
        HttpResponse(
          status = StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            JsObject(
              "status"  -> JsNumber(0),
              "message" -> JsString("Logout OK")
            ).compactPrint
          )
        )
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // GET USER PROFILE / AUTHENTICATION ENTRY POINT
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Returns a User that match the credentials found in the [[RequestContext]].
   * The credentials can be email/password as parameters or auth headers, or session token in a cookie header. If no
   * credentials are found, then a default UserProfile is returned. If the credentials are not correct, then the
   * corresponding error is returned.
   *
   * @param requestContext       a [[RequestContext]] containing the http request
   * @return a [[UserADM]]
   */
  override def getUserADM(requestContext: RequestContext): Task[UserADM] = {
    val credentials: Option[KnoraCredentialsV2] = extractCredentialsV2(requestContext)
    if (credentials.isEmpty) {
      ZIO.succeed(KnoraSystemInstances.Users.AnonymousUser)
    } else {
      getUserADMThroughCredentialsV2(credentials).map(_.ofType(UserInformationTypeADM.Full))
    }
  }

  /**
   * Tries to authenticate the supplied credentials (email/password or token). In the case of email/password,
   * authentication is performed checking if the supplied email/password combination is valid by retrieving the
   * user's profile. In the case of the token, the token itself is validated. If both are supplied, then both need
   * to be valid.
   *
   * @param credentials          the user supplied and extracted credentials.
   * @return true if the credentials are valid. If the credentials are invalid, then the corresponding exception
   *         will be returned.
   *
   *         [[BadCredentialsException]] when no credentials are supplied; when user is not active;
   *         when the password does not match; when the supplied token is not valid.
   */
  override def authenticateCredentialsV2(credentials: Option[KnoraCredentialsV2]): Task[Boolean] =
    for {
      result <- credentials match {
                  case Some(passCreds: KnoraPasswordCredentialsV2) =>
                    for {
                      user <- getUserByIdentifier(passCreds.identifier)

                      /* check if the user is active, if not, then no need to check the password */
                      _ <- ZIO.fail(BadCredentialsException(BAD_CRED_USER_INACTIVE)).when(!user.isActive)
                      _ <- ZIO
                             .fail(BadCredentialsException(BAD_CRED_NOT_VALID))
                             .when(!user.passwordMatch(passCreds.password))
                    } yield true
                  case Some(KnoraJWTTokenCredentialsV2(jwtToken)) =>
                    ZIO
                      .fail(BadCredentialsException(BAD_CRED_NOT_VALID))
                      .whenZIO(jwtService.validateToken(jwtToken).map(!_))
                      .as(true)
                  case Some(KnoraSessionCredentialsV2(sessionToken)) =>
                    ZIO
                      .fail(BadCredentialsException(BAD_CRED_NOT_VALID))
                      .whenZIO(
                        jwtService.validateToken(sessionToken).map(!_)
                      )
                      .as(true)
                  case None =>
                    ZIO.fail(BadCredentialsException(BAD_CRED_NONE_SUPPLIED))
                }

    } yield result

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // HELPER METHODS
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Tries to extract the credentials from the requestContext (parameters, auth headers, token)
   *
   * @param requestContext a [[RequestContext]] containing the http request
   * @return an optional [[KnoraCredentialsV2]].
   */
  private def extractCredentialsV2(requestContext: RequestContext): Option[KnoraCredentialsV2] = {

    val credentialsFromParameters: Option[KnoraCredentialsV2] = extractCredentialsFromParametersV2(requestContext)
    logger.debug("extractCredentialsV2 - credentialsFromParameters: {}", credentialsFromParameters)

    val credentialsFromHeaders: Option[KnoraCredentialsV2] = extractCredentialsFromHeaderV2(requestContext)
    logger.debug("extractCredentialsV2 - credentialsFromHeader: {}", credentialsFromHeaders)

    // return found credentials based on precedence: 1. url parameters, 2. header (basic auth, token)
    val credentials = if (credentialsFromParameters.nonEmpty) {
      credentialsFromParameters
    } else {
      credentialsFromHeaders
    }

    logger.debug("extractCredentialsV2 - returned credentials: '{}'", credentials)
    credentials
  }

  /**
   * Tries to extract credentials supplied as URL parameters.
   *
   * @param requestContext the HTTP request context.
   * @return an optional [[KnoraCredentialsV2]].
   */
  private def extractCredentialsFromParametersV2(requestContext: RequestContext): Option[KnoraCredentialsV2] = {
    // extract email/password from parameters
    val params: Map[String, Seq[String]] = requestContext.request.uri.query().toMultiMap

    // check for iri, email, or username parameters
    val maybeIriIdentifier: Option[String]      = params.get("iri").map(_.head)
    val maybeEmailIdentifier: Option[String]    = params.get("email").map(_.head)
    val maybeUsernameIdentifier: Option[String] = params.get("username").map(_.head)
    val maybeIdentifier: Option[String] =
      List(maybeIriIdentifier, maybeEmailIdentifier, maybeUsernameIdentifier).flatten.headOption

    val maybePassword: Option[String] = params.get("password").map(_.head)

    val maybePassCreds: Option[KnoraPasswordCredentialsV2] = if (maybeIdentifier.nonEmpty && maybePassword.nonEmpty) {
      Some(
        KnoraPasswordCredentialsV2(
          UserIdentifierADM(
            maybeIri = maybeIriIdentifier,
            maybeEmail = maybeEmailIdentifier,
            maybeUsername = maybeUsernameIdentifier
          ),
          maybePassword.get
        )
      )
    } else {
      None
    }

    val maybeToken: Option[String] = params get "token" map (_.head)

    val maybeTokenCreds: Option[KnoraJWTTokenCredentialsV2] = if (maybeToken.nonEmpty) {
      Some(KnoraJWTTokenCredentialsV2(maybeToken.get))
    } else {
      None
    }

    // prefer password credentials
    if (maybePassCreds.nonEmpty) {
      maybePassCreds
    } else {
      maybeTokenCreds
    }
  }

  /**
   * Tries to extract the credentials (email/password, token) from the authorization header and the session token
   * from the cookie header.
   *
   * The authorization header looks something like this: 'Authorization: Basic xyz, Bearer xyz.xyz.xyz'
   * if both the email/password and token are sent.
   *
   * If more then one set of credentials is found, then they are selected as follows:
   *    1. email/password
   *    2. authorization token
   *    3. session token
   *
   * @param requestContext   the HTTP request context.
   * @return an optional [[KnoraCredentialsV2]].
   */
  private def extractCredentialsFromHeaderV2(requestContext: RequestContext): Option[KnoraCredentialsV2] = {

    // Session token from cookie header
    val cookies: Seq[HttpCookiePair] = requestContext.request.cookies
    val maybeSessionCreds: Option[KnoraSessionCredentialsV2] =
      cookies.find(_.name == calculateCookieName()) match {
        case Some(authCookie) =>
          val value: String = authCookie.value
          Some(KnoraSessionCredentialsV2(value))
        case None =>
          None
      }

    // Authorization header
    val headers: Seq[HttpHeader] = requestContext.request.headers
    val (maybePassCreds, maybeTokenCreds) = headers.find(_.name == "Authorization") match {
      case Some(authHeader: HttpHeader) =>
        // the authorization header can hold different schemes
        val credsArr: Array[String] = authHeader.value().split(",")

        // in v2 we support the basic scheme
        val maybeBasicAuthValue = credsArr.find(_.contains("Basic"))

        // try to decode email/password
        val (maybeEmail, maybePassword) = maybeBasicAuthValue match {
          case Some(value) =>
            val trimmedValue    = value.substring(5).trim() // remove 'Basic '
            val decodedValue    = ByteString.fromArray(Base64.getDecoder.decode(trimmedValue)).decodeString("UTF8")
            val decodedValueArr = decodedValue.split(":", 2)
            (Some(decodedValueArr(0)), Some(decodedValueArr(1)))
          case None =>
            (None, None)
        }

        val maybePassCreds: Option[KnoraPasswordCredentialsV2] = if (maybeEmail.nonEmpty && maybePassword.nonEmpty) {
          Some(KnoraPasswordCredentialsV2(UserIdentifierADM(maybeEmail = maybeEmail), maybePassword.get))
        } else {
          None
        }

        // and the bearer scheme
        val maybeToken = credsArr.find(_.contains("Bearer")) match {
          case Some(value) =>
            Some(value.substring(6).trim()) // remove 'Bearer '
          case None =>
            None
        }

        val maybeTokenCreds: Option[KnoraJWTTokenCredentialsV2] = if (maybeToken.nonEmpty) {
          Some(KnoraJWTTokenCredentialsV2(maybeToken.get))
        } else {
          None
        }

        (maybePassCreds, maybeTokenCreds)

      case None =>
        (None, None)
    }

    // prefer password over token over session
    if (maybePassCreds.nonEmpty) {
      maybePassCreds
    } else if (maybeTokenCreds.nonEmpty) {
      maybeTokenCreds
    } else {
      maybeSessionCreds
    }
  }

  /**
   * Tries to retrieve a [[UserADM]] based on the supplied credentials. If both email/password and session
   * token are supplied, then the user profile for the session token is returned. This method should only be used
   * with authenticated credentials.
   *
   * @param credentials          the user supplied credentials.
   * @return a [[UserADM]]
   *
   *         [[AuthenticationException]] when the IRI can not be found inside the token, which is probably a bug.
   */
  override def getUserADMThroughCredentialsV2(credentials: Option[KnoraCredentialsV2]): Task[UserADM] =
    for {
      _ <- authenticateCredentialsV2(credentials)

      user <- credentials match {
                case Some(passCreds: KnoraPasswordCredentialsV2) =>
                  getUserByIdentifier(passCreds.identifier)
                case Some(KnoraJWTTokenCredentialsV2(jwtToken)) =>
                  for {
                    userIri <-
                      jwtService
                        .extractUserIriFromToken(jwtToken)
                        .some
                        .orElseFail(
                          AuthenticationException("No IRI found inside token. Please report this as a possible bug.")
                        )
                    user <- getUserByIdentifier(UserIdentifierADM(maybeIri = Some(userIri)))
                  } yield user
                case Some(KnoraSessionCredentialsV2(sessionToken)) =>
                  for {
                    userIri <-
                      jwtService
                        .extractUserIriFromToken(sessionToken)
                        .some
                        .orElseFail(
                          AuthenticationException("No IRI found inside token. Please report this as a possible bug.")
                        )
                    user <- getUserByIdentifier(UserIdentifierADM(maybeIri = Some(userIri)))
                  } yield user
                case None =>
                  ZIO.fail(BadCredentialsException(BAD_CRED_NONE_SUPPLIED))
              }
    } yield user

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // TRIPLE STORE ACCESS
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Tries to get a [[UserADM]].
   *
   * @param identifier           the IRI, email, or username of the user to be queried
   * @return a [[UserADM]]
   *
   *         [[BadCredentialsException]] when either the supplied email is empty or no user with such an email could be found.
   */
  override def getUserByIdentifier(identifier: UserIdentifierADM): Task[UserADM] =
    messageRelay
      .ask[Option[UserADM]](UserGetADM(identifier, UserInformationTypeADM.Full, KnoraSystemInstances.Users.SystemUser))
      .flatMap(ZIO.fromOption(_))
      .orElseFail(BadCredentialsException(BAD_CRED_USER_NOT_FOUND))

  /**
   * Calculates the cookie name, where the external host and port are encoded as a base32 string
   * to make the name of the cookie unique between environments.
   *
   * The default padding needs to be changed from '=' to '9' because '=' is not allowed inside the cookie!!!
   * This also needs to be changed in all the places that base32 is used to calculate the cookie name, e.g., sipi.
   *
   * @return the calculated cookie name as [[String]]
   */
  override def calculateCookieName(): String = {
    //
    val base32 = new Base32('9'.toByte)
    "KnoraAuthentication" + base32.encodeAsString(appConfig.knoraApi.externalKnoraApiHostPort.getBytes())
  }
}

object AuthenticatorLive {
  val layer: URLayer[AppConfig with JwtService with MessageRelay with StringFormatter, AuthenticatorLive] =
    ZLayer.fromFunction(AuthenticatorLive.apply _)
}

case class Jwt(jwtString: String, expiration: Long)

/**
 * Provides functions for creating, decoding, and validating JWT tokens.
 */
@accessible
trait JwtService {

  /**
   * Creates a JWT.
   *
   * @param user the user IRI that will be encoded into the token.
   * @param content   any other content to be included in the token.
   * @return a [[String]] containing the JWT.
   */
  def createJwt(user: UserADM, content: Map[String, JsValue] = Map.empty): UIO[Jwt]
  def createJwtForDspIngest(): UIO[Jwt]

  /**
   * Validates a JWT, taking the invalidation cache into account. The invalidation cache holds invalidated
   * tokens, which would otherwise validate. This method also makes sure that the required headers and claims are
   * present.
   *
   * @param token  the JWT.
   * @return a [[Boolean]].
   */
  def validateToken(token: String): Task[Boolean]

  /**
   * Extracts the encoded user IRI. This method also makes sure that the required headers and claims are present.
   *
   * @param token  the JWT.
   * @return an optional [[IRI]].
   */
  def extractUserIriFromToken(token: String): Task[Option[IRI]]
}

final case class JwtServiceLive(
  private val jwtConfig: JwtConfig,
  private val dspIngestConfig: DspIngestConfig
) extends JwtService {
  private val algorithm: JwtAlgorithm = JwtAlgorithm.HS256
  private val header: String          = """{"typ":"JWT","alg":"HS256"}"""
  private val logger                  = Logger(LoggerFactory.getLogger(this.getClass))

  override def createJwt(user: UserADM, content: Map[String, JsValue] = Map.empty): UIO[Jwt] =
    createJwtToken(jwtConfig.issuerAsString(), user.id, Set("Knora", "Sipi"), Some(JsObject(content)))
  override def createJwtForDspIngest(): UIO[Jwt] =
    createJwtToken(
      jwtConfig.issuerAsString(),
      jwtConfig.issuerAsString(),
      Set(dspIngestConfig.audience),
      expiration = Some(10.minutes)
    )

  private def createJwtToken(
    issuer: String,
    subject: String,
    audience: Set[String],
    content: Option[JsObject] = None,
    expiration: Option[Duration] = None
  ) =
    for {
      now  <- Clock.instant
      uuid <- Random.nextUUID
      exp   = now.plus(expiration.getOrElse(jwtConfig.expiration))
      claim = JwtClaim(
                content = content.getOrElse(JsObject.empty).compactPrint,
                issuer = Some(issuer),
                subject = Some(subject),
                audience = Some(audience),
                issuedAt = Some(now.getEpochSecond),
                expiration = Some(exp.getEpochSecond),
                jwtId = Some(UuidUtil.base64Encode(uuid))
              ).toJson
    } yield Jwt(JwtSprayJson.encode(header, claim, jwtConfig.secret, algorithm), exp.getEpochSecond)

  /**
   * Validates a JWT, taking the invalidation cache into account. The invalidation cache holds invalidated
   * tokens, which would otherwise validate. This method also makes sure that the required headers and claims are
   * present.
   *
   * @param token  the JWT.
   * @return a [[Boolean]].
   */
  override def validateToken(token: String): Task[Boolean] =
    ZIO.attempt(if (CacheUtil.get[UserADM](AUTHENTICATION_INVALIDATION_CACHE_NAME, token).nonEmpty) {
      // token invalidated so no need to decode
      logger.debug("validateToken - token found in invalidation cache, so not valid")
      false
    } else {
      decodeToken(token).isDefined
    })

  /**
   * Extracts the encoded user IRI. This method also makes sure that the required headers and claims are present.
   *
   * @param token  the JWT.
   * @return an optional [[IRI]].
   */
  override def extractUserIriFromToken(token: String): Task[Option[IRI]] =
    ZIO.attempt(decodeToken(token)).map(_.flatMap(_._2.subject))

  /**
   * Decodes and validates a JWT token.
   *
   * @param token  the token to be decoded.
   * @return the token's header and claim, or `None` if the token is invalid.
   */
  private def decodeToken(token: String): Option[(JwtHeader, JwtClaim)] =
    JwtSprayJson.decodeAll(token, jwtConfig.secret, Seq(JwtAlgorithm.HS256)) match {
      case Success((header: JwtHeader, claim: JwtClaim, _)) =>
        val missingRequiredContent: Boolean = Set(
          header.typ.isDefined,
          claim.issuer == jwtConfig.issuer,
          claim.subject.isDefined,
          claim.jwtId.isDefined,
          claim.issuedAt.isDefined,
          claim.expiration.isDefined,
          claim.audience.isDefined
        ).contains(false)

        if (!missingRequiredContent) {
          claim.subject.flatMap(iri => Iri.validateAndEscapeIri(iri).toOption.map(_ => (header, claim)))
        } else {
          logger.debug("Missing required content in JWT")
          None
        }

      case Failure(_) =>
        logger.debug("Invalid JWT")
        None
    }
}
object JwtServiceLive {
  val layer: URLayer[DspIngestConfig with JwtConfig, JwtServiceLive] =
    ZLayer.fromFunction(JwtServiceLive.apply _)
}
