/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.actor
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import zio._

import scala.concurrent.ExecutionContext

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core
import org.knora.webapi.core.ActorSystem
import org.knora.webapi.core.AppRouter
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.http.version.ServerVersion
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.routing
import org.knora.webapi.routing.admin._
import org.knora.webapi.routing.v2._
import org.knora.webapi.slice.admin.api.service.ProjectADMRestService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoService
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

trait ApiRoutes {
  val routes: Route
}

object ApiRoutes {

  /**
   * All routes composed together.
   */
  val layer: URLayer[
    ActorSystem
      with AppConfig
      with AppRouter
      with IriConverter
      with KnoraProjectRepo
      with MessageRelay
      with ProjectADMRestService
      with RestCardinalityService
      with RestResourceInfoService
      with StringFormatter
      with core.State
      with routing.Authenticator,
    ApiRoutes
  ] =
    ZLayer {
      for {
        sys       <- ZIO.service[ActorSystem]
        router    <- ZIO.service[AppRouter]
        appConfig <- ZIO.service[AppConfig]
        routeData <- ZIO.succeed(KnoraRouteData(sys.system, router.ref, appConfig))
        runtime <- ZIO.runtime[
                     AppConfig
                       with IriConverter
                       with KnoraProjectRepo
                       with MessageRelay
                       with ProjectADMRestService
                       with RestCardinalityService
                       with RestResourceInfoService
                       with StringFormatter
                       with core.State
                       with routing.Authenticator
                   ]
      } yield ApiRoutesImpl(routeData, runtime, appConfig)
    }
}

/**
 * All routes composed together and CORS activated based on the
 * the configuration in application.conf (akka-http-cors).
 *
 * ALL requests go through each of the routes in ORDER.
 * The FIRST matching route is used for handling a request.
 */
private final case class ApiRoutesImpl(
  private val routeData: KnoraRouteData,
  private implicit val runtime: Runtime[
    AppConfig
      with IriConverter
      with KnoraProjectRepo
      with MessageRelay
      with ProjectADMRestService
      with RestCardinalityService
      with RestResourceInfoService
      with StringFormatter
      with core.State
      with routing.Authenticator
  ],
  private val appConfig: AppConfig
) extends ApiRoutes
    with AroundDirectives {

  private implicit val system: actor.ActorSystem          = routeData.system
  private implicit val executionContext: ExecutionContext = system.dispatcher

  val routes: Route =
    logDuration {
      ServerVersion.addServerHeader {
        DSPApiDirectives.handleErrors(routeData.system, appConfig) {
          CorsDirectives.cors(CorsSettings(routeData.system)) {
            DSPApiDirectives.handleErrors(routeData.system, appConfig) {
              HealthRoute().makeRoute ~
                VersionRoute().makeRoute ~
                RejectingRoute(appConfig, runtime).makeRoute ~
                OntologiesRouteV2().makeRoute ~
                SearchRouteV2(appConfig.v2.fulltextSearch.searchValueMinLength).makeRoute ~
                ResourcesRouteV2(appConfig).makeRoute ~
                ValuesRouteV2().makeRoute ~
                StandoffRouteV2().makeRoute ~
                ListsRouteV2().makeRoute ~
                AuthenticationRouteV2().makeRoute ~
                GroupsRouteADM(routeData, runtime).makeRoute ~
                ListsRouteADM(routeData, runtime).makeRoute ~
                PermissionsRouteADM(routeData, runtime).makeRoute ~
                ProjectsRouteADM().makeRoute ~
                StoreRouteADM(routeData, runtime).makeRoute ~
                UsersRouteADM().makeRoute ~
                FilesRouteADM(routeData, runtime).makeRoute
            }
          }
        }
      }
    }
}
