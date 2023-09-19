/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko
import zio._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.store.cache.settings.CacheServiceSettings

object ActorSystemTest {

  def layer(sys: pekko.actor.ActorSystem): ZLayer[AppConfig, Nothing, ActorSystem] =
    ZLayer.scoped {
      for {
        appConfig <- ZIO.service[AppConfig]
        context   <- ZIO.executor.map(_.asExecutionContext)
      } yield new ActorSystem {
        override val system: pekko.actor.ActorSystem            = sys
        override val cacheServiceSettings: CacheServiceSettings = new CacheServiceSettings(appConfig)
      }
    }
}
