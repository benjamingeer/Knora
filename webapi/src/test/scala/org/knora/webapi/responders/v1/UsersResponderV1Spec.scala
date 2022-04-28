/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * To be able to test UsersResponder, we need to be able to start UsersResponder isolated. Now the UsersResponder
 * extend ResponderV1 which messes up testing, as we cannot inject the TestActor system.
 */
package org.knora.webapi.responders.v1

import akka.actor.Status.Failure
import akka.testkit.ImplicitSender
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.exceptions.NotFoundException
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.sharedtestdata.SharedTestDataV1

import scala.concurrent.duration._

object UsersResponderV1Spec {

  val config: Config = ConfigFactory.parseString("""
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * This spec is used to test the messages received by the [[UsersResponderV1]] actor.
 */
class UsersResponderV1Spec extends CoreSpec(UsersResponderV1Spec.config) with ImplicitSender {

  private val timeout = 5.seconds

  private val rootUser      = SharedTestDataV1.rootUser
  private val rootUserIri   = rootUser.userData.user_id.get
  private val rootUserEmail = rootUser.userData.email.get

  private val normalUser    = SharedTestDataV1.normalUser
  private val normalUserIri = normalUser.userData.user_id.get

  private val incunabulaUser      = SharedTestDataV1.incunabulaProjectAdminUser
  private val incunabulaUserIri   = incunabulaUser.userData.user_id.get
  private val incunabulaUserEmail = incunabulaUser.userData.email.get

  private val imagesProjectIri = SharedTestDataV1.imagesProjectInfo.id

  "The UsersResponder " when {

    "asked about all users" should {
      "return a list" in {
        responderManager ! UsersGetRequestV1(rootUser)
        val response = expectMsgType[UsersGetResponseV1](timeout)
        // println(response.users)
        response.users.nonEmpty should be(true)
        response.users.size should be(20)
      }
    }

    "asked about an user identified by 'iri' " should {

      "return a profile if the user (root user) is known" in {
        responderManager ! UserProfileByIRIGetV1(
          userIri = rootUserIri,
          UserProfileTypeV1.FULL,
          featureFactoryConfig = defaultFeatureFactoryConfig
        )
        val response = expectMsgType[Option[UserProfileV1]](timeout)
        // println(response)
        response should equal(Some(rootUser.ofType(UserProfileTypeV1.FULL)))
      }

      "return a profile if the user (incunabula user) is known" in {
        responderManager ! UserProfileByIRIGetV1(
          incunabulaUserIri,
          UserProfileTypeV1.FULL,
          featureFactoryConfig = defaultFeatureFactoryConfig
        )
        expectMsg(Some(incunabulaUser.ofType(UserProfileTypeV1.FULL)))
      }

      "return 'NotFoundException' when the user is unknown " in {
        responderManager ! UserProfileByIRIGetRequestV1(
          userIri = "http://rdfh.ch/users/notexisting",
          userProfileType = UserProfileTypeV1.RESTRICTED,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          userProfile = rootUser
        )
        expectMsg(Failure(NotFoundException(s"User 'http://rdfh.ch/users/notexisting' not found")))
      }

      "return 'None' when the user is unknown " in {
        responderManager ! UserProfileByIRIGetV1(
          userIri = "http://rdfh.ch/users/notexisting",
          userProfileType = UserProfileTypeV1.RESTRICTED,
          featureFactoryConfig = defaultFeatureFactoryConfig
        )
        expectMsg(None)
      }
    }

    "asked about an user identified by 'email'" should {

      "return a profile if the user (root user) is known" in {
        responderManager ! UserProfileByEmailGetV1(
          email = rootUserEmail,
          userProfileType = UserProfileTypeV1.RESTRICTED,
          featureFactoryConfig = defaultFeatureFactoryConfig
        )
        expectMsg(Some(rootUser.ofType(UserProfileTypeV1.RESTRICTED)))
      }

      "return a profile if the user (incunabula user) is known" in {
        responderManager ! UserProfileByEmailGetV1(
          email = incunabulaUserEmail,
          userProfileType = UserProfileTypeV1.RESTRICTED,
          featureFactoryConfig = defaultFeatureFactoryConfig
        )
        expectMsg(Some(incunabulaUser.ofType(UserProfileTypeV1.RESTRICTED)))
      }

      "return 'NotFoundException' when the user is unknown" in {
        responderManager ! UserProfileByEmailGetRequestV1(
          email = "userwrong@example.com",
          userProfileType = UserProfileTypeV1.RESTRICTED,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          userProfile = rootUser
        )
        expectMsg(Failure(NotFoundException(s"User 'userwrong@example.com' not found")))
      }

      "return 'None' when the user is unknown" in {
        responderManager ! UserProfileByEmailGetV1(
          email = "userwrong@example.com",
          userProfileType = UserProfileTypeV1.RESTRICTED,
          featureFactoryConfig = defaultFeatureFactoryConfig
        )
        expectMsg(None)
      }
    }
  }
}
