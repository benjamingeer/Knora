/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v1

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.scalatest.DoNotDiscover

import scala.concurrent.duration._

import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoV1
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectV1JsonProtocol
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.sharedtestdata.SharedTestDataV1
import org.knora.webapi.util.AkkaHttpUtils

/**
 * End-to-End (E2E) test specification for testing groups endpoint.
 */
@DoNotDiscover
class ProjectsV1E2ESpec
    extends E2ESpec
    with SessionJsonProtocol
    with ProjectV1JsonProtocol
    with TriplestoreJsonProtocol {

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(30.seconds)

  private val rootEmail           = SharedTestDataV1.rootUser.userData.email.get
  private val testPass            = java.net.URLEncoder.encode("test", "utf-8")
  private val projectIri          = SharedTestDataV1.imagesProjectInfo.id
  private val projectIriEnc       = java.net.URLEncoder.encode(projectIri, "utf-8")
  private val projectShortName    = SharedTestDataV1.imagesProjectInfo.shortname
  private val projectShortnameEnc = java.net.URLEncoder.encode(projectShortName, "utf-8")

  "The Projects Route ('v1/projects')" when {

    "used to query for project information" should {

      "return all projects" in {
        val request                = Get(baseApiUrl + s"/v1/projects") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        // logger.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)

        // logger.debug("projects as objects: {}", AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[ProjectInfoV1]])

        val projects: Seq[ProjectInfoV1] =
          AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[ProjectInfoV1]]

        projects.size should be(8)

      }

      "return the information for a single project identified by iri" in {
        val request =
          Get(baseApiUrl + s"/v1/projects/$projectIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        // logger.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)
      }

      "return the information for a single project identified by shortname" in {
        val request = Get(baseApiUrl + s"/v1/projects/$projectShortnameEnc?identifier=shortname") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // logger.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)
      }
    }
  }
}
