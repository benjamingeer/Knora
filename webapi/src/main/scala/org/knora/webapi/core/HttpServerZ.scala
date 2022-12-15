/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core
import zhttp.service.Server
import zio.ZLayer
import zio._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.routing.admin.ProjectsRouteZ

object HttpServerZ {

  val layer: ZLayer[AppRouter & AppConfig & State & ProjectsRouteZ, Nothing, Unit] =
    ZLayer {
      for {
        appConfig     <- ZIO.service[AppConfig]
        projectsRoute <- ZIO.service[ProjectsRouteZ]
        r              = projectsRoute.route
        port           = appConfig.knoraApi.externalZioPort
        _             <- Server.start(port, r).forkDaemon
        _             <- ZIO.logInfo(">>> Acquire ZIO HTTP Server <<<")
      } yield ()
    }
}
