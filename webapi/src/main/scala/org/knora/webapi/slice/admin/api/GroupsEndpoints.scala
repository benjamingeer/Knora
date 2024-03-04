/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.spray.jsonBody as sprayJsonBody
import sttp.tapir.json.zio.jsonBody as zioJsonBody
import zio.*
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import dsp.valueobjects.Group.GroupDescriptions
import dsp.valueobjects.Group.GroupName
import dsp.valueobjects.Group.GroupSelfJoin
import dsp.valueobjects.Group.GroupStatus
import org.knora.webapi.messages.admin.responder.groupsmessages.*
import org.knora.webapi.messages.admin.responder.usersmessages.GroupMembersGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol.*
import org.knora.webapi.slice.admin.api.AdminPathVariables.groupIriPathVar
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupStatusUpdateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class GroupsEndpoints(baseEndpoints: BaseEndpoints) {

  private val base = "admin" / "groups"

  val getGroups = baseEndpoints.publicEndpoint.get
    .in(base)
    .out(sprayJsonBody[GroupsGetResponseADM])
    .description("Returns all groups.")

  val getGroupByIri = baseEndpoints.publicEndpoint.get
    .in(base / groupIriPathVar)
    .out(sprayJsonBody[GroupGetResponseADM])
    .description("Returns a single group identified by IRI.")

  val getGroupMembers = baseEndpoints.securedEndpoint.get
    .in(base / groupIriPathVar / "members")
    .out(sprayJsonBody[GroupMembersGetResponseADM])
    .description("Returns all members of a single group.")

  val postGroup = baseEndpoints.securedEndpoint.post
    .in(base)
    .in(zioJsonBody[GroupCreateRequest])
    .out(sprayJsonBody[GroupGetResponseADM])
    .description("Creates a new group.")

  val putGroup = baseEndpoints.securedEndpoint.put
    .in(base / groupIriPathVar)
    .in(zioJsonBody[GroupUpdateRequest])
    .out(sprayJsonBody[GroupGetResponseADM])
    .description("Updates a group.")

  val putGroupStatus = baseEndpoints.securedEndpoint.put
    .in(base / groupIriPathVar / "status")
    .in(zioJsonBody[GroupStatusUpdateRequest])
    .out(sprayJsonBody[GroupGetResponseADM])
    .description("Updates a group's status.")

  private val securedEndpoins = Seq(getGroupMembers, postGroup, putGroup).map(_.endpoint)

  val endpoints: Seq[AnyEndpoint] = (Seq(getGroups, getGroupByIri) ++ securedEndpoins)
    .map(_.tag("Admin Groups"))
}

object GroupsRequests {
  import org.knora.webapi.slice.admin.api.Codecs.ZioJsonCodec.*
  final case class GroupCreateRequest(
    id: Option[GroupIri] = None,
    name: GroupName,
    descriptions: GroupDescriptions,
    project: ProjectIri,
    status: GroupStatus,
    selfjoin: GroupSelfJoin
  )
  object GroupCreateRequest {
    implicit val jsonCodec: JsonCodec[GroupCreateRequest] = DeriveJsonCodec.gen[GroupCreateRequest]
  }

  final case class GroupUpdateRequest(
    name: Option[GroupName] = None,
    descriptions: Option[GroupDescriptions] = None,
    status: Option[GroupStatus] = None,
    selfjoin: Option[GroupSelfJoin] = None
  )
  object GroupUpdateRequest {
    implicit val jsonCodec: JsonCodec[GroupUpdateRequest] = DeriveJsonCodec.gen[GroupUpdateRequest]
  }

  final case class GroupStatusUpdateRequest(
    status: GroupStatus
  )
  object GroupStatusUpdateRequest {
    implicit val jsonCodec: JsonCodec[GroupStatusUpdateRequest] = DeriveJsonCodec.gen[GroupStatusUpdateRequest]
  }
}

object GroupsEndpoints {
  val layer = ZLayer.derive[GroupsEndpoints]
}
