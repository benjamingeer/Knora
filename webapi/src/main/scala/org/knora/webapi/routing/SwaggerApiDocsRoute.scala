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

import akka.http.scaladsl.server.Route
import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.Info
import io.swagger.models.auth.BasicAuthDefinition
import io.swagger.models.{ExternalDocs, Scheme}
import org.knora.webapi.routing.admin._

/**
  * Provides the '/api-docs' endpoint serving the 'swagger.json' OpenAPI specification
  */
class SwaggerApiDocsRoute(routeData: KnoraRouteData) extends KnoraRoute(routeData) with SwaggerHttpService {

    // List all routes here
    override val apiClasses: Set[Class[_]] = Set(
        classOf[GroupsRouteADM],
        classOf[ListsRouteADM],
        classOf[PermissionsRouteADM],
        classOf[ProjectsRouteADM],
        classOf[StoreRouteADM],
        classOf[UsersRouteADM],
        classOf[HealthRoute]
    )

    override val schemes: List[Scheme] = if (settings.externalKnoraApiProtocol == "http") {
        List(Scheme.HTTP)
    } else if (settings.externalKnoraApiProtocol == "https") {
        List(Scheme.HTTPS)
    } else {
        List(Scheme.HTTP)
    }

    // swagger will publish at: http://locahost:3333/api-docs/swagger.json

    override val host: String = settings.externalKnoraApiHostPort // the url of your api, not swagger's json endpoint
    override val basePath = "/"    //the basePath for the API you are exposing
    override val apiDocsPath = "api-docs" //where you want the swagger-json endpoint exposed
    override val info = Info(version = "1.8.0") //provides license and other description details
    override val externalDocs = Some(new ExternalDocs("Knora Docs", "http://docs.knora.org"))
    override val securitySchemeDefinitions = Map("basicAuth" -> new BasicAuthDefinition())

    def knoraApiPath: Route = {
        routes
    }

}
