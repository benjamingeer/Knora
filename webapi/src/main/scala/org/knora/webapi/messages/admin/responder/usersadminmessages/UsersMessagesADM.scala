/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.admin.responder.usersadminmessages

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.KnoraAdminResponse
import org.knora.webapi.messages.admin.responder.groupsadminmessages.{GroupADM, GroupsAdminJsonProtocol}
import org.knora.webapi.messages.admin.responder.projectsadminmessages.{ProjectADM, ProjectInfoADM, ProjectsAdminJsonProtocol}
import org.knora.webapi.messages.admin.responder.usersadminmessages.UserTemplateTypeADM.UserTemplateTypeADM
import org.knora.webapi.messages.v1.responder.groupmessages.GroupV1JsonProtocol
import org.knora.webapi.messages.v1.responder.permissionmessages.{PermissionDataV1, PermissionV1JsonProtocol}
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoV1, ProjectV1JsonProtocol}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileTypeV1.UserProfileType
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserTypeADM.UserTypeADM
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import spray.json._


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to create a new user.
  *
  * @param email       the email of the user to be created (unique).
  * @param givenName   the given name of the user to be created.
  * @param familyName  the family name of the user to be created
  * @param password    the password of the user to be created.
  * @param status      the status of the user to be created (active = true, inactive = false).
  * @param lang        the default language of the user to be created.
  * @param systemAdmin the system admin membership.
  */
case class CreateUserApiRequestADM(email: String,
                                  givenName: String,
                                  familyName: String,
                                  password: String,
                                  status: Boolean,
                                  lang: String,
                                  systemAdmin: Boolean) {

    def toJsValue: JsValue = UserV1JsonProtocol.createUserApiRequestV1Format.write(this)
}

/**
  * Represents an API request payload that asks the Knora API server to update an existing user. Information that can
  * be changed include the user's email, given name, family name, language, password, user status, and system admin
  * membership.
  *
  * @param email       the new email address. Needs to be unique on the server.
  * @param givenName   the new given name.
  * @param familyName  the new family name.
  * @param lang        the new ISO 639-1 code of the new preferred language.
  * @param oldPassword the old password.
  * @param newPassword the new password.
  * @param status      the new user status (active = true, inactive = false).
  * @param systemAdmin the new system admin membership status.
  */
