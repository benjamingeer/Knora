/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.messages.admin.responder.groupsmessages.GroupGetResponseADM
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupStatusUpdateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.admin.api.service.GroupRestService
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

case class GroupsEndpointsHandler(
  endpoints: GroupsEndpoints,
  restService: GroupRestService,
  mapper: HandlerMapper,
) {
  private val getGroupsHandler =
    PublicEndpointHandler(
      endpoints.getGroups,
      (_: Unit) => restService.getGroups,
    )

  private val getGroupByIriHandler =
    PublicEndpointHandler(
      endpoints.getGroupByIri,
      (iri: GroupIri) => restService.getGroupByIri(iri),
    )

  private val getGroupMembersHandler =
    SecuredEndpointHandler(
      endpoints.getGroupMembers,
      user => iri => restService.getGroupMembers(iri, user),
    )

  private val postGroupHandler =
    SecuredEndpointHandler(
      endpoints.postGroup,
      user => request => restService.postGroup(request, user),
    )

  private val putGroupHandler =
    SecuredEndpointHandler[(GroupIri, GroupUpdateRequest), GroupGetResponseADM](
      endpoints.putGroup,
      user => { case (iri, request) =>
        restService.putGroup(iri, request, user)
      },
    )

  private val putGroupStatusHandler =
    SecuredEndpointHandler[(GroupIri, GroupStatusUpdateRequest), GroupGetResponseADM](
      endpoints.putGroupStatus,
      user => { case (iri, request) =>
        restService.putGroupStatus(iri, request, user)
      },
    )

  private val deleteGroupHandler =
    SecuredEndpointHandler(
      endpoints.deleteGroup,
      user => iri => restService.deleteGroup(iri, user),
    )

  private val securedHandlers =
    List(getGroupMembersHandler, postGroupHandler, putGroupHandler, putGroupStatusHandler, deleteGroupHandler)
      .map(mapper.mapSecuredEndpointHandler(_))

  val allHandlers = List(getGroupsHandler, getGroupByIriHandler).map(mapper.mapPublicEndpointHandler(_))
    ++ securedHandlers
}

object GroupsEndpointsHandler {
  val layer = ZLayer.derive[GroupsEndpointsHandler]
}
