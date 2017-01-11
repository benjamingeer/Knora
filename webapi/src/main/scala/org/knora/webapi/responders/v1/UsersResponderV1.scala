/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.responders.v1

import java.util.UUID

import akka.actor.Status
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.permissionmessages._
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, SparqlUpdateRequest, SparqlUpdateResponse}
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{CacheUtil, KnoraIdUtil, SparqlUtil}
import org.mindrot.jbcrypt.BCrypt

import scala.concurrent.Future

/**
  * Provides information about Knora users to other responders.
  */
class UsersResponderV1 extends ResponderV1 {

    // Creates IRIs for new Knora user objects.
    val knoraIdUtil = new KnoraIdUtil

    /**
      * Receives a message extending [[org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1]], and returns a message of type [[UserProfileV1]]
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case UserProfileByIRIGetRequestV1(userIri, profileType) => future2Message(sender(), getUserProfileByIRIV1(userIri, profileType), log)
        case UserProfileByEmailGetRequestV1(username, profileType) => future2Message(sender(), getUserProfileByEmailV1(username, profileType), log)
        case UserCreateRequestV1(createRequest, userProfile, apiRequestID) => future2Message(sender(), createNewUserV1(createRequest, userProfile, apiRequestID), log)
        case UserUpdateRequestV1(userIri, changeUserData, userProfile, apiRequestID) => future2Message(sender(), updateUserDataV1(userIri, changeUserData, userProfile, apiRequestID), log)
        case UserChangePasswordRequestV1(userIri, changePasswordRequest, userProfile, apiRequestID) => future2Message(sender(),)
        case UserChangeStatusRequestV1(userIri, changeUserStatusApiRequestV, userProfile, apiRequestID) => future2Message(sender(),)
        case other => handleUnexpectedMessage(sender(), other, log)
    }

    /**
      * Gets information about a Knora user, and returns it in a [[UserProfileV1]].
      *
      * @param userIRI the IRI of the user.
      * @return a [[UserProfileV1]] describing the user.
      */
    private def getUserProfileByIRIV1(userIRI: IRI, profileType: UserProfileType.Value): Future[UserProfileV1] = {
        //log.debug(s"getUserProfileByIRIV1: userIri = $userIRI', clean = '$clean'")
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getUserByIri(
                triplestore = settings.triplestoreType,
                userIri = userIRI
            ).toString())

            //_ = log.debug(s"getUserProfileByIRIV1 - sparqlQueryString: $sparqlQueryString")

            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

