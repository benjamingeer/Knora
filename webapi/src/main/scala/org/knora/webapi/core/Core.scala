/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import org.knora.webapi.settings.KnoraSettingsImpl

import scala.concurrent.ExecutionContext

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
