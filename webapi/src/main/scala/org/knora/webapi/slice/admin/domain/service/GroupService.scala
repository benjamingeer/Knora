/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.ZIO
import zio._
import org.knora.webapi.slice.admin.api.GroupsRequests.{GroupCreateRequest, GroupUpdateRequest}
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.{
  Group,
  GroupDescriptions,
  GroupIri,
  GroupName,
  GroupSelfJoin,
  GroupStatus,
  KnoraGroup,
  KnoraProject,
}

final case class GroupService(
  private val knoraGroupService: KnoraGroupService,
  private val projectService: ProjectService,
) {

  def findAllRegularGroups: Task[Chunk[Group]] = knoraGroupService.findAllRegularGroups().flatMap(toGroups)

  def findById(id: GroupIri): Task[Option[Group]] = knoraGroupService.findById(id).flatMap(ZIO.foreach(_)(toGroup))

  def findByIds(ids: Seq[GroupIri]): Task[Chunk[Group]] = knoraGroupService.findByIds(ids).flatMap(toGroups)

  private def toGroups(knoraGroups: Chunk[KnoraGroup]): Task[Chunk[Group]] = ZIO.foreach(knoraGroups)(toGroup)

  private def toGroup(knoraGroup: KnoraGroup): Task[Group] =
    for {
      project <- knoraGroup.belongsToProject.map(projectService.findById).getOrElse(ZIO.none)
    } yield Group(
      id = knoraGroup.id.value,
      name = knoraGroup.groupName.value,
      descriptions = knoraGroup.groupDescriptions.value,
      project = project,
      status = knoraGroup.status.value,
      selfjoin = knoraGroup.hasSelfJoinEnabled.value,
    )

  private def toKnoraGroup(group: Group): KnoraGroup =
    KnoraGroup(
      id = GroupIri.unsafeFrom(group.id),
      groupName = GroupName.unsafeFrom(group.name),
      groupDescriptions = GroupDescriptions.unsafeFrom(group.descriptions),
      status = GroupStatus.from(group.status),
      belongsToProject = group.project.map(it => ProjectIri.unsafeFrom(it.id)),
      hasSelfJoinEnabled = GroupSelfJoin.from(group.selfjoin),
    )

  def createGroup(request: GroupCreateRequest, project: KnoraProject): Task[Group] =
    knoraGroupService.createGroup(request, project).flatMap(toGroup)

  def updateGroup(groupToUpdate: Group, request: GroupUpdateRequest): Task[Group] =
    knoraGroupService.updateGroup(toKnoraGroup(groupToUpdate), request).flatMap(toGroup)
}

object GroupService {
  val layer = ZLayer.derive[GroupService]
}
