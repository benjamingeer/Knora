/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.api

import dsp.valueobjects.Iri
import eu.timepit.refined.api.{Refined, RefinedTypeOps}
import eu.timepit.refined.numeric.Greater
import org.apache.pekko.http.scaladsl.server.Route
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.{FormatOptions, RenderedResponse}
import org.knora.webapi.slice.common.api.{ApiV2, BaseEndpoints, HandlerMapper, KnoraResponseRenderer, SecuredEndpointAndZioHandler, TapirToPekkoInterpreter}
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.search.api.SearchEndpointsInputs.{InputIri, Offset}
import sttp.model.{HeaderNames, MediaType}
import sttp.tapir.*
import sttp.tapir.codec.refined.*
import zio.{Task, ZIO, ZLayer}

object SearchEndpointsInputs {

  type Offset = Int Refined Greater[-1]

  object Offset extends RefinedTypeOps[Offset, Int] {
    val default: Offset = unsafeFrom(0)
  }

  final case class InputIri private (value: String) extends AnyVal
  object InputIri {

    implicit val tapirCodec: Codec[String, InputIri, CodecFormat.TextPlain] =
      Codec.string.mapEither(InputIri.from)(_.value)

    def from(value: String): Either[String, InputIri] =
      if (Iri.isIri(value)) { Right(InputIri(value)) }
      else { Left(s"Invalid IRI: $value") }

    def unsafeFrom(value: String): InputIri = from(value).fold(e => throw new IllegalArgumentException(e), identity)
  }

  val offset: EndpointInput.Query[Offset] =
    query[Offset]("offset").description("The offset to be used for paging.").default(Offset.default)

  val limitToProject: EndpointInput.Query[Option[ProjectIri]] =
    query[Option[ProjectIri]]("limitToProject").description("The project to limit the search to.")

  val limitToResourceClass: EndpointInput.Query[Option[InputIri]] =
    query[Option[InputIri]]("limitToResourceClass").description("The resource class to limit the search to.")

  val limitToStandoffClass: EndpointInput.Query[Option[InputIri]] =
    query[Option[InputIri]]("limitToStandoffClass").description("The standoff class to limit the search to.")

  val returnFiles: EndpointInput.Query[Boolean] =
    query[Boolean]("returnFiles").description("Whether to return files in the search results.").default(false)
}

final case class SearchEndpoints(baseEndpoints: BaseEndpoints) {

  private val tags = List("v2", "search")
  private val gravsearchDescription =
    "The Gravsearch query. See https://docs.dasch.swiss/latest/DSP-API/03-endpoints/api-v2/query-language/"

  val postGravsearch = baseEndpoints.withUserEndpoint.post
    .in("v2" / "searchextended")
    .in(stringBody.description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Search for resources using a Gravsearch query.")

  val getGravsearch = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchextended" / path[String].description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Search for resources using a Gravsearch query.")

  val postGravsearchCount = baseEndpoints.withUserEndpoint.post
    .in("v2" / "searchextended" / "count")
    .in(stringBody.description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Count resources using a Gravsearch query.")

  val getGravsearchCount = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchextended" / "count" / path[String].description(gravsearchDescription))
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Count resources using a Gravsearch query.")

  val getSearchByLabel = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchbylabel" / path[String]("searchTerm"))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.offset)
    .in(SearchEndpointsInputs.limitToProject)
    .in(SearchEndpointsInputs.limitToResourceClass)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Search for resources by label.")

  val getSearchByLabelCount = baseEndpoints.withUserEndpoint.get
    .in("v2" / "searchbylabel" / "count" / path[String]("searchTerm"))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .in(SearchEndpointsInputs.limitToResourceClass)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Search for resources by label.")

  val getFullTextSearch = baseEndpoints.withUserEndpoint.get
    .in("v2" / "search" / path[String]("searchTerm"))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.offset)
    .in(SearchEndpointsInputs.limitToProject)
    .in(SearchEndpointsInputs.limitToResourceClass)
    .in(SearchEndpointsInputs.limitToStandoffClass)
    .in(SearchEndpointsInputs.returnFiles)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Search for resources by label.")

  val getFullTextSearchCount = baseEndpoints.withUserEndpoint.get
    .in("v2" / "search" / "count" / path[String]("searchTerm"))
    .in(ApiV2.Inputs.formatOptions)
    .in(SearchEndpointsInputs.limitToProject)
    .in(SearchEndpointsInputs.limitToResourceClass)
    .in(SearchEndpointsInputs.limitToStandoffClass)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))
    .tags(tags)
    .description("Search for resources by label.")

  val endpoints: Seq[AnyEndpoint] =
    Seq(
      postGravsearch,
      getGravsearch,
      postGravsearchCount,
      getGravsearchCount,
      getSearchByLabel,
      getSearchByLabelCount,
      getFullTextSearch,
      getFullTextSearchCount
    ).map(_.endpoint)
}

