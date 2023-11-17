/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.resourceinfo.api.model.ListResponseDto
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.{Order, OrderBy}
import org.knora.webapi.slice.search.search.api.ApiV2.Headers.xKnoraAcceptSchemaHeader
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.*
import zio.ZLayer

final case class ResourceInfoEndpoints(baseEndpoints: BaseEndpoints) {
  val getResourcesInfo = baseEndpoints.publicEndpoint.get
    .in("v2" / "resources" / "info")
    .in(header[IriIdentifier](xKnoraAcceptSchemaHeader))
    .in(query[String]("resourceClass"))
    .in(query[Option[Order]](Order.queryParamKey))
    .in(query[Option[OrderBy]](OrderBy.queryParamKey))
    .out(jsonBody[ListResponseDto])
}

object ResourceInfoEndpoints {
  val layer = ZLayer.derive[ResourceInfoEndpoints]
}
