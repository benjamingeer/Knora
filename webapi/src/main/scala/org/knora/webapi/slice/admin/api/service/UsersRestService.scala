/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.*

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserGroupMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationType
import org.knora.webapi.messages.admin.responder.usersmessages.UserOperationResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectAdminMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.responders.admin.UsersResponder
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.BasicUserInformationChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.PasswordChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.StatusChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.SystemAdminChangeRequest
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.domain.service.ProjectADMService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer

final case class UsersRestService(
  auth: AuthorizationRestService,
  userService: UserService,
  userRepo: KnoraUserRepo,
  projectService: ProjectADMService,
  responder: UsersResponder,
  format: KnoraResponseRenderer
) {

  def getAllUsers(requestingUser: User): Task[UsersGetResponseADM] = for {
    _ <- auth.ensureSystemAdminSystemUserOrProjectAdminInAnyProject(requestingUser)
    internal <- userService.findAll
                  .filterOrFail(_.nonEmpty)(NotFoundException("No users found"))
                  .map(_.sorted)
                  .map(UsersGetResponseADM.apply)
    external <- format.toExternal(internal)
  } yield external

  def deleteUser(requestingUser: User, deleteIri: UserIri): Task[UserOperationResponseADM] = for {
    _        <- ensureNotABuiltInUser(deleteIri)
    _        <- ensureSelfUpdateOrSystemAdmin(deleteIri, requestingUser)
    uuid     <- Random.nextUUID
    internal <- responder.changeUserStatus(deleteIri, UserStatus.Inactive, uuid)
    external <- format.toExternal(internal)
  } yield external

  def getUserByEmail(requestingUser: User, email: Email): Task[UserResponseADM] = for {
    user <- userService
              .findUserByEmail(email)
              .someOrFail(NotFoundException(s"User with email '${email.value}' not found"))
    internal  = UserResponseADM(user.filterUserInformation(requestingUser, UserInformationType.Restricted))
    external <- format.toExternal(internal)
  } yield external

  def getGroupMemberShipsByIri(userIri: UserIri): Task[UserGroupMembershipsGetResponseADM] =
    userService
      .findUserByIri(userIri)
      .map(_.map(_.groups).getOrElse(Seq.empty))
      .map(UserGroupMembershipsGetResponseADM)
      .flatMap(format.toExternal)

  def createUser(requestingUser: User, userCreateRequest: Requests.UserCreateRequest): Task[UserOperationResponseADM] =
    for {
      _        <- auth.ensureSystemAdmin(requestingUser)
      uuid     <- Random.nextUUID
      internal <- responder.createNewUserADM(userCreateRequest, uuid)
      external <- format.toExternal(internal)
    } yield external

  def getProjectMemberShipsByUserIri(userIri: UserIri): Task[UserProjectMembershipsGetResponseADM] =
    for {
      kUser    <- getKnoraUserOrNotFound(userIri)
      projects <- projectService.findByIds(kUser.isInProject)
      external <- format.toExternal(UserProjectMembershipsGetResponseADM(projects))
    } yield external

  private def getKnoraUserOrNotFound(userIri: UserIri) =
    userRepo.findById(userIri).someOrFail(NotFoundException(s"User with iri ${userIri.value} not found."))

  def getProjectAdminMemberShipsByUserIri(userIri: UserIri): Task[UserProjectAdminMembershipsGetResponseADM] =
    for {
      kUser    <- getKnoraUserOrNotFound(userIri)
      projects <- projectService.findByIds(kUser.isInProjectAdminGroup)
      external <- format.toExternal(UserProjectAdminMembershipsGetResponseADM(projects))
    } yield external

  def getUserByUsername(requestingUser: User, username: Username): Task[UserResponseADM] = for {
    user <- userService
              .findUserByUsername(username)
              .someOrFail(NotFoundException(s"User with username '${username.value}' not found"))
    internal  = UserResponseADM(user.filterUserInformation(requestingUser, UserInformationType.Restricted))
    external <- format.toExternal(internal)
  } yield external

  def getUserByIri(requestingUser: User, userIri: UserIri): Task[UserResponseADM] = for {
    internal <- responder
                  .findUserByIri(userIri, UserInformationType.Restricted, requestingUser)
                  .someOrFail(NotFoundException(s"User '${userIri.value}' not found"))
                  .map(UserResponseADM.apply)
    external <- format.toExternal(internal)
  } yield external

  private def ensureSelfUpdateOrSystemAdmin(userIri: UserIri, requestingUser: User) =
    ZIO.when(userIri != requestingUser.userIri)(auth.ensureSystemAdmin(requestingUser))
  private def ensureNotABuiltInUser(userIri: UserIri) =
    ZIO.when(userIri.isBuiltInUser)(ZIO.fail(BadRequestException("Changes to built-in users are not allowed.")))

  def updateUser(
    requestingUser: User,
    userIri: UserIri,
    changeRequest: BasicUserInformationChangeRequest
  ): Task[UserOperationResponseADM] = for {
    _    <- ensureNotABuiltInUser(userIri)
    _    <- ensureSelfUpdateOrSystemAdmin(userIri, requestingUser)
    uuid <- Random.nextUUID
    response <-
      responder.changeBasicUserInformationADM(userIri, changeRequest, uuid).flatMap(format.toExternal)
  } yield response

  def changePassword(
    requestingUser: User,
    userIri: UserIri,
    changeRequest: PasswordChangeRequest
  ): Task[UserOperationResponseADM] =
    for {
      _        <- ensureNotABuiltInUser(userIri)
      _        <- ensureSelfUpdateOrSystemAdmin(userIri, requestingUser)
      uuid     <- Random.nextUUID
      response <- responder.changePassword(userIri, changeRequest, requestingUser, uuid).flatMap(format.toExternal)
    } yield response

  def changeStatus(
    requestingUser: User,
    userIri: UserIri,
    changeRequest: StatusChangeRequest
  ): Task[UserOperationResponseADM] =
    for {
      _        <- ensureNotABuiltInUser(userIri)
      _        <- ensureSelfUpdateOrSystemAdmin(userIri, requestingUser)
      uuid     <- Random.nextUUID
      response <- responder.changeUserStatus(userIri, changeRequest.status, uuid)
    } yield response

  def changeSystemAdmin(
    requestingUser: User,
    userIri: UserIri,
    changeRequest: SystemAdminChangeRequest
  ): Task[UserOperationResponseADM] =
    for {
      _        <- ensureNotABuiltInUser(userIri)
      _        <- auth.ensureSystemAdmin(requestingUser)
      uuid     <- Random.nextUUID
      response <- responder.changeSystemAdmin(userIri, changeRequest.systemAdmin, uuid)
    } yield response

  def addProjectToUserIsInProject(
    requestingUser: User,
    userIri: UserIri,
    projectIri: ProjectIdentifierADM.IriIdentifier
  ): Task[UserOperationResponseADM] =
    for {
      _        <- ensureNotABuiltInUser(userIri)
      _        <- auth.ensureSystemAdminOrProjectAdmin(requestingUser, projectIri.value)
      uuid     <- Random.nextUUID
      response <- responder.addProjectToUserIsInProject(userIri, projectIri.value, uuid)
    } yield response

  def addProjectToUserIsInProjectAdminGroup(
    requestingUser: User,
    userIri: UserIri,
    projectIri: ProjectIdentifierADM.IriIdentifier
  ): Task[UserOperationResponseADM] =
    for {
      _        <- ensureNotABuiltInUser(userIri)
      _        <- auth.ensureSystemAdminOrProjectAdmin(requestingUser, projectIri.value)
      uuid     <- Random.nextUUID
      response <- responder.addProjectToUserIsInProjectAdminGroup(userIri, projectIri.value, uuid)
    } yield response

  def removeProjectToUserIsInProject(
    requestingUser: User,
    userIri: UserIri,
    projectIri: ProjectIdentifierADM.IriIdentifier
  ): Task[UserOperationResponseADM] =
    for {
      _        <- ensureNotABuiltInUser(userIri)
      _        <- auth.ensureSystemAdminOrProjectAdmin(requestingUser, projectIri.value)
      uuid     <- Random.nextUUID
      response <- responder.removeProjectFromUserIsInProjectAndIsInProjectAdminGroup(userIri, projectIri.value, uuid)
    } yield response

  def removeProjectFromUserIsInProjectAdminGroup(
    requestingUser: User,
    userIri: UserIri,
    projectIri: ProjectIdentifierADM.IriIdentifier
  ): Task[UserOperationResponseADM] =
    for {
      _        <- ensureNotABuiltInUser(userIri)
      _        <- auth.ensureSystemAdminOrProjectAdmin(requestingUser, projectIri.value)
      uuid     <- Random.nextUUID
      response <- responder.removeProjectFromUserIsInProjectAdminGroup(userIri, projectIri.value, uuid)
    } yield response

  def addGroupToUserIsInGroup(
    requestingUser: User,
    userIri: UserIri,
    groupIri: GroupIri
  ): Task[UserOperationResponseADM] =
    for {
      _        <- ensureNotABuiltInUser(userIri)
      _        <- auth.ensureSystemAdminOrProjectAdminOfGroup(requestingUser, groupIri)
      uuid     <- Random.nextUUID
      response <- responder.addGroupToUserIsInGroup(userIri, groupIri, uuid)
    } yield response

  def removeGroupFromUserIsInGroup(
    requestingUser: User,
    userIri: UserIri,
    groupIri: GroupIri
  ): Task[UserOperationResponseADM] =
    for {
      _        <- ensureNotABuiltInUser(userIri)
      _        <- auth.ensureSystemAdminOrProjectAdminOfGroup(requestingUser, groupIri)
      uuid     <- Random.nextUUID
      response <- responder.removeGroupFromUserIsInGroup(userIri, groupIri, uuid)
    } yield response
}

object UsersRestService {
  val layer = ZLayer.derive[UsersRestService]
}
