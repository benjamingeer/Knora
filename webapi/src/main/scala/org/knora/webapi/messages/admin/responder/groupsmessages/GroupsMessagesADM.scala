/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.admin.responder.groupsmessages

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectsADMJsonProtocol}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.{KnoraRequestADM, KnoraResponseADM}
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, RootJsonFormat}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
 * Represents an API request payload that asks the Knora API server to create a new group.
 *
 * @param id          the optional IRI of the group to be created (unique).
 * @param name        the name of the group to be created (unique).
 * @param description the description of the group to be created.
 * @param project     the project inside which the group will be created.
 * @param status      the status of the group to be created (active = true, inactive = false).
 * @param selfjoin    the status of self-join of the group to be created.
 */
case class CreateGroupApiRequestADM(
  id: Option[IRI] = None,
  name: String,
  description: Option[String],
  project: IRI,
  status: Boolean,
  selfjoin: Boolean
) extends GroupsADMJsonProtocol {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  def toJsValue: JsValue = createGroupApiRequestADMFormat.write(this)

  //check the custom Iri
  stringFormatter.validateOptionalGroupIri(id, throw BadRequestException(s"Invalid group IRI"))
}

/**
 * Represents an API request payload that asks the Knora API server to update
 * an existing group. There are two change cases that are covered with this
 * data structure:
 * (1) change of name, description, and selfjoin
 * (2) change of status
 *
 * @param name        the new group's name.
 * @param description the new group's description.
 * @param status      the new group's status.
 * @param selfjoin    the new group's self-join status.
 */
