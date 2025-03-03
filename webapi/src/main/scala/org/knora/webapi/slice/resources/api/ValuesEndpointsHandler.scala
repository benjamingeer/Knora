/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import sttp.model.MediaType
import zio.ZLayer

import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.SecuredEndpointHandler
import org.knora.webapi.slice.resources.api.model.ValueUuid
import org.knora.webapi.slice.resources.api.model.VersionDate
import org.knora.webapi.slice.resources.api.service.ValuesRestService

final class ValuesEndpointsHandler(
  endpoints: ValuesEndpoints,
  valuesRestService: ValuesRestService,
  mapper: HandlerMapper,
) {

  val allHandlers =
    Seq(
      SecuredEndpointHandler(endpoints.getValue, valuesRestService.getValue),
      SecuredEndpointHandler(endpoints.postValues, valuesRestService.createValue),
      SecuredEndpointHandler(endpoints.putValues, valuesRestService.updateValue),
      SecuredEndpointHandler(endpoints.deleteValues, valuesRestService.deleteValue),
    ).map(mapper.mapSecuredEndpointHandler(_))
}

object ValuesEndpointsHandler {
  val layer = ZLayer.derive[ValuesEndpointsHandler]
}
