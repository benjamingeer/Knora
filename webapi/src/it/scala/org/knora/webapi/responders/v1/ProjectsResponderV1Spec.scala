/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * To be able to test UsersResponder, we need to be able to start UsersResponder isolated. Now the UsersResponder
 * extend ResponderV1 which messes up testing, as we cannot inject the TestActor system.
 */
package org.knora.webapi.responders.v1

import akka.actor.Status.Failure
import akka.testkit.ImplicitSender

import scala.concurrent.duration._

import dsp.errors.NotFoundException
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.sharedtestdata.SharedTestDataV1

/**
 * This spec is used to test the messages received by the [[ProjectsResponderV1]] actor.
 */
class ProjectsResponderV1Spec extends CoreSpec with ImplicitSender {

  private val rootUserProfileV1 = SharedTestDataV1.rootUser

  "The ProjectsResponderV1 " when {

    "used to query for project information" should {

      "return information for every project" in {

        appActor ! ProjectsGetRequestV1(
          userProfile = Some(rootUserProfileV1)
        )
        val received = expectMsgType[ProjectsResponseV1](timeout)

        assert(received.projects.contains(SharedTestDataV1.imagesProjectInfo))
        assert(received.projects.contains(SharedTestDataV1.incunabulaProjectInfo))
      }

      "return information about a project identified by IRI" in {

        /* Incunabula project */
        appActor ! ProjectInfoByIRIGetRequestV1(
          iri = SharedTestDataV1.incunabulaProjectInfo.id,
          userProfileV1 = Some(SharedTestDataV1.rootUser)
        )
        expectMsg(ProjectInfoResponseV1(SharedTestDataV1.incunabulaProjectInfo))

        /* Images project */
        appActor ! ProjectInfoByIRIGetRequestV1(
          iri = SharedTestDataV1.imagesProjectInfo.id,
          userProfileV1 = Some(SharedTestDataV1.rootUser)
        )
        expectMsg(ProjectInfoResponseV1(SharedTestDataV1.imagesProjectInfo))

        /* 'SystemProject' */
        appActor ! ProjectInfoByIRIGetRequestV1(
          iri = SharedTestDataV1.systemProjectInfo.id,
          userProfileV1 = Some(SharedTestDataV1.rootUser)
        )
        expectMsg(ProjectInfoResponseV1(SharedTestDataV1.systemProjectInfo))

      }

      "return information about a project identified by shortname" in {
        appActor ! ProjectInfoByShortnameGetRequestV1(
          SharedTestDataV1.incunabulaProjectInfo.shortname,
          Some(rootUserProfileV1)
        )
        expectMsg(ProjectInfoResponseV1(SharedTestDataV1.incunabulaProjectInfo))
      }

      "return 'NotFoundException' when the project IRI is unknown" in {

        appActor ! ProjectInfoByIRIGetRequestV1(
          iri = "http://rdfh.ch/projects/notexisting",
          userProfileV1 = Some(rootUserProfileV1)
        )
        expectMsg(Failure(NotFoundException(s"Project 'http://rdfh.ch/projects/notexisting' not found")))

      }

      "return 'NotFoundException' when the project shortname unknown " in {
        appActor ! ProjectInfoByShortnameGetRequestV1(
          shortname = "projectwrong",
          userProfileV1 = Some(rootUserProfileV1)
        )
        expectMsg(Failure(NotFoundException(s"Project 'projectwrong' not found")))
      }
    }
  }

}
