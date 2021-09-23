/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

package org.knora.webapi.responders.admin

import java.util.UUID
import akka.actor.Status.Failure
import akka.testkit.ImplicitSender
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.exceptions.{BadRequestException, DuplicateValueException, ForbiddenException, NotFoundException}
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages.{GroupMembersGetRequestADM, GroupMembersGetResponseADM}
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.{UserChangeRequestADM, _}
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraPasswordCredentialsV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.sharedtestdata.SharedTestDataADM

import scala.concurrent.duration._

object UsersResponderADMSpec {

  val config: Config = ConfigFactory.parseString("""
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
         app.use-redis-cache = true
        """.stripMargin)
}

/**
 * This spec is used to test the messages received by the [[UsersResponderADM]] actor.
 */
class UsersResponderADMSpec extends CoreSpec(UsersResponderADMSpec.config) with ImplicitSender with Authenticator {

  private val timeout: FiniteDuration = 8.seconds

  private val rootUser = SharedTestDataADM.rootUser
  private val anythingAdminUser = SharedTestDataADM.anythingAdminUser
  private val normalUser = SharedTestDataADM.normalUser

  private val incunabulaUser = SharedTestDataADM.incunabulaProjectAdminUser

  private val imagesProject = SharedTestDataADM.imagesProject
  private val imagesReviewerGroup = SharedTestDataADM.imagesReviewerGroup

  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  "The UsersResponder " when {

    "asked about all users" should {
      "return a list if asked by SystemAdmin" in {
        responderManager ! UsersGetRequestADM(
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = rootUser
        )
        val response = expectMsgType[UsersGetResponseADM](timeout)
        response.users.nonEmpty should be(true)
        response.users.size should be(18)
      }

      "return a list if asked by ProjectAdmin" in {
        responderManager ! UsersGetRequestADM(
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = anythingAdminUser
        )
        val response = expectMsgType[UsersGetResponseADM](timeout)
        response.users.nonEmpty should be(true)
        response.users.size should be(18)
      }

      "return 'ForbiddenException' if asked by normal user'" in {
        responderManager ! UsersGetRequestADM(
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = normalUser
        )
        expectMsg(timeout, Failure(ForbiddenException("ProjectAdmin or SystemAdmin permissions are required.")))
      }

      "not return the system and anonymous users" in {
        responderManager ! UsersGetRequestADM(
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = rootUser
        )
        val response = expectMsgType[UsersGetResponseADM](timeout)
        response.users.nonEmpty should be(true)
        response.users.size should be(18)
        response.users.count(_.id == KnoraSystemInstances.Users.AnonymousUser.id) should be(0)
        response.users.count(_.id == KnoraSystemInstances.Users.SystemUser.id) should be(0)
      }
    }

    "asked about an user identified by 'iri' " should {

      "return a profile if the user (root user) is known" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeIri = Some(rootUser.id)),
          userInformationTypeADM = UserInformationTypeADM.FULL,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(rootUser.ofType(UserInformationTypeADM.FULL)))
      }

      "return a profile if the user (incunabula user) is known" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeIri = Some(incunabulaUser.id)),
          userInformationTypeADM = UserInformationTypeADM.FULL,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(incunabulaUser.ofType(UserInformationTypeADM.FULL)))
      }

      "return 'NotFoundException' when the user is unknown" in {
        responderManager ! UserGetRequestADM(
          identifier = UserIdentifierADM(maybeIri = Some("http://rdfh.ch/users/notexisting")),
          userInformationTypeADM = UserInformationTypeADM.FULL,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Failure(NotFoundException(s"User 'http://rdfh.ch/users/notexisting' not found")))
      }

      "return 'None' when the user is unknown" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeIri = Some("http://rdfh.ch/users/notexisting")),
          userInformationTypeADM = UserInformationTypeADM.FULL,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(None)
      }
    }

    "asked about an user identified by 'email'" should {

      "return a profile if the user (root user) is known" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeEmail = Some(rootUser.email)),
          userInformationTypeADM = UserInformationTypeADM.FULL,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(rootUser.ofType(UserInformationTypeADM.FULL)))
      }

      "return a profile if the user (incunabula user) is known" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeEmail = Some(incunabulaUser.email)),
          userInformationTypeADM = UserInformationTypeADM.FULL,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(incunabulaUser.ofType(UserInformationTypeADM.FULL)))
      }

      "return 'NotFoundException' when the user is unknown" in {
        responderManager ! UserGetRequestADM(
          identifier = UserIdentifierADM(maybeEmail = Some("userwrong@example.com")),
          userInformationTypeADM = UserInformationTypeADM.FULL,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Failure(NotFoundException(s"User 'userwrong@example.com' not found")))
      }

      "return 'None' when the user is unknown" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeEmail = Some("userwrong@example.com")),
          userInformationTypeADM = UserInformationTypeADM.FULL,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(None)
      }
    }

    "asked about an user identified by 'username'" should {

      "return a profile if the user (root user) is known" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeUsername = Some(rootUser.username)),
          userInformationTypeADM = UserInformationTypeADM.FULL,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(rootUser.ofType(UserInformationTypeADM.FULL)))
      }

      "return a profile if the user (incunabula user) is known" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeUsername = Some(incunabulaUser.username)),
          userInformationTypeADM = UserInformationTypeADM.FULL,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Some(incunabulaUser.ofType(UserInformationTypeADM.FULL)))
      }

      "return 'NotFoundException' when the user is unknown" in {
        responderManager ! UserGetRequestADM(
          identifier = UserIdentifierADM(maybeUsername = Some("userwrong")),
          userInformationTypeADM = UserInformationTypeADM.FULL,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(Failure(NotFoundException(s"User 'userwrong' not found")))
      }

      "return 'None' when the user is unknown" in {
        responderManager ! UserGetADM(
          identifier = UserIdentifierADM(maybeUsername = Some("userwrong")),
          userInformationTypeADM = UserInformationTypeADM.FULL,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(None)
      }
    }

    "asked to create a new user" should {

      "CREATE the user and return it's profile if the supplied email is unique " in {
        responderManager ! UserCreateRequestADM(
          userCreatePayloadADM = UserCreatePayloadADM.create(
            username = Username.create("donald.duck").fold(error => throw error, value => value),
            email = Email
              .create("donald.duck@example.com")
              .fold(error => throw error, value => value),
            givenName = GivenName.create("Donald").fold(error => throw error, value => value),
            familyName = FamilyName.create("Duck").fold(error => throw error, value => value),
            password = Password.create("test").fold(error => throw error, value => value),
            status = Status.create(true).fold(error => throw error, value => value),
            lang = LanguageCode.create("en").fold(error => throw error, value => value),
            systemAdmin = SystemAdmin.create(false).fold(error => throw error, value => value)
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.anonymousUser,
          apiRequestID = UUID.randomUUID
        )
        val u = expectMsgType[UserOperationResponseADM](timeout).user
        u.username shouldBe "donald.duck"
        u.givenName shouldBe "Donald"
        u.familyName shouldBe "Duck"
        u.email shouldBe "donald.duck@example.com"
        u.lang shouldBe "en"

      }

      "return a 'DuplicateValueException' if the supplied 'username' is not unique" in {
        responderManager ! UserCreateRequestADM(
          userCreatePayloadADM = UserCreatePayloadADM.create(
            username = Username.create("root").fold(error => throw error, value => value),
            email = Email
              .create("root2@example.com")
              .fold(error => throw error, value => value),
            givenName = GivenName.create("Donald").fold(error => throw error, value => value),
            familyName = FamilyName.create("Duck").fold(error => throw error, value => value),
            password = Password.create("test").fold(error => throw error, value => value),
            status = Status.create(true).fold(error => throw error, value => value),
            lang = LanguageCode.create("en").fold(error => throw error, value => value),
            systemAdmin = SystemAdmin.create(false).fold(error => throw error, value => value)
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          SharedTestDataADM.anonymousUser,
          UUID.randomUUID
        )
        expectMsg(Failure(DuplicateValueException(s"User with the username 'root' already exists")))
      }

      "return a 'DuplicateValueException' if the supplied 'email' is not unique" in {
        responderManager ! UserCreateRequestADM(
          userCreatePayloadADM = UserCreatePayloadADM.create(
            username = Username.create("root2").fold(error => throw error, value => value),
            email = Email
              .create("root@example.com")
              .fold(error => throw error, value => value),
            givenName = GivenName.create("Donald").fold(error => throw error, value => value),
            familyName = FamilyName.create("Duck").fold(error => throw error, value => value),
            password = Password.create("test").fold(error => throw error, value => value),
            status = Status.create(true).fold(error => throw error, value => value),
            lang = LanguageCode.create("en").fold(error => throw error, value => value),
            systemAdmin = SystemAdmin.create(false).fold(error => throw error, value => value)
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          SharedTestDataADM.anonymousUser,
          UUID.randomUUID
        )
        expectMsg(Failure(DuplicateValueException(s"User with the email 'root@example.com' already exists")))
      }
    }

    "asked to update a user" should {

      "UPDATE the user's basic information" in {

        /* User information is updated by the user */
        responderManager ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            givenName = Some(GivenName.create("Donald").fold(error => throw error, value => value))
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.normalUser,
          apiRequestID = UUID.randomUUID
        )

        val response1 = expectMsgType[UserOperationResponseADM](timeout)
        response1.user.givenName should equal("Donald")

        /* User information is updated by a system admin */
        responderManager ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            familyName = Some(FamilyName.create("Duck").fold(error => throw error, value => value))
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.superUser,
          apiRequestID = UUID.randomUUID
        )

        val response2 = expectMsgType[UserOperationResponseADM](timeout)
        response2.user.familyName should equal("Duck")

        /* User information is updated by a system admin */
        responderManager ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            givenName =
              Some(GivenName.create(SharedTestDataADM.normalUser.givenName).fold(error => throw error, value => value)),
            familyName = Some(
              FamilyName.create(SharedTestDataADM.normalUser.familyName).fold(error => throw error, value => value)
            )
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.superUser,
          apiRequestID = UUID.randomUUID
        )

        val response3 = expectMsgType[UserOperationResponseADM](timeout)
        response3.user.givenName should equal(SharedTestDataADM.normalUser.givenName)
        response3.user.familyName should equal(SharedTestDataADM.normalUser.familyName)
      }

      "return a 'DuplicateValueException' if the supplied 'username' is not unique" in {
        val duplicateUsername =
          Some(Username.create(SharedTestDataADM.anythingUser1.username).fold(error => throw error, value => value))
        responderManager ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            username = duplicateUsername
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          SharedTestDataADM.superUser,
          UUID.randomUUID
        )
        expectMsg(
          Failure(
            DuplicateValueException(
              s"User with the username '${SharedTestDataADM.anythingUser1.username}' already exists"
            )
          )
        )
      }

      "return a 'DuplicateValueException' if the supplied 'email' is not unique" in {
        val duplicateEmail =
          Some(Email.create(SharedTestDataADM.anythingUser1.email).fold(error => throw error, value => value))
        responderManager ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            email = duplicateEmail
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          SharedTestDataADM.superUser,
          UUID.randomUUID
        )
        expectMsg(
          Failure(
            DuplicateValueException(s"User with the email '${SharedTestDataADM.anythingUser1.email}' already exists")
          )
        )
      }

      "UPDATE the user's password (by himself)" in {
        val requesterPassword = Password.create("test").fold(error => throw error, value => value)
        val newPassword = Password.create("test123456").fold(error => throw error, value => value)
        responderManager ! UserChangePasswordRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdatePasswordPayload = UserUpdatePasswordPayloadADM(
            requesterPassword = requesterPassword,
            newPassword = newPassword
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.normalUser,
          apiRequestID = UUID.randomUUID()
        )

        expectMsgType[UserOperationResponseADM](timeout)

        // need to be able to authenticate credentials with new password
        val resF = Authenticator.authenticateCredentialsV2(
          credentials =
            Some(KnoraPasswordCredentialsV2(UserIdentifierADM(maybeEmail = Some(normalUser.email)), "test123456")),
          featureFactoryConfig = defaultFeatureFactoryConfig
        )(system, responderManager, executionContext)

        resF map { res =>
          assert(res)
        }
      }

      "UPDATE the user's password (by a system admin)" in {
        val requesterPassword = Password.create("test").fold(error => throw error, value => value)
        val newPassword = Password.create("test654321").fold(error => throw error, value => value)

        responderManager ! UserChangePasswordRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          userUpdatePasswordPayload = UserUpdatePasswordPayloadADM(
            requesterPassword = requesterPassword,
            newPassword = newPassword
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.rootUser,
          apiRequestID = UUID.randomUUID()
        )

        expectMsgType[UserOperationResponseADM](timeout)

        // need to be able to authenticate credentials with new password
        val resF = Authenticator.authenticateCredentialsV2(
          credentials =
            Some(KnoraPasswordCredentialsV2(UserIdentifierADM(maybeEmail = Some(normalUser.email)), "test654321")),
          featureFactoryConfig = defaultFeatureFactoryConfig
        )(system, responderManager, executionContext)

        resF map { res =>
          assert(res)
        }
      }

      "UPDATE the user's status, (deleting) making him inactive " in {
        responderManager ! UserChangeStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          status = Status.create(false).fold(error => throw error, value => value),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        val response1 = expectMsgType[UserOperationResponseADM](timeout)
        response1.user.status should equal(false)

        responderManager ! UserChangeStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          status = Status.create(true).fold(error => throw error, value => value),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        val response2 = expectMsgType[UserOperationResponseADM](timeout)
        response2.user.status should equal(true)
      }

      "UPDATE the user's system admin membership" in {
        responderManager ! UserChangeSystemAdminMembershipStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          systemAdmin = SystemAdmin.create(true).fold(error => throw error, value => value),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        val response1 = expectMsgType[UserOperationResponseADM](timeout)
        response1.user.isSystemAdmin should equal(true)

        responderManager ! UserChangeSystemAdminMembershipStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          systemAdmin = SystemAdmin.create(false).fold(error => throw error, value => value),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        val response2 = expectMsgType[UserOperationResponseADM](timeout)
        response2.user.permissions.isSystemAdmin should equal(false)
      }

      "return a 'ForbiddenException' if the user requesting update is not the user itself or system admin" in {

        /* User information is updated by other normal user */
        responderManager ! UserChangeBasicInformationRequestADM(
          userIri = SharedTestDataADM.superUser.id,
          userUpdateBasicInformationPayload = UserUpdateBasicInformationPayloadADM(
            email = None,
            givenName = Some(GivenName.create("Donald").fold(error => throw error, value => value)),
            familyName = None,
            lang = None
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.normalUser,
          UUID.randomUUID
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User information can only be changed by the user itself or a system administrator")
          )
        )

        /* Password is updated by other normal user */
        responderManager ! UserChangePasswordRequestADM(
          userIri = SharedTestDataADM.superUser.id,
          userUpdatePasswordPayload = UserUpdatePasswordPayloadADM(
            requesterPassword = Password.create("test").fold(error => throw error, value => value),
            newPassword = Password.create("test123456").fold(error => throw error, value => value)
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.normalUser,
          UUID.randomUUID
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's password can only be changed by the user itself or a system administrator")
          )
        )

        /* Status is updated by other normal user */
        responderManager ! UserChangeStatusRequestADM(
          userIri = SharedTestDataADM.superUser.id,
          status = Status.create(false).fold(error => throw error, value => value),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.normalUser,
          UUID.randomUUID
        )
        expectMsg(
          timeout,
          Failure(ForbiddenException("User's status can only be changed by the user itself or a system administrator"))
        )

        /* System admin group membership */
        responderManager ! UserChangeSystemAdminMembershipStatusRequestADM(
          userIri = SharedTestDataADM.normalUser.id,
          systemAdmin = SystemAdmin.create(true).fold(error => throw error, value => value),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(ForbiddenException("User's system admin membership can only be changed by a system administrator"))
        )
      }

      "return 'BadRequest' if system user is requested to change" in {

        responderManager ! UserChangeStatusRequestADM(
          userIri = KnoraSystemInstances.Users.SystemUser.id,
          status = Status.create(false).fold(error => throw error, value => value),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        expectMsg(timeout, Failure(BadRequestException("Changes to built-in users are not allowed.")))
      }

      "return 'BadRequest' if anonymous user is requested to change" in {

        responderManager ! UserChangeStatusRequestADM(
          userIri = KnoraSystemInstances.Users.AnonymousUser.id,
          status = Status.create(false).fold(error => throw error, value => value),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.superUser,
          UUID.randomUUID()
        )

        expectMsg(timeout, Failure(BadRequestException("Changes to built-in users are not allowed.")))
      }
    }

    "asked to update the user's project membership" should {

      "ADD user to project" in {

        responderManager ! UserProjectMembershipsGetRequestADM(normalUser.id, defaultFeatureFactoryConfig, rootUser)
        val membershipsBeforeUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.projects should equal(Seq())

        responderManager ! UserProjectMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        val membershipUpdateResponse = expectMsgType[UserOperationResponseADM](timeout)

        responderManager ! UserProjectMembershipsGetRequestADM(normalUser.id, defaultFeatureFactoryConfig, rootUser)
        val membershipsAfterUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.projects should equal(Seq(imagesProject))

        responderManager ! ProjectMembersGetRequestADM(
          ProjectIdentifierADM(maybeIri = Some(imagesProject.id)),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)

        received.members.map(_.id) should contain(normalUser.id)
      }

      "DELETE user from project" in {

        responderManager ! UserProjectMembershipsGetRequestADM(normalUser.id, defaultFeatureFactoryConfig, rootUser)
        val membershipsBeforeUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.projects should equal(Seq(imagesProject))

        responderManager ! UserProjectMembershipRemoveRequestADM(
          normalUser.id,
          imagesProject.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        responderManager ! UserProjectMembershipsGetRequestADM(normalUser.id, defaultFeatureFactoryConfig, rootUser)
        val membershipsAfterUpdate = expectMsgType[UserProjectMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.projects should equal(Seq())

        responderManager ! ProjectMembersGetRequestADM(
          ProjectIdentifierADM(maybeIri = Some(imagesProject.id)),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = rootUser
        )
        val received: ProjectMembersGetResponseADM = expectMsgType[ProjectMembersGetResponseADM](timeout)

        received.members should not contain normalUser.ofType(UserInformationTypeADM.RESTRICTED)
      }

      "return a 'ForbiddenException' if the user requesting update is not the project or system admin" in {

        /* User is added to a project by a normal user */
        responderManager ! UserProjectMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          defaultFeatureFactoryConfig,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's project membership can only be changed by a project or system administrator")
          )
        )

        /* User is removed from a project by a normal user */
        responderManager ! UserProjectMembershipRemoveRequestADM(
          normalUser.id,
          imagesProject.id,
          defaultFeatureFactoryConfig,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's project membership can only be changed by a project or system administrator")
          )
        )
      }

    }

    "asked to update the user's project admin group membership" should {

      "ADD user to project admin group" in {

        responderManager ! UserProjectAdminMembershipsGetRequestADM(
          normalUser.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        val membershipsBeforeUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.projects should equal(Seq())

        responderManager ! UserProjectAdminMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        responderManager ! UserProjectAdminMembershipsGetRequestADM(
          normalUser.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        val membershipsAfterUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.projects should equal(Seq(imagesProject))

        responderManager ! ProjectAdminMembersGetRequestADM(
          ProjectIdentifierADM(maybeIri = Some(imagesProject.id)),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = rootUser
        )
        val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)

        received.members should contain(normalUser.ofType(UserInformationTypeADM.RESTRICTED))
      }

      "DELETE user from project admin group" in {
        responderManager ! UserProjectAdminMembershipsGetRequestADM(
          normalUser.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        val membershipsBeforeUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.projects should equal(Seq(imagesProject))

        responderManager ! UserProjectAdminMembershipRemoveRequestADM(
          normalUser.id,
          imagesProject.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        responderManager ! UserProjectAdminMembershipsGetRequestADM(
          normalUser.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        val membershipsAfterUpdate = expectMsgType[UserProjectAdminMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.projects should equal(Seq())

        responderManager ! ProjectAdminMembersGetRequestADM(
          ProjectIdentifierADM(maybeIri = Some(imagesProject.id)),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = rootUser
        )
        val received: ProjectAdminMembersGetResponseADM = expectMsgType[ProjectAdminMembersGetResponseADM](timeout)

        received.members should not contain normalUser.ofType(UserInformationTypeADM.RESTRICTED)
      }

      "return a 'ForbiddenException' if the user requesting update is not the project or system admin" in {

        /* User is added to a project by a normal user */
        responderManager ! UserProjectAdminMembershipAddRequestADM(
          normalUser.id,
          imagesProject.id,
          defaultFeatureFactoryConfig,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException(
              "User's project admin membership can only be changed by a project or system administrator"
            )
          )
        )

        /* User is removed from a project by a normal user */
        responderManager ! UserProjectAdminMembershipRemoveRequestADM(
          normalUser.id,
          imagesProject.id,
          defaultFeatureFactoryConfig,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException(
              "User's project admin membership can only be changed by a project or system administrator"
            )
          )
        )
      }

    }

    "asked to update the user's group membership" should {

      "ADD user to group" in {
        responderManager ! UserGroupMembershipsGetRequestADM(normalUser.id, defaultFeatureFactoryConfig, rootUser)
        val membershipsBeforeUpdate = expectMsgType[UserGroupMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.groups should equal(Seq())

        responderManager ! UserGroupMembershipAddRequestADM(
          normalUser.id,
          imagesReviewerGroup.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        responderManager ! UserGroupMembershipsGetRequestADM(normalUser.id, defaultFeatureFactoryConfig, rootUser)
        val membershipsAfterUpdate = expectMsgType[UserGroupMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.groups.map(_.id) should equal(Seq(imagesReviewerGroup.id))

        responderManager ! GroupMembersGetRequestADM(
          groupIri = imagesReviewerGroup.id,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = rootUser
        )
        val received: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)

        received.members.map(_.id) should contain(normalUser.id)
      }

      "DELETE user from group" in {
        responderManager ! UserGroupMembershipsGetRequestADM(normalUser.id, defaultFeatureFactoryConfig, rootUser)
        val membershipsBeforeUpdate = expectMsgType[UserGroupMembershipsGetResponseADM](timeout)
        membershipsBeforeUpdate.groups.map(_.id) should equal(Seq(imagesReviewerGroup.id))

        responderManager ! UserGroupMembershipRemoveRequestADM(
          normalUser.id,
          imagesReviewerGroup.id,
          defaultFeatureFactoryConfig,
          rootUser,
          UUID.randomUUID()
        )
        expectMsgType[UserOperationResponseADM](timeout)

        responderManager ! UserGroupMembershipsGetRequestADM(normalUser.id, defaultFeatureFactoryConfig, rootUser)
        val membershipsAfterUpdate = expectMsgType[UserGroupMembershipsGetResponseADM](timeout)
        membershipsAfterUpdate.groups should equal(Seq())

        responderManager ! GroupMembersGetRequestADM(
          groupIri = imagesReviewerGroup.id,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = rootUser
        )
        val received: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)

        received.members.map(_.id) should not contain normalUser.id
      }

      "return a 'ForbiddenException' if the user requesting update is not the project or system admin" in {

        /* User is added to a project by a normal user */
        responderManager ! UserGroupMembershipAddRequestADM(
          normalUser.id,
          imagesReviewerGroup.id,
          defaultFeatureFactoryConfig,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's group membership can only be changed by a project or system administrator")
          )
        )

        /* User is removed from a project by a normal user */
        responderManager ! UserGroupMembershipRemoveRequestADM(
          normalUser.id,
          imagesReviewerGroup.id,
          defaultFeatureFactoryConfig,
          normalUser,
          UUID.randomUUID()
        )
        expectMsg(
          timeout,
          Failure(
            ForbiddenException("User's group membership can only be changed by a project or system administrator")
          )
        )
      }

    }
  }
}
