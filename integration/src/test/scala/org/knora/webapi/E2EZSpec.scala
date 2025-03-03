/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio.*
import zio.http.*
import zio.json.*
import zio.json.DecoderOps
import zio.json.ast.Json
import zio.json.ast.JsonCursor
import zio.test.*

import org.knora.webapi.core.AppServer
import org.knora.webapi.core.LayersTestMock
import org.knora.webapi.core.TestStartupUtils
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.UserIri

abstract class E2EZSpec extends ZIOSpecDefault with TestStartupUtils {

  private val testLayers =
    util.Logger.text() >>> LayersTestMock.layer()

  def rdfDataObjects: List[RdfDataObject] = List.empty[RdfDataObject]

  type env = LayersTestMock.Environment with Client with Scope

  private def prepare: ZIO[AppServer.AppServerEnvironment, Throwable, AppServer] = for {
    appServer <- AppServer.init()
    _         <- appServer.start(requiresAdditionalRepositoryChecks = false).orDie
    _         <- prepareRepository(rdfDataObjects)
  } yield appServer

  def withResettedTriplestore =
    TestAspect.before(prepareRepository(rdfDataObjects))

  def e2eSpec: Spec[env, Any]

  override def spec = (
    e2eSpec
      @@ TestAspect.beforeAll(prepare)
      @@ TestAspect.sequential
  ).provideShared(testLayers, Client.default, Scope.default)
    @@ TestAspect.withLiveEnvironment

  def sendGetRequestAsRoot(url: String): URIO[env, Response] =
    getRootToken.mapError(Exception(_)).orDie.flatMap(token => sendGetRequest(url, Some(token)))

  def sendGetRequestAsRootDecode[A: JsonDecoder](url: String): ZIO[env, String, A] =
    sendGetRequestAsRoot(url)
      .flatMap(response => response.body.asString.mapBoth(_.getMessage, (response.status, _)))
      .filterOrElseWith { case (status, _) => status.isSuccess } { case (status, body) =>
        ZIO.fail(s"Failed request: Status $status, $body")
      }
      .flatMap { case (_, body) => ZIO.fromEither(body.fromJson[A]) }

  def sendGetRequest(url: String, token: Option[String] = None): URIO[env, Response] =
    for {
      client   <- ZIO.service[Client]
      urlStr    = s"http://localhost:3333$url"
      urlFull  <- ZIO.fromEither(URL.decode(urlStr)).orDie
      _        <- ZIO.logDebug(s"GET   ${urlFull.encode}")
      bearer    = token.map(Header.Authorization.Bearer(_)).toList
      response <- client.url(urlFull).addHeaders(Headers(bearer)).get("").orDie
    } yield response

  def sendPutRequestAsRoot(url: String, data: String): ZIO[env, String, Response] =
    sendPutRequestAsRoot(url, Body.fromString(data))

  def sendPutRequestAsRoot(url: String, body: Body): URIO[env, Response] =
    for {
      token    <- getRootToken.mapError(Exception(_)).orDie
      response <- sendPutRequest(url, body, Some(token))
    } yield response

  def sendPutRequest(url: String, body: Body, token: Option[String] = None): URIO[env, Response] =
    for {
      client   <- ZIO.service[Client]
      bearer    = token.map(Header.Authorization.Bearer(_)).toList
      response <- client.url(url"http://localhost:3333").addHeaders(Headers(bearer)).put(url)(body).orDie
    } yield response

  def sendGetRequestStringOrFail(url: String, token: Option[String] = None): ZIO[env, String, String] =
    for {
      response <- sendGetRequest(url, token)
      data     <- response.body.asString.orDie
      _        <- ZIO.fail(s"Failed request: Status ${response.status} - $data").when(response.status != Status.Ok)
    } yield data

  def sendGetRequestAsOrFail[B](url: String, token: Option[String] = None)(implicit
    dec: JsonDecoder[B],
  ): ZIO[env, String, B] =
    for {
      response <- sendGetRequestStringOrFail(url, token)
      result   <- ZIO.fromEither(response.fromJson[B])
    } yield result

  def sendPostRequestAsRoot(url: String, data: String): ZIO[env, String, Response] =
    getRootToken.flatMap(token => sendPostRequest(url, data, Some(token)))