            _ = if (userDataQueryResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"User '$userIRI' not found")
            }

            userProfileV1 <- userDataQueryResponse2UserProfile(userDataQueryResponse, profileType)

        } yield userProfileV1 // UserProfileV1(userDataV1, groupIris, projectIris)
    }

    /**
      * Gets information about a Knora user, and returns it in a [[UserProfileV1]].
      *
      * @param email the username of the user.
      * @return a [[UserProfileV1]] describing the user.
      */
    private def getUserProfileByEmailV1(email: String, profileType: UserProfileType.Value): Future[UserProfileV1] = {
        log.debug(s"getUserProfileByEmailV1: username = '$email', type = '$profileType'")
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getUserByEmail(
                triplestore = settings.triplestoreType,
                email = email
            ).toString())
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

            //_ = log.debug(MessageUtil.toSource(userDataQueryResponse))

            _ = if (userDataQueryResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"User '$email' not found")
            }

            userProfileV1 <- userDataQueryResponse2UserProfile(userDataQueryResponse, profileType)

        } yield userProfileV1 // UserProfileV1(userDataV1, groupIris, projectIris)
    }

    /**
      * Creates a new user. Self-registration is allowed, so even the default user, i.e. with no credentials supplied,
      * is allowed to create the new user.
      *
      * Referenced Websites:
      *                     - https://crackstation.net/hashing-security.htm
      *                     - http://blog.ircmaxell.com/2012/12/seven-ways-to-screw-up-bcrypt.html
      *
      * @param createRequest a [[CreateUserApiRequestV1]] object containing information about the new user to be created.
      * @param userProfile   a [[UserProfileV1]] object containing information about the requesting user.
      * @return a future containing the [[UserOperationResponseV1]].
      */
    private def createNewUserV1(createRequest: CreateUserApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[UserOperationResponseV1] = {
        for {
            a <- Future("")

            // check if email or password are not empty
            _ = if (createRequest.email.isEmpty) throw BadRequestException("Email cannot be empty")
            _ = if (createRequest.password.isEmpty) throw BadRequestException("Password cannot be empty")

            // check if the supplied email for the new user is unique, i.e. not already registered
            sparqlQueryString = queries.sparql.v1.txt.getUserByEmail(
                triplestore = settings.triplestoreType,
                email = createRequest.email
            ).toString()
            _ = log.debug(s"createNewUser - check duplicate email: $sparqlQueryString")
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

            //_ = log.debug(MessageUtil.toSource(userDataQueryResponse))

            _ = if (userDataQueryResponse.results.bindings.nonEmpty) {
                throw DuplicateValueException(s"User with the email: '${createRequest.email}' already exists")
            }

            userIri = knoraIdUtil.makeRandomPersonIri

            hashedPassword = BCrypt.hashpw(createRequest.password, BCrypt.gensalt())

            // Create the new user.
            createNewUserSparqlString = queries.sparql.v1.txt.createNewUser(
                adminNamedGraphIri = "http://www.knora.org/data/admin",
                triplestore = settings.triplestoreType,
                userIri = userIri,
                userClassIri = OntologyConstants.KnoraBase.User,
                email = createRequest.email,
                password = hashedPassword,
                maybeGivenName = createRequest.givenName,
                maybeFamilyName = createRequest.familyName,
                preferredLanguage = createRequest.lang
            ).toString
            //_ = log.debug(s"createNewUser: $createNewUserSparqlString")
            createResourceResponse <- (storeManager ? SparqlUpdateRequest(createNewUserSparqlString)).mapTo[SparqlUpdateResponse]


            // Verify that the user was created.
            sparqlQuery = queries.sparql.v1.txt.getUserByIri(
                triplestore = settings.triplestoreType,
                userIri = userIri
            ).toString()
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            _ = if (userDataQueryResponse.results.bindings.isEmpty) {
                throw UpdateNotPerformedException(s"User $userIri was not created. Please report this as a possible bug.")
            }

            // create the user profile
            newUserProfile <- userDataQueryResponse2UserProfile(userDataQueryResponse, UserProfileType.RESTRICTED)

            // create the user operation response
            userOperationResponseV1 = UserOperationResponseV1(newUserProfile, userProfile.userData)

        } yield userOperationResponseV1

    }

    /**
      * Updates an existing user. Only basic user data information (email, givenName, familyName, lang)
      * can be changed. For changing the password or user status, use the separate methods.
      *
      * @param userIri              the IRI of the existing user that we want to update.
      * @param updateUserRequest the updated information.
      * @param userProfile          the user profile of the requesting user.
      * @param apiRequestID         the unique api request ID.
      * @return a [[UserOperationResponseV1]]
      */
    private def updateBasicUserDataV1(userIri: IRI, updateUserRequest: UpdateUserApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        // check if the requesting user is allowed to perform updates
        if (!userProfile.userData.user_id.contains(userIri) && !userProfile.permissionData.isSystemAdmin) {
            // not the user and not a system admin
            throw ForbiddenException("User information can only be changed by the user itself or a system administrator")
        }

        // check if necessary information is present
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        // check that we don't want to change the password or status
        if (updateUserRequest.password.isDefined) throw BadRequestException("The password cannot be changed by this method.")
        if (updateUserRequest.status.isDefined) throw BadRequestException("The status cannot be changed by this method.")

        // run the user update with an IRI lock
        for {
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => updateUserDataV1(userIri, updateUserRequest, userProfile, apiRequestID)
            )
        } yield taskResult
    }


    private def changePasswordV1(userIri: IRI, changePasswordRequest: ChangeUserPasswordApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        // check if necessary information is present
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        // check if the requesting user is allowed to perform updates
        if (!userProfile.userData.user_id.contains(userIri) && !userProfile.permissionData.isSystemAdmin) {
            // not the user and not a system admin
            throw ForbiddenException("User information can only be changed by the user itself or a system administrator")
        }

        def checkIfOldPasswordMatches(userIri: IRI, oldPassword: String): Future[Boolean] = ???

        for {
            // check if old password matches with an IRI lock
            checkResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => checkIfOldPasswordMatches(userIri, oldPassword = changePasswordRequest.oldPassword)
            )
            _ = if (!checkResult) throw BadRequestException("The supplied old password does not match current users password.")

            updateUserRequest = UpdateUserApiRequestV1(password = Some(changePasswordRequest.newPassword))
            // run the user update with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => updateUserDataV1(userIri, updateUserRequest, userProfile, apiRequestID)
            )
        } yield taskResult
    }

    private def updateBasicUserDataV1(userIri: IRI, updateUserApiRequest: UpdateUserApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        // check if necessary information is present
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        // check if the requesting user is allowed to perform updates
        if (!userProfile.userData.user_id.contains(userIri) && !userProfile.permissionData.isSystemAdmin) {
            // not the user and not a system admin
            throw ForbiddenException("User information can only be changed by the user itself or a system administrator")
        }

        // run the user update with an IRI lock
        for {
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => updateUserDataV1(userIri, updateUserApiRequest, userProfile, apiRequestID)
            )
        } yield taskResult
    }


    // TODO: Refactor method so it doesn't use Any or asInstanceOf (issue #371)
    /**
      *
      *
      * @param userIri          the IRI of the existing user that we want to update.
      * @param apiUpdateRequest the updated information.
      * @param userProfile      the user profile of the requesting user.
      * @param apiRequestID     the unique api request ID.
      * @return a [[UserOperationResponseV1]]
      */
    private def updateUserDataV1(userIri: IRI, apiUpdateRequest: UpdateUserApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        for {
            // get current value.
            sparqlQueryString <- Future(queries.sparql.v1.txt.getUserByIri(
                triplestore = settings.triplestoreType,
                userIri = userIri
            ).toString())
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

            // Update the user
            updateUserSparqlString = queries.sparql.v1.txt.updateUser(
                adminNamedGraphIri = "http://www.knora.org/data/admin",
                triplestore = settings.triplestoreType,
                userIri = userIri,
                maybeEmail = apiUpdateRequest.email,
                maybeGivenName = apiUpdateRequest.givenName,
                maybeFamilyName = apiUpdateRequest.familyName,
                maybeLang = apiUpdateRequest.lang
            ).toString
            //_ = log.debug(s"updateUserV1 - query: $updateUserSparqlString")
            createResourceResponse <- (storeManager ? SparqlUpdateRequest(updateUserSparqlString)).mapTo[SparqlUpdateResponse]

            // Verify that the user was updated.
            sparqlQueryString = queries.sparql.v1.txt.getUserByIri(
                triplestore = settings.triplestoreType,
                userIri = userIri
            ).toString()
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]


            // create the user profile including sensitive information
            updatedUserProfile <- userDataQueryResponse2UserProfile(userDataQueryResponse, UserProfileType.FULL)
            updatedUserData = updatedUserProfile.userData

            // check if what we wanted to update actually got updated
            _ = if (apiUpdateRequest.email.isDefined) {
                if (updatedUserData.email != apiUpdateRequest.email) throw UpdateNotPerformedException("User's 'email' was not updated. Please report this as a possible bug.")
            }

            _ = if (apiUpdateRequest.givenName.isDefined) {
                if (updatedUserData.firstname != apiUpdateRequest.givenName) throw UpdateNotPerformedException("User's 'givenName' was not updated. Please report this as a possible bug.")
            }

            _ = if (apiUpdateRequest.familyName.isDefined) {
                if (updatedUserData.lastname != apiUpdateRequest.familyName) throw UpdateNotPerformedException("User's 'familyName' was not updated. Please report this as a possible bug.")
            }

            _ = if (apiUpdateRequest.lang.isDefined) {
                if (updatedUserData.lang != apiUpdateRequest.lang.get) throw UpdateNotPerformedException("User's 'lang' was not updated. Please report this as a possible bug.")
            }

            // create the user operation response
            userOperationResponseV1 = if (userIri == userProfile.userData.user_id.get) {
                // the user is updating itself

                // update cache if session id is available
                userProfile.sessionId match {
                    case Some(sessionId) => CacheUtil.put[UserProfileV1](Authenticator.AUTHENTICATION_CACHE_NAME, sessionId, updatedUserProfile.setSessionId(sessionId))
                    case None => // user has not session id, so no cache to update
                }

                UserOperationResponseV1(updatedUserProfile.ofType(UserProfileType.RESTRICTED), updatedUserProfile.ofType(UserProfileType.RESTRICTED).userData)
            } else {
                UserOperationResponseV1(updatedUserProfile.ofType(UserProfileType.RESTRICTED), userProfile.userData)
            }
        } yield userOperationResponseV1
    }


    ////////////////////
    // Helper Methods //
    ////////////////////

    /**
      * Helper method used to create a [[UserProfileV1]] from the [[SparqlSelectResponse]] containing user data.
      *
      * @param userDataQueryResponse a [[SparqlSelectResponse]] containing user data.
      * @param userProfileType       a flag denoting if sensitive information should be stripped from the returned [[UserProfileV1]]
      * @return a [[UserProfileV1]] containing the user's data.
      */
    private def userDataQueryResponse2UserProfile(userDataQueryResponse: SparqlSelectResponse, userProfileType: UserProfileType.Value): Future[UserProfileV1] = {

        //log.debug("userDataQueryResponse2UserProfile - " + MessageUtil.toSource(userDataQueryResponse))

        for {
            a <- Future("")

            returnedUserIri = userDataQueryResponse.getFirstRow.rowMap("s")

            groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            //_ = log.debug(s"userDataQueryResponse2UserProfile - groupedUserData: ${MessageUtil.toSource(groupedUserData)}")

            userDataV1 = UserDataV1(
                lang = groupedUserData.get(OntologyConstants.KnoraBase.PreferredLanguage) match {
                    case Some(langList) => langList.head
                    case None => settings.fallbackLanguage
                },
                user_id = Some(returnedUserIri),
                email = groupedUserData.get(OntologyConstants.KnoraBase.Email).map(_.head),
                firstname = groupedUserData.get(OntologyConstants.KnoraBase.GivenName).map(_.head),
                lastname = groupedUserData.get(OntologyConstants.KnoraBase.FamilyName).map(_.head),
                password = groupedUserData.get(OntologyConstants.KnoraBase.Password).map(_.head),
                isActiveUser = groupedUserData.get(OntologyConstants.KnoraBase.IsActiveUser).map(_.head.toBoolean),
                active_project = groupedUserData.get(OntologyConstants.KnoraBase.UsersActiveProject).map(_.head)
            )
            //_ = log.debug(s"userDataQueryResponse2UserProfile - userDataV1: ${MessageUtil.toSource(userDataV1)}")


            /* the projects the user is member of */
            projectIris = groupedUserData.get(OntologyConstants.KnoraBase.IsInProject) match {
                case Some(projects) => projects
                case None => Vector.empty[IRI]
            }
            //_ = log.debug(s"userDataQueryResponse2UserProfile - projectIris: ${MessageUtil.toSource(projectIris)}")

            /* the groups the user is member of (only explicit groups) */
            groupIris = groupedUserData.get(OntologyConstants.KnoraBase.IsInGroup) match {
                case Some(groups) => groups
                case None => Vector.empty[IRI]
            }
            //_ = log.debug(s"userDataQueryResponse2UserProfile - groupIris: ${MessageUtil.toSource(groupIris)}")


            /* the projects for which the user is implicitly considered a member of the 'http://www.knora.org/ontology/knora-base#ProjectAdmin' group */
            isInProjectAdminGroups = groupedUserData.getOrElse(OntologyConstants.KnoraBase.IsInProjectAdminGroup, Vector.empty[IRI])

            /* is the user implicitly considered a member of the 'http://www.knora.org/ontology/knora-base#SystemAdmin' group */
            isInSystemAdminGroup = groupedUserData.get(OntologyConstants.KnoraBase.IsInSystemAdminGroup).exists(p => p.head.toBoolean)

            /* get the user's permission profile from the permissions responder */
            permissionDataFuture = (responderManager ? PermissionDataGetV1(projectIris = projectIris, groupIris = groupIris, isInProjectAdminGroups = isInProjectAdminGroups, isInSystemAdminGroup = isInSystemAdminGroup)).mapTo[PermissionDataV1]
            permissionData <- permissionDataFuture

            /* construct the user profile from the different parts */
            up = UserProfileV1(
                userData = userDataV1,
                groups = groupIris,
                projects = projectIris,
                sessionId = None,
                permissionData = permissionData
            )
            //_ = log.debug(s"Retrieved UserProfileV1: ${up.toString}")

            result = up.ofType(userProfileType)
        } yield result

    }

}
