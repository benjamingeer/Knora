/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.stream.IOResult
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.swagger.annotations._
import zio.prelude.Validation

import java.nio.file.Files
import java.util.UUID
import javax.ws.rs.Path
import scala.concurrent.Future
import scala.util.Try

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri.ProjectIri
import dsp.valueobjects.Project._
import org.knora.webapi.IRI
import org.knora.webapi.annotation.ApiMayChange
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM

@Api(value = "projects", produces = "application/json")
@Path("/admin/projects")
class ProjectsRouteADM(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Authenticator
    with ProjectsADMJsonProtocol {

  val projectsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "projects")

  /**
   * Returns the route.
   */
  override def makeRoute(): Route =
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

  /* return all projects */
  @ApiOperation(
    value = "Get projects",
    nickname = "getProjects",
    httpMethod = "GET",
    response = classOf[ProjectsGetResponseADM]
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  private def getProjects(): Route = path(projectsBasePath) {
    get { requestContext =>
      log.info("All projects requested.")
      val requestMessage: Future[ProjectsGetRequestADM] = for {
        requestingUser <- getUserADM(
                            requestContext = requestContext
                          )
      } yield ProjectsGetRequestADM(
        requestingUser = requestingUser
      )

      RouteUtilADM.runJsonRoute(
        requestMessageF = requestMessage,
        requestContext = requestContext,
        settings = settings,
        appActor = appActor,
        log = log
      )
    }
  }

  /* create a new project */
  @ApiOperation(
    value = "Add new project",
    nickname = "addProject",
    httpMethod = "POST",
    response = classOf[ProjectOperationResponseADM]
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "\"project\" to create",
        required = true,
        dataTypeClass = classOf[CreateProjectApiRequestADM],
        paramType = "body"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
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
            ProjectCreatePayloadADM
          )

        val requestMessage: Future[ProjectCreateRequestADM] = for {
          projectCreatePayload <- toFuture(projectCreatePayload)
          requestingUser       <- getUserADM(requestContext)
        } yield ProjectCreateRequestADM(
          createRequest = projectCreatePayload,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }
  }

  /* returns all unique keywords for all projects as a list */
  private def getKeywords(): Route = path(projectsBasePath / "Keywords") {
    get { requestContext =>
      val requestMessage: Future[ProjectsKeywordsGetRequestADM] = for {
        requestingUser <- getUserADM(
                            requestContext = requestContext
                          )
      } yield ProjectsKeywordsGetRequestADM(
        requestingUser = requestingUser
      )

      RouteUtilADM.runJsonRoute(
        requestMessageF = requestMessage,
        requestContext = requestContext,
        settings = settings,
        appActor = appActor,
        log = log
      )
    }
  }

  /* returns all keywords for a single project */
  private def getProjectKeywords(): Route =
    path(projectsBasePath / "iri" / Segment / "Keywords") { value =>
      get { requestContext =>
        val checkedProjectIri =
          stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

        val requestMessage: Future[ProjectKeywordsGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
        } yield ProjectKeywordsGetRequestADM(
          projectIri = checkedProjectIri,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * returns a single project identified through iri
   */
  private def getProjectByIri(): Route =
    path(projectsBasePath / "iri" / Segment) { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
          checkedProjectIri =
            stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

        } yield ProjectGetRequestADM(
          identifier = ProjectIdentifierADM(maybeIri = Some(checkedProjectIri)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * returns a single project identified through shortname.
   */
  private def getProjectByShortname(): Route =
    path(projectsBasePath / "shortname" / Segment) { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
          shortNameDec = stringFormatter.validateAndEscapeProjectShortname(
                           value,
                           throw BadRequestException(s"Invalid project shortname $value")
                         )

        } yield ProjectGetRequestADM(
          identifier = ProjectIdentifierADM(maybeShortname = Some(shortNameDec)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * returns a single project identified through shortcode.
   */
  private def getProjectByShortcode(): Route =
    path(projectsBasePath / "shortcode" / Segment) { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
          checkedShortcode = stringFormatter.validateAndEscapeProjectShortcode(
                               value,
                               throw BadRequestException(s"Invalid project shortcode $value")
                             )

        } yield ProjectGetRequestADM(
          identifier = ProjectIdentifierADM(maybeShortcode = Some(checkedShortcode)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * update a project identified by iri
   */
  private def changeProject(): Route =
    path(projectsBasePath / "iri" / Segment) { value =>
      put {
        entity(as[ChangeProjectApiRequestADM]) { apiRequest => requestContext =>
          val checkedProjectIri =
            stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

          /* the api request is already checked at time of creation. see case class. */

          val requestMessage: Future[ProjectChangeRequestADM] = for {
            requestingUser <- getUserADM(
                                requestContext = requestContext
                              )
          } yield ProjectChangeRequestADM(
            projectIri = checkedProjectIri,
            changeProjectRequest = apiRequest.validateAndEscape,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            settings = settings,
            appActor = appActor,
            log = log
          )
        }
      }
    }

  /**
   * API MAY CHANGE: update project status to false
   */
  @ApiMayChange
  private def deleteProject(): Route =
    path(projectsBasePath / "iri" / Segment) { value =>
      delete { requestContext =>
        val checkedProjectIri =
          stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

        val requestMessage: Future[ProjectChangeRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
        } yield ProjectChangeRequestADM(
          projectIri = checkedProjectIri,
          changeProjectRequest = ChangeProjectApiRequestADM(status = Some(false)),
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * API MAY CHANGE: returns all members part of a project identified through iri
   */
  @ApiMayChange
  private def getProjectMembersByIri(): Route =
    path(projectsBasePath / "iri" / Segment / "members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectMembersGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
          checkedProjectIri =
            stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

        } yield ProjectMembersGetRequestADM(
          identifier = ProjectIdentifierADM(maybeIri = Some(checkedProjectIri)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * API MAY CHANGE: returns all members part of a project identified through shortname
   */
  @ApiMayChange
  private def getProjectMembersByShortname(): Route =
    path(projectsBasePath / "shortname" / Segment / "members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectMembersGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
          shortNameDec = stringFormatter.validateAndEscapeProjectShortname(
                           value,
                           throw BadRequestException(s"Invalid project shortname $value")
                         )

        } yield ProjectMembersGetRequestADM(
          identifier = ProjectIdentifierADM(maybeShortname = Some(shortNameDec)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * API MAY CHANGE: returns all members part of a project identified through shortcode
   */
  @ApiMayChange
  private def getProjectMembersByShortcode(): Route =
    path(projectsBasePath / "shortcode" / Segment / "members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectMembersGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
          checkedShortcode = stringFormatter.validateAndEscapeProjectShortcode(
                               value,
                               throw BadRequestException(s"Invalid project shortcode $value")
                             )

        } yield ProjectMembersGetRequestADM(
          identifier = ProjectIdentifierADM(maybeShortcode = Some(checkedShortcode)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * API MAY CHANGE: returns all admin members part of a project identified through iri
   */
  @ApiMayChange
  private def getProjectAdminMembersByIri(): Route =
    path(projectsBasePath / "iri" / Segment / "admin-members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectAdminMembersGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
          checkedProjectIri =
            stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

        } yield ProjectAdminMembersGetRequestADM(
          identifier = ProjectIdentifierADM(maybeIri = Some(checkedProjectIri)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * API MAY CHANGE: returns all admin members part of a project identified through shortname
   */
  @ApiMayChange
  private def getProjectAdminMembersByShortname(): Route =
    path(projectsBasePath / "shortname" / Segment / "admin-members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectAdminMembersGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
          checkedShortname = stringFormatter.validateAndEscapeProjectShortname(
                               value,
                               throw BadRequestException(s"Invalid project shortname $value")
                             )

        } yield ProjectAdminMembersGetRequestADM(
          identifier = ProjectIdentifierADM(maybeShortname = Some(checkedShortname)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * API MAY CHANGE: returns all admin members part of a project identified through shortcode
   */
  @ApiMayChange
  private def getProjectAdminMembersByShortcode(): Route =
    path(projectsBasePath / "shortcode" / Segment / "admin-members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectAdminMembersGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
          checkedShortcode = stringFormatter.validateProjectShortcode(
                               value,
                               throw BadRequestException(s"Invalid project shortcode $value")
                             )

        } yield ProjectAdminMembersGetRequestADM(
          identifier = ProjectIdentifierADM(maybeShortcode = Some(checkedShortcode)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Returns the project's restricted view settings identified through IRI.
   */
  @ApiMayChange
  private def getProjectRestrictedViewSettingsByIri(): Route =
    path(projectsBasePath / "iri" / Segment / "RestrictedViewSettings") { value: String =>
      get { requestContext =>
        val requestMessage: Future[ProjectRestrictedViewSettingsGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )

        } yield ProjectRestrictedViewSettingsGetRequestADM(
          identifier = ProjectIdentifierADM(maybeIri = Some(value)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Returns the project's restricted view settings identified through shortname.
   */
  @ApiMayChange
  private def getProjectRestrictedViewSettingsByShortname(): Route =
    path(projectsBasePath / "shortname" / Segment / "RestrictedViewSettings") { value: String =>
      get { requestContext =>
        val requestMessage: Future[ProjectRestrictedViewSettingsGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
          shortNameDec = java.net.URLDecoder.decode(value, "utf-8")

        } yield ProjectRestrictedViewSettingsGetRequestADM(
          identifier = ProjectIdentifierADM(maybeShortname = Some(shortNameDec)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Returns the project's restricted view settings identified through shortcode.
   */
  @ApiMayChange
  private def getProjectRestrictedViewSettingsByShortcode(): Route =
    path(projectsBasePath / "shortcode" / Segment / "RestrictedViewSettings") { value: String =>
      get { requestContext =>
        val requestMessage: Future[ProjectRestrictedViewSettingsGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
        } yield ProjectRestrictedViewSettingsGetRequestADM(
          identifier = ProjectIdentifierADM(maybeShortcode = Some(value)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  private val projectDataHeader =
    `Content-Disposition`(ContentDispositionTypes.attachment, Map(("filename", "project-data.trig")))

  /**
   * Returns all ontologies, data, and configuration belonging to a project.
   */
  private def getProjectData(): Route =
    path(projectsBasePath / "iri" / Segment / "AllData") { projectIri: IRI =>
      get {
        respondWithHeaders(projectDataHeader) {
          getProjectDataEntity(
            projectIri = projectIri
          )
        }
      }
    }

  private def getProjectDataEntity(projectIri: IRI): Route = { requestContext =>
    val projectIdentifier = ProjectIdentifierADM(maybeIri = Some(projectIri))

    val httpEntityFuture: Future[HttpEntity.Chunked] = for {
      requestingUser <- getUserADM(
                          requestContext = requestContext
                        )

      requestMessage = ProjectDataGetRequestADM(
                         projectIdentifier = projectIdentifier,
                         requestingUser = requestingUser
                       )

      responseMessage <- (appActor.ask(requestMessage)).mapTo[ProjectDataGetResponseADM]

      // Stream the output file back to the client, then delete the file.

      source: Source[ByteString, Unit] = FileIO.fromPath(responseMessage.projectDataFile).watchTermination() {
                                           case (_: Future[IOResult], result: Future[Done]) =>
                                             result.onComplete((_: Try[Done]) =>
                                               Files.delete(responseMessage.projectDataFile)
                                             )
                                         }

      httpEntity = HttpEntity(ContentTypes.`application/octet-stream`, source)
    } yield httpEntity

    requestContext.complete(httpEntityFuture)
  }
}
