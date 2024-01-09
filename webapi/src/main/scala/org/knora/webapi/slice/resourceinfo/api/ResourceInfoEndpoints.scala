/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.*
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.slice.common.api.ApiV2
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.resourceinfo.api.model.ListResponseDto
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.Order
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.OrderBy

final case class ResourceInfoEndpoints(baseEndpoints: BaseEndpoints) {
  val getResourcesInfo = baseEndpoints.publicEndpoint.get
    .in("v2" / "resources" / "info")
    .in(header[IriIdentifier](ApiV2.Headers.xKnoraAcceptProject))
    .in(query[String]("resourceClass"))
    .in(query[Option[Order]](Order.queryParamKey))
    .in(query[Option[OrderBy]](OrderBy.queryParamKey))
    .out(jsonBody[ListResponseDto])

  val endpoints: Seq[AnyEndpoint] =
    Seq(getResourcesInfo)
}

object ResourceInfoEndpoints {
  val layer = ZLayer.derive[ResourceInfoEndpoints]
}
