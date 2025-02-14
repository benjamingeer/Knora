/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api
import zio.ZLayer

import org.knora.webapi.slice.admin.api.service.ProjectsLegalInfoRestService
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

final class ProjectsLegalInfoEndpointsHandler(
  endpoints: ProjectsLegalInfoEndpoints,
  restService: ProjectsLegalInfoRestService,
  mapper: HandlerMapper,
) {
  val getProjectLicensesHandler = SecuredEndpointHandler(
    endpoints.getProjectLicenses,
    user => (shortcode, pageAndSize) => restService.findByProjectId(shortcode, pageAndSize, user),
  )

  val postProjectAuthorshipsHandler = SecuredEndpointHandler(
    endpoints.postProjectAuthorships,
    user => (shortcode, req) => restService.addPredefinedAuthorships(shortcode, req, user),
  )

  val getProjectAuthorshipsHandler = SecuredEndpointHandler(
    endpoints.getProjectAuthorships,
    user => (shortcode, pageAndSize) => restService.findAuthorshipsByProject(shortcode, pageAndSize, user),
  )

  val putProjectAuthorshipsHandler = SecuredEndpointHandler(
    endpoints.putProjectAuthorships,
    user => (shortcode, req) => restService.replacePredefinedAuthorship(shortcode, req, user),
  )

  val allHandlers = List(
    getProjectLicensesHandler,
    getProjectAuthorshipsHandler,
    postProjectAuthorshipsHandler,
    putProjectAuthorshipsHandler,
  ).map(
    mapper.mapSecuredEndpointHandler(_),
  )
}

object ProjectsLegalInfoEndpointsHandler {
  val layer = ZLayer.derive[ProjectsLegalInfoEndpointsHandler]
}
