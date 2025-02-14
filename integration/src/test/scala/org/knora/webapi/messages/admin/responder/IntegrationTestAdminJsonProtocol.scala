/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder

import spray.json.*

import org.knora.webapi.messages.admin.responder.groupsmessages.GroupGetResponseADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionGroupApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionHasPermissionsApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionPropertyApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionResourceClassApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateAdministrativePermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateDefaultObjectAccessPermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ObjectAccessPermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionDeleteResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionInfoADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionProfileType
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionProfileType.Full
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionProfileType.Restricted
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.GroupMembersGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserGroupMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectAdminMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.api.model.ProjectAdminMembersGetResponseADM
import org.knora.webapi.slice.admin.api.model.ProjectMembersGetResponseADM
import org.knora.webapi.slice.admin.api.model.ProjectOperationResponseADM
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Logo
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Longname
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.SelfJoin
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Status
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.Value.BooleanValue
import org.knora.webapi.slice.common.Value.StringValue

/**
 * A spray-json protocol for generating Knora API JSON providing data about projects.
 *
 * @deprecated This is deprecated because spray-json is deprecated.
 */
object IntegrationTestAdminJsonProtocol extends TriplestoreJsonProtocol {

  implicit object PermissionProfileTypeFormat extends JsonFormat[PermissionProfileType] {

    /**
     * Not implemented.
     */
    def read(jsonVal: JsValue): PermissionProfileType = ???

    /**
     * Converts a [[PermissionProfileType]] into [[JsValue]] for formatting as JSON.
     *
     * @param permissionProfileType the [[PermissionProfileType]] to be converted.
     * @return a [[JsValue]].
     */
    def write(permissionProfileType: PermissionProfileType): JsValue =
      permissionProfileType match {
        case Full =>
          JsObject {
            Map("permission_profile_type" -> "full".toJson)
          }
        case Restricted =>
          JsObject {
            Map("permission_profile_type" -> "restricted".toJson)
          }
      }
  }

  implicit val permissionADMFormat: JsonFormat[PermissionADM] =
    jsonFormat(PermissionADM.apply, "name", "additionalInformation", "permissionCode")

  implicit val permissionInfoADMFormat: JsonFormat[PermissionInfoADM] =
    lazyFormat(jsonFormat(PermissionInfoADM.apply, "iri", "permissionType"))

  implicit val administrativePermissionADMFormat: JsonFormat[AdministrativePermissionADM] =
    lazyFormat(jsonFormat(AdministrativePermissionADM.apply, "iri", "forProject", "forGroup", "hasPermissions"))

  implicit val objectAccessPermissionADMFormat: JsonFormat[ObjectAccessPermissionADM] =
    jsonFormat(ObjectAccessPermissionADM.apply, "forResource", "forValue", "hasPermissions")

  implicit val defaultObjectAccessPermissionADMFormat: JsonFormat[DefaultObjectAccessPermissionADM] =
    lazyFormat(jsonFormat6(DefaultObjectAccessPermissionADM.apply))

  implicit val permissionsDataADMFormat: JsonFormat[PermissionsDataADM] =
    jsonFormat2(PermissionsDataADM.apply)

  implicit val permissionsForProjectGetResponseADMFormat: RootJsonFormat[PermissionsForProjectGetResponseADM] =
    jsonFormat(PermissionsForProjectGetResponseADM.apply, "permissions")

  implicit val administrativePermissionsForProjectGetResponseADMFormat
    : RootJsonFormat[AdministrativePermissionsForProjectGetResponseADM] =
    jsonFormat(AdministrativePermissionsForProjectGetResponseADM.apply, "administrative_permissions")

  implicit val defaultObjectAccessPermissionsForProjectGetResponseADMFormat
    : RootJsonFormat[DefaultObjectAccessPermissionsForProjectGetResponseADM] =
    jsonFormat(DefaultObjectAccessPermissionsForProjectGetResponseADM.apply, "default_object_access_permissions")

  implicit val administrativePermissionGetResponseADMFormat: RootJsonFormat[AdministrativePermissionGetResponseADM] =
    jsonFormat(AdministrativePermissionGetResponseADM.apply, "administrative_permission")

