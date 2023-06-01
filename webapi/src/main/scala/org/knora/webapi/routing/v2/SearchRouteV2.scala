/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.Route
import zio._
import zio.metrics._

import java.time.temporal.ChronoUnit

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.v2.responder.KnoraResponseV2
import org.knora.webapi.messages.v2.responder.searchmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.errors.TriplestoreTimeoutException

/**
 * Provides a function for API routes that deal with search.
 */
final case class SearchRouteV2(searchValueMinLength: Int)(
  private implicit val runtime: Runtime[AppConfig with Authenticator with IriConverter with MessageRelay]
) {

  private val LIMIT_TO_PROJECT        = "limitToProject"
  private val LIMIT_TO_RESOURCE_CLASS = "limitToResourceClass"
  private val OFFSET                  = "offset"
  private val LIMIT_TO_STANDOFF_CLASS = "limitToStandoffClass"
  private val RETURN_FILES            = "returnFiles"

  def makeRoute: Route =
    fullTextSearchCount() ~
      fullTextSearch() ~
      gravsearchCountGet() ~
      gravsearchCountPost() ~
      gravsearchGet() ~
      gravsearchPost() ~
      searchByLabelCount() ~
      searchByLabel()

  /**
   * Gets the requested offset. Returns zero if no offset is indicated.
   *
   * @param params the GET parameters.
   * @return the offset to be used for paging.
   */
  private def getOffsetFromParams(params: Map[String, String]): IO[BadRequestException, Int] =
    params
      .get(OFFSET)
      .map { offsetStr =>
        ZIO
          .fromOption(ValuesValidator.validateInt(offsetStr))
          .orElseFail(BadRequestException(s"offset is expected to be an Integer, but $offsetStr given"))
          .filterOrFail(_ >= 0)(
            BadRequestException(s"offset is expected to be a positive Integer, but $offsetStr given")
          )
      }
      .getOrElse(ZIO.succeed(0))

  /**
   * Gets the the project the search should be restricted to, if any.
   *
   * @param params the GET parameters.
   * @return the project Iri, if any.
   */
  private def getProjectFromParams(params: Map[String, String]): IO[BadRequestException, Option[IRI]] =
    params
      .get(LIMIT_TO_PROJECT)
      .map { projectIriStr =>
        Iri
          .validateAndEscapeIri(projectIriStr)
          .toZIO
          .mapBoth(_ => BadRequestException(s"$projectIriStr is not a valid Iri"), Some(_))
      }
      .getOrElse(ZIO.none)

  /**
   * Gets the resource class the search should be restricted to, if any.
   *
   * @param params the GET parameters.
   * @return the internal resource class, if any.
   */
  private def getResourceClassFromParams(
    params: Map[String, String]
  ): ZIO[IriConverter, BadRequestException, Option[SmartIri]] =
    params
      .get(LIMIT_TO_RESOURCE_CLASS)
      .map { resourceClassIriStr =>
        IriConverter
          .asSmartIri(resourceClassIriStr)
          .orElseFail(BadRequestException(s"Invalid resource class IRI: $resourceClassIriStr"))
          .filterOrFail(_.isKnoraApiV2EntityIri)(
            BadRequestException(s"$resourceClassIriStr is not a valid knora-api resource class IRI")
          )
          .map(asSomeIriWithInternalSchema)
      }
      .getOrElse(ZIO.none)

  private def asSomeIriWithInternalSchema(iri: SmartIri): Some[SmartIri] = Some(iri.toOntologySchema(InternalSchema))

  /**
   * Gets the standoff class the search should be restricted to.
   *
   * @param params the GET parameters.
   * @return the internal standoff class, if any.
   */
  private def getStandoffClass(params: Map[String, String]): ZIO[IriConverter, BadRequestException, Option[SmartIri]] =
    params
      .get(LIMIT_TO_STANDOFF_CLASS)
      .map { standoffClassIriStr =>
        IriConverter
          .asSmartIri(standoffClassIriStr)
          .orElseFail(BadRequestException(s"Invalid standoff class IRI: $standoffClassIriStr"))
          .filterOrFail(_.isApiV2ComplexSchema)(
            BadRequestException(s"$standoffClassIriStr is not a valid knora-api standoff class IRI")
          )
          .map(asSomeIriWithInternalSchema)
      }
      .getOrElse(ZIO.none)

  private def fullTextSearchCount(): Route =
    path("v2" / "search" / "count" / Segment) {
      searchStr => // TODO: if a space is encoded as a "+", this is not converted back to a space
        get { requestContext =>
          val params: Map[String, String] = requestContext.request.uri.query().toMap
          val requestTask = for {
            _                    <- ensureIsNotFullTextSearch(searchStr)
            escapedSearchStr     <- validateSearchString(searchStr)
            limitToProject       <- getProjectFromParams(params)
            limitToResourceClass <- getResourceClassFromParams(params)
            limitToStandoffClass <- getStandoffClass(params)
            user                 <- Authenticator.getUserADM(requestContext)
          } yield FullTextSearchCountRequestV2(
            escapedSearchStr,
            limitToProject,
            limitToResourceClass,
            limitToStandoffClass,
            user
          )
          RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
        }
    }

  private def validateSearchString(searchStr: String) =
    ZIO
      .fromOption(Iri.toSparqlEncodedString(searchStr))
      .orElseFail(throw BadRequestException(s"Invalid search string: '$searchStr'"))
      .filterOrElseWith(_.length >= searchValueMinLength) { it =>
        val errorMsg =
          s"A search value is expected to have at least length of $searchValueMinLength, but '$it' given of length ${it.length}."
        ZIO.fail(BadRequestException(errorMsg))
      }

  private def ensureIsNotFullTextSearch(searchStr: String) =
    ZIO
      .fail(
        BadRequestException(
          "It looks like you are submitting a Gravsearch request to a full-text search route"
        )
      )
      .when(searchStr.contains(OntologyConstants.KnoraApi.ApiOntologyHostname))

  private def fullTextSearch(): Route = path("v2" / "search" / Segment) {
    searchStr => // TODO: if a space is encoded as a "+", this is not converted back to a space
      get { requestContext =>
        val targetSchemaTask  = RouteUtilV2.getOntologySchema(requestContext)
        val schemaOptionsTask = RouteUtilV2.getSchemaOptions(requestContext)

        val params: Map[String, String] = requestContext.request.uri.query().toMap
        val requestTask = for {
          _                    <- ensureIsNotFullTextSearch(searchStr)
          escapedSearchStr     <- validateSearchString(searchStr)
          offset               <- getOffsetFromParams(params)
          limitToProject       <- getProjectFromParams(params)
          limitToResourceClass <- getResourceClassFromParams(params)
          limitToStandoffClass <- getStandoffClass(params)
          returnFiles           = ValuesValidator.optionStringToBoolean(params.get(RETURN_FILES), fallback = false)
          requestingUser       <- Authenticator.getUserADM(requestContext)
          targetSchema         <- targetSchemaTask
          schemaOptions        <- schemaOptionsTask
        } yield FulltextSearchRequestV2(
          searchValue = escapedSearchStr,
          offset = offset,
          limitToProject = limitToProject,
          limitToResourceClass = limitToResourceClass,
          limitToStandoffClass = limitToStandoffClass,
          returnFiles = returnFiles,
          requestingUser = requestingUser,
          targetSchema = targetSchema,
          schemaOptions = schemaOptions
        )
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext, targetSchemaTask, schemaOptionsTask.map(Some(_)))
      }
  }

  private def gravsearchCountGet(): Route =
    path("v2" / "searchextended" / "count" / Segment) {
      gravsearchQuery => // Segment is a URL encoded string representing a Gravsearch query
        get { requestContext =>
          val constructQuery = GravsearchParser.parseQuery(gravsearchQuery)
          val requestTask    = Authenticator.getUserADM(requestContext).map(GravsearchCountRequestV2(constructQuery, _))
          RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
        }
    }

  private def gravsearchCountPost(): Route =
    path("v2" / "searchextended" / "count") {
      post {
        entity(as[String]) { gravsearchQuery => requestContext =>
          {
            val constructQuery = GravsearchParser.parseQuery(gravsearchQuery)
            val requestTask    = Authenticator.getUserADM(requestContext).map(GravsearchCountRequestV2(constructQuery, _))
            RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
          }
        }
      }
    }

  private def gravsearchGet(): Route = path(
    "v2" / "searchextended" / Segment
  ) { query => // Segment is a URL encoded string representing a Gravsearch query
    get(requestContext => gravsearch(query, requestContext))
  }

  private def gravsearchPost(): Route = path("v2" / "searchextended") {
    post(entity(as[String])(query => requestContext => gravsearch(query, requestContext)))
  }

  private val gravsearchDuration = Metric.timer("gravsearch", ChronoUnit.MILLIS, Chunk.iterate(1.0, 17)(_ * 2))
  private val gravsearchDurationSummary =
    Metric.summary("gravsearch_summary", 1.day, 100, 0.03d, Chunk(0.01, 0.1, 0.2, 0.5, 0.8, 0.9, 0.99))
  private val gravsearchFailCounter    = Metric.counter("gravsearch_fail").fromConst(1)
  private val gravsearchTimeoutCounter = Metric.counter("gravsearch_timeout").fromConst(1)

  private def gravsearch(query: String, requestContext: RequestContext) = {
    val constructQuery    = GravsearchParser.parseQuery(query)
    val targetSchemaTask  = RouteUtilV2.getOntologySchema(requestContext)
    val schemaOptionsTask = RouteUtilV2.getSchemaOptions(requestContext)
    val task = for {
      start          <- Clock.instant.map(_.toEpochMilli).map(_.toDouble)
      targetSchema   <- targetSchemaTask
      requestingUser <- Authenticator.getUserADM(requestContext)
      schemaOptions  <- schemaOptionsTask
      request         = GravsearchRequestV2(constructQuery, targetSchema, schemaOptions, requestingUser)
      response <- MessageRelay.ask[KnoraResponseV2](request).tapError {
                    case _: TriplestoreTimeoutException => ZIO.unit @@ gravsearchTimeoutCounter
                    case _                              => ZIO.unit @@ gravsearchFailCounter
                  } @@ gravsearchDuration.trackDuration
      _ <- Clock.instant.map(_.toEpochMilli).map(_.-(start)) @@ gravsearchDurationSummary
    } yield response
    RouteUtilV2.completeResponse(task, requestContext, targetSchemaTask, schemaOptionsTask.map(Some(_)))
  }

  private def searchByLabelCount(): Route =
    path("v2" / "searchbylabel" / "count" / Segment) {
      searchval => // TODO: if a space is encoded as a "+", this is not converted back to a space
        get { requestContext =>
          val params: Map[String, String] = requestContext.request.uri.query().toMap
          val requestMessage = for {
            searchString         <- validateSearchString(searchval)
            limitToProject       <- getProjectFromParams(params)
            limitToResourceClass <- getResourceClassFromParams(params)
            user                 <- Authenticator.getUserADM(requestContext)
          } yield SearchResourceByLabelCountRequestV2(searchString, limitToProject, limitToResourceClass, user)
          RouteUtilV2.runRdfRouteZ(requestMessage, requestContext, RouteUtilV2.getOntologySchema(requestContext))
        }
    }

  private def searchByLabel(): Route = path(
    "v2" / "searchbylabel" / Segment
  ) { searchval =>
    get { requestContext =>
      val targetSchemaTask            = RouteUtilV2.getOntologySchema(requestContext)
      val params: Map[String, String] = requestContext.request.uri.query().toMap
      val requestMessage = for {
        sparqlEncodedSearchString <- validateSearchString(searchval)
        offset                    <- getOffsetFromParams(params)
        limitToProject            <- getProjectFromParams(params)
        limitToResourceClass      <- getResourceClassFromParams(params)
        targetSchema              <- targetSchemaTask
        requestingUser            <- Authenticator.getUserADM(requestContext)
      } yield SearchResourceByLabelRequestV2(
        searchValue = sparqlEncodedSearchString,
        offset = offset,
        limitToProject = limitToProject,
        limitToResourceClass = limitToResourceClass,
        targetSchema = targetSchema,
        requestingUser = requestingUser
      )
      RouteUtilV2.runRdfRouteZ(requestMessage, requestContext, targetSchemaTask)
    }
  }
}
