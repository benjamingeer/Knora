/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import zio._
import zio.http._
import zio.http.model._
import zio.json._

import dsp.errors.BadRequestException
import dsp.errors.InternalServerException
import dsp.errors.RequestRejectedException
import dsp.valueobjects.Iri
import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.handler.ExceptionHandlerZ
import org.knora.webapi.http.middleware.AuthenticationMiddleware
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectCreatePayloadADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectUpdatePayloadADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.responders.admin.ProjectsService
import org.knora.webapi.routing.RouteUtilZ

final case class ProjectsRouteZ(
  appConfig: AppConfig,
  projectsService: ProjectsService,
  authenticationMiddleware: AuthenticationMiddleware
) {

  lazy val route: HttpApp[Any, Nothing] = projectRoutes @@ authenticationMiddleware.authenticationMiddleware

  private val projectRoutes =
    Http
      .collectZIO[(Request, UserADM)] {
        case (Method.GET -> !! / "admin" / "projects", _)                           => getProjects()
        case (Method.GET -> !! / "admin" / "projects" / "iri" / iriUrlEncoded, _)   => getProjectByIri(iriUrlEncoded)
        case (Method.GET -> !! / "admin" / "projects" / "shortname" / shortname, _) => getProjectByShortname(shortname)
        case (Method.GET -> !! / "admin" / "projects" / "shortcode" / shortcode, _) => getProjectByShortcode(shortcode)
        case (request @ Method.POST -> !! / "admin" / "projects", requestingUser) =>
          createProject(request, requestingUser)
        case (Method.DELETE -> !! / "admin" / "projects" / "iri" / iriUrlEncoded, requestingUser) =>
          deleteProject(iriUrlEncoded, requestingUser)
        case (request @ Method.PUT -> !! / "admin" / "projects" / "iri" / iriUrlEncoded, requestingUser) =>
          updateProject(iriUrlEncoded, request, requestingUser)
      }
      .catchAll {
        case RequestRejectedException(e) => ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
        case InternalServerException(e)  => ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
      }

  private def getProjects(): Task[Response] =
    for {
      projectGetResponse <- projectsService.getProjectsADMRequest()
    } yield Response.json(projectGetResponse.toJsValue.toString)

  private def getProjectByIri(iriUrlEncoded: String): Task[Response] =
    for {
      iriDecoded         <- RouteUtilZ.urlDecode(iriUrlEncoded, s"Failed to URL decode IRI parameter $iriUrlEncoded.")
      iri                <- IriIdentifier.fromString(iriDecoded).toZIO.mapError(e => BadRequestException(e.msg))
      projectGetResponse <- projectsService.getSingleProjectADMRequest(identifier = iri)
    } yield Response.json(projectGetResponse.toJsValue.toString)

  private def getProjectByShortname(shortname: String): Task[Response] =
    for {
      shortnameIdentifier <- ShortnameIdentifier.fromString(shortname).toZIO.mapError(e => BadRequestException(e.msg))
      projectGetResponse  <- projectsService.getSingleProjectADMRequest(identifier = shortnameIdentifier)
    } yield Response.json(projectGetResponse.toJsValue.toString)

  private def getProjectByShortcode(shortcode: String): Task[Response] =
    for {
      shortcodeIdentifier <- ShortcodeIdentifier.fromString(shortcode).toZIO.mapError(e => BadRequestException(e.msg))
      projectGetResponse  <- projectsService.getSingleProjectADMRequest(identifier = shortcodeIdentifier)
    } yield Response.json(projectGetResponse.toJsValue.toString())

  private def createProject(request: Request, requestingUser: UserADM): Task[Response] =
    for {
      body                  <- request.body.asString
      payload               <- ZIO.fromEither(body.fromJson[ProjectCreatePayloadADM]).mapError(e => BadRequestException(e))
      projectCreateResponse <- projectsService.createProjectADMRequest(payload, requestingUser)
    } yield Response.json(projectCreateResponse.toJsValue.toString)

  private def deleteProject(iriUrlEncoded: String, requestingUser: UserADM): Task[Response] =
    for {
      iriDecoded            <- RouteUtilZ.urlDecode(iriUrlEncoded, s"Failed to URL decode IRI parameter $iriUrlEncoded.")
      projectIri            <- Iri.ProjectIri.make(iriDecoded).toZIO.mapError(e => BadRequestException(e.msg))
      projectDeleteResponse <- projectsService.deleteProject(projectIri, requestingUser)
    } yield Response.json(projectDeleteResponse.toJsValue.toString())

  private def updateProject(iriUrlEncoded: String, request: Request, requestingUser: UserADM): Task[Response] =
    for {
      iriDecoded            <- RouteUtilZ.urlDecode(iriUrlEncoded, s"Failed to URL decode IRI parameter $iriUrlEncoded.")
      projectIri            <- Iri.ProjectIri.make(iriDecoded).toZIO.mapError(e => BadRequestException(e.msg))
      body                  <- request.body.asString
      payload               <- ZIO.fromEither(body.fromJson[ProjectUpdatePayloadADM]).mapError(e => BadRequestException(e))
      projectChangeResponse <- projectsService.updateProject(projectIri, payload, requestingUser)
    } yield Response.json(projectChangeResponse.toJsValue.toString)
}

object ProjectsRouteZ {
  val layer: URLayer[AppConfig with ProjectsService with AuthenticationMiddleware, ProjectsRouteZ] =
    ZLayer.fromFunction(ProjectsRouteZ.apply _)
}
