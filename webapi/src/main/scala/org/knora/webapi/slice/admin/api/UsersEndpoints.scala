/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.spray.jsonBody as sprayJsonBody
import zio.*

import org.knora.webapi.messages.admin.responder.usersmessages.UserOperationResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol.*
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.slice.admin.api.PathVars.emailPathVar
import org.knora.webapi.slice.admin.api.PathVars.usernamePathVar
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.common.api.BaseEndpoints

object PathVars {
  import Codecs.TapirCodec.*
  val userIriPathVar: EndpointInput.PathCapture[UserIri] =
    path[UserIri].description("The user IRI. Must be URL-encoded.")

  val emailPathVar: EndpointInput.PathCapture[Email] =
    path[Email].description("The user email. Must be URL-encoded.")

  val usernamePathVar: EndpointInput.PathCapture[Username] =
    path[Username].description("The user name. Must be URL-encoded.")
}
final case class UsersEndpoints(baseEndpoints: BaseEndpoints) {

  private val base = "admin" / "users"

  val getUsers = baseEndpoints.securedEndpoint.get
    .in(base)
    .out(sprayJsonBody[UsersGetResponseADM])
    .description("Returns all users.")

  val getUserByIri = baseEndpoints.withUserEndpoint.get
    .in(base / "iri" / PathVars.userIriPathVar)
    .out(sprayJsonBody[UserResponseADM])
    .description("Returns a user identified by their IRI.")

  val getUserByEmail = baseEndpoints.withUserEndpoint.get
    .in(base / "email" / emailPathVar)
    .out(sprayJsonBody[UserResponseADM])
    .description("Returns a user identified by their Email.")

  val getUserByUsername = baseEndpoints.withUserEndpoint.get
    .in(base / "username" / usernamePathVar)
    .out(sprayJsonBody[UserResponseADM])
    .description("Returns a user identified by their Username.")

  val deleteUser = baseEndpoints.securedEndpoint.delete
    .in(base / "iri" / PathVars.userIriPathVar)
    .out(sprayJsonBody[UserOperationResponseADM])
    .description("Delete a user identified by IRI (change status to false).")

  val endpoints: Seq[AnyEndpoint] =
    Seq(getUsers, getUserByIri, getUserByEmail, getUserByUsername, deleteUser).map(_.endpoint.tag("Admin Users"))
}

object UsersEndpoints {
  val layer = ZLayer.derive[UsersEndpoints]
}
