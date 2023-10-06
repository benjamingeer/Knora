/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.model.ContentTypes
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.headers.ContentDispositionTypes
import org.apache.pekko.http.scaladsl.model.headers.`Content-Disposition`
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.PathMatcher
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.scaladsl.FileIO
import zio._

import java.nio.file.Files
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import dsp.valueobjects.Iri.ProjectIri
import org.knora.webapi.IRI
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilADM._
import org.knora.webapi.routing._
import org.knora.webapi.slice.admin.api.service.ProjectADMRestService

final case class ProjectsRouteADM(
  interpreter: TapirToPekkoInterpreter,
  projectsEndpointsHandlerF: ProjectsEndpointsHandlerF
)(
  private implicit val runtime: Runtime[
    org.knora.webapi.routing.Authenticator with StringFormatter with MessageRelay with ProjectADMRestService
  ],
  private implicit val executionContext: ExecutionContext
) extends ProjectsADMJsonProtocol {

  private val tapirRoutes: Route = projectsEndpointsHandlerF.handlers.map(interpreter.toRoute(_)).reduce(_ ~ _)
  private val tapirSecureRoutes: Route =
    projectsEndpointsHandlerF.secureHandlers.map(interpreter.toRoute(_)).reduce(_ ~ _)

  private val projectsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "projects")

  def makeRoute: Route =
    tapirRoutes ~
      tapirSecureRoutes ~
      changeProject() ~
      getProjectData()

  /**
   * Updates a project identified by the IRI.
   */
  private def changeProject(): Route =
    path(projectsBasePath / "iri" / Segment) { value =>
      put {
        entity(as[ChangeProjectApiRequestADM]) { apiRequest => requestContext =>
          val getProjectIri =
            ZIO
              .fromOption(Iri.validateAndEscapeProjectIri(value))
              .orElseFail(BadRequestException(s"Invalid project IRI $value"))
              .flatMap(ProjectIri.make(_).toZIO)

          val requestTask = for {
            projectIri           <- getProjectIri
            projectUpdatePayload <- ProjectUpdatePayloadADM.make(apiRequest).toZIO
            requestingUser       <- Authenticator.getUserADM(requestContext)
            uuid                 <- RouteUtilZ.randomUuid()
          } yield ProjectChangeRequestADM(projectIri, projectUpdatePayload, requestingUser, uuid)
          runJsonRouteZ(requestTask, requestContext)
        }
      }
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
        projectData    <- ProjectADMRestService.getAllProjectData(id, requestingUser)
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
