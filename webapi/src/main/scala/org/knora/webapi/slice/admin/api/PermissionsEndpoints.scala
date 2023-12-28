/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.spray.jsonBody as sprayJsonBody
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateAdministrativePermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsADMJsonProtocol
import org.knora.webapi.slice.common.api.BaseEndpoints
final case class PermissionsEndpoints(base: BaseEndpoints) extends PermissionsADMJsonProtocol {

  private val permissionsBase = "admin" / "permissions"

  val postPermissionsAp = base.securedEndpoint.post
    .in(permissionsBase / "ap")
    .description("Create a new administrative permission")
    .in(sprayJsonBody[CreateAdministrativePermissionAPIRequestADM])
    .out(sprayJsonBody[AdministrativePermissionCreateResponseADM])
}

object PermissionsEndpoints {
  val layer = ZLayer.derive[PermissionsEndpoints]
}
