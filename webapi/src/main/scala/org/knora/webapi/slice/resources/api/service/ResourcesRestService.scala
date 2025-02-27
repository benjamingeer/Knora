/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api.service
import sttp.model.MediaType
import zio.*

import dsp.errors.BadRequestException
import org.knora.webapi.responders.v2.ResourcesResponderV2
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resources.api.model.GraphDirection
import org.knora.webapi.slice.resources.api.model.IriDto
import org.knora.webapi.slice.resources.api.model.VersionDate

final case class ResourcesRestService(
  private val resourcesService: ResourcesResponderV2,
  private val searchService: SearchResponderV2,
  private val iriConverter: IriConverter,
  private val requestParser: ApiComplexV2JsonLdRequestParser,
  private val renderer: KnoraResponseRenderer,
) {

  def getResourcesIiifManifest(user: User)(
    resourceIri: IriDto,
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] =
    resourcesService
      .getIiifManifestV2(resourceIri.value, user)
      .flatMap(renderer.render(_, formatOptions))

  def getResourcesPreview(user: User)(
    resourceIris: List[String],
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] =
    ensureIris(resourceIris) *>
      resourcesService
        .getResourcePreviewV2(resourceIris, withDeleted = true, formatOptions.schema, user)
        .flatMap(renderer.render(_, formatOptions))

  def getResourcesProjectHistoryEvents(
    user: User,
  )(projectIri: ProjectIri, formatOptions: FormatOptions): Task[(RenderedResponse, MediaType)] =
    resourcesService
      .getProjectResourceHistoryEvents(projectIri, user)
      .flatMap(renderer.render(_, formatOptions))

  def getResourcesHistoryEvents(
    user: User,
  )(resourceIri: IriDto, formatOptions: FormatOptions): Task[(RenderedResponse, MediaType)] =
    resourcesService
      .getResourceHistoryEvents(resourceIri.value, user)
      .flatMap(renderer.render(_, formatOptions))

  def searchResourcesByProjectAndClass(user: User)(
    resourceClass: IriDto,
    orderByProperty: Option[IriDto],
    page: Int,
    projectIri: ProjectIri,
    format: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = for {
    resourceClass <- iriConverter
                       .asResourceClassIri(resourceClass.value)
                       .mapBoth(BadRequestException.apply, _.smartIri.toInternalSchema)
    order <- ZIO
               .foreach(orderByProperty.map(_.value))(iriConverter.asPropertyIri)
               .mapBoth(BadRequestException.apply, _.map(_.smartIri.toInternalSchema))
    rendering = format.schemaRendering
    result   <- searchService.searchResourcesByProjectAndClassV2(projectIri, resourceClass, order, page, rendering, user)
    response <- renderer.render(result, format)
  } yield response

  def getResourceHistory(user: User)(
    resourceIri: IriDto,
    formatOptions: FormatOptions,
    startDate: Option[VersionDate],
    endDate: Option[VersionDate],
  ): Task[(RenderedResponse, MediaType)] =
    resourcesService
      .getResourceHistory(resourceIri.value, startDate, endDate, user)
      .flatMap(renderer.render(_, formatOptions))

  def getResources(user: User)(
    resourceIris: List[String],
    formatOptions: FormatOptions,
    version: Option[VersionDate],
  ): Task[(RenderedResponse, MediaType)] =
    ensureIris(resourceIris) *>
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

  private def ensureIris(values: List[String]): Task[Unit] =
    ZIO.foreachDiscard(values)(str => ZIO.fromEither(IriDto.from(str)).mapError(BadRequestException.apply))

  def getResourcesGraph(user: User)(
    resourceIri: IriDto,
    formatOptions: FormatOptions,
    depth: Int,
    direction: GraphDirection,
    excludeProperty: Option[IriDto],
  ): Task[(RenderedResponse, MediaType)] = for {
    excludeProperty <- ZIO.foreach(excludeProperty.map(_.value))(iriConverter.asSmartIri)
    result          <- resourcesService.getGraphDataResponseV2(resourceIri.value, depth, direction, excludeProperty, user)
    response        <- renderer.render(result, formatOptions)
  } yield response

  def getResourceAsTeiV2(user: User)(
    resourceIri: IriDto,
    mappingIri: Option[IriDto],
    textProperty: IriDto,
    gravsearchTemplateIri: Option[IriDto],
    headerXSLTIri: Option[IriDto],
  ) = for {
    textProp          <- iriConverter.asSmartIri(textProperty.value)
    resource           = resourceIri.value
    mapping            = mappingIri.map(_.value)
    gravsearchTemplate = gravsearchTemplateIri.map(_.value)
    headerXslt         = headerXSLTIri.map(_.value)
    result            <- resourcesService.getResourceAsTeiV2(resource, textProp, mapping, gravsearchTemplate, headerXslt, user)
  } yield (result.toXML, MediaType.ApplicationXml)

  def eraseResource(user: User)(formatOptions: FormatOptions, jsonLd: String): Task[(RenderedResponse, MediaType)] =
    for {
      uuid <- Random.nextUUID
      eraseRequest <- requestParser
                        .deleteOrEraseResourceRequestV2(jsonLd, user, uuid)
                        .mapError(BadRequestException.apply)
      result   <- resourcesService.eraseResourceV2(eraseRequest)
      response <- renderer.render(result, formatOptions)
    } yield response

  def deleteResource(user: User)(formatOptions: FormatOptions, jsonLd: String): Task[(RenderedResponse, MediaType)] =
    for {
      uuid <- Random.nextUUID
      eraseRequest <- requestParser
                        .deleteOrEraseResourceRequestV2(jsonLd, user, uuid)
                        .mapError(BadRequestException.apply)
      result   <- resourcesService.markResourceAsDeletedV2(eraseRequest)
      response <- renderer.render(result, formatOptions)
    } yield response

  def updateResourceMetadata(
    user: User,
  )(formatOptions: FormatOptions, jsonLd: String): Task[(RenderedResponse, MediaType)] =
    for {
      uuid <- Random.nextUUID
      eraseRequest <- requestParser
                        .updateResourceMetadataRequestV2(jsonLd, user, uuid)
                        .mapError(BadRequestException.apply)
      result   <- resourcesService.updateResourceMetadataV2(eraseRequest)
      response <- renderer.render(result, formatOptions)
    } yield response
}

object ResourcesRestService {
  val layer = ZLayer.derive[ResourcesRestService]
}