  implicit val defaultObjectAccessPermissionGetResponseADMFormat
    : RootJsonFormat[DefaultObjectAccessPermissionGetResponseADM] =
    jsonFormat(DefaultObjectAccessPermissionGetResponseADM.apply, "")

  implicit val permissionGetResponseADMFormat: RootJsonFormat[PermissionGetResponseADM] =
    new RootJsonFormat[PermissionGetResponseADM] {
      def write(response: PermissionGetResponseADM): JsValue =
        response match {
          case admin: AdministrativePermissionGetResponseADM =>
            administrativePermissionGetResponseADMFormat.write(admin)
          case default: DefaultObjectAccessPermissionGetResponseADM =>
            defaultObjectAccessPermissionGetResponseADMFormat.write(default)
        }
      def read(json: JsValue): PermissionGetResponseADM = throw new UnsupportedOperationException("Not implemented.")
    }

  implicit val createAdministrativePermissionAPIRequestADMFormat
    : RootJsonFormat[CreateAdministrativePermissionAPIRequestADM] = rootFormat(
    lazyFormat(
      jsonFormat(CreateAdministrativePermissionAPIRequestADM.apply, "id", "forProject", "forGroup", "hasPermissions"),
    ),
  )

  implicit val createDefaultObjectAccessPermissionAPIRequestADMFormat
    : RootJsonFormat[CreateDefaultObjectAccessPermissionAPIRequestADM] = rootFormat(
    lazyFormat(
      jsonFormat(
        CreateDefaultObjectAccessPermissionAPIRequestADM.apply,
        "id",
        "forProject",
        "forGroup",
        "forResourceClass",
        "forProperty",
        "hasPermissions",
      ),
    ),
  )

  implicit val administrativePermissionCreateResponseADMFormat
    : RootJsonFormat[AdministrativePermissionCreateResponseADM] = rootFormat(
    lazyFormat(jsonFormat(AdministrativePermissionCreateResponseADM.apply, "administrative_permission")),
  )

  implicit val defaultObjectAccessPermissionCreateResponseADMFormat
    : RootJsonFormat[DefaultObjectAccessPermissionCreateResponseADM] =
    jsonFormat(DefaultObjectAccessPermissionCreateResponseADM.apply, "default_object_access_permission")

  implicit val changePermissionGroupApiRequestADMFormat: RootJsonFormat[ChangePermissionGroupApiRequestADM] =
    jsonFormat(ChangePermissionGroupApiRequestADM.apply, "forGroup")

  implicit val changePermissionHasPermissionsApiRequestADMFormat
    : RootJsonFormat[ChangePermissionHasPermissionsApiRequestADM] =
    jsonFormat(ChangePermissionHasPermissionsApiRequestADM.apply, "hasPermissions")

  implicit val changePermissionResourceClassApiRequestADMFormat
    : RootJsonFormat[ChangePermissionResourceClassApiRequestADM] =
    jsonFormat(ChangePermissionResourceClassApiRequestADM.apply, "forResourceClass")

  implicit val changePermissionPropertyApiRequestADMFormat: RootJsonFormat[ChangePermissionPropertyApiRequestADM] =
    jsonFormat(ChangePermissionPropertyApiRequestADM.apply, "forProperty")

  implicit val permissionDeleteResponseADMFormat: RootJsonFormat[PermissionDeleteResponseADM] =
    jsonFormat(PermissionDeleteResponseADM.apply, "permissionIri", "deleted")

  implicit val projectFormat: JsonFormat[Project] = lazyFormat(
    jsonFormat(
      Project.apply,
      "id",
      "shortname",
      "shortcode",
      "longname",
      "description",
      "keywords",
      "logo",
      "ontologies",
      "status",
      "selfjoin",
    ),
  )

  trait StringValueFormat[T <: StringValue] extends JsonFormat[T] { self =>
    def from: String => Either[String, T]
    override def write(v: T): JsValue = JsString(v.value)
    override def read(json: JsValue): T = json match
      case JsString(str) => self.from(str).fold(err => throw DeserializationException(err), identity)
      case _             => throw DeserializationException("Value must be a JSON string.")
  }

