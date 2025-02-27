/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.tapir.*
import sttp.tapir.model.UsernamePassword
import sttp.tapir.server.PartialServerEndpoint
import zio.ZLayer

import scala.concurrent.Future

import dsp.errors.RequestRejectedException
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.ApiV2
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.resources.api.model.IriDto
import org.knora.webapi.slice.resources.api.model.VersionDate

final case class ResourcesEndpoints(private val baseEndpoints: BaseEndpoints) {

  private val base = "v2" / "resources"

  private val versionQuery = query[Option[VersionDate]]("version")
    .and(query[Option[VersionDate]]("version date"))
    .map {
      case (Some(v), _) => Some(v)
      case (_, Some(v)) => Some(v)
      case _            => None
    }(d => (d, d))

  private val startDateQuery = query[Option[VersionDate]]("startDate")
    .and(query[Option[VersionDate]]("start date"))
    .map {
      case (Some(v), _) => Some(v)
      case (_, Some(v)) => Some(v)
      case _            => None
    }(d => (d, d))

  private val endDateQuery = query[Option[VersionDate]]("endDate")
    .and(query[Option[VersionDate]]("end date"))
    .map {
      case (Some(v), _) => Some(v)
      case (_, Some(v)) => Some(v)
      case _            => None
    }(d => (d, d))

  val getResourcesHistoryEvents = baseEndpoints.withUserEndpoint.get
    .in(base / "resourceHistoryEvents" / path[IriDto].name("resourceIri"))
    .in(ApiV2.Inputs.formatOptions)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val getResourcesHistory = baseEndpoints.withUserEndpoint.get
    .in(base / "history" / path[IriDto].name("resourceIri"))
    .in(ApiV2.Inputs.formatOptions)
    .in(startDateQuery)
    .in(endDateQuery)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val getResources = baseEndpoints.withUserEndpoint.get
    .in(base / paths)
    .in(ApiV2.Inputs.formatOptions)
    .in(versionQuery)
    .out(stringBody)
    .out(header[MediaType](HeaderNames.ContentType))

  val endpoints: Seq[AnyEndpoint] = Seq(
    getResourcesHistoryEvents,
    getResourcesHistory,
    getResources,
  ).map(_.endpoint.tag("V2 Resources"))
}

object ResourcesEndpoints {
  val layer = ZLayer.derive[ResourcesEndpoints]
}
