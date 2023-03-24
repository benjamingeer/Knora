/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import akka.Done
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.headers.ContentDispositionTypes
import akka.http.scaladsl.model.headers.`Content-Disposition`
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.Route
import akka.stream.IOResult
import akka.stream.scaladsl.FileIO
import zio._
import zio.prelude.Validation

import java.nio.file.Files
import scala.concurrent.Future
import scala.util.Try

import dsp.errors.BadRequestException
import dsp.errors.ValidationException
import dsp.valueobjects.Iri.ProjectIri
import dsp.valueobjects.Project._
import org.knora.webapi.IRI
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilADM._
import org.knora.webapi.routing._

final case class ProjectsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator with MessageRelay]
) extends KnoraRoute(routeData, runtime)
    with ProjectsADMJsonProtocol {

  private val projectsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "projects")

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    getProjects() ~
      addProject() ~
      getKeywords() ~
      getProjectKeywords() ~
      getProjectByIri() ~
      getProjectByShortname() ~
      getProjectByShortcode() ~
      changeProject() ~
      deleteProject() ~
      getProjectMembersByIri() ~
      getProjectMembersByShortname() ~
      getProjectMembersByShortcode() ~
      getProjectAdminMembersByIri() ~
      getProjectAdminMembersByShortname() ~
      getProjectAdminMembersByShortcode() ~
      getProjectRestrictedViewSettingsByIri() ~
      getProjectRestrictedViewSettingsByShortname() ~
      getProjectRestrictedViewSettingsByShortcode() ~
      getProjectData()

  /**
   * Returns all projects.
   */
  private def getProjects(): Route = path(projectsBasePath) {
    get(runJsonRoute(ProjectsGetRequestADM(), _))
  }

  /**
   * Creates a new project.
   */
  private def addProject(): Route = path(projectsBasePath) {
    post {
      entity(as[CreateProjectApiRequestADM]) { apiRequest => requestContext =>
        val id: Validation[Throwable, Option[ProjectIri]]          = ProjectIri.make(apiRequest.id)
        val shortname: Validation[Throwable, ShortName]            = ShortName.make(apiRequest.shortname)
        val shortcode: Validation[Throwable, ShortCode]            = ShortCode.make(apiRequest.shortcode)
        val longname: Validation[Throwable, Option[Name]]          = Name.make(apiRequest.longname)
        val description: Validation[Throwable, ProjectDescription] = ProjectDescription.make(apiRequest.description)
        val keywords: Validation[Throwable, Keywords]              = Keywords.make(apiRequest.keywords)
        val logo: Validation[Throwable, Option[Logo]]              = Logo.make(apiRequest.logo)
        val status: Validation[Throwable, ProjectStatus]           = ProjectStatus.make(apiRequest.status)
        val selfjoin: Validation[Throwable, ProjectSelfJoin]       = ProjectSelfJoin.make(apiRequest.selfjoin)

        val projectCreatePayload: Validation[Throwable, ProjectCreatePayloadADM] =
          Validation.validateWith(id, shortname, shortcode, longname, description, keywords, logo, status, selfjoin)(
            ProjectCreatePayloadADM.apply
          )

        val requestTask = for {
          projectCreatePayload <- projectCreatePayload.toZIO
          requestingUser       <- Authenticator.getUserADM(requestContext)
          uuid                 <- getApiRequestId
        } yield ProjectCreateRequestADM(projectCreatePayload, requestingUser, uuid)
        runJsonRouteZ(requestTask, requestContext)
      }
    }
  }

  /**
   * Returns all unique keywords for all projects as a list.
   */
  private def getKeywords(): Route = path(projectsBasePath / "Keywords") {
    get(runJsonRoute(ProjectsKeywordsGetRequestADM(), _))
  }

  /**
   * Returns all keywords for a single project.
   */
  private def getProjectKeywords(): Route =
    path(projectsBasePath / "iri" / Segment / "Keywords") { projectIri =>
      get { requestContext =>
        val requestTask =
          ProjectIri
            .make(projectIri)
            .toZIO
            .mapBoth(_ => BadRequestException(s"Invalid project IRI $projectIri"), ProjectKeywordsGetRequestADM)
        runJsonRouteZ(requestTask, requestContext)
      }
    }

  /**
   * Returns a single project identified through the IRI.
   */
  private def getProjectByIri(): Route =
    path(projectsBasePath / "iri" / Segment) { value =>
      get(getProject(IriIdentifier.fromString(value).toZIO, _))
    }

  private def getProject(idTask: Task[ProjectIdentifierADM], requestContext: RequestContext) = {
    val requestTask = idTask.mapBoth(e => BadRequestException(e.getMessage), id => ProjectGetRequestADM(id))
    runJsonRouteZ(requestTask, requestContext)
  }

  /**
   * Returns a single project identified through the shortname.
   */
  private def getProjectByShortname(): Route =
    path(projectsBasePath / "shortname" / Segment) { value =>
      get(getProject(ShortnameIdentifier.fromString(value).toZIO, _))
    }

  /**
   * Returns a single project identified through the shortcode.
   */
  private def getProjectByShortcode(): Route =
    path(projectsBasePath / "shortcode" / Segment) { value =>
      get(getProject(ShortcodeIdentifier.fromString(value).toZIO, _))
    }

  /**
   * Updates a project identified by the IRI.
   */
  private def changeProject(): Route =
    path(projectsBasePath / "iri" / Segment) { value =>
      put {
        entity(as[ChangeProjectApiRequestADM]) { apiRequest => requestContext =>
          val checkedProjectIri =
            stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))
          val projectIri: ProjectIri =
            ProjectIri.make(checkedProjectIri).getOrElse(throw BadRequestException(s"Invalid Project IRI"))

          val shortname: Validation[ValidationException, Option[ShortName]] = ShortName.make(apiRequest.shortname)
          val longname: Validation[Throwable, Option[Name]]                 = Name.make(apiRequest.longname)
          val description: Validation[Throwable, Option[ProjectDescription]] =
            ProjectDescription.make(apiRequest.description)
          val keywords: Validation[Throwable, Option[Keywords]]        = Keywords.make(apiRequest.keywords)
          val logo: Validation[Throwable, Option[Logo]]                = Logo.make(apiRequest.logo)
          val status: Validation[Throwable, Option[ProjectStatus]]     = ProjectStatus.make(apiRequest.status)
          val selfjoin: Validation[Throwable, Option[ProjectSelfJoin]] = ProjectSelfJoin.make(apiRequest.selfjoin)

          val projectUpdatePayloadValidation: Validation[Throwable, ProjectUpdatePayloadADM] =
            Validation.validateWith(shortname, longname, description, keywords, logo, status, selfjoin)(
              ProjectUpdatePayloadADM.apply
            )

          /* the api request is already checked at time of creation. see case class. */

          val requestTask = for {
            projectUpdatePayload <- projectUpdatePayloadValidation.toZIO
            requestingUser       <- Authenticator.getUserADM(requestContext)
            uuid                 <- getApiRequestId
          } yield ProjectChangeRequestADM(projectIri, projectUpdatePayload, requestingUser, uuid)
          runJsonRouteZ(requestTask, requestContext)
        }
      }
    }

  /**
   * Updates project status to false.
   */
  private def deleteProject(): Route =
    path(projectsBasePath / "iri" / Segment) { value =>
      delete { requestContext =>
        val projectIri = ProjectIri.make(value).getOrElse(throw BadRequestException(s"Invalid Project IRI $value"))
        val projectStatus =
          ProjectStatus.make(false).getOrElse(throw BadRequestException(s"Invalid Project Status"))
        val projectUpdatePayload = ProjectUpdatePayloadADM(status = Some(projectStatus))
        val requestTask = for {
          requestingUser <- Authenticator.getUserADM(requestContext)
          uuid           <- getApiRequestId
        } yield ProjectChangeRequestADM(projectIri, projectUpdatePayload, requestingUser, uuid)
        runJsonRouteZ(requestTask, requestContext)
      }
    }

  /**
   * Returns all members of a project identified through the IRI.
   */
  private def getProjectMembersByIri(): Route =
    path(projectsBasePath / "iri" / Segment / "members") { value =>
      get(getProjectMembers(IriIdentifier.fromString(value).toZIO, _))
    }

  private def getProjectMembers(idTask: Task[ProjectIdentifierADM], requestContext: RequestContext) = {
    val requestTask = for {
      id             <- idTask.mapError(e => BadRequestException(e.getMessage))
      requestingUser <- Authenticator.getUserADM(requestContext)
    } yield ProjectMembersGetRequestADM(id, requestingUser)
    runJsonRouteZ(requestTask, requestContext)
  }

  /**
   * Returns all members of a project identified through the shortname.
   */
  private def getProjectMembersByShortname(): Route =
    path(projectsBasePath / "shortname" / Segment / "members") { value =>
      get(getProjectMembers(ShortnameIdentifier.fromString(value).toZIO, _))
    }

  /**
   * Returns all members of a project identified through the shortcode.
   */
  private def getProjectMembersByShortcode(): Route =
    path(projectsBasePath / "shortcode" / Segment / "members") { value =>
      get(getProjectMembers(ShortcodeIdentifier.fromString(value).toZIO, _))
    }

  /**
   * Returns all admin members of a project identified through the IRI.
   */
  private def getProjectAdminMembersByIri(): Route =
    path(projectsBasePath / "iri" / Segment / "admin-members") { value =>
      get(getProjectAdminMembers(IriIdentifier.fromString(value).toZIO, _))
    }

  private def getProjectAdminMembers(idTask: Task[ProjectIdentifierADM], requestContext: RequestContext) = {
    val requestTask = for {
      id             <- idTask.mapError(e => BadRequestException(e.getMessage))
      requestingUser <- Authenticator.getUserADM(requestContext)
    } yield ProjectAdminMembersGetRequestADM(id, requestingUser)
    runJsonRouteZ(requestTask, requestContext)
  }

  /**
   * Returns all admin members of a project identified through the shortname.
   */
  private def getProjectAdminMembersByShortname(): Route =
    path(projectsBasePath / "shortname" / Segment / "admin-members") { value =>
      get(getProjectAdminMembers(ShortnameIdentifier.fromString(value).toZIO, _))
    }

  /**
   * Returns all admin members of a project identified through shortcode.
   */
  private def getProjectAdminMembersByShortcode(): Route =
    path(projectsBasePath / "shortcode" / Segment / "admin-members") { value =>
      get(getProjectAdminMembers(ShortcodeIdentifier.fromString(value).toZIO, _))
    }

  /**
   * Returns the project's restricted view settings identified through the IRI.
   */
  private def getProjectRestrictedViewSettingsByIri(): Route =
    path(projectsBasePath / "iri" / Segment / "RestrictedViewSettings") { value: String =>
      get(getProjectRestrictedViewSettings(IriIdentifier.fromString(value).toZIO, _))
    }

  private def getProjectRestrictedViewSettings(idTask: Task[ProjectIdentifierADM], requestContext: RequestContext) = {
    val requestTask = for {
      id     <- idTask.mapError(e => BadRequestException(e.getMessage))
      request = ProjectRestrictedViewSettingsGetRequestADM(id)
    } yield request
    runJsonRouteZ(requestTask, requestContext)
  }

  /**
   * Returns the project's restricted view settings identified through the shortname.
   */
  private def getProjectRestrictedViewSettingsByShortname(): Route =
    path(projectsBasePath / "shortname" / Segment / "RestrictedViewSettings") { value: String =>
      get(getProjectRestrictedViewSettings(ShortnameIdentifier.fromString(value).toZIO, _))
    }

  /**
   * Returns the project's restricted view settings identified through shortcode.
   */
  private def getProjectRestrictedViewSettingsByShortcode(): Route =
    path(projectsBasePath / "shortcode" / Segment / "RestrictedViewSettings") { value: String =>
      get(getProjectRestrictedViewSettings(ShortcodeIdentifier.fromString(value).toZIO, _))
    }

  private val projectDataHeader =
    `Content-Disposition`(ContentDispositionTypes.attachment, Map(("filename", "project-data.trig")))

  /**
   * Returns all ontologies, data, and configuration belonging to a project.
   */
  private def getProjectData(): Route =
    path(projectsBasePath / "iri" / Segment / "AllData") { projectIri: IRI =>
      get(respondWithHeaders(projectDataHeader)(getProjectDataEntity(projectIri)))
    }

  private def getProjectDataEntity(projectIri: IRI): Route = { requestContext =>
    UnsafeZioRun.runToFuture {
      val requestTask = for {
        id             <- IriIdentifier.fromString(projectIri).toZIO
        requestingUser <- Authenticator.getUserADM(requestContext)
        projectData    <- MessageRelay.ask[ProjectDataGetResponseADM](ProjectDataGetRequestADM(id, requestingUser))
        response <- ZIO.attemptBlocking(
                      HttpEntity(
                        ContentTypes.`application/octet-stream`,
                        FileIO.fromPath(projectData.projectDataFile).watchTermination() {
                          case (_: Future[IOResult], result: Future[Done]) =>
                            result.onComplete((_: Try[Done]) => Files.delete(projectData.projectDataFile))
                        }
                      )
                    )
      } yield response
      requestTask.flatMap(response => ZIO.fromFuture(_ => requestContext.complete(response)))
    }
  }
}
