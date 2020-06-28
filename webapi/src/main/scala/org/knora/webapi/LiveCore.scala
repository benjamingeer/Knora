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

package org.knora.webapi

import akka.actor._
import akka.stream.Materializer
import org.knora.webapi.app._
import org.knora.webapi.util.StringFormatter

import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.languageFeature.postfixOps


/**
  * Knora Core abstraction.
  */
trait Core {
    implicit val system: ActorSystem

    implicit val settings: KnoraSettingsImpl

    implicit val materializer: Materializer

    implicit val executionContext: ExecutionContext

    val appActor: ActorRef
}

/**
  * The applications actor system.
  */
trait LiveCore extends Core {

    /**
      * The application's actor system.
      */
    implicit lazy val system: ActorSystem = ActorSystem("webapi")

    /**
      * The application's configuration.
      */
    implicit lazy val settings: KnoraSettingsImpl = KnoraSettings(system)

    /**
      * Provides the actor materializer (akka-http)
      */
    implicit val materializer: Materializer = Materializer.matFromSystem(system)

    /**
      * Provides the default global execution context
      */
    implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)


    // Initialise StringFormatter with the system settings. This must happen before any responders are constructed.
    StringFormatter.init(settings)

    // #supervisor
    /**
      * The main application supervisor actor which is at the top of the actor
      * hierarchy. All other actors are instantiated as child actors. Further,
      * this actor is responsible for the execution of the startup and shutdown
      * sequences.
      */
    lazy val appActor: ActorRef = system.actorOf(
        Props(new ApplicationActor with LiveManagers)
          .withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
        name = APPLICATION_MANAGER_ACTOR_NAME
    )
    // #supervisor
}
