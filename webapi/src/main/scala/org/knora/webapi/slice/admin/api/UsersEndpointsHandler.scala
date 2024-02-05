/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.messages.admin.responder.usersmessages.UserOperationResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.slice.admin.api.service.UsersRestService
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

case class UsersEndpointsHandler(
  usersEndpoints: UsersEndpoints,
  restService: UsersRestService,
  mapper: HandlerMapper
) {

  private val getUsersHandler = SecuredEndpointHandler[Unit, UsersGetResponseADM](
    usersEndpoints.getUsers,
    requestingUser => _ => restService.listAllUsers(requestingUser)
  )

  private val getUserByIriHandler = SecuredEndpointHandler[UserIri, UserResponseADM](
    usersEndpoints.getUserByIri,
    requestingUser => userIri => restService.getUserByIri(requestingUser, userIri)
  )

  private val getUserByEmailHandler = SecuredEndpointHandler[Email, UserResponseADM](
    usersEndpoints.getUserByEmail,
    requestingUser => email => restService.getUserByEmail(requestingUser, email)
  )

  private val getUserByUsernameHandler = SecuredEndpointHandler[Username, UserResponseADM](
    usersEndpoints.getUserByUsername,
    requestingUser => username => restService.getUserByUsername(requestingUser, username)
  )

  private val deleteUserByIriHandler = SecuredEndpointHandler[UserIri, UserOperationResponseADM](
    usersEndpoints.deleteUser,
    requestingUser => userIri => restService.deleteUser(requestingUser, userIri)
  )

  val allHanders =
    List(getUsersHandler, getUserByIriHandler, getUserByEmailHandler, getUserByUsernameHandler, deleteUserByIriHandler)
      .map(mapper.mapSecuredEndpointHandler(_))
}

object UsersEndpointsHandler {
  val layer = ZLayer.derive[UsersEndpointsHandler]
}