object SearchEndpoints {
  val layer = ZLayer.derive[SearchEndpoints]
}

final case class SearchApiRoutes(
  searchEndpoints: SearchEndpoints,
  searchRestService: SearchRestService,
  mapper: HandlerMapper,
  tapirToPekko: TapirToPekkoInterpreter,
  iriConverter: IriConverter
) {
  private type GravsearchQuery = String

  private val postGravsearch =
    SecuredEndpointAndZioHandler[(GravsearchQuery, FormatOptions), (RenderedResponse, MediaType)](
      searchEndpoints.postGravsearch,
      user => { case (query, opts) => searchRestService.gravsearch(query, opts, user) }
    )

  private val getGravsearch =
    SecuredEndpointAndZioHandler[(GravsearchQuery, FormatOptions), (RenderedResponse, MediaType)](
      searchEndpoints.getGravsearch,
      user => { case (query, opts) => searchRestService.gravsearch(query, opts, user) }
    )

  private val postGravsearchCount =
    SecuredEndpointAndZioHandler[(GravsearchQuery, FormatOptions), (RenderedResponse, MediaType)](
      searchEndpoints.postGravsearchCount,
      user => { case (query, opts) => searchRestService.gravsearchCount(query, opts, user) }
    )

  private val getGravsearchCount =
    SecuredEndpointAndZioHandler[(GravsearchQuery, FormatOptions), (RenderedResponse, MediaType)](
      searchEndpoints.getGravsearchCount,
      user => { case (query, opts) => searchRestService.gravsearchCount(query, opts, user) }
    )

  private val getSearchByLabel =
    SecuredEndpointAndZioHandler[
      (String, FormatOptions, Offset, Option[ProjectIri], Option[InputIri]),
      (RenderedResponse, MediaType)
    ](
      searchEndpoints.getSearchByLabel,
      user => { case (query, opts, offset, project, resourceClass) =>
        searchRestService.searchResourcesByLabelV2(query, opts, offset, project, resourceClass, user)
      }
    )

  private val getSearchByLabelCount =
    SecuredEndpointAndZioHandler[
      (String, FormatOptions, Option[ProjectIri], Option[InputIri]),
      (RenderedResponse, MediaType)
    ](
      searchEndpoints.getSearchByLabelCount,
      _ => { case (query, opts, project, resourceClass) =>
        searchRestService.searchResourcesByLabelCountV2(query, opts, project, resourceClass)
      }
    )

  private val getFullTextSearch =
    SecuredEndpointAndZioHandler[
      (String, FormatOptions, Offset, Option[ProjectIri], Option[InputIri], Option[InputIri], Boolean),
      (RenderedResponse, MediaType)
    ](
      searchEndpoints.getFullTextSearch,
      user => { case (query, opts, offset, project, resourceClass, standoffClass, returnFiles) =>
        searchRestService.fullTextSearch(query, opts, offset, project, resourceClass, standoffClass, returnFiles, user)
      }
    )

  private val getFullTextSearchCount =
    SecuredEndpointAndZioHandler[
      (String, FormatOptions, Option[ProjectIri], Option[InputIri], Option[InputIri]),
      (RenderedResponse, MediaType)
    ](
      searchEndpoints.getFullTextSearchCount,
      _ => { case (query, opts, project, resourceClass, standoffClass) =>
        searchRestService.fullTextSearchCount(query, opts, project, resourceClass, standoffClass)
      }
    )

  val routes: Seq[Route] =
    Seq(
      getFullTextSearch,
      getFullTextSearchCount,
      getSearchByLabel,
      getSearchByLabelCount,
      postGravsearch,
      getGravsearch,
      postGravsearchCount,
      getGravsearchCount
    )
      .map(it => mapper.mapEndpointAndHandler(it))
      .map(it => tapirToPekko.toRoute(it))
}

