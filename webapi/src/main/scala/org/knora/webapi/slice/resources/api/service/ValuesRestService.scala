/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api.service
import sttp.model.MediaType
import zio.Random
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.BadRequestException
import org.knora.webapi.messages.v2.responder.KnoraResponseV2
import org.knora.webapi.responders.v2.ResourcesResponderV2
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import org.knora.webapi.slice.resources.api.model.ValueUuid
import org.knora.webapi.slice.resources.api.model.ValueVersionDate

final class ValuesRestService(
  private val valuesService: ValuesResponderV2,
  private val resourcesService: ResourcesResponderV2,
  private val requestParser: ApiComplexV2JsonLdRequestParser,
  private val renderer: KnoraResponseRenderer,
) {

  def getValue(user: User)(
    resourceIri: String,
    valueUuid: ValueUuid,
    versionDate: Option[ValueVersionDate],
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] =
    render(
      resourcesService.getResourcesV2(
        Seq(resourceIri),
        None,
        Some(valueUuid.value),
        versionDate.map(_.value),
        withDeleted = true,
        showDeletedValues = false,
        formatOptions.schema,
        formatOptions.rendering,
        user,
      ),
      formatOptions,
    )

  def createValue(user: User)(jsonLd: String): Task[(RenderedResponse, MediaType)] =
    for {
      valueToCreate <- requestParser.createValueV2FromJsonLd(jsonLd).mapError(BadRequestException.apply)
      apiRequestId  <- Random.nextUUID
      knoraResponse <- valuesService.createValueV2(valueToCreate, user, apiRequestId)
      response      <- render(knoraResponse)
    } yield response

  def updateValue(user: User)(jsonLd: String): Task[(RenderedResponse, MediaType)] =
    for {
      valueToUpdate <- requestParser.updateValueV2fromJsonLd(jsonLd).mapError(BadRequestException.apply)
      apiRequestId  <- Random.nextUUID
      knoraResponse <- valuesService.updateValueV2(valueToUpdate, user, apiRequestId)
      response      <- render(knoraResponse)
    } yield response

  def deleteValue(user: User)(jsonLd: String): Task[(RenderedResponse, MediaType)] =
    for {
      valueToDelete <- requestParser.deleteValueV2FromJsonLd(jsonLd).mapError(BadRequestException.apply)
      apiRequestId  <- Random.nextUUID
      knoraResponse <- valuesService.deleteValueV2(valueToDelete, user, apiRequestId)
      response      <- render(knoraResponse)
    } yield response

  private def render(task: Task[KnoraResponseV2], formatOptions: FormatOptions): Task[(RenderedResponse, MediaType)] =
    task.flatMap(renderer.render(_, formatOptions))

  private def render(resp: KnoraResponseV2): Task[(RenderedResponse, MediaType)] =
    renderer.render(resp, FormatOptions.default)
}

object ValuesRestService {
  val layer = ZLayer.derive[ValuesRestService]
}
