/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package webapi.src.main.scala.org.knora.webapi.http.version

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.Server
import akka.http.scaladsl.server.Directives.respondWithHeader
import akka.http.scaladsl.server.Route
import org.knora.webapi.http.version.VersionInfo

/**
  * This object provides methods that can be used to add the [[Server]] header to an [[HttpResponse]].
  */
object ServerVersion {

    private val ServerVersionString = s"${VersionInfo.name}/${VersionInfo.version}" + " " + s"akka-http/${VersionInfo.akkaHttpVersion}"
    private val ServerVersionHeader = `Server`(ServerVersionString)

    def addServerHeader(route: Route):Route = respondWithHeader(ServerVersionHeader) {route}
    def getServerVersionHeader() = ServerVersionHeader
}
