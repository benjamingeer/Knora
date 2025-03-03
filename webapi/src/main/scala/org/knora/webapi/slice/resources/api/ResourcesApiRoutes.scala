/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api
import org.apache.pekko.http.scaladsl.server.Route
import zio.ZLayer

import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter

class ResourcesApiRoutes(
  valuesEndpoints: ValuesEndpointsHandler,
  tapirToPekko: TapirToPekkoInterpreter,
) {

  private val handlers = valuesEndpoints.allHandlers

  val routes: Seq[Route] = handlers.map(tapirToPekko.toRoute(_))
}

object ResourcesApiRoutes {
  val layer = ZLayer.derive[ResourcesApiRoutes]
}
