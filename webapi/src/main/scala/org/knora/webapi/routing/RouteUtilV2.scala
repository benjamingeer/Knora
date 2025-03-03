/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.server.RequestContext
import org.apache.pekko.http.scaladsl.server.RouteResult
import zio.*
import zio.prelude.Validation

import scala.concurrent.Future
import scala.util.control.Exception.catching

import dsp.errors.BadRequestException
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest.KnoraRequestV2
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.rdf.RdfFormat
import org.knora.webapi.messages.v2.responder.KnoraResponseV2
import org.knora.webapi.slice.common.api.ApiV2
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

import ApiV2.Headers.xKnoraAcceptMarkup
import ApiV2.Headers.xKnoraAcceptProject
import ApiV2.Headers.xKnoraAcceptSchemaHeader
import ApiV2.Headers.xKnoraJsonLdRendering
import ApiV2.QueryParams
import ApiV2.QueryParams.schema

/**
 * Handles message formatting, content negotiation, and simple interactions with responders, on behalf of Knora routes.
 */
object RouteUtilV2 {

  def getStringQueryParam(ctx: RequestContext, key: String): Option[String] = getQueryParamsMap(ctx).get(key)
  private def getQueryParamsMap(ctx: RequestContext): Map[String, String]   = ctx.request.uri.query().toMap

  /**
   * Gets the ontology schema that is specified in an HTTP request. The schema can be specified
   * either in the HTTP header "x-knora-accept-schema-header" or in the URL parameter "schema".
   * If no schema is specified in the request, the default of [[ApiV2Complex]] is returned.
   *
   * @param ctx the pekko-http [[RequestContext]].
   * @return the specified schema, or [[ApiV2Complex]] if no schema was specified in the request.
   */
  def getOntologySchema(ctx: RequestContext): IO[BadRequestException, ApiV2Schema] = {
    def stringToSchema(str: String): IO[BadRequestException, ApiV2Schema] =
      ZIO.fromEither(ApiV2Schema.from(str)).mapError(BadRequestException(_))
    def fromQueryParams: Option[IO[BadRequestException, ApiV2Schema]] =
      ctx.request.uri.query().get(schema).map(stringToSchema)
    def fromHeaders: Option[IO[BadRequestException, ApiV2Schema]] =
      ctx.request.headers.find(_.lowercaseName == xKnoraAcceptSchemaHeader).map(h => stringToSchema(h.value))
    fromQueryParams.orElse(fromHeaders).getOrElse(ZIO.succeed(ApiV2Schema.default))
  }

  /**
   * Gets the type of standoff rendering that should be used when returning text with standoff.
   * The name of the standoff rendering can be specified either as HTTP header or query parameter.
   *
   * @param ctx the pekko-http [[RequestContext]].
   * @return the optional rendering that was specified in the request.
   */
  private def getStandoffRendering(ctx: RequestContext): Validation[BadRequestException, Option[MarkupRendering]] = {
    def toMarkupRendering(str: String) = MarkupRendering.from(str).left.map(BadRequestException(_)).map(Some(_))
    def fromQueryParam                 = getStringQueryParam(ctx, QueryParams.markup)
    def fromHeader                     = firstHeaderValue(ctx, xKnoraAcceptMarkup)
    fromQueryParam
      .orElse(fromHeader)
      .map(toMarkupRendering)
      .fold[Validation[BadRequestException, Option[MarkupRendering]]](Validation.succeed(None))(
        Validation.fromEither(_),
      )
  }

  private def firstHeaderValue(ctx: RequestContext, headerName: String): Option[String] =
    ctx.request.headers.find(_.lowercaseName == headerName).map(_.value)

  private def getJsonLDRendering(ctx: RequestContext): Validation[BadRequestException, Option[JsonLdRendering]] =
    firstHeaderValue(ctx, xKnoraJsonLdRendering)
      .map(JsonLdRendering.from(_).left.map(BadRequestException(_)).map(Some(_)))
      .fold[Validation[BadRequestException, Option[JsonLdRendering]]](Validation.succeed(None))(
        Validation.fromEither,
      )

