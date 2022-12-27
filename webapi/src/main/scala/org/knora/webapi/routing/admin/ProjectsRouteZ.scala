package org.knora.webapi.routing.admin

import zhttp.http._
import zio.ZLayer

import dsp.errors.InternalServerException
import dsp.errors.RequestRejectedException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.handler.ExceptionHandlerZ
import org.knora.webapi.responders.admin.RestProjectsService
import org.knora.webapi.routing.RouteUtilZ

final case class ProjectsRouteZ(
  appConfig: AppConfig,
  projectsService: RestProjectsService
) {

  val route: HttpApp[Any, Nothing] =
    Http
      .collectZIO[Request] {
        // Returns a single project identified by an urlencoded IRI
        case Method.GET -> !! / "admin" / "projects" / "iri" / iri =>
          RouteUtilZ
            .decodeUrl(iri)
            .flatMap(
              projectsService
                .getSingleProjectADMRequest(_)
                .map(project => Response.json(project.toJsValue.toString()))
            )
      }
      .catchAll {
        case RequestRejectedException(e) =>
          ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
        case InternalServerException(e) =>
          ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
      }
}

object ProjectsRouteZ {
  val layer: ZLayer[AppConfig with RestProjectsService, Nothing, ProjectsRouteZ] =
    ZLayer.fromFunction(ProjectsRouteZ.apply _)
}
