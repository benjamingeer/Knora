/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio._
import zio.logging.backend.SLF4J

import org.knora.webapi.core._
import org.knora.webapi.instrumentation.prometheus.PrometheusServer

object Main extends ZIOApp {

  override def environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  /**
   * The `Environment` that we require to exist at startup.
   */
  override type Environment = LayersLive.DspEnvironmentLive

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  override def bootstrap: ZLayer[
    ZIOAppArgs,
    Any,
    Environment
  ] = ZLayer.empty ++ Runtime.removeDefaultLoggers ++ SLF4J.slf4j ++ LayersLive.dspLayersLive

  /* Here we start our Application */
  override def run = for {
    f1 <- PrometheusServer.make.forkDaemon
    f2 <- (AppServer.live *> ZIO.never).forkDaemon
    _  <- f1.join
    _  <- f2.join
  } yield ()
}
