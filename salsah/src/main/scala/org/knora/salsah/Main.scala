/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.salsah

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.concurrent.Future

object Main extends App {
    implicit val system = ActorSystem("salsah-system")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher

    val log = akka.event.Logging(system, this.getClass)

    val handler =
        get {
            getFromDirectory("src/public/")
        }

    val (host, port) = ("localhost", 3335)

    log.info(s"Salsah online at http://$host:$port/index.html")

    val bindingFuture: Future[ServerBinding] =
        Http().bindAndHandle(handler, host, port)

    bindingFuture onFailure {
        case ex: Exception =>
            log.error(ex, s"Failed to bind to $host:$port")
    }
}