object SearchApiRoutes {
  val layer = SearchRestService.layer >+> SearchEndpoints.layer >>> ZLayer.derive[SearchApiRoutes]
}

final case class SearchRestService(
  searchResponderV2: SearchResponderV2,
  renderer: KnoraResponseRenderer,
  iriConverter: IriConverter
) {

  def searchResourcesByLabelV2(
    query: String,
    opts: FormatOptions,
    offset: Offset,
    project: Option[ProjectIri],
    limitByResourceClass: Option[InputIri],
    user: User
  ): Task[(RenderedResponse, MediaType)] = for {
    resourceClass <- ZIO.foreach(limitByResourceClass.map(_.value))(iriConverter.asSmartIri)
    searchResult <-
      searchResponderV2.searchResourcesByLabelV2(query, offset.value, project, resourceClass, opts.schema, user)
    response <- renderer.render(searchResult, opts)
  } yield response

  def searchResourcesByLabelCountV2(
    query: String,
    opts: FormatOptions,
    project: Option[ProjectIri],
    limitByResourceClass: Option[InputIri]
  ): Task[(RenderedResponse, MediaType)] = for {
    resourceClass <- ZIO.foreach(limitByResourceClass.map(_.value))(iriConverter.asSmartIri)
    searchResult <-
      searchResponderV2.searchResourcesByLabelCountV2(query, project, resourceClass)
    response <- renderer.render(searchResult, opts)
  } yield response

  def gravsearch(query: String, opts: FormatOptions, user: User): Task[(RenderedResponse, MediaType)] = for {
    searchResult <- searchResponderV2.gravsearchV2(query, opts.schemaRendering, user)
    response     <- renderer.render(searchResult, opts)
  } yield response

  def gravsearchCount(query: String, opts: FormatOptions, user: User): Task[(RenderedResponse, MediaType)] = for {
    searchResult <- searchResponderV2.gravsearchCountV2(query, user)
    response     <- renderer.render(searchResult, opts)
  } yield response

  def fullTextSearch(
    query: RenderedResponse,
    opts: FormatOptions,
    offset: Offset,
    project: Option[ProjectIri],
    resourceClass: Option[InputIri],
    standoffClass: Option[InputIri],
    returnFiles: Boolean,
    user: User
  ): Task[(RenderedResponse, MediaType)] = for {
    resourceClass <- ZIO.foreach(resourceClass.map(_.value))(iriConverter.asSmartIri)
    standoffClass <- ZIO.foreach(standoffClass.map(_.value))(iriConverter.asSmartIri)
    searchResult <- searchResponderV2.fulltextSearchV2(
                      query,
                      offset.value,
                      project,
                      resourceClass,
                      standoffClass,
                      returnFiles,
                      opts.schemaRendering,
                      user
                    )
    response <- renderer.render(searchResult, opts)
  } yield response

  def fullTextSearchCount(
    query: RenderedResponse,
    opts: FormatOptions,
    project: Option[ProjectIri],
    resourceClass: Option[InputIri],
    standoffClass: Option[InputIri]
  ): zio.Task[
    (_root_.org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse, _root_.sttp.model.MediaType)
  ] = for {
    resourceClass <- ZIO.foreach(resourceClass.map(_.value))(iriConverter.asSmartIri)
    standoffClass <- ZIO.foreach(standoffClass.map(_.value))(iriConverter.asSmartIri)
    searchResult  <- searchResponderV2.fulltextSearchCountV2(query, project, resourceClass, standoffClass)
    response      <- renderer.render(searchResult, opts)
  } yield response
}

object SearchRestService {
  val layer = ZLayer.derive[SearchRestService]
}
