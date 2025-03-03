/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.cors.scaladsl.CorsDirectives
import org.apache.pekko.http.cors.scaladsl.settings.CorsSettings
import org.apache.pekko.http.scaladsl.model.HttpMethods.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.http.version.ServerVersion
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.routing
import org.knora.webapi.routing.v2.*
import org.knora.webapi.slice.admin.api.AdminApiRoutes
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.infrastructure.api.ManagementRoutes
import org.knora.webapi.slice.lists.api.ListsApiV2Routes
import org.knora.webapi.slice.ontology.api.OntologyV2RequestParser
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoRoutes
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resources.api.ResourcesApiRoutes
import org.knora.webapi.slice.search.api.SearchApiRoutes
import org.knora.webapi.slice.security.Authenticator as WebApiAuthenticator
import org.knora.webapi.slice.security.api.AuthenticationApiRoutes
import org.knora.webapi.slice.shacl.api.ShaclApiRoutes
import org.knora.webapi.store.iiif.api.SipiService

/**
 * All routes composed together and CORS activated based on the
 * the configuration in application.conf (pekko-http-cors).
 *
 * ALL requests go through each of the routes in ORDER.
 * The FIRST matching route is used for handling a request.
 */
final case class ApiRoutes(
  appConfig: AppConfig,
  adminApiRoutes: AdminApiRoutes,
  authenticationApiRoutes: AuthenticationApiRoutes,
  listsApiV2Routes: ListsApiV2Routes,
  resourceInfoRoutes: ResourceInfoRoutes,
  resourcesApiRoutes: ResourcesApiRoutes,
  searchApiRoutes: SearchApiRoutes,
  shaclApiRoutes: ShaclApiRoutes,
  managementRoutes: ManagementRoutes,
)(implicit val runtime: Runtime[ApiRoutes.ApiRoutesRuntime], system: ActorSystem)
    extends AroundDirectives {

  val routes: Route =
    logDuration {
      ServerVersion.addServerHeader {
        DSPApiDirectives.handleErrors(appConfig) {
          CorsDirectives.cors(
            CorsSettings(system)
              .withAllowedMethods(List(GET, PUT, POST, DELETE, PATCH, HEAD, OPTIONS)),
          ) {
            DSPApiDirectives.handleErrors(appConfig) {
              val tapirRoutes =
                (
                  adminApiRoutes.routes ++
                    authenticationApiRoutes.routes ++
                    listsApiV2Routes.routes ++
                    managementRoutes.routes ++
                    resourceInfoRoutes.routes ++
                    resourcesApiRoutes.routes ++
                    searchApiRoutes.routes ++
                    shaclApiRoutes.routes
                ).reduce(_ ~ _)
              val pekkoRoutes =
                OntologiesRouteV2().makeRoute ~ StandoffRouteV2().makeRoute
              tapirRoutes ~ pekkoRoutes
            }
          }
        }
      }
    }
}

object ApiRoutes {

  private type ApiRoutesRuntime =
    ApiComplexV2JsonLdRequestParser & AppConfig & AuthenticationApiRoutes & AuthorizationRestService & core.State &
      IriConverter & ListsApiV2Routes & MessageRelay & OntologyV2RequestParser & ProjectService &
      RestCardinalityService & SearchApiRoutes & SearchResponderV2 & SipiService & StringFormatter & UserService &
      ValuesResponderV2 & WebApiAuthenticator

  /**
   * All routes composed together.
   */
  val layer: URLayer[
    ActorSystem & AdminApiRoutes & ApiRoutesRuntime & ManagementRoutes & ResourceInfoRoutes & ResourcesApiRoutes &
      ShaclApiRoutes,
    ApiRoutes,
  ] =
    ZLayer {
      for {
        system                  <- ZIO.service[ActorSystem]
        appConfig               <- ZIO.service[AppConfig]
        adminApiRoutes          <- ZIO.service[AdminApiRoutes]
        authenticationApiRoutes <- ZIO.service[AuthenticationApiRoutes]
        listsApiV2Routes        <- ZIO.service[ListsApiV2Routes]
        resourceInfoRoutes      <- ZIO.service[ResourceInfoRoutes]
        resourcesApiRoutes      <- ZIO.service[ResourcesApiRoutes]
        searchApiRoutes         <- ZIO.service[SearchApiRoutes]
        shaclApiRoutes          <- ZIO.service[ShaclApiRoutes]
        managementRoutes        <- ZIO.service[ManagementRoutes]
        runtime                 <- ZIO.runtime[ApiRoutesRuntime]
      } yield ApiRoutes(
        appConfig,
        adminApiRoutes,
        authenticationApiRoutes,
        listsApiV2Routes,
        resourceInfoRoutes,
        resourcesApiRoutes,
        searchApiRoutes,
        shaclApiRoutes,
        managementRoutes,
      )(runtime, system)
    }
}
