package dsp.api.main

import zio._
import dsp.user.route.UserRoutes
import dsp.user.handler.UserHandler
import dsp.user.repo.impl.UserRepoLive
import zio.logging.removeDefaultLoggers
import zio.metrics.connectors.MetricsConfig
import zio.logging.backend.SLF4J
import dsp.config.AppConfig

object DspMain extends ZIOAppDefault {

  /**
   * Configures Metrics to be run at a set interval, in this case every 5 seconds
   */
  val metricsConfig = ZLayer.succeed(MetricsConfig(5.seconds))
  override val run: Task[Unit] =
    ZIO
      .serviceWithZIO[DspServer](server => server.start())
      .provide(
        // configuration
        AppConfig.live,
        // ZLayer.Debug.mermaid,
        DspServer.layer,
        // Routes
        UserRoutes.layer,
        // Handlers
        UserHandler.layer,
        // Repositories
        UserRepoLive.layer,
        // Operations
        SLF4J.slf4j,
        removeDefaultLoggers
        // metricsConfig
      )

}
