/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio._

import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.NotFoundException
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.GroupDescriptions
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupName
import org.knora.webapi.slice.admin.domain.model.GroupSelfJoin
import org.knora.webapi.slice.admin.domain.model.GroupStatus
import org.knora.webapi.slice.admin.domain.model.KnoraGroup
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

final case class GroupService(
  private val knoraGroupService: KnoraGroupService,
  private val projectService: ProjectService,
) {
  def findAll: Task[List[Group]] = knoraGroupService.findAll.flatMap(ZIO.foreachPar(_)(toGroup))

  def findById(id: GroupIri): Task[Option[Group]] = knoraGroupService.findById(id).flatMap(ZIO.foreach(_)(toGroup))

  private def toGroup(knoraGroup: KnoraGroup): Task[Group] =
    for {
      projectIri <- ZIO.fromOption(knoraGroup.belongsToProject).orElseFail(NotFoundException("Project IRI not found."))
      project <-
        projectService
          .findById(ProjectIri.unsafeFrom(projectIri.value))
          .someOrFail(
            InconsistentRepositoryDataException(
              s"Project ${projectIri.value} was referenced by ${knoraGroup.id.value} but was not found in the triplestore.",
            ),
          )
      group <-
        ZIO.attempt(
          Group(
            id = knoraGroup.id.value,
            name = knoraGroup.groupName.value,
            descriptions = knoraGroup.groupDescriptions.value,
            project = project,
            status = knoraGroup.status.value,
            selfjoin = knoraGroup.hasSelfJoinEnabled.value,
          ),
        )
    } yield group

  def toKnoraGroup(group: Group): KnoraGroup =
    KnoraGroup(
      id = GroupIri.unsafeFrom(group.id),
      groupName = GroupName.unsafeFrom(group.name),
      groupDescriptions = GroupDescriptions.unsafeFrom(group.descriptions),
      status = GroupStatus.from(group.status),
      belongsToProject = Some(ProjectIri.unsafeFrom(group.project.id)),
      hasSelfJoinEnabled = GroupSelfJoin.from(group.selfjoin),
    )
}

object GroupService {
  val layer = ZLayer.derive[GroupService]
}
