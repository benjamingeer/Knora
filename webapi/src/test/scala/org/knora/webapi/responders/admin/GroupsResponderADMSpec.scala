/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * To be able to test UsersResponder, we need to be able to start UsersResponder isolated. Now the UsersResponder
 * extend ResponderADM which messes up testing, as we cannot inject the TestActor system.
 */
package org.knora.webapi.responders.admin

import java.util.UUID
import akka.actor.Status.Failure
import akka.testkit.ImplicitSender
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.exceptions.{BadRequestException, DuplicateValueException, NotFoundException}
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationTypeADM
import org.knora.webapi.messages.admin.responder.valueObjects.{Description, Name, Selfjoin, Status}
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.MutableTestIri

import scala.concurrent.duration._

object GroupsResponderADMSpec {

  val config: Config = ConfigFactory.parseString("""
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * This spec is used to test the messages received by the [[org.knora.webapi.responders.admin.UsersResponderADM]] actor.
 */
class GroupsResponderADMSpec extends CoreSpec(GroupsResponderADMSpec.config) with ImplicitSender {

  private val timeout = 5.seconds

  private val imagesProject = SharedTestDataADM.imagesProject
  private val imagesReviewerGroup = SharedTestDataADM.imagesReviewerGroup

  private val rootUser = SharedTestDataADM.rootUser

  "The GroupsResponder " when {

    "asked about all groups" should {
      "return a list" in {
        responderManager ! GroupsGetRequestADM(
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.rootUser
        )

        val response = expectMsgType[GroupsGetResponseADM](timeout)
        response.groups.nonEmpty should be(true)
        response.groups.size should be(2)
      }
    }

    "asked about a group identified by 'iri' " should {
      "return group info if the group is known " in {
        responderManager ! GroupGetRequestADM(
          groupIri = imagesReviewerGroup.id,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = rootUser
        )

        expectMsg(GroupGetResponseADM(imagesReviewerGroup))
      }
      "return 'NotFoundException' when the group is unknown " in {
        responderManager ! GroupGetRequestADM(
          groupIri = "http://rdfh.ch/groups/notexisting",
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = rootUser
        )

        expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
          msg.cause.isInstanceOf[NotFoundException] should ===(true)
        }
      }
    }

    "used to modify group information" should {

      val newGroupIri = new MutableTestIri

      "CREATE the group and return the group's info if the supplied group name is unique" in {
        responderManager ! GroupCreateRequestADM(
          createRequest = GroupCreatePayloadADM.create(
            id = None,
            name = Name.create("NewGroup").fold(e => throw e, v => v),
            descriptions = Description
              .make(
                Seq(
                  StringLiteralV2(value = """NewGroupDescription with "quotes" and <html tag>""", language = Some("en"))
                )
              )
              .fold(e => throw e.head, v => v),
            project = SharedTestDataADM.IMAGES_PROJECT_IRI,
            status = Status.make(true).fold(e => throw e.head, v => v),
            selfjoin = Selfjoin.make(false).fold(e => throw e.head, v => v)
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        val received: GroupOperationResponseADM = expectMsgType[GroupOperationResponseADM](timeout)
        val newGroupInfo = received.group

        newGroupInfo.name should equal("NewGroup")
        newGroupInfo.descriptions should equal(
          Seq(StringLiteralV2("""NewGroupDescription with "quotes" and <html tag>""", Some("en")))
        )
        newGroupInfo.project should equal(imagesProject)
        newGroupInfo.status should equal(true)
        newGroupInfo.selfjoin should equal(false)

        // store for later usage
        newGroupIri.set(newGroupInfo.id)
      }

      "return a 'DuplicateValueException' if the supplied group name is not unique" in {
        responderManager ! GroupCreateRequestADM(
          createRequest = GroupCreatePayloadADM.create(
            id = Some(imagesReviewerGroup.id),
            name = Name.create("NewGroup").fold(e => throw e, v => v),
            descriptions = Description
              .make(Seq(StringLiteralV2(value = "NewGroupDescription", language = Some("en"))))
              .fold(e => throw e.head, v => v),
            project = SharedTestDataADM.IMAGES_PROJECT_IRI,
            status = Status.make(true).fold(e => throw e.head, v => v),
            selfjoin = Selfjoin.make(false).fold(e => throw e.head, v => v)
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
          msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
        }
      }

      "return 'BadRequestException' if project IRI are missing" in {
        responderManager ! GroupCreateRequestADM(
          createRequest = GroupCreatePayloadADM.create(
            id = Some(""),
            name = Name.create("OtherNewGroup").fold(e => throw e, v => v),
            descriptions = Description
              .make(Seq(StringLiteralV2(value = "OtherNewGroupDescription", language = Some("en"))))
              .fold(e => throw e.head, v => v),
            project = "",
            status = Status.make(true).fold(e => throw e.head, v => v),
            selfjoin = Selfjoin.make(false).fold(e => throw e.head, v => v)
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )
        expectMsg(Failure(BadRequestException("Project IRI cannot be empty")))
      }

      "UPDATE a group" in {
        responderManager ! GroupChangeRequestADM(
          groupIri = newGroupIri.get,
          changeGroupRequest = ChangeGroupApiRequestADM(
            Some("UpdatedGroupName"),
            Some(
              Seq(StringLiteralV2(value = """UpdatedDescription with "quotes" and <html tag>""", Some("en")))
            )
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        val received: GroupOperationResponseADM = expectMsgType[GroupOperationResponseADM](timeout)
        val updatedGroupInfo = received.group

        updatedGroupInfo.name should equal("UpdatedGroupName")
        updatedGroupInfo.descriptions should equal(
          Seq(StringLiteralV2("""UpdatedDescription with "quotes" and <html tag>""", Some("en")))
        )
        updatedGroupInfo.project should equal(imagesProject)
        updatedGroupInfo.status should equal(true)
        updatedGroupInfo.selfjoin should equal(false)
      }

      "return 'NotFound' if a not-existing group IRI is submitted during update" in {
        responderManager ! GroupChangeRequestADM(
          groupIri = "http://rdfh.ch/groups/notexisting",
          ChangeGroupApiRequestADM(
            Some("UpdatedGroupName"),
            Some(Seq(StringLiteralV2(value = "UpdatedDescription", language = Some("en"))))
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
          msg.cause.isInstanceOf[NotFoundException] should ===(true)
        }
      }

      "return 'BadRequest' if the new group name already exists inside the project" in {
        responderManager ! GroupChangeRequestADM(
          groupIri = newGroupIri.get,
          changeGroupRequest = ChangeGroupApiRequestADM(
            Some("Image reviewer"),
            Some(Seq(StringLiteralV2(value = "UpdatedDescription", language = Some("en"))))
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
          msg.cause.isInstanceOf[BadRequestException] should ===(true)
        }
      }

      "return 'BadRequest' if nothing would be changed during the update" in {
        an[BadRequestException] should be thrownBy ChangeGroupApiRequestADM(None, None, None, None)
      }
    }

    "used to query members" should {

      "return all members of a group identified by IRI" in {
        responderManager ! GroupMembersGetRequestADM(
          groupIri = SharedTestDataADM.imagesReviewerGroup.id,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.rootUser
        )

        val received: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)

        received.members.map(_.id) should contain allElementsOf Seq(
          SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.Restricted),
          SharedTestDataADM.imagesReviewerUser.ofType(UserInformationTypeADM.Restricted)
        ).map(_.id)
      }

      "remove all members when group is deactivated" in {
        responderManager ! GroupMembersGetRequestADM(
          groupIri = SharedTestDataADM.imagesReviewerGroup.id,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.rootUser
        )

        val membersBeforeStatusChange: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)
        membersBeforeStatusChange.members.size shouldBe 2

        responderManager ! GroupChangeStatusRequestADM(
          groupIri = SharedTestDataADM.imagesReviewerGroup.id,
          changeGroupRequest = ChangeGroupApiRequestADM(status = Some(false)),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        val statusChangeResponse = expectMsgType[GroupOperationResponseADM](timeout)

        statusChangeResponse.group.status shouldBe false

        responderManager ! GroupMembersGetRequestADM(
          groupIri = SharedTestDataADM.imagesReviewerGroup.id,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.rootUser
        )

        expectMsg(Failure(NotFoundException("No members found.")))
      }

      "return 'NotFound' when the group IRI is unknown" in {
        responderManager ! GroupMembersGetRequestADM(
          groupIri = "http://rdfh.ch/groups/notexisting",
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.rootUser
        )

        expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
          msg.cause.isInstanceOf[NotFoundException] should ===(true)
        }
      }
    }
  }
}
