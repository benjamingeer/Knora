/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorSystem
import zio.ZLayer
import scala.concurrent.duration.DurationInt

object ActorDepsTest {

  val stub = ZLayer.succeed {
    class StubActor extends Actor {
      def receive = println(_)
    }
    val system = ActorSystem("test-system")
    val ref    = system.actorOf(Props[StubActor], "stub-actor")
    ActorDeps(system, ref, 5.seconds)
  }
}
