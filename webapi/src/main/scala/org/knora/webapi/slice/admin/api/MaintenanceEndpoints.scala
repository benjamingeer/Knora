/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.zio.{jsonBody => zioJsonBody}
import zio.ZLayer
import zio.json.ast.Json

import org.knora.webapi.slice.common.api.BaseEndpoints

final case class MaintenanceEndpoints(baseEndpoints: BaseEndpoints) {

  private val maintenanceBase = "admin" / "maintenance"

  val postMaintenance = baseEndpoints.securedEndpoint.post
    .in(
      maintenanceBase / path[String]
        .name("Maintenance action name")
        .description("""
                       |The name of the maintenance action to be executed. 
                       |Maintenance actions are executed asynchronously in the background.
                       |""".stripMargin)
        .example("fix-top-left-dimensions")
    )
    .in(
      zioJsonBody[Option[Json]]
        .description("""
                       |The optional parameters as json for the maintenance action. 
                       |May be required by certain actions.
                       |""".stripMargin)
    )
    .out(statusCode(StatusCode.Accepted))
}

object MaintenanceEndpoints {
  val layer = ZLayer.derive[MaintenanceEndpoints]
}