case class ChangeUserApiRequestADM(email: Option[String] = None,
                                  givenName: Option[String] = None,
                                  familyName: Option[String] = None,
                                  lang: Option[String] = None,
                                  oldPassword: Option[String] = None,
                                  newPassword: Option[String] = None,
                                  status: Option[Boolean] = None,
                                  systemAdmin: Option[Boolean] = None) {

    val parametersCount: Int = List(
        email,
        givenName,
        familyName,
        lang,
        oldPassword,
        newPassword,
        status,
        systemAdmin
    ).flatten.size

    // something needs to be sent, i.e. everything 'None' is not allowed
    if (parametersCount == 0) throw BadRequestException("No data sent in API request.")


    /* check that only allowed information for the 4 cases is send and not more. */

    // change password case
    if (oldPassword.isDefined || newPassword.isDefined) {
        if (parametersCount > 2) {
            throw BadRequestException("To many parameters sent for password change.")
        } else if (parametersCount < 2) {
            throw BadRequestException("To few parameters sent for password change.")
        }
    }

    // change status case
    if (status.isDefined) {
        if (parametersCount > 1) throw BadRequestException("To many parameters sent for user status change.")
    }

    // change system admin membership case
    if (systemAdmin.isDefined) {
        if (parametersCount > 1) throw BadRequestException("To many parameters sent for system admin membership change.")
    }

    // change basic user information case
    if (parametersCount > 4) throw BadRequestException("To many parameters sent for basic user information change.")

    def toJsValue: JsValue = UsersAdminJsonProtocol.changeUserApiRequestV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing message that can be sent to `UsersResponderV1`.
  */
sealed trait UsersAdminResponderRequest extends KnoraRequestV1

/**
  * Get all information about all users in form of [[UsersGetResponseV1]]. The UsersGetRequestV1 returns either
  * something or a NotFound exception if there are no users found. Administration permission checking is performed.
  *
  * @param userProfileV1 the profile of the user that is making the request.
  */
case class UsersGetRequestV1(userProfileV1: UserProfileV1) extends UsersResponderRequestV1


/**
  * Get all information about all users in form of a sequence of [[UserADM]]. Returns an empty sequence if
  * no users are found. Administration permission checking is skipped.
  *
  */
case class UsersGetADM() extends UsersResponderRequestV1


/**
  * A message that requests basic user data. A successful response will be a [[UserADM]].
  *
  * @param userIri the IRI of the user to be queried.
  * @param short   denotes if all information should be returned. If short == true, then token and password are not returned.
  */
case class UserByIriGetADM(userIri: IRI, short: Boolean = true) extends UsersResponderRequestV1


/**
  * A message that requests a user's profile. A successful response will be a [[UserProfileResponseV1]].
  *
  * @param userIri         the IRI of the user to be queried.
  * @param userProfileType the extent of the information returned.
  */
case class UserByIRIGetRequestADM(userIri: IRI,
                                        userProfileType: UserProfileType,
                                        userProfile: UserProfileV1) extends UsersResponderRequestV1


/**
  * A message that requests a user's profile. A successful response will be a [[UserProfileV1]].
  *
  * @param userIri         the IRI of the user to be queried.
  * @param userProfileType the extent of the information returned.
  */
case class UserProfileByIRIGetV1(userIri: IRI,
                                 userProfileType: UserProfileType) extends UsersResponderRequestV1

/**
  * A message that requests a user's profile. A successful response will be a [[UserProfileResponseV1]].
  *
  * @param email           the email of the user to be queried.
  * @param userProfileType the extent of the information returned.
  * @param userProfile     the requesting user's profile.
  */
case class UserProfileByEmailGetRequestV1(email: String,
                                          userProfileType: UserProfileType,
                                          userProfile: UserProfileV1) extends UsersResponderRequestV1


/**
  * A message that requests a user's profile. A successful response will be a [[UserProfileV1]].
  *
  * @param email           the email of the user to be queried.
  * @param userProfileType the extent of the information returned.
  */
case class UserProfileByEmailGetV1(email: String,
                                   userProfileType: UserProfileType) extends UsersResponderRequestV1

/**
  * Requests the creation of a new user.
  *
  * @param createRequest the [[CreateUserApiRequestV1]] information used for creating the new user.
  * @param userProfile   the user profile of the user creating the new user.
  * @param apiRequestID  the ID of the API request.
  */
case class UserCreateRequestV1(createRequest: CreateUserApiRequestV1,
                               userProfile: UserProfileV1,
                               apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Request updating of an existing user.
  *
  * @param userIri           the IRI of the user to be updated.
  * @param changeUserRequest the data which needs to be update.
  * @param userProfile       the user profile of the user requesting the update.
  * @param apiRequestID      the ID of the API request.
  */
case class UserChangeBasicUserDataRequestV1(userIri: IRI,
                                            changeUserRequest: ChangeUserApiRequestV1,
                                            userProfile: UserProfileV1,
                                            apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Request updating the users password.
  *
  * @param userIri           the IRI of the user to be updated.
  * @param changeUserRequest the [[ChangeUserApiRequestV1]] object containing the old and new password.
  * @param userProfile       the user profile of the user requesting the update.
  * @param apiRequestID      the ID of the API request.
  */
case class UserChangePasswordRequestV1(userIri: IRI,
                                       changeUserRequest: ChangeUserApiRequestV1,
                                       userProfile: UserProfileV1,
                                       apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Request updating the users status ('knora-base:isActiveUser' property)
  *
  * @param userIri           the IRI of the user to be updated.
  * @param changeUserRequest the [[ChangeUserApiRequestV1]] containing the new status (true / false).
  * @param userProfile       the user profile of the user requesting the update.
  * @param apiRequestID      the ID of the API request.
  */
case class UserChangeStatusRequestV1(userIri: IRI,
                                     changeUserRequest: ChangeUserApiRequestV1,
                                     userProfile: UserProfileV1,
                                     apiRequestID: UUID) extends UsersResponderRequestV1


/**
  * Request updating the users system admin status ('knora-base:isInSystemAdminGroup' property)
  *
  * @param userIri           the IRI of the user to be updated.
  * @param changeUserRequest the [[ChangeUserApiRequestV1]] containing
  *                          the new system admin membership status (true / false).
  * @param userProfile       the user profile of the user requesting the update.
  * @param apiRequestID      the ID of the API request.
  */
case class UserChangeSystemAdminMembershipStatusRequestV1(userIri: IRI,
                                                          changeUserRequest: ChangeUserApiRequestV1,
                                                          userProfile: UserProfileV1,
                                                          apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests user's project memberships.
  *
  * @param userIri       the IRI of the user.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserProjectMembershipsGetRequestV1(userIri: IRI,
                                              userProfileV1: UserProfileV1,
                                              apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests adding the user to a project.
  *
  * @param userIri       the IRI of the user to be updated.
  * @param projectIri    the IRI of the project.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserProjectMembershipAddRequestV1(userIri: IRI,
                                             projectIri: IRI,
                                             userProfileV1: UserProfileV1,
                                             apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests removing the user from a project.
  *
  * @param userIri       the IRI of the user to be updated.
  * @param projectIri    the IRI of the project.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserProjectMembershipRemoveRequestV1(userIri: IRI,
                                                projectIri: IRI,
                                                userProfileV1: UserProfileV1,
                                                apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests user's project admin memberships.
  *
  * @param userIri       the IRI of the user.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserProjectAdminMembershipsGetRequestV1(userIri: IRI,
                                                   userProfileV1: UserProfileV1,
                                                   apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests adding the user to a project as project admin.
  *
  * @param userIri       the IRI of the user to be updated.
  * @param projectIri    the IRI of the project.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserProjectAdminMembershipAddRequestV1(userIri: IRI,
                                                  projectIri: IRI,
                                                  userProfileV1: UserProfileV1,
                                                  apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests removing the user from a project as project admin.
  *
  * @param userIri       the IRI of the user to be updated.
  * @param projectIri    the IRI of the project.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserProjectAdminMembershipRemoveRequestV1(userIri: IRI,
                                                     projectIri: IRI,
                                                     userProfileV1: UserProfileV1,
                                                     apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests user's group memberships.
  *
  * @param userIri       the IRI of the user.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserGroupMembershipsGetRequestV1(userIri: IRI,
                                            userProfileV1: UserProfileV1,
                                            apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests adding the user to a group.
  *
  * @param userIri       the IRI of the user to be updated.
  * @param groupIri      the IRI of the group.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserGroupMembershipAddRequestV1(userIri: IRI,
                                           groupIri: IRI,
                                           userProfileV1: UserProfileV1,
                                           apiRequestID: UUID) extends UsersResponderRequestV1

/**
  * Requests removing the user from a group.
  *
  * @param userIri       the IRI of the user to be updated.
  * @param groupIri      the IRI of the group.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class UserGroupMembershipRemoveRequestV1(userIri: IRI,
                                              groupIri: IRI,
                                              userProfileV1: UserProfileV1,
                                              apiRequestID: UUID) extends UsersResponderRequestV1


// Responses

/**
  * Represents an answer to a request for a list of all users.
  *
  * @param users a sequence of user profiles of the requested type.
  */
case class UsersGetResponseADM(users: Seq[UserADM]) extends KnoraAdminResponse {
    def toJsValue = UserV1JsonProtocol.usersGetResponseV1Format.write(this)
}

/**
  * Represents an answer to a user profile request.
  *
  * @param userProfile the user's profile of the requested type.
  */
case class UserResponseADM(userProfile: UserProfileV1) extends KnoraAdminResponse {
    def toJsValue: JsValue = UserV1JsonProtocol.userProfileResponseV1Format.write(this)
}

/**
  * Represents an answer to a request for a list of all projects the user is member of.
  *
  * @param projects a sequence of projects the user is member of.
  */
case class UserProjectMembershipsGetResponseADM(projects: Seq[ProjectADM]) extends KnoraAdminResponse {
    def toJsValue: JsValue = UserV1JsonProtocol.userProjectMembershipsGetResponseV1Format.write(this)
}

/**
  * Represents an answer to a request for a list of all projects the user is member of the project admin group.
  *
  * @param projects a sequence of projects the user is member of the project admin group.
  */
case class UserProjectAdminMembershipsGetResponseADM(projects: Seq[ProjectADM]) extends KnoraAdminResponse {
    def toJsValue: JsValue = UserV1JsonProtocol.userProjectAdminMembershipsGetResponseV1Format.write(this)
}

/**
  * Represents an answer to a request for a list of all groups the user is member of.
  *
  * @param groups a sequence of groups the user is member of.
  */
case class UserGroupMembershipsGetResponseADM(groups: Seq[GroupADM]) extends KnoraAdminResponse {
    def toJsValue: JsValue = UserV1JsonProtocol.userGroupMembershipsGetResponseV1Format.write(this)
}

/**
  * Represents an answer to a user creating/modifying operation.
  *
  * @param user the new user profile of the created/modified user.
  */
case class UserOperationResponseADM(user: UserADM) extends KnoraResponseV1 {
    def toJsValue: JsValue = UserV1JsonProtocol.userOperationResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents a user's profile.
  *
  * @param id        The user's IRI.
  * @param email     The user's email address.
  * @param password  The user's hashed password.
  * @param token     The API token. Can be used instead of email/password for authentication.
  * @param firstname The user's given name.
  * @param lastname  The user's surname.
  * @param status    The user's status.
  * @param lang      The ISO 639-1 code of the user's preferred language.
  * @param groups         the groups that the user belongs to.
  * @param projects  the projects that the user belongs to.
  * @param sessionId      the sessionId,.
  * @param permissions the user's permissions.
  */
case class UserADM(id: IRI,
                   email: Option[String] = None,
                   password: Option[String] = None,
                   token: Option[String] = None,
                   firstname: Option[String] = None,
                   lastname: Option[String] = None,
                   status: Boolean,
                   lang: String,
                   groups: Seq[GroupADM] = Vector.empty[GroupADM],
                   projects: Seq[ProjectADM] = Seq.empty[ProjectADM],
                   sessionId: Option[String] = None,
                   isSystemUser: Boolean = false,
                   permissions: PermissionDataV1 = PermissionDataV1(anonymousUser = true)) {

    /**
      * Check password using either SHA-1 or SCrypt.
      * The SCrypt password always starts with '$e0801$' (spring.framework implementation)
      *
      * @param password the password to check.
      * @return true if password matches and false if password doesn't match.
      */
    def passwordMatch(password: String): Boolean = {
        password.exists {
            hashedPassword =>
                if (hashedPassword.startsWith("$e0801$")) {
                    //println(s"UserProfileV1 - passwordMatch - password: $password, hashedPassword: hashedPassword")
                    import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder
                    val encoder = new SCryptPasswordEncoder
                    encoder.matches(password, hashedPassword)
                } else {
                    val md = java.security.MessageDigest.getInstance("SHA-1")
                    md.digest(password.getBytes("UTF-8")).map("%02x".format(_)).mkString.equals(hashedPassword)
                }
        }
    }

    /**
      * Creating a [[UserProfileV1]] of the requested type.
      *
      * @return a [[UserProfileV1]]
      */
    def ofType(userTemplateType: UserTemplateTypeADM): UserADM = {

        userTemplateType match {
            case UserTemplateTypeADM.SHORT => {

                UserADM(
                    id = id,
                    token = None, // remove token
                    firstname = firstname,
                    lastname = lastname,
                    email = email,
                    password = None, // remove password
                    status = status,
                    lang = lang,
                    groups = Seq.empty[GroupADM], // removed groups
                    projects = Seq.empty[ProjectADM], // removed projects
                    permissions = PermissionDataV1(anonymousUser = false), // removed permissions
                    sessionId = None // removed sessionId
                )
            }
            case UserTemplateTypeADM.RESTRICTED => {

                UserADM(
                    id = id,
                    token = None, // remove token
                    firstname = firstname,
                    lastname = lastname,
                    email = email,
                    password = None, // remove password
                    status = status,
                    lang = lang,
                    groups = groups,
                    projects = projects,
                    permissions = permissions,
                    sessionId = None // removed sessionId
                )
            }
            case UserTemplateTypeADM.FULL => {
                UserADM(
                    id = id,
                    token = token,
                    firstname = firstname,
                    lastname = lastname,
                    email = email,
                    password = password,
                    status = status,
                    lang = lang,
                    groups = groups,
                    projects = projects,
                    permissions = permissions,
                    sessionId = sessionId
                )
            }
            case _ => throw BadRequestException(s"The requested userTemplateType: $userTemplateType is invalid.")
        }
    }

    def fullname: Option[String] = {
        (firstname, lastname) match {
            case (Some(firstnameStr), Some(lastnameStr)) => Some(firstnameStr + " " + lastnameStr)
            case (Some(firstnameStr), None) => Some(firstnameStr)
            case (None, Some(lastnameStr)) => Some(lastnameStr)
            case (None, None) => None
        }
    }

    def getDigest: String = {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        val time = System.currentTimeMillis().toString
        val value = (time + this.toString).getBytes("UTF-8")
        md.digest(value).map("%02x".format(_)).mkString
    }

    def setSessionId(sessionId: String): UserADM = {
        UserADM(
            id = id,
            token = token,
            firstname = firstname,
            lastname = lastname,
            email = email,
            password = password,
            status = status,
            lang = lang,
            groups = groups,
            projects = projects,
            permissions = permissions,
            sessionId = Some(sessionId)
        )
    }

    def isAnonymousUser: Boolean = {
        permissions.anonymousUser
    }

    def isActive: Boolean = {
        status
    }

    def toJsValue: JsValue = UsersAdminJsonProtocol.userADMFormat.write(this)

}


/**
  * UserADM types:
  *     full: everything
  *     restricted: everything without sensitive information, i.e. token, password, session.
  *     short: like restricted and additionally without groups, projects and permissions.
  *
  * Mainly used in combination with the 'ofType' method, to make sure that a request receiving this information
  * also returns the user profile of the correct type. Should be used in cases where we don't want to expose
  * sensitive information to the outside world. Since in API Admin [[UserADM]] is returned with some responses,
  * we use 'restricted' in those cases.
  */
object UserTemplateTypeADM extends Enumeration {
    /* TODO: Extend to incorporate user privacy wishes */

    type UserTemplateTypeADM = Value

    val SHORT = Value(0, "short") // only userdata
    val RESTRICTED = Value(1, "restricted") // without sensitive information
    val FULL = Value(2, "full") // everything, including sensitive information

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    /**
      * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
      * [[InconsistentTriplestoreDataException]].
      *
      * @param name the name of the value.
      * @return the requested value.
      */
    def lookup(name: String): Value = {
        valueMap.get(name) match {
            case Some(value) => value
            case None => throw InconsistentTriplestoreDataException(s"User profile type not supported: $name")
        }
    }
}


/**
  * Payload used for updating of an existing user.
  *
  * @param email         the new email address. Needs to be unique on the server.
  * @param givenName     the new given name.
  * @param familyName    the new family name.
  * @param password      the new password.
  * @param status        the new status.
  * @param lang          the new language.
  * @param projects      the new project memberships list.
  * @param projectsAdmin the new projects admin membership list.
  * @param groups        the new group memberships list.
  * @param systemAdmin   the new system admin membership
  */
case class UserUpdatePayloadV1(email: Option[String] = None,
                               givenName: Option[String] = None,
                               familyName: Option[String] = None,
                               password: Option[String] = None,
                               status: Option[Boolean] = None,
                               lang: Option[String] = None,
                               projects: Option[Seq[IRI]] = None,
                               projectsAdmin: Option[Seq[IRI]] = None,
                               groups: Option[Seq[IRI]] = None,
                               systemAdmin: Option[Boolean] = None) {

    val parametersCount: Int = List(
        email,
        givenName,
        familyName,
        password,
        status,
        lang,
        projects,
        projectsAdmin,
        groups,
        systemAdmin
    ).flatten.size

    // something needs to be sent, i.e. everything 'None' is not allowed
    if (parametersCount == 0) {
        throw BadRequestException("No data sent in API request.")
    }

    /* check that only allowed information for the 4 cases is send and not more. */

    // change password case
    if (password.isDefined && parametersCount > 1) {
        throw BadRequestException("To many parameters sent for password change.")
    }

    // change status case
    if (status.isDefined && parametersCount > 1) {
        throw BadRequestException("To many parameters sent for user status change.")
    }

    // change system admin membership case
    if (systemAdmin.isDefined && parametersCount > 1) {
        throw BadRequestException("To many parameters sent for system admin membership change.")
    }

    // change project memberships
    if (projects.isDefined && parametersCount > 1) {
        throw BadRequestException("To many parameters sent for project membership change.")
    }

    // change projectAdmin memberships
    if (projectsAdmin.isDefined && parametersCount > 1) {
        throw BadRequestException("To many parameters sent for projectAdmin membership change.")
    }

    // change group memberships
    if (groups.isDefined && parametersCount > 1) {
        throw BadRequestException("To many parameters sent for group membership change.")
    }

    // change basic user information case
    if (parametersCount > 4) {
        throw BadRequestException("To many parameters sent for basic user information change.")
    }

}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for formatting objects as JSON.
  */
object UsersAdminJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with ProjectsAdminJsonProtocol with GroupsAdminJsonProtocol with PermissionV1JsonProtocol {

    implicit val userADMFormat: JsonFormat[UserADM] = jsonFormat13(UserADM)
    implicit val createUserApiRequestV1Format: RootJsonFormat[CreateUserApiRequestADM] = jsonFormat7(CreateUserApiRequestADM)
    implicit val changeUserApiRequestV1Format: RootJsonFormat[ChangeUserApiRequestV1] = jsonFormat(ChangeUserApiRequestV1, "email", "givenName", "familyName", "lang", "oldPassword", "newPassword", "status", "systemAdmin")
    implicit val usersGetResponseV1Format: RootJsonFormat[UsersGetResponseADM] = jsonFormat1(UsersGetResponseADM)
    implicit val userProfileResponseV1Format: RootJsonFormat[UserProfileResponseV1] = jsonFormat1(UserProfileResponseV1)
    implicit val userProjectMembershipsGetResponseV1Format: RootJsonFormat[UserProjectMembershipsGetResponseV1] = jsonFormat1(UserProjectMembershipsGetResponseV1)
    implicit val userProjectAdminMembershipsGetResponseV1Format: RootJsonFormat[UserProjectAdminMembershipsGetResponseV1] = jsonFormat1(UserProjectAdminMembershipsGetResponseV1)
    implicit val userGroupMembershipsGetResponseV1Format: RootJsonFormat[UserGroupMembershipsGetResponseV1] = jsonFormat1(UserGroupMembershipsGetResponseV1)
    implicit val userOperationResponseV1Format: RootJsonFormat[UserOperationResponseV1] = jsonFormat1(UserOperationResponseV1)
}