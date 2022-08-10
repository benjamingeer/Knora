package org.knora.webapi.core

import org.knora.webapi.settings.KnoraSettingsImpl
import akka.actor
import org.knora.webapi.config.AppConfig
import zio._
import org.knora.webapi.settings.KnoraSettings

final case class ActorSystemTestImpl(_system: actor.ActorSystem) extends ActorSystem { self =>

  override val system: actor.ActorSystem                  = self._system
  override val settings: KnoraSettingsImpl                = KnoraSettings(self._system)
  override val cacheServiceSettings: CacheServiceSettings = new CacheServiceSettings(self._system.settings.config)
}

object ActorSystemTestImpl {

  private def acquire(config: AppConfig) = ZIO.attempt {
    // TODO: need to use TestActorSystem here!!!
    actor.TestActorSystem("webapi")
  }.tap(_ => ZIO.debug(">>> Acquire Live Actor System <<<")).orDie

  private def release(system: actor.ActorSystem) =
    ZIO
      .attempt(system.terminate())
      .tap(_ => ZIO.logInfo(">>> Release Live Actor System <<<"))
      .orDie

  val layer: ZLayer[AppConfig, Nothing, ActorSystem] =
    ZLayer.scoped {
      for {
        config <- ZIO.service[AppConfig]
        system <- ZIO.acquireRelease(acquire(config))(release(_))
        _      <- ZIO.attempt(StringFormatter.initForTest()).orDie   // needs early init before first usage
        _      <- ZIO.attempt(RdfFeatureFactory.init(KnoraSettings(system))).orDie // needs early init before first usage
      } yield ActorSystemTest(system)
    }
}
