/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.apache.pekko

import scala.util.Try

import org.knora.webapi.routing.RouteUtilV2

import pekko.http.scaladsl.model.headers.ModeledCustomHeader
import pekko.http.scaladsl.model.headers.ModeledCustomHeaderCompanion

/**
 * A custom Pekko HTTP header representing [[RouteUtilV2.PROJECT_HEADER]], which a client can send to specify
 * a project from which results should be returned.
 *
 * The definition follows [[https://doc.pekko.io/docs/pekko-http/current/common/http-model.html#custom-headers]].
 */
class ProjectHeader(token: String) extends ModeledCustomHeader[ProjectHeader] {
  override def renderInRequests              = true
  override def renderInResponses             = true
  override val companion: ProjectHeader.type = ProjectHeader
  override def value: String                 = token
}

object ProjectHeader extends ModeledCustomHeaderCompanion[ProjectHeader] {
  override val name: String         = RouteUtilV2.PROJECT_HEADER
  override def parse(value: String) = Try(new ProjectHeader(value))
}
