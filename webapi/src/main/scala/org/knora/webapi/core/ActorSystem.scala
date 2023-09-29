/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko
import zio._
import zio.macros.accessible

import scala.concurrent.ExecutionContext

import org.knora.webapi.config.AppConfig
import org.knora.webapi.store.cache.settings.CacheServiceSettings

import pekko.actor

@accessible
trait ActorSystem {
  val system: pekko.actor.ActorSystem
  val cacheServiceSettings: CacheServiceSettings
}

object ActorSystem {

  private def acquire(executionContext: ExecutionContext): URIO[Any, actor.ActorSystem] =
    ZIO
      .attempt(
        pekko.actor.ActorSystem(
          name = "webapi",
          config = None,
          classLoader = None,
          defaultExecutionContext = Some(executionContext)
        )
      )
      .zipLeft(ZIO.logInfo(">>> Acquire Actor System <<<"))
      .orDie

  private def release(system: pekko.actor.ActorSystem): URIO[Any, actor.Terminated] =
    ZIO
      .fromFuture(_ => system.terminate())
      .zipLeft(ZIO.logInfo(">>> Release Actor System <<<"))
      .orDie

  val layer: ZLayer[AppConfig, Nothing, ActorSystem] =
    ZLayer.scoped {
      for {
        config      <- ZIO.service[AppConfig]
        context     <- ZIO.executor.map(_.asExecutionContext)
        actorSystem <- ZIO.acquireRelease(acquire(context))(release)
      } yield new ActorSystem {
        override val system: pekko.actor.ActorSystem            = actorSystem
        override val cacheServiceSettings: CacheServiceSettings = new CacheServiceSettings(config)
      }
    }
}
