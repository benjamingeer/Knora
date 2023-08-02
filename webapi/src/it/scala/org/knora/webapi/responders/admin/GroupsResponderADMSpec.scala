/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import akka.actor.Status.Failure
import dsp.errors.{BadRequestException, DuplicateValueException, NotFoundException}
import dsp.valueobjects.Group._
import dsp.valueobjects.Iri._
import dsp.valueobjects.V2
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationTypeADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.MutableTestIri

import java.util.UUID

/**
 * This spec is used to test the messages received by the [[org.knora.webapi.responders.admin.GroupsResponderADMSpec]] actor.
 */
class GroupsResponderADMSpec extends CoreSpec {
  private val imagesProject       = SharedTestDataADM.imagesProject
  private val imagesReviewerGroup = SharedTestDataADM.imagesReviewerGroup

  "The GroupsResponder " when {
    "asked about all groups" should {
      "return a list" in {
        appActor ! GroupsGetRequestADM()

        val response = expectMsgType[GroupsGetResponseADM](timeout)
        response.groups.nonEmpty should be(true)
        response.groups.size should be(2)
      }
    }

    "asked about a group identified by 'iri' " should {
      "return group info if the group is known " in {
        appActor ! GroupGetRequestADM(
          groupIri = imagesReviewerGroup.id
        )

        expectMsg(GroupGetResponseADM(imagesReviewerGroup))
      }

      "return 'NotFoundException' when the group is unknown " in {
        appActor ! GroupGetRequestADM(
          groupIri = "http://rdfh.ch/groups/notexisting"
        )

        expectMsgPF(timeout) { case msg: Failure =>
          msg.cause.isInstanceOf[NotFoundException] should ===(true)
        }
      }
    }

    "used to modify group information" should {
      val newGroupIri = new MutableTestIri

      "CREATE the group and return the group's info if the supplied group name is unique" in {
        appActor ! GroupCreateRequestADM(
          createRequest = GroupCreatePayloadADM(
            id = None,
            name = GroupName.make("NewGroup").fold(e => throw e.head, v => v),
            descriptions = GroupDescriptions
              .make(
                Seq(
                  V2.StringLiteralV2(
                    value = """NewGroupDescription with "quotes" and <html tag>""",
                    language = Some("en")
                  )
                )
              )
              .fold(e => throw e.head, v => v),
            project = ProjectIri.make(SharedTestDataADM.imagesProjectIri).fold(e => throw e.head, v => v),
            status = GroupStatus.active,
            selfjoin = GroupSelfJoin.make(false).fold(e => throw e.head, v => v)
          ),
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        val received: GroupOperationResponseADM = expectMsgType[GroupOperationResponseADM](timeout)
        val newGroupInfo                        = received.group

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
        appActor ! GroupCreateRequestADM(
          createRequest = GroupCreatePayloadADM(
            id = Some(
              GroupIri.make(imagesReviewerGroup.id).fold(e => throw e.head, v => v)
            ),
            name = GroupName.make("NewGroup").fold(e => throw e.head, v => v),
            descriptions = GroupDescriptions
              .make(Seq(V2.StringLiteralV2(value = "NewGroupDescription", language = Some("en"))))
              .fold(e => throw e.head, v => v),
            project = ProjectIri.make(SharedTestDataADM.imagesProjectIri).fold(e => throw e.head, v => v),
            status = GroupStatus.active,
            selfjoin = GroupSelfJoin.make(false).fold(e => throw e.head, v => v)
          ),
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        expectMsgPF(timeout) { case msg: Failure =>
          msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
        }
      }

      "UPDATE a group" in {
        appActor ! GroupChangeRequestADM(
          groupIri = newGroupIri.get,
          changeGroupRequest = GroupUpdatePayloadADM(
            Some(GroupName.make("UpdatedGroupName").fold(e => throw e.head, v => v)),
            Some(
              GroupDescriptions
                .make(
                  Seq(V2.StringLiteralV2(value = """UpdatedDescription with "quotes" and <html tag>""", Some("en")))
                )
                .fold(e => throw e.head, v => v)
            )
          ),
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        val received: GroupOperationResponseADM = expectMsgType[GroupOperationResponseADM](timeout)
        val updatedGroupInfo                    = received.group

        updatedGroupInfo.name should equal("UpdatedGroupName")
        updatedGroupInfo.descriptions should equal(
          Seq(StringLiteralV2("""UpdatedDescription with "quotes" and <html tag>""", Some("en")))
        )
        updatedGroupInfo.project should equal(imagesProject)
        updatedGroupInfo.status should equal(true)
        updatedGroupInfo.selfjoin should equal(false)
      }

      "return 'NotFound' if a not-existing group IRI is submitted during update" in {
        appActor ! GroupChangeRequestADM(
          groupIri = "http://rdfh.ch/groups/notexisting",
          GroupUpdatePayloadADM(
            Some(GroupName.make("UpdatedGroupName").fold(e => throw e.head, v => v)),
            Some(
              GroupDescriptions
                .make(Seq(V2.StringLiteralV2(value = "UpdatedDescription", language = Some("en"))))
                .fold(e => throw e.head, v => v)
            )
          ),
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        expectMsgPF(timeout) { case msg: Failure =>
          msg.cause.isInstanceOf[NotFoundException] should ===(true)
        }
      }

      "return 'BadRequest' if the new group name already exists inside the project" in {
        appActor ! GroupChangeRequestADM(
          groupIri = newGroupIri.get,
          changeGroupRequest = GroupUpdatePayloadADM(
            Some(GroupName.make("Image reviewer").fold(e => throw e.head, v => v)),
            Some(
              GroupDescriptions
                .make(Seq(V2.StringLiteralV2(value = "UpdatedDescription", language = Some("en"))))
                .fold(e => throw e.head, v => v)
            )
          ),
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        expectMsgPF(timeout) { case msg: Failure =>
          msg.cause.isInstanceOf[BadRequestException] should ===(true)
        }
      }

      "return 'BadRequest' if nothing would be changed during the update" in {
        an[BadRequestException] should be thrownBy ChangeGroupApiRequestADM(None, None, None, None)
      }
    }

    "used to query members" should {
      "return all members of a group identified by IRI" in {
        appActor ! GroupMembersGetRequestADM(
          groupIri = SharedTestDataADM.imagesReviewerGroup.id,
          requestingUser = SharedTestDataADM.rootUser
        )

        val received: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)

        received.members.map(_.id) should contain allElementsOf Seq(
          SharedTestDataADM.multiuserUser.ofType(UserInformationTypeADM.Restricted),
          SharedTestDataADM.imagesReviewerUser.ofType(UserInformationTypeADM.Restricted)
        ).map(_.id)
      }

      "remove all members when group is deactivated" in {
        appActor ! GroupMembersGetRequestADM(
          groupIri = SharedTestDataADM.imagesReviewerGroup.id,
          requestingUser = SharedTestDataADM.rootUser
        )

        val membersBeforeStatusChange: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)
        membersBeforeStatusChange.members.size shouldBe 2

        appActor ! GroupChangeStatusRequestADM(
          groupIri = SharedTestDataADM.imagesReviewerGroup.id,
          changeGroupRequest = ChangeGroupApiRequestADM(status = Some(false)),
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID
        )

        val statusChangeResponse = expectMsgType[GroupOperationResponseADM](timeout)
        statusChangeResponse.group.status shouldBe false

        appActor ! GroupMembersGetRequestADM(
          groupIri = SharedTestDataADM.imagesReviewerGroup.id,
          requestingUser = SharedTestDataADM.rootUser
        )

        val noMembers: GroupMembersGetResponseADM = expectMsgType[GroupMembersGetResponseADM](timeout)
        noMembers.members.size shouldBe 0
      }

      "return 'NotFound' when the group IRI is unknown" in {
        appActor ! GroupMembersGetRequestADM(
          groupIri = "http://rdfh.ch/groups/notexisting",
          requestingUser = SharedTestDataADM.rootUser
        )

        expectMsgPF(timeout) { case msg: Failure =>
          msg.cause.isInstanceOf[NotFoundException] should ===(true)
        }
      }
    }
  }
}
