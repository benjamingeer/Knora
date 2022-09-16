/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import zio.prelude.Validation

import java.util.UUID
import scala.concurrent.Future

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri.UserIri
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.User._
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM

/**
 * Provides an akka-http-routing function for API routes that deal with users.
 */
class UsersRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

  val usersBasePath: PathMatcher[Unit] = PathMatcher("admin" / "users")

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    getUsers() ~
      addUser() ~
      getUserByIri() ~
      getUserByEmail() ~
      getUserByUsername() ~
      changeUserBasicInformation() ~
      changeUserPassword() ~
      changeUserStatus() ~
      deleteUser() ~
      changeUserSystemAdminMembership() ~
      getUsersProjectMemberships() ~
      addUserToProjectMembership() ~
      removeUserFromProjectMembership() ~
      getUsersProjectAdminMemberships() ~
      addUserToProjectAdminMembership() ~
      removeUserFromProjectAdminMembership() ~
      getUsersGroupMemberships() ~
      addUserToGroupMembership() ~
      removeUserFromGroupMembership()

  /* return all users */
  def getUsers(): Route = path(usersBasePath) {
    get { requestContext =>
      val requestMessage: Future[UsersGetRequestADM] = for {
        requestingUser <- getUserADM(
                            requestContext = requestContext,
                            routeData.appConfig
                          )
      } yield UsersGetRequestADM(
        requestingUser = requestingUser
      )

      RouteUtilADM.runJsonRoute(
        requestMessageF = requestMessage,
        requestContext = requestContext,
        appActor = appActor,
        log = log
      )
    }
  }

  /* create a new user */
  def addUser(): Route = path(usersBasePath) {
    post {
      entity(as[CreateUserApiRequestADM]) { apiRequest => requestContext =>
        // get all values from request and make value objects from it
        val iri: Validation[Throwable, Option[UserIri]] = apiRequest.id match {
          case Some(iri) => UserIri.make(iri).map(Some(_))
          case None      => Validation.succeed(None)
        }
        val username: Validation[Throwable, Username]         = Username.make(apiRequest.username)
        val email: Validation[Throwable, Email]               = Email.make(apiRequest.email)
        val givenName: Validation[Throwable, GivenName]       = GivenName.make(apiRequest.givenName)
        val familyName: Validation[Throwable, FamilyName]     = FamilyName.make(apiRequest.familyName)
        val password: Validation[Throwable, Password]         = Password.make(apiRequest.password)
        val status: Validation[Throwable, UserStatus]         = UserStatus.make(apiRequest.status)
        val languageCode: Validation[Throwable, LanguageCode] = LanguageCode.make(apiRequest.lang)
        val systemAdmin: Validation[Throwable, SystemAdmin]   = SystemAdmin.make(apiRequest.systemAdmin)

        // TODO try this out with ZIO.collectAllPar (https://zio.github.io/zio-prelude/docs/functionaldatatypes/validation#accumulating-errors)
        val validatedUserCreatePayload: Validation[Throwable, UserCreatePayloadADM] =
          Validation.validateWith(
            iri,
            username,
            email,
            givenName,
            familyName,
            password,
            status,
            languageCode,
            systemAdmin
          )(UserCreatePayloadADM)

        val requestMessage: Future[UserCreateRequestADM] = for {
          payload        <- toFuture(validatedUserCreatePayload)
          requestingUser <- getUserADM(requestContext, routeData.appConfig)
        } yield UserCreateRequestADM(
          userCreatePayloadADM = payload,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }
  }

  /**
   * return a single user identified by iri
   */
  private def getUserByIri(): Route = path(usersBasePath / "iri" / Segment) { userIri =>
    get { requestContext =>
      val requestMessage: Future[UserGetRequestADM] = for {
        requestingUser <- getUserADM(
                            requestContext = requestContext,
                            routeData.appConfig
                          )
      } yield UserGetRequestADM(
        identifier = UserIdentifierADM(maybeIri = Some(userIri)),
        userInformationTypeADM = UserInformationTypeADM.Restricted,
        requestingUser = requestingUser
      )

      RouteUtilADM.runJsonRoute(
        requestMessageF = requestMessage,
        requestContext = requestContext,
        appActor = appActor,
        log = log
      )
    }
  }

  /**
   * return a single user identified by email
   */
  private def getUserByEmail(): Route =
    path(usersBasePath / "email" / Segment) { userIri =>
      get { requestContext =>
        val requestMessage: Future[UserGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield UserGetRequestADM(
          identifier = UserIdentifierADM(maybeEmail = Some(userIri)),
          userInformationTypeADM = UserInformationTypeADM.Restricted,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * return a single user identified by username
   */
  private def getUserByUsername(): Route =
    path(usersBasePath / "username" / Segment) { userIri =>
      get { requestContext =>
        val requestMessage: Future[UserGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield UserGetRequestADM(
          identifier = UserIdentifierADM(maybeUsername = Some(userIri)),
          userInformationTypeADM = UserInformationTypeADM.Restricted,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Change existing user's basic information.
   */
  private def changeUserBasicInformation(): Route =
    path(usersBasePath / "iri" / Segment / "BasicUserInformation") { userIri =>
      put {
        entity(as[ChangeUserApiRequestADM]) { apiRequest => requestContext =>
          if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

          val checkedUserIri =
            stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

          if (
            checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
              KnoraSystemInstances.Users.AnonymousUser.id
            )
          ) {
            throw BadRequestException("Changes to built-in users are not allowed.")
          }

          val maybeUsername: Option[Username] = apiRequest.username match {
            case Some(username) => Username.make(username).fold(e => throw e.head, v => Some(v))
            case None           => None
          }

          val maybeEmail: Option[Email] = apiRequest.email match {
            case Some(email) => Email.make(email).fold(e => throw e.head, v => Some(v))
            case None        => None
          }

          val maybeGivenName: Option[GivenName] = apiRequest.givenName match {
            case Some(givenName) => GivenName.make(givenName).fold(e => throw e.head, v => Some(v))
            case None            => None
          }

          val maybeFamilyName: Option[FamilyName] = apiRequest.familyName match {
            case Some(familyName) => FamilyName.make(familyName).fold(e => throw e.head, v => Some(v))
            case None             => None
          }

          val maybeLanguageCode: Option[LanguageCode] = apiRequest.lang match {
            case Some(lang) => LanguageCode.make(lang).fold(e => throw e.head, v => Some(v))
            case None       => None
          }

          val userUpdatePayload: UserUpdateBasicInformationPayloadADM =
            UserUpdateBasicInformationPayloadADM(
              maybeUsername,
              maybeEmail,
              maybeGivenName,
              maybeFamilyName,
              maybeLanguageCode
            )

          /* the api request is already checked at time of creation. see case class. */
          val requestMessage: Future[UsersResponderRequestADM] = for {
            requestingUser <- getUserADM(
                                requestContext = requestContext,
                                routeData.appConfig
                              )
          } yield UserChangeBasicInformationRequestADM(
            userIri = checkedUserIri,
            userUpdateBasicInformationPayload = userUpdatePayload,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            appActor = appActor,
            log = log
          )
        }
      }
    }

  /**
   * Change user's password.
   */
  private def changeUserPassword(): Route =
    path(usersBasePath / "iri" / Segment / "Password") { userIri =>
      put {
        entity(as[ChangeUserPasswordApiRequestADM]) { apiRequest => requestContext =>
          if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

          val checkedUserIri =
            stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

          if (
            checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
              KnoraSystemInstances.Users.AnonymousUser.id
            )
          ) {
            throw BadRequestException("Changes to built-in users are not allowed.")
          }

          val requesterPassword = apiRequest.requesterPassword match {
            case Some(password) => Password.make(password).fold(e => throw e.head, v => v)
            case None           => throw BadRequestException("The requester's password is missing.")
          }
          val changedPassword = apiRequest.newPassword match {
            case Some(password) => Password.make(password).fold(e => throw e.head, v => v)
            case None           => throw BadRequestException("The new password is missing.")
          }

          val requestMessage: Future[UsersResponderRequestADM] = for {
            requestingUser <- getUserADM(
                                requestContext = requestContext,
                                routeData.appConfig
                              )
          } yield UserChangePasswordRequestADM(
            userIri = checkedUserIri,
            userUpdatePasswordPayload = UserUpdatePasswordPayloadADM(requesterPassword, changedPassword),
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            appActor = appActor,
            log = log
          )
        }
      }
    }

  /**
   * Change user's status.
   */
  private def changeUserStatus(): Route =
    path(usersBasePath / "iri" / Segment / "Status") { userIri =>
      put {
        entity(as[ChangeUserApiRequestADM]) { apiRequest => requestContext =>
          if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

          val checkedUserIri =
            stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

          if (
            checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
              KnoraSystemInstances.Users.AnonymousUser.id
            )
          ) {
            throw BadRequestException("Changes to built-in users are not allowed.")
          }

          val newStatus = apiRequest.status match {
            case Some(status) => UserStatus.make(status).fold(error => throw error.head, value => value)
            case None         => throw BadRequestException("The status is missing.")
          }

          val requestMessage: Future[UsersResponderRequestADM] = for {
            requestingUser <- getUserADM(
                                requestContext = requestContext,
                                routeData.appConfig
                              )
          } yield UserChangeStatusRequestADM(
            userIri = checkedUserIri,
            status = newStatus,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            appActor = appActor,
            log = log
          )
        }
      }
    }

  /**
   * delete a user identified by iri (change status to false).
   */
  private def deleteUser(): Route = path(usersBasePath / "iri" / Segment) { userIri =>
    delete { requestContext =>
      if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

      val checkedUserIri =
        stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

      if (
        checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
          KnoraSystemInstances.Users.AnonymousUser.id
        )
      ) {
        throw BadRequestException("Changes to built-in users are not allowed.")
      }

      /* update existing user's status to false */
      val status = UserStatus.make(false).fold(error => throw error.head, value => value)

      val requestMessage: Future[UserChangeStatusRequestADM] = for {
        requestingUser <- getUserADM(
                            requestContext = requestContext,
                            routeData.appConfig
                          )
      } yield UserChangeStatusRequestADM(
        userIri = checkedUserIri,
        status = status,
        requestingUser = requestingUser,
        apiRequestID = UUID.randomUUID()
      )

      RouteUtilADM.runJsonRoute(
        requestMessageF = requestMessage,
        requestContext = requestContext,
        appActor = appActor,
        log = log
      )
    }
  }

  /**
   * Change user's SystemAdmin membership.
   */
  private def changeUserSystemAdminMembership(): Route =
    path(usersBasePath / "iri" / Segment / "SystemAdmin") { userIri =>
      put {
        entity(as[ChangeUserApiRequestADM]) { apiRequest => requestContext =>
          if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

          val checkedUserIri =
            stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

          if (
            checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
              KnoraSystemInstances.Users.AnonymousUser.id
            )
          ) {
            throw BadRequestException("Changes to built-in users are not allowed.")
          }

          val newSystemAdmin = apiRequest.systemAdmin match {
            case Some(systemAdmin) => SystemAdmin.make(systemAdmin).fold(e => throw e.head, v => v)
            case None              => throw BadRequestException("The systemAdmin is missing.")
          }

          val requestMessage: Future[UsersResponderRequestADM] = for {
            requestingUser <- getUserADM(
                                requestContext = requestContext,
                                routeData.appConfig
                              )
          } yield UserChangeSystemAdminMembershipStatusRequestADM(
            userIri = checkedUserIri,
            systemAdmin = newSystemAdmin,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            appActor = appActor,
            log = log
          )
        }
      }
    }

  /**
   * get user's project memberships
   */
  private def getUsersProjectMemberships(): Route =
    path(usersBasePath / "iri" / Segment / "project-memberships") { userIri =>
      get { requestContext =>
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        val requestMessage: Future[UserProjectMembershipsGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield UserProjectMembershipsGetRequestADM(
          userIri = checkedUserIri,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * add user to project
   */
  private def addUserToProjectMembership(): Route =
    path(usersBasePath / "iri" / Segment / "project-memberships" / Segment) { (userIri, projectIri) =>
      post { requestContext =>
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        if (
          checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
            KnoraSystemInstances.Users.AnonymousUser.id
          )
        ) {
          throw BadRequestException("Changes to built-in users are not allowed.")
        }

        val checkedProjectIri =
          stringFormatter.validateAndEscapeProjectIri(
            projectIri,
            throw BadRequestException(s"Invalid project IRI $projectIri")
          )

        val requestMessage: Future[UserProjectMembershipAddRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield UserProjectMembershipAddRequestADM(
          userIri = checkedUserIri,
          projectIri = checkedProjectIri,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * remove user from project (and all groups belonging to this project)
   */
  private def removeUserFromProjectMembership(): Route =
    path(usersBasePath / "iri" / Segment / "project-memberships" / Segment) { (userIri, projectIri) =>
      delete { requestContext =>
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        if (
          checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
            KnoraSystemInstances.Users.AnonymousUser.id
          )
        ) {
          throw BadRequestException("Changes to built-in users are not allowed.")
        }

        val checkedProjectIri =
          stringFormatter.validateAndEscapeProjectIri(
            projectIri,
            throw BadRequestException(s"Invalid project IRI $projectIri")
          )

        val requestMessage: Future[UserProjectMembershipRemoveRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield UserProjectMembershipRemoveRequestADM(
          userIri = checkedUserIri,
          projectIri = checkedProjectIri,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * get user's project admin memberships
   */
  private def getUsersProjectAdminMemberships(): Route =
    path(usersBasePath / "iri" / Segment / "project-admin-memberships") { userIri =>
      get { requestContext =>
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        val requestMessage: Future[UserProjectAdminMembershipsGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield UserProjectAdminMembershipsGetRequestADM(
          userIri = checkedUserIri,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * add user to project admin
   */
  private def addUserToProjectAdminMembership(): Route =
    path(usersBasePath / "iri" / Segment / "project-admin-memberships" / Segment) { (userIri, projectIri) =>
      post { requestContext =>
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        if (
          checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
            KnoraSystemInstances.Users.AnonymousUser.id
          )
        ) {
          throw BadRequestException("Changes to built-in users are not allowed.")
        }

        val checkedProjectIri =
          stringFormatter.validateAndEscapeProjectIri(
            projectIri,
            throw BadRequestException(s"Invalid project IRI $projectIri")
          )

        val requestMessage: Future[UserProjectAdminMembershipAddRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield UserProjectAdminMembershipAddRequestADM(
          userIri = checkedUserIri,
          projectIri = checkedProjectIri,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * remove user from project admin membership
   */
  private def removeUserFromProjectAdminMembership(): Route =
    path(usersBasePath / "iri" / Segment / "project-admin-memberships" / Segment) { (userIri, projectIri) =>
      delete { requestContext =>
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        if (
          checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
            KnoraSystemInstances.Users.AnonymousUser.id
          )
        ) {
          throw BadRequestException("Changes to built-in users are not allowed.")
        }

        val checkedProjectIri =
          stringFormatter.validateAndEscapeProjectIri(
            projectIri,
            throw BadRequestException(s"Invalid project IRI $projectIri")
          )

        val requestMessage: Future[UserProjectAdminMembershipRemoveRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield UserProjectAdminMembershipRemoveRequestADM(
          userIri = checkedUserIri,
          projectIri = checkedProjectIri,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * get user's group memberships
   */
  private def getUsersGroupMemberships(): Route =
    path(usersBasePath / "iri" / Segment / "group-memberships") { userIri =>
      get { requestContext =>
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        val requestMessage: Future[UserGroupMembershipsGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield UserGroupMembershipsGetRequestADM(
          userIri = checkedUserIri,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * add user to group
   */
  private def addUserToGroupMembership(): Route =
    path(usersBasePath / "iri" / Segment / "group-memberships" / Segment) { (userIri, groupIri) =>
      post { requestContext =>
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        if (
          checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
            KnoraSystemInstances.Users.AnonymousUser.id
          )
        ) {
          throw BadRequestException("Changes to built-in users are not allowed.")
        }

        val checkedGroupIri =
          stringFormatter.validateAndEscapeIri(groupIri, throw BadRequestException(s"Invalid group IRI $groupIri"))

        val requestMessage: Future[UserGroupMembershipAddRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield UserGroupMembershipAddRequestADM(
          userIri = checkedUserIri,
          groupIri = checkedGroupIri,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * remove user from group
   */
  private def removeUserFromGroupMembership(): Route =
    path(usersBasePath / "iri" / Segment / "group-memberships" / Segment) { (userIri, groupIri) =>
      delete { requestContext =>
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        if (
          checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
            KnoraSystemInstances.Users.AnonymousUser.id
          )
        ) {
          throw BadRequestException("Changes to built-in users are not allowed.")
        }

        val checkedGroupIri =
          stringFormatter.validateAndEscapeIri(groupIri, throw BadRequestException(s"Invalid group IRI $groupIri"))

        val requestMessage: Future[UserGroupMembershipRemoveRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield UserGroupMembershipRemoveRequestADM(
          userIri = checkedUserIri,
          groupIri = checkedGroupIri,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }
}