  /**
   * Gets the schema options submitted in the request.
   *
   * @param requestContext the request context.
   * @return the set of schema options submitted in the request, including default options.
   */
  def getSchemaOptions(requestContext: RequestContext): IO[BadRequestException, Set[Rendering]] =
    Validation
      .validateWith(
        getStandoffRendering(requestContext),
        getJsonLDRendering(requestContext),
      )((standoff, jsonLd) => Set(standoff, jsonLd).flatten)
      .toZIO

  /**
   * Gets the project IRI specified in a Knora-specific HTTP header.
   *
   * @param requestContext the pekko-http [[RequestContext]].
   * @return The specified project IRI, or [[None]] if no project header was included in the request.
   *         Fails with a [[BadRequestException]] if the project IRI is invalid.
   */
  def getProjectIri(requestContext: RequestContext): ZIO[IriConverter, BadRequestException, Option[SmartIri]] = {
    val maybeProjectIriStr =
      requestContext.request.headers.find(_.lowercaseName == xKnoraAcceptProject).map(_.value())
    ZIO.foreach(maybeProjectIriStr)(iri =>
      ZIO
        .serviceWithZIO[IriConverter](_.asSmartIri(iri))
        .orElseFail(BadRequestException(s"Invalid project IRI: $iri in request header $xKnoraAcceptSchemaHeader")),
    )
  }

  /**
   * Sends a message to a responder and completes the HTTP request by returning the response as RDF using content negotiation.
   *
   * @param requestZio          A Task containing a [[KnoraRequestV2]] message that should be evaluated.
   * @param requestContext      The pekko-http [[RequestContext]].
   * @param targetSchemaTask    The API schema that should be used in the response, default is [[ApiV2Complex]].
   * @param schemaOptionsOption The schema options that should be used when processing the request.
   *                            Uses RouteUtilV2.getSchemaOptions if not present.
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runRdfRouteZ[R](
    requestZio: ZIO[R, Throwable, KnoraRequestV2],
    requestContext: RequestContext,
    targetSchemaTask: ZIO[R, Throwable, OntologySchema] = ZIO.succeed(ApiV2Complex),
    schemaOptionsOption: ZIO[R, Throwable, Option[Set[Rendering]]] = ZIO.none,
  )(implicit runtime: Runtime[R & MessageRelay & AppConfig]): Future[RouteResult] = {
    val responseZio = requestZio.flatMap(request => ZIO.serviceWithZIO[MessageRelay](_.ask[KnoraResponseV2](request)))
    completeResponse(responseZio, requestContext, targetSchemaTask, schemaOptionsOption)
  }

  /**
   * Completes the HTTP request in the [[RequestContext]] by returning the response as RDF [[ApiV2Complex]].
   * Determines the content type of the representation using content negotiation.
   * The response is calculated by _unsafely_ running the `responseZio` in the provided [[zio.Runtime]]
   *
   * @param responseTask         A [[Task]] containing a [[KnoraResponseV2]] message that will be run unsafe.
   * @param requestContext       The pekko-http [[RequestContext]].
   * @param targetSchemaTask     The API schema that should be used in the response, default is ApiV2Complex.
   * @param schemaOptionsOption  The schema options that should be used when processing the request.
   *                             Uses RouteUtilV2.getSchemaOptions if not present.
   *
   * @param runtime           A [[zio.Runtime]] used for executing the response zio effect.
   *
   * @tparam R                The requirements for the response zio, must be present in the [[zio.Runtime]].
   *
   * @return a [[Future]]     Containing the [[RouteResult]] for Pekko HTTP.
   */
  def completeResponse[R](
    responseTask: ZIO[R, Throwable, KnoraResponseV2],
    requestContext: RequestContext,
    targetSchemaTask: ZIO[R, Throwable, OntologySchema] = ZIO.succeed(ApiV2Complex),
    schemaOptionsOption: ZIO[R, Throwable, Option[Set[Rendering]]] = ZIO.none,
  )(implicit runtime: Runtime[R & AppConfig]): Future[RouteResult] =
    UnsafeZioRun.runToFuture(for {
      targetSchema      <- targetSchemaTask
      schemaOptions     <- schemaOptionsOption.some.orElse(getSchemaOptions(requestContext))
      appConfig         <- ZIO.service[AppConfig]
      knoraResponse     <- responseTask
      responseMediaType <- chooseRdfMediaTypeForResponse(requestContext)
      rdfFormat          = RdfFormat.fromMediaType(RdfMediaTypes.toMostSpecificMediaType(responseMediaType))
      contentType        = RdfMediaTypes.toUTF8ContentType(responseMediaType)
      content            = knoraResponse.format(rdfFormat, targetSchema, schemaOptions, appConfig)
      response           = HttpResponse(StatusCodes.OK, entity = HttpEntity(contentType, content))
      routeResult       <- ZIO.fromFuture(_ => requestContext.complete(response))
    } yield routeResult)

