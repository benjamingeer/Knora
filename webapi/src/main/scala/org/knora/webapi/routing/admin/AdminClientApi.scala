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

package org.knora.webapi.routing.admin

import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.util.clientapi._


/**
  * Represents the structure of generated client library code for the admin API.
  */
class AdminClientApi(routeData: KnoraRouteData) extends ClientApi {
    /**
      * The endpoints in this [[ClientApi]].
      */
    override val endpoints: Seq[ClientEndpoint] = Seq(
        new UsersRouteADM(routeData),
        new GroupsRouteADM(routeData),
        new ProjectsRouteADM(routeData),
        new PermissionsRouteADM(routeData)
    )

    /**
      * The name of this [[ClientApi]].
      */
    override val name: String = "AdminApi"

    /**
      * The URL path of this [[ClientApi]].
      */
    override val urlPath: String = "/admin"

    /**
      * A description of this [[ClientApi]].
      */
    override val description: String = "A client API for administering Knora."
}