  implicit object CopyrightHolderFormat extends StringValueFormat[CopyrightHolder] {
    override val from: String => Either[String, CopyrightHolder] = CopyrightHolder.from
  }

  implicit object LicenseIriFormat extends StringValueFormat[LicenseIri] {
    override val from: String => Either[String, LicenseIri] = LicenseIri.from
  }

  implicit object ProjectIriFormat extends StringValueFormat[ProjectIri] {
    override val from: String => Either[String, ProjectIri] = ProjectIri.from
  }

  implicit object ShortnameFormat extends StringValueFormat[Shortname] {
    override val from: String => Either[String, Shortname] = Shortname.from
  }

  implicit object ShortcodeFormat extends StringValueFormat[Shortcode] {
    override val from: String => Either[String, Shortcode] = Shortcode.from
  }

  implicit object LongnameFormat extends StringValueFormat[Longname] {
    override val from: String => Either[String, Longname] = Longname.from
  }

  implicit object LogoFormat extends StringValueFormat[Logo] {
    override val from: String => Either[String, Logo] = Logo.from
  }

  trait BooleanValueFormat[T <: BooleanValue] extends JsonFormat[T] { self =>
    def from: Boolean => T
    override def write(v: T): JsValue = JsBoolean(v.value)
    override def read(json: JsValue): T = json match
      case JsBoolean(bool) => self.from(bool)
      case _               => throw DeserializationException("Must be a json Boolean")
  }

  implicit object SelfJoinValueFormat extends BooleanValueFormat[SelfJoin] {
    override val from: Boolean => SelfJoin = SelfJoin.from
  }

  implicit object StatusFormat extends BooleanValueFormat[Status] {
    override val from: Boolean => Status = Status.from
  }

  implicit val groupFormat: JsonFormat[Group] = jsonFormat6(Group.apply)

  implicit val projectAdminMembersGetResponseADMFormat: RootJsonFormat[ProjectAdminMembersGetResponseADM] = rootFormat(
    lazyFormat(jsonFormat(ProjectAdminMembersGetResponseADM.apply, "members")),
  )
  implicit val projectMembersGetResponseADMFormat: RootJsonFormat[ProjectMembersGetResponseADM] = rootFormat(
    lazyFormat(jsonFormat(ProjectMembersGetResponseADM.apply, "members")),
  )
  implicit val projectOperationResponseADMFormat: RootJsonFormat[ProjectOperationResponseADM] = rootFormat(
    lazyFormat(jsonFormat(ProjectOperationResponseADM.apply, "project")),
  )
  implicit val userADMFormat: JsonFormat[User] =
    jsonFormat11(User.apply)
  implicit val groupMembersGetResponseADMFormat: RootJsonFormat[GroupMembersGetResponseADM] =
    jsonFormat(GroupMembersGetResponseADM.apply, "members")
  implicit val usersGetResponseADMFormat: RootJsonFormat[UsersGetResponseADM] =
    jsonFormat1(UsersGetResponseADM.apply)
  implicit val userProfileResponseADMFormat: RootJsonFormat[UserResponseADM] =
    jsonFormat1(UserResponseADM.apply)
  implicit val userProjectMembershipsGetResponseADMFormat: RootJsonFormat[UserProjectMembershipsGetResponseADM] =
    jsonFormat1(UserProjectMembershipsGetResponseADM.apply)
  implicit val userProjectAdminMembershipsGetResponseADMFormat
    : RootJsonFormat[UserProjectAdminMembershipsGetResponseADM] =
    jsonFormat1(UserProjectAdminMembershipsGetResponseADM.apply)
  implicit val userGroupMembershipsGetResponseADMFormat: RootJsonFormat[UserGroupMembershipsGetResponseADM] =
    jsonFormat1(UserGroupMembershipsGetResponseADM.apply)
  implicit val groupsGetResponseADMFormat: RootJsonFormat[GroupsGetResponseADM] =
    jsonFormat(GroupsGetResponseADM.apply, "groups")
  implicit val groupResponseADMFormat: RootJsonFormat[GroupGetResponseADM] =
    jsonFormat(GroupGetResponseADM.apply, "group")
}
