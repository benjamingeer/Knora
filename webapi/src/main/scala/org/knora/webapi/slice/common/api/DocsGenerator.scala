/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.server.RequestContext
import sttp.apispec.openapi.Server
import sttp.apispec.openapi.circe.yaml.RichOpenAPI
import sttp.tapir.AnyEndpoint
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import zio.Chunk
import zio.IO
import zio.Task
import zio.UIO
import zio.ZIO
import zio.ZIOAppArgs
import zio.ZIOAppDefault
import zio.ZLayer
import zio.nio.file.Files
import zio.nio.file.Path

import org.knora.webapi.http.version.BuildInfo
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2
import org.knora.webapi.slice.admin.api.AdminApiEndpoints
import org.knora.webapi.slice.admin.api.FilesEndpoints
import org.knora.webapi.slice.admin.api.GroupsEndpoints
import org.knora.webapi.slice.admin.api.ListsEndpoints
import org.knora.webapi.slice.admin.api.MaintenanceEndpoints
import org.knora.webapi.slice.admin.api.PermissionsEndpoints
import org.knora.webapi.slice.admin.api.ProjectsEndpoints
import org.knora.webapi.slice.admin.api.StoreEndpoints
import org.knora.webapi.slice.admin.api.UsersEndpoints
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.infrastructure.Jwt
import org.knora.webapi.slice.infrastructure.api.ManagementEndpoints
import org.knora.webapi.slice.lists.api.ListsEndpointsV2
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoEndpoints
import org.knora.webapi.slice.search.api.SearchEndpoints
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.AuthenticatorErrors
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2

final case class DocsNoopAuthenticator() extends Authenticator {
  override def getUserADM(requestContext: RequestContext): Task[User]                                            = ???
  override def calculateCookieName(): String                                                                     = "KnoraAuthenticationMFYGSLTEMFZWG2BOON3WS43THI2DIMY9"
  override def getUserADMThroughCredentialsV2(credentials: KnoraCredentialsV2): Task[User]                       = ???
  override def doLoginV2(credentials: KnoraCredentialsV2.KnoraPasswordCredentialsV2): Task[HttpResponse]         = ???
  override def authenticateCredentialsV2(credentials: KnoraCredentialsV2): Task[Unit]                            = ???
  override def authenticate(userIri: UserIri, password: String): IO[AuthenticatorErrors.LoginFailed.type, Jwt]   = ???
  override def authenticate(username: Username, password: String): IO[AuthenticatorErrors.LoginFailed.type, Jwt] = ???
  override def authenticate(email: Email, password: String): IO[AuthenticatorErrors.LoginFailed.type, Jwt]       = ???
  override def invalidateToken(jwt: String): UIO[Unit]                                                           = ???
}
object DocsNoopAuthenticator {
  val layer = ZLayer.succeed(DocsNoopAuthenticator())
}

object DocsGenerator extends ZIOAppDefault {

  private val interp: OpenAPIDocsInterpreter = OpenAPIDocsInterpreter()
  override def run: ZIO[ZIOAppArgs, java.io.IOException, Int] = {
    for {
      _                   <- ZIO.logInfo("Generating OpenAPI docs")
      args                <- getArgs
      adminEndpoints      <- ZIO.serviceWith[AdminApiEndpoints](_.endpoints)
      managementEndpoints <- ZIO.serviceWith[ManagementEndpoints](_.endpoints)
      v2Endpoints         <- ZIO.serviceWith[ApiV2Endpoints](_.endpoints)
      path                 = Path(args.headOption.getOrElse("/tmp"))
      filesWritten <-
        writeToFile(adminEndpoints, path, "admin-api") <*>
          writeToFile(v2Endpoints, path, "v2") <*>
          writeToFile(managementEndpoints, path, "management")
      _ <- ZIO.logInfo(s"Wrote $filesWritten")
    } yield 0
  }.provideSome[ZIOAppArgs](
    AdminApiEndpoints.layer,
    AuthenticationEndpointsV2.layer,
    ApiV2Endpoints.layer,
    BaseEndpoints.layer,
    DocsNoopAuthenticator.layer,
    GroupsEndpoints.layer,
    FilesEndpoints.layer,
    ListsEndpoints.layer,
    MaintenanceEndpoints.layer,
    PermissionsEndpoints.layer,
    ProjectsEndpoints.layer,
    ResourceInfoEndpoints.layer,
    SearchEndpoints.layer,
    StoreEndpoints.layer,
    UsersEndpoints.layer,
    ManagementEndpoints.layer,
    ListsEndpointsV2.layer,
  )

  private def writeToFile(endpoints: Seq[AnyEndpoint], path: Path, name: String) = {
    val content = interp
      .toOpenAPI(endpoints, s"${BuildInfo.name}-$name", BuildInfo.version)
      .servers(
        List(
          Server(url = "http://localhost:3333", description = Some("Local development server")),
          Server(url = "https://api.dasch.swiss", description = Some("Production server")),
        ),
      )
    for {
      _     <- ZIO.logInfo(s"Writing to $path")
      target = path / s"openapi-$name.yml"
      _     <- Files.deleteIfExists(target) *> Files.createFile(target)
      _     <- Files.writeBytes(target, Chunk.fromArray(content.toYaml.getBytes))
    } yield target
  }
}
