/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.RouteResult
import akka.util.ByteString
import zio.Runtime
import zio.Task
import zio.UIO
import zio.ZIO

import java.util.UUID
import scala.concurrent.Future

import dsp.errors.BadRequestException
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.IRI
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest.KnoraRequestADM
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.KnoraResponseADM
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages._

/**
 * Convenience methods for Knora Admin routes.
 */
object RouteUtilADM {

  /**
   * Transforms all ontology IRIs referenced inside a KnoraResponseADM into their external format.
   *
   * @param response the response that should be transformed
   * @return the transformed [[KnoraResponseADM]]
   */
  def transformResponseIntoExternalFormat(
    response: KnoraResponseADM
  ): KnoraResponseADM = {
    val sf = StringFormatter.getGeneralInstance

    def projectAsExternalRepresentation(project: ProjectADM): ProjectADM = {
      val ontologiesExternal = project.ontologies.map(sf.toSmartIri(_)).map(_.toOntologySchema(ApiV2Complex).toString)
      project.copy(ontologies = ontologiesExternal)
    }

    def groupAsExternalRepresentation(group: GroupADM): GroupADM = {
      val projectExternal = projectAsExternalRepresentation(group.project)
      group.copy(project = projectExternal)
    }

    def userAsExternalRepresentation(user: UserADM): UserADM = {
      val groupsExternal   = user.groups.map { g: GroupADM => groupAsExternalRepresentation(g) }
      val projectsExternal = user.projects.map { p: ProjectADM => projectAsExternalRepresentation(p) }
      user.copy(groups = groupsExternal, projects = projectsExternal)
    }

    response match {
      case ProjectsGetResponseADM(projects) => ProjectsGetResponseADM(projects.map(projectAsExternalRepresentation(_)))
      case ProjectGetResponseADM(project)   => ProjectGetResponseADM(projectAsExternalRepresentation(project))
      case ProjectMembersGetResponseADM(members) =>
        ProjectMembersGetResponseADM(members.map(userAsExternalRepresentation(_)))
      case ProjectAdminMembersGetResponseADM(members) =>
        ProjectAdminMembersGetResponseADM(members.map(userAsExternalRepresentation(_)))
      case ProjectOperationResponseADM(project) => ProjectOperationResponseADM(projectAsExternalRepresentation(project))

      case GroupsGetResponseADM(groups) => GroupsGetResponseADM(groups.map(groupAsExternalRepresentation(_)))
      case GroupGetResponseADM(group)   => GroupGetResponseADM(groupAsExternalRepresentation(group))
      case GroupMembersGetResponseADM(members) =>
        GroupMembersGetResponseADM(members.map(userAsExternalRepresentation(_)))
      case GroupOperationResponseADM(group) => GroupOperationResponseADM(groupAsExternalRepresentation(group))

      case UsersGetResponseADM(users) => UsersGetResponseADM(users.map(userAsExternalRepresentation(_)))
      case UserResponseADM(user)      => UserResponseADM(userAsExternalRepresentation(user))
      case UserProjectMembershipsGetResponseADM(projects) =>
        UserProjectMembershipsGetResponseADM(projects.map(projectAsExternalRepresentation(_)))
      case UserProjectAdminMembershipsGetResponseADM(projects) =>
        UserProjectAdminMembershipsGetResponseADM(projects.map(projectAsExternalRepresentation(_)))
      case UserGroupMembershipsGetResponseADM(groups) =>
        UserGroupMembershipsGetResponseADM(groups.map(groupAsExternalRepresentation(_)))
      case UserOperationResponseADM(user) => UserOperationResponseADM(userAsExternalRepresentation(user))

      case _ => response
    }
  }

  /**
   * Sends a message to a responder and completes the HTTP request by returning the response as JSON.
   *
   * @param requestTask    A [[Task]] containing a [[KnoraRequestADM]] message that should be sent to the responder manager.
   * @param requestContext The akka-http [[RequestContext]].
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runJsonRouteZ[R](
    requestTask: ZIO[R, Throwable, KnoraRequestADM],
    requestContext: RequestContext
  )(implicit runtime: Runtime[R with MessageRelay]): Future[RouteResult] =
    UnsafeZioRun.runToFuture(requestTask.flatMap(doRunJsonRoute(_, requestContext)))

  /**
   * Sends a message to a responder and completes the HTTP request by returning the response as JSON.
   *
   * @param requestFuture    A [[Task]] containing a [[KnoraRequestADM]] message that should be sent to the responder manager.
   * @param requestContext The akka-http [[RequestContext]].
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runJsonRouteF(
    requestFuture: Future[KnoraRequestADM],
    requestContext: RequestContext
  )(implicit runtime: Runtime[MessageRelay]): Future[RouteResult] =
    UnsafeZioRun.runToFuture(ZIO.fromFuture(_ => requestFuture).flatMap(doRunJsonRoute(_, requestContext)))

  def runJsonRoute(
    request: KnoraRequestADM,
    requestContext: RequestContext
  )(implicit runtime: Runtime[MessageRelay]): Future[RouteResult] =
    UnsafeZioRun.runToFuture(doRunJsonRoute(request, requestContext))

  private def doRunJsonRoute(request: KnoraRequestADM, ctx: RequestContext): ZIO[MessageRelay, Throwable, RouteResult] =
    createResponse(request).flatMap(completeContext(ctx, _))

  private def createResponse(request: KnoraRequestADM): ZIO[MessageRelay, Throwable, HttpResponse] =
    for {
      knoraResponse         <- MessageRelay.ask[KnoraResponseADM](request)
      knoraResponseExternal <- ZIO.attempt(transformResponseIntoExternalFormat(knoraResponse))
      jsonBody               = knoraResponseExternal.toJsValue.asJsObject.compactPrint
    } yield okResponse(`application/json`, jsonBody)

  private def okResponse(contentType: ContentType, body: String) =
    HttpResponse(StatusCodes.OK, entity = HttpEntity(contentType, ByteString(body)))

  def completeContext(ctx: RequestContext, response: HttpResponse): Task[RouteResult] =
    ZIO.fromFuture(_ => ctx.complete(response))

  case class IriUserUuid(iri: IRI, user: UserADM, uuid: UUID)
  case class IriUser(iri: IRI, user: UserADM)
  case class Iri(iri: IRI)
  case class UserUuid(user: UserADM, uuid: UUID)

  def getIriUserUuid(
    iri: String,
    requestContext: RequestContext
  ): ZIO[Authenticator with StringFormatter, Throwable, IriUserUuid] =
    for {
      r    <- getIriUser(iri, requestContext)
      uuid <- getApiRequestId
    } yield IriUserUuid(r.iri, r.user, uuid)

  def getApiRequestId: UIO[UUID] = ZIO.random.flatMap(_.nextUUID)

  def getIriUser(
    iri: String,
    requestContext: RequestContext
  ): ZIO[Authenticator with StringFormatter, Throwable, IriUser] =
    for {
      validatedIri <- validateAndEscape(iri)
      user         <- Authenticator.getUserADM(requestContext)
    } yield IriUser(validatedIri, user)

  def validateAndEscape(iri: String): ZIO[StringFormatter, Throwable, IRI] =
    ZIO
      .service[StringFormatter]
      .flatMap(sf => ZIO.attempt(sf.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid IRI: $iri"))))

  def getUserUuid(ctx: RequestContext): ZIO[Authenticator, Throwable, UserUuid] =
    for {
      user <- Authenticator.getUserADM(ctx)
      uuid <- getApiRequestId
    } yield UserUuid(user, uuid)
}