  private def extractMediaTypeFromHeaderItem(
    headerValueItem: String,
    headerValue: String,
  ): Task[Option[MediaRange.One]] = {
    val mediaRangeParts: Array[String] = headerValueItem.split(';').map(_.trim)

    // Get the qValue, if provided; it defaults to 1.
    val qValue: Float = mediaRangeParts.tail.flatMap { param =>
      param.split('=').map(_.trim) match {
        case Array("q", qValueStr) => catching(classOf[NumberFormatException]).opt(qValueStr.toFloat)
        case _                     => None // Ignore other parameters.
      }
    }.headOption
      .getOrElse(1)

    for {
      mediaTypeStr <- ZIO
                        .fromOption(mediaRangeParts.headOption)
                        .orElseFail(
                          BadRequestException(s"Invalid Accept header: $headerValue"),
                        )
      maybeMediaType = RdfMediaTypes.registry.get(mediaTypeStr) match {
                         case Some(mediaType: MediaType) => Some(mediaType)
                         case _                          => None // Ignore non-RDF media types.
                       }
      mediaRange = maybeMediaType.map(mediaType => MediaRange.One(mediaType, qValue))
    } yield mediaRange

  }

  /**
   * Completes the HTTP request in the [[RequestContext]] by _unsafely_ running the ZIO.
   * @param ctx The pekko-http [[RequestContext]].
   * @param task The ZIO to run.
   * @param runtime The [[zio.Runtime]] used for executing the ZIO.
   * @tparam R The requirements for the ZIO, must be present in the [[zio.Runtime]].
   * @return A [[Future]] containing the [[RouteResult]] for Pekko HTTP.
   */
  def complete[R](ctx: RequestContext, task: ZIO[R, Throwable, HttpResponse])(implicit
    runtime: Runtime[R],
  ): Future[RouteResult] = ctx.complete(UnsafeZioRun.runToFuture(task))

  /**
   * Chooses an RDF media type for the response, using content negotiation as per [[https://tools.ietf.org/html/rfc7231#section-5.3.2]].
   *
   * @param requestContext the request context.
   * @return an RDF media type.
   */
  private def chooseRdfMediaTypeForResponse(requestContext: RequestContext): Task[MediaType.NonBinary] = {
    // Get the client's HTTP Accept header, if provided.
    val maybeAcceptHeader: Option[HttpHeader] = requestContext.request.headers.find(_.lowercaseName == "accept")

    maybeAcceptHeader match {
      case Some(acceptHeader) =>
        // Parse the value of the accept header, filtering out non-RDF media types, and sort the results
        // in reverse order by q value.
        val parts: Array[String] = acceptHeader.value.split(',')
        for {
          mediaRanges <-
            ZIO
              .foreach(parts)(headerValueItem => extractMediaTypeFromHeaderItem(headerValueItem, acceptHeader.value))
              .map(_.flatten)
          mediaTypes =
            mediaRanges
              .sortBy(_.qValue)
              .reverse
              .map(_.mediaType)
              .collect { case nonBinary: MediaType.NonBinary => nonBinary }
          highestRankingMediaType = mediaTypes.headOption.getOrElse(RdfMediaTypes.`application/ld+json`)
        } yield highestRankingMediaType

      case None => ZIO.succeed(RdfMediaTypes.`application/ld+json`)
    }
  }
}