case class ChangeGroupApiRequestADM(
  name: Option[String] = None,
  description: Option[String] = None,
  status: Option[Boolean] = None,
  selfjoin: Option[Boolean] = None
) extends GroupsADMJsonProtocol {

  private val parametersCount = List(
    name,
    description,
    status,
    selfjoin
  ).flatten.size

  // something needs to be sent, i.e. everything 'None' is not allowed
  if (parametersCount == 0) throw BadRequestException("No data sent in API request.")

  /**
   * check that only allowed information for the 2 cases is sent and not more.
   */
  // change status case
  if (status.isDefined) {
    if (parametersCount > 1) throw BadRequestException("Too many parameters sent for group status change.")
  }

  // change basic group information case
  if (parametersCount > 3) throw BadRequestException("Too many parameters sent for basic group information change.")

  def toJsValue: JsValue = changeGroupApiRequestADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
 * An abstract trait representing a request message that can be sent to 'GroupsResponderADM'.
 */
sealed trait GroupsResponderRequestADM extends KnoraRequestADM

// Requests

/**
 * Get all information about all groups.
 *
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user initiating the request.
 */
case class GroupsGetADM(featureFactoryConfig: FeatureFactoryConfig, requestingUser: UserADM)
    extends GroupsResponderRequestADM

/**
 * Get all information about all groups.
 *
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user initiating the request.
 */
case class GroupsGetRequestADM(featureFactoryConfig: FeatureFactoryConfig, requestingUser: UserADM)
    extends GroupsResponderRequestADM

/**
 * Get everything about a single group identified through its IRI. A successful response will be
 * an [[Option[GroupADM] ]], which will be `None` if the group was not found.
 *
 * @param groupIri             IRI of the group.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user initiating the request.
 */
case class GroupGetADM(groupIri: IRI, featureFactoryConfig: FeatureFactoryConfig, requestingUser: UserADM)
    extends GroupsResponderRequestADM

/**
 * Get everything about a single group identified through its IRI. The response will be a
 * [[GroupGetResponseADM]], or an error if the group was not found.
 *
 * @param groupIri             IRI of the group.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user initiating the request.
 */
case class GroupGetRequestADM(groupIri: IRI, featureFactoryConfig: FeatureFactoryConfig, requestingUser: UserADM)
    extends GroupsResponderRequestADM

/**
 * Get everything about a multiple groups identified by their IRIs. The response will be a
 * [[Set[GroupGetResponseADM] ]], or an error if one or more groups was not found.
 *
 * @param groupIris            the IRIs of the groups being requested.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user initiating the request.
 */
case class MultipleGroupsGetRequestADM(
  groupIris: Set[IRI],
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM
) extends GroupsResponderRequestADM

/**
 * Returns all members of the group identified by iri.
 *
 * @param groupIri             IRI of the group.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user initiating the request.
 */
case class GroupMembersGetRequestADM(groupIri: IRI, featureFactoryConfig: FeatureFactoryConfig, requestingUser: UserADM)
    extends GroupsResponderRequestADM

/**
 * Requests the creation of a new group.
 *
 * @param createRequest        the [[CreateGroupApiRequestADM]] information for creating the new group.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class GroupCreateRequestADM(
  createRequest: CreateGroupApiRequestADM,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends GroupsResponderRequestADM

/**
 * Request updating of an existing group.
 *
 * @param groupIri             the IRI of the group to be updated.
 * @param changeGroupRequest   the data which needs to be update.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class GroupChangeRequestADM(
  groupIri: IRI,
  changeGroupRequest: ChangeGroupApiRequestADM,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends GroupsResponderRequestADM

/**
 * Request changing the status (active/inactive) of an existing group.
 *
 * @param groupIri             the IRI of the group to be deleted.
 * @param changeGroupRequest   the data which needs to be update.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class GroupChangeStatusRequestADM(
  groupIri: IRI,
  changeGroupRequest: ChangeGroupApiRequestADM,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends GroupsResponderRequestADM

/**
 * Request updating the group's permissions.
 *
 * @param requestingUser the user initiating the request.
 * @param apiRequestID   the ID of the API request.
 */
case class GroupPermissionUpdateRequestADM(requestingUser: UserADM, apiRequestID: UUID)
    extends GroupsResponderRequestADM

// Responses
/**
 * Represents the Knora API v1 JSON response to a request for information about all groups.
 *
 * @param groups information about all existing groups.
 */
case class GroupsGetResponseADM(groups: Seq[GroupADM]) extends KnoraResponseADM with GroupsADMJsonProtocol {
  def toJsValue = groupsGetResponseADMFormat.write(this)
}

/**
 * Represents the Knora API v1 JSON response to a request for information about a single group.
 *
 * @param group all information about the group.
 */
case class GroupGetResponseADM(group: GroupADM) extends KnoraResponseADM with GroupsADMJsonProtocol {
  def toJsValue = groupResponseADMFormat.write(this)
}

/**
 * Represents an answer to a group membership request.
 *
 * @param members the group's members.
 */
case class GroupMembersGetResponseADM(members: Seq[UserADM]) extends KnoraResponseADM with GroupsADMJsonProtocol {
  def toJsValue = groupMembersResponseADMFormat.write(this)
}

/**
 * Represents an answer to a group creating/modifying operation.
 *
 * @param group the new group information of the created/modified group.
 */
case class GroupOperationResponseADM(group: GroupADM) extends KnoraResponseADM with GroupsADMJsonProtocol {
  def toJsValue = groupOperationResponseADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
 * The information describing a group.
 *
 * @param id          the IRI if the group.
 * @param name        the name of the group.
 * @param description the description of the group.
 * @param project     the project this group belongs to.
 * @param status      the group's status.
 * @param selfjoin    the group's self-join status.
 */
case class GroupADM(id: IRI, name: String, description: String, project: ProjectADM, status: Boolean, selfjoin: Boolean)
    extends Ordered[GroupADM] {

  /**
   * Allows to sort collections of GroupADM. Sorting is done by the id.
   */
  def compare(that: GroupADM): Int = this.id.compareTo(that.id)

  def asGroupShortADM: GroupShortADM =
    GroupShortADM(
      id = id,
      name = name,
      description = description,
      status = status,
      selfjoin = selfjoin
    )
}

/**
 * The information describing a group (without project).
 *
 * @param id          the IRI if the group.
 * @param name        the name of the group.
 * @param description the description of the group.
 * @param status      the group's status.
 * @param selfjoin    the group's self-join status.
 */
case class GroupShortADM(id: IRI, name: String, description: String, status: Boolean, selfjoin: Boolean)

/**
 * Payload used for updating of an existing group.
 *
 * @param name        the name of the group.
 * @param description the description of the group.
 * @param status      the group's status.
 * @param selfjoin    the group's self-join status.
 */
case class GroupUpdatePayloadADM(
  name: Option[String] = None,
  description: Option[String] = None,
  status: Option[Boolean] = None,
  selfjoin: Option[Boolean] = None
)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
 * A spray-json protocol for generating Knora API v1 JSON providing data about groups.
 */
trait GroupsADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with ProjectsADMJsonProtocol {

  import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._

  implicit val groupADMFormat: JsonFormat[GroupADM] = jsonFormat6(GroupADM)
  implicit val groupShortADMFormat: JsonFormat[GroupShortADM] = jsonFormat5(GroupShortADM)
  implicit val groupsGetResponseADMFormat: RootJsonFormat[GroupsGetResponseADM] =
    jsonFormat(GroupsGetResponseADM, "groups")
  implicit val groupResponseADMFormat: RootJsonFormat[GroupGetResponseADM] = jsonFormat(GroupGetResponseADM, "group")
  implicit val groupMembersResponseADMFormat: RootJsonFormat[GroupMembersGetResponseADM] =
    jsonFormat(GroupMembersGetResponseADM, "members")
  implicit val createGroupApiRequestADMFormat: RootJsonFormat[CreateGroupApiRequestADM] =
    jsonFormat(CreateGroupApiRequestADM, "id", "name", "description", "project", "status", "selfjoin")
  implicit val changeGroupApiRequestADMFormat: RootJsonFormat[ChangeGroupApiRequestADM] =
    jsonFormat(ChangeGroupApiRequestADM, "name", "description", "status", "selfjoin")
  implicit val groupOperationResponseADMFormat: RootJsonFormat[GroupOperationResponseADM] =
    jsonFormat(GroupOperationResponseADM, "group")
}
