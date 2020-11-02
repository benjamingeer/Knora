/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{get, path}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.app.appmessages.{AppState, AppStates, GetAppState}
import spray.json.{JsObject, JsString}

import scala.concurrent.Future
import scala.concurrent.duration._


case class HealthCheckResult(name: String,
                             severity: String,
                             status: Boolean,
                             message: String)

/**
 * Provides health check logic
 */
trait HealthCheck {
    this: HealthRoute =>

    override implicit val timeout: Timeout = 2997.millis

    protected def healthCheck(): Future[HttpResponse] = for {

        state: AppState <- (applicationActor ? GetAppState()).mapTo[AppState]

        result: HealthCheckResult = state match {
            case AppStates.Stopped => unhealthy("Stopped. Please retry later.")
            case AppStates.StartingUp => unhealthy("Starting up. Please retry later.")
            case AppStates.WaitingForTriplestore => unhealthy("Waiting for triplestore. Please retry later.")
            case AppStates.TriplestoreReady => unhealthy("Triplestore ready. Please retry later.")
            case AppStates.UpdatingRepository => unhealthy("Updating repository. Please retry later.")
            case AppStates.RepositoryUpToDate => unhealthy("Repository up to date. Please retry later.")
            case AppStates.CreatingCaches => unhealthy("Creating caches. Please retry later.")
            case AppStates.CachesReady => unhealthy("Caches ready. Please retry later.")
            case AppStates.UpdatingSearchIndex => unhealthy("Updating search index. Please retry later.")
            case AppStates.SearchIndexReady => unhealthy("Search index ready. Please retry later.")
            case AppStates.LoadingOntologies => unhealthy("Loading ontologies. Please retry later.")
            case AppStates.OntologiesReady => unhealthy("Ontologies ready. Please retry later.")
            case AppStates.WaitingForIIIFService => unhealthy("Waiting for IIIF service. Please retry later.")
            case AppStates.IIIFServiceReady => unhealthy("IIIF service ready. Please retry later.")
            case AppStates.WaitingForCacheService => unhealthy("Waiting for cache service. Please retry later.")
            case AppStates.CacheServiceReady => unhealthy("Cache service ready. Please retry later.")
            case AppStates.MaintenanceMode => unhealthy("Application is in maintenance mode. Please retry later.")
            case AppStates.Running => healthy()
        }

        response = createResponse(result)

    } yield response

    protected def createResponse(result: HealthCheckResult): HttpResponse = {

        HttpResponse(
            status = statusCode(result.status),
            entity = HttpEntity(
                ContentTypes.`application/json`,
                JsObject(
                    "name" -> JsString(result.name),
                    "severity" -> JsString(result.severity),
                    "status" -> JsString(status(result.status)),
                    "message" -> JsString(result.message)
                ).compactPrint
            )
        )
    }

    private def status(s: Boolean) = if (s) "healthy" else "unhealthy"

    private def statusCode(s: Boolean) = if (s) StatusCodes.OK else StatusCodes.ServiceUnavailable

    private def unhealthy(str: String) = {
        HealthCheckResult(
            name = "AppState",
            severity = "non fatal",
            status = false,
            message = str
        )
    }

    private def healthy() = {
        HealthCheckResult(
            name = "AppState",
            severity = "non fatal",
            status = true,
            message = "Application is healthy"
        )
    }
}

/**
 * Provides the '/health' endpoint serving the health status.
 */
class HealthRoute(routeData: KnoraRouteData) extends KnoraRoute(routeData) with HealthCheck {

    /**
     * Returns the route.
     */
    override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route = {
        path("health") {
            get {
                requestContext =>
                    requestContext.complete(healthCheck())
            }
        }
    }
}