  def sendPostRequest(url: String, data: String, token: Option[String] = None): ZIO[env, String, Response] =
    for {
      client   <- ZIO.service[Client]
      urlStr    = s"http://localhost:3333$url"
      urlFull  <- ZIO.fromEither(URL.decode(urlStr)).mapError(_.getMessage)
      _        <- ZIO.logDebug(s"POST  ${urlFull.encode}")
      body      = Body.fromString(data)
      bearer    = token.map(Header.Authorization.Bearer(_))
      headers   = Headers(List(Header.ContentType(MediaType.application.json)) ++ bearer.toList)
      response <- client.url(urlFull).addHeaders(headers).post("")(body).mapError(_.getMessage)
    } yield response

  def sendPostRequestStringOrFail(url: String, data: String, token: Option[String] = None): ZIO[env, String, String] =
    for {
      response <- sendPostRequest(url, data, token)
      data     <- response.body.asString.mapError(_.getMessage)
      _        <- ZIO.fail(s"Failed request: Status ${response.status} - $data").when(response.status != Status.Ok)
    } yield data

  def sendPostRequestString(url: String, data: String, token: Option[String] = None): ZIO[env, String, String] =
    for {
      response <- sendPostRequest(url, data, token)
      data     <- response.body.asString.mapError(_.getMessage)
    } yield data

  def sendDeleteRequest(url: String, token: Option[String]): ZIO[env, String, Response] =
    (for {
      client   <- ZIO.service[Client]
      urlStr    = s"http://localhost:3333$url"
      urlFull  <- ZIO.fromEither(URL.decode(urlStr))
      _        <- ZIO.logDebug(s"POST  ${urlFull.encode}")
      bearer    = token.map(Header.Authorization.Bearer.apply)
      headers   = Headers(List(Header.ContentType(MediaType.application.json)) ++ bearer.toList)
      request   = Request.delete(urlFull).addHeaders(headers)
      response <- client.request(request)
    } yield response).mapError(_.getMessage)

  def getToken(email: String, password: String): ZIO[env, String, String] =
    for {
      response <-
        sendPostRequestStringOrFail(
          "/v2/authentication",
          s"""|{
              |  "email": "$email",
              |  "password": "$password"
              |}""".stripMargin,
        )
      result <- ZIO.fromEither(response.fromJson[Map[String, String]])
      token  <- ZIO.fromOption(result.get("token")).orElseFail("No token in response")
    } yield token

  def getRootToken: ZIO[env, String, String] =
    getToken("root@example.com", "test")

  def urlEncode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

  def getOntologyLastModificationDate(ontlogyIri: String): ZIO[env, String, String] = {
    val cursor = JsonCursor.field("knora-api:lastModificationDate").isObject.field("@value").isString
    for {
      responseStr <- sendGetRequestStringOrFail(s"/v2/ontologies/allentities/${urlEncode(ontlogyIri)}")
      responseAst <- ZIO.fromEither(responseStr.fromJson[Json])
      lmd         <- ZIO.fromEither(responseAst.get(cursor))
    } yield lmd.value
  }

  object AdminApiRestClient {
    private val shortcodePlaceholder                       = ":shortcode"
    private def replace(shortcode: Shortcode, url: String) = url.replace(shortcodePlaceholder, shortcode.value)

    val projectsShortcodePath: String      = s"/admin/projects/shortcode/$shortcodePlaceholder"
    val projectsShortcodeErasePath: String = s"$projectsShortcodePath/erase"

    private val userIriPlaceholder                     = ":userIri"
    private def replace(userIri: UserIri, url: String) = url.replace(userIriPlaceholder, userIri.value)

    val usersPath                       = "/admin/users"
    val usersIriPath                    = s"$usersPath/iri/$userIriPlaceholder"
    val usersIriProjectMemberships      = s"$usersIriPath/project-memberships"
    val usersIriProjectAdminMemberships = s"$usersIriPath/project-admin-memberships"
    val usersIriGroupMemberships        = s"$usersIriPath/group-memberships"

    def eraseProjectAsRoot(shortcode: Shortcode): ZIO[env, String, Response] = for {
      jwt      <- getRootToken.map(Some.apply)
      response <- sendDeleteRequest(replace(shortcode, projectsShortcodeErasePath), jwt)
    } yield response

    def getProjectAsRoot(shortcode: Shortcode): ZIO[env, String, Response] = for {
      jwt      <- getRootToken.map(Some.apply)
      response <- sendGetRequest(replace(shortcode, projectsShortcodePath))
    } yield response

    def getUserAsRoot(userIri: UserIri): ZIO[env, IRI, Response] = for {
      jwt      <- getRootToken.map(Some.apply)
      response <- sendGetRequest(replace(userIri, usersIriPath))
    } yield response
  }
}
