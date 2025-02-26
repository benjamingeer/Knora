/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api.service
import sttp.model.MediaType
import zio.*

import org.knora.webapi.responders.v2.ResourcesResponderV2
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import org.knora.webapi.slice.resources.api.model.VersionDate

final case class ResourcesRestService(resourcesService: ResourcesResponderV2, renderer: KnoraResponseRenderer) {

  def getResources(user: User)(
    resourceIris: List[String],
    formatOptions: FormatOptions,
    version: Option[VersionDate],
  ): Task[(RenderedResponse, MediaType)] =
    resourcesService
      .getResourcesV2(
        resourceIris,
        propertyIri = None,
        valueUuid = None,
        version.map(_.value),
        withDeleted = true,
        showDeletedValues = false,
        formatOptions.schema,
        formatOptions.rendering,
        user,
      )
      .flatMap(renderer.render(_, formatOptions))

}

object ResourcesRestService {
  val layer = ZLayer.derive[ResourcesRestService]
}
