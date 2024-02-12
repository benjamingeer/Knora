/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.responders.admin.PermissionsResponderADM
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username

case class UserService(
  private val userRepo: KnoraUserRepo,
  private val projectsService: ProjectADMService,
  private val groupsService: GroupsResponderADM,
  private val permissionService: PermissionsResponderADM
) {

  def findUserByIri(iri: UserIri): Task[Option[User]] =
    userRepo.findById(iri).flatMap(ZIO.foreach(_)(toUser))
  def findUserByEmail(email: Email): Task[Option[User]] =
    userRepo.findByEmail(email).flatMap(ZIO.foreach(_)(toUser))
  def findUserByUsername(username: Username): Task[Option[User]] =
    userRepo.findByUsername(username).flatMap(ZIO.foreach(_)(toUser))
  def findAll: Task[Seq[User]] =
    userRepo.findAll().flatMap(ZIO.foreach(_)(toUser))

  private def toUser(kUser: KnoraUser): Task[User] = for {
    projects <- ZIO.foreach(kUser.isInProject)(projectsService.findById).map(_.flatten)
    groups   <- ZIO.foreach(kUser.isInGroup.map(_.value))(groupsService.groupGetADM).map(_.flatten)
    permissionData <-
      permissionService.permissionsDataGetADM(
        kUser.isInProject.map(_.value),
        kUser.isInGroup.map(_.value),
        kUser.isInProjectAdminGroup.map(_.value),
        kUser.isInSystemAdminGroup.value
      )
  } yield User(
    kUser.id.value,
    kUser.username.value,
    kUser.email.value,
    kUser.givenName.value,
    kUser.familyName.value,
    kUser.status.value,
    kUser.preferredLanguage.value,
    Some(kUser.password.value),
    groups,
    projects,
    permissionData
  )
}

object UserService {
  val layer = ZLayer.derive[UserService]
}
