/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin.lists

import org.apache.pekko

import scala.concurrent.Await
import scala.concurrent.duration.*

import org.knora.webapi.E2ESpec
import org.knora.webapi.IRI
import org.knora.webapi.e2e.admin.lists
import org.knora.webapi.messages.admin.responder.listsmessages.*
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.AkkaHttpUtils
import org.knora.webapi.util.MutableTestIri

import pekko.http.scaladsl.model.*
import pekko.http.scaladsl.testkit.RouteTestTimeout
import pekko.http.scaladsl.unmarshalling.Unmarshal

/**
 * End-to-End (E2E) test specification for testing lists endpoint.
 */
class CreateListItemsRouteADME2ESpec
    extends E2ESpec
    with TriplestoreJsonProtocol
    with IntegrationTestListADMJsonProtocol {

  implicit def default: RouteTestTimeout = RouteTestTimeout(5.seconds)

  val anythingUserCreds: CredentialsADM = lists.CredentialsADM(
    SharedTestDataADM.anythingUser1,
    "test",
  )

  val anythingAdminUserCreds: CredentialsADM = lists.CredentialsADM(
    SharedTestDataADM.anythingAdminUser,
    "test",
  )

  val beolAdminUserCreds: CredentialsADM = lists.CredentialsADM(
    SharedTestDataADM.beolUser,
    "test",
  )

  private val customChildNodeIRI = "http://rdfh.ch/lists/0001/vQgijJZKSqawFooJPyhYkw"
  def addChildListNodeRequest(parentNodeIri: IRI, name: String, label: String, comment: String): String =
    s"""{
       |    "parentNodeIri": "$parentNodeIri",
       |    "projectIri": "${SharedTestDataADM.anythingProjectIri}",
       |    "name": "$name",
       |    "labels": [{ "value": "$label", "language": "en"}],
       |    "comments": [{ "value": "$comment", "language": "en"}]
       |}""".stripMargin

  "The admin lists route (/admin/lists)" when {
    "creating list items with a custom Iri" should {
      "create a list with the provided custom Iri" in {
        val createListWithCustomIriRequest: String =
          s"""{
             |    "id": "${SharedTestDataADM.customListIRI}",
             |    "projectIri": "${SharedTestDataADM.anythingProjectIri}",
             |    "labels": [{ "value": "New list with a custom IRI", "language": "en"}],
             |    "comments": [{ "value": "XXXXX", "language": "en"}]
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/lists",
          HttpEntity(ContentTypes.`application/json`, createListWithCustomIriRequest),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val receivedList: ListADM = AkkaHttpUtils.httpResponseToJson(response).fields("list").convertTo[ListADM]

        val listInfo = receivedList.listinfo
        listInfo.id should be(SharedTestDataADM.customListIRI)

        val labels: Seq[StringLiteralV2] = listInfo.labels.stringLiterals
        labels.size should be(1)
        labels.head should be(StringLiteralV2.from(value = "New list with a custom IRI", language = Some("en")))
      }

      "return a DuplicateValueException during list creation when the supplied list IRI is not unique" in {
        // duplicate list IRI
        val params =
          s"""
             |{
             |    "id": "${SharedTestDataADM.customListIRI}",
             |    "projectIri": "${SharedTestDataADM.anythingProjectIri}",
             |    "labels": [{ "value": "New List", "language": "en"}],
             |    "comments": [{ "value": "XXXXX", "language": "en"}]
             |}
                """.stripMargin

        val request =
          Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
            anythingAdminUserCreds.basicHttpCredentials,
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)

        val errorMessage: String = Await.result(Unmarshal(response.entity).to[String], 1.second)
        val invalidIri: Boolean =
          errorMessage.contains(s"IRI: '${SharedTestDataADM.customListIRI}' already exists, try another one.")
        invalidIri should be(true)
      }

      "add a child with a custom IRI" in {
        val createChildNodeWithCustomIriRequest =
          s"""
             |{   "id": "$customChildNodeIRI",
             |    "parentNodeIri": "${SharedTestDataADM.customListIRI}",
             |    "projectIri": "${SharedTestDataADM.anythingProjectIri}",
             |    "name": "node with a custom IRI",
             |    "labels": [{ "value": "New List Node", "language": "en"}],
             |    "comments": [{ "value": "XXXXX", "language": "en"}]
             |}""".stripMargin

        val encodedParentNodeUrl = java.net.URLEncoder.encode(SharedTestDataADM.customListIRI, "utf-8")

        val request = Post(
          baseApiUrl + s"/admin/lists/" + encodedParentNodeUrl,
          HttpEntity(ContentTypes.`application/json`, createChildNodeWithCustomIriRequest),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val received: ListNodeInfoADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListNodeInfoADM]

        // check correct node info
        val childNodeInfo = received match {
          case info: ListChildNodeInfoADM => info
          case something                  => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
        }
        childNodeInfo.id should be(customChildNodeIRI)
      }
    }

    "used to create list items" should {
      val newListIri     = new MutableTestIri
      val firstChildIri  = new MutableTestIri
      val secondChildIri = new MutableTestIri
      val thirdChildIri  = new MutableTestIri

      "create a list" in {
        val createListRequest: String =
          s"""{
             |    "projectIri": "${SharedTestDataADM.anythingProjectIri}",
             |    "labels": [{ "value": "Neue Liste", "language": "de"}],
             |    "comments": [{ "value": "XXXXX", "language": "en"}]
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/lists",
          HttpEntity(ContentTypes.`application/json`, createListRequest),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val receivedList: ListADM = AkkaHttpUtils.httpResponseToJson(response).fields("list").convertTo[ListADM]

        val listInfo = receivedList.listinfo
        listInfo.projectIri should be(SharedTestDataADM.anythingProjectIri.value)

        val labels: Seq[StringLiteralV2] = listInfo.labels.stringLiterals
        labels.size should be(1)
        labels.head should be(StringLiteralV2.from(value = "Neue Liste", language = Some("de")))

        val comments = receivedList.listinfo.comments.stringLiterals
        comments.isEmpty should be(false)

        val children = receivedList.children
        children.size should be(0)

        // store list IRI for next test
        newListIri.set(listInfo.id)
      }

      "create a list using bad project IRI, created in the old way with bad UUID version" in {
        val createListRequest: String =
          s"""{
             |    "projectIri": "${SharedTestDataADM.beolProjectIri}",
             |    "labels": [{ "value": "Neue Liste", "language": "de"}],
             |    "comments": [{ "value": "XXXXX", "language": "en"}]
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/lists",
          HttpEntity(ContentTypes.`application/json`, createListRequest),
        ) ~> addCredentials(beolAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val receivedList: ListADM = AkkaHttpUtils.httpResponseToJson(response).fields("list").convertTo[ListADM]

        val listInfo = receivedList.listinfo
        listInfo.projectIri should be(SharedTestDataADM.beolProjectIri.value)
      }

      "return a ForbiddenException if the user creating the list is not project or system admin" in {
        val params =
          s"""
             |{
             |    "projectIri": "${SharedTestDataADM.anythingProjectIri}",
             |    "labels": [{ "value": "Neue Liste", "language": "de"}],
             |    "comments": [{ "value": "XXXXX", "language": "en"}]
             |}
                """.stripMargin

        val request =
          Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
            anythingUserCreds.basicHttpCredentials,
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.Forbidden)
      }

      "return a BadRequestException during list creation when request does not contain a projectIri" in {
        //
        val params01 =
          s"""
             |{
             |    "projectIri": "",
             |    "labels": [{ "value": "Neue Liste", "language": "de"}],
             |    "comments": [{ "value": "XXXXX", "language": "en"}]
             |}
                """.stripMargin

        val request01 =
          Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, params01)) ~> addCredentials(
            anythingAdminUserCreds.basicHttpCredentials,
          )
        val response01: HttpResponse = singleAwaitingRequest(request01)
        response01.status should be(StatusCodes.BadRequest)
      }

      "return a BadRequestException during list creation when request has invalid project IRI" in {
        val params02 =
          s"""
             |{
             |    "projectIri": "notvalidIRI",
             |    "labels": [{ "value": "Neue Liste", "language": "de"}],
             |    "comments": [{ "value": "XXXXX", "language": "en"}]
             |}
                """.stripMargin

        val request02 =
          Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, params02)) ~> addCredentials(
            anythingAdminUserCreds.basicHttpCredentials,
          )
        val response02: HttpResponse = singleAwaitingRequest(request02)
        response02.status should be(StatusCodes.BadRequest)
      }

      "return a BadRequestException during list creation when request has a missing label" in {
        val params03 =
          s"""
             |{
             |    "projectIri": "${SharedTestDataADM.anythingProjectIri}",
             |    "labels": [],
             |    "comments": [{ "value": "XXXXX", "language": "en"}]
             |}
                """.stripMargin

        val request03 =
          Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, params03)) ~> addCredentials(
            anythingAdminUserCreds.basicHttpCredentials,
          )
        val response03: HttpResponse = singleAwaitingRequest(request03)
        response03.status should be(StatusCodes.BadRequest)

      }

      "add child to list - to the root node" in {
        val encodedListUrl = java.net.URLEncoder.encode(newListIri.get, "utf-8")

        val name    = "first"
        val label   = "New First Child List Node Value"
        val comment = "New First Child List Node Comment"

        val addChildToRoot = addChildListNodeRequest(
          parentNodeIri = newListIri.get,
          name = name,
          label = label,
          comment = comment,
        )

        val request = Post(
          baseApiUrl + s"/admin/lists/" + encodedListUrl,
          HttpEntity(ContentTypes.`application/json`, addChildToRoot),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val received: ListNodeInfoADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListNodeInfoADM]

        // check correct node info
        val childNodeInfo = received match {
          case info: ListChildNodeInfoADM => info
          case something                  => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
        }

        // check labels
        val labels: Seq[StringLiteralV2] = childNodeInfo.labels.stringLiterals
        labels.size should be(1)
        labels.sorted should be(Seq(StringLiteralV2.from(value = label, language = Some("en"))))

        // check comments
        val comments = childNodeInfo.comments.stringLiterals
        comments.size should be(1)
        comments.sorted should be(Seq(StringLiteralV2.from(value = comment, language = Some("en"))))

        // check position
        val position = childNodeInfo.position
        position should be(0)

        // check has root node
        val rootNode = childNodeInfo.hasRootNode
        rootNode should be(newListIri.get)
        firstChildIri.set(childNodeInfo.id)
      }

      "add second child to list - to the root node" in {
        val encodedListUrl = java.net.URLEncoder.encode(newListIri.get, "utf-8")

        val name    = "second"
        val label   = "New Second Child List Node Value"
        val comment = "New Second Child List Node Comment"

        val addSecondChildToRoot = addChildListNodeRequest(
          parentNodeIri = newListIri.get,
          name = name,
          label = label,
          comment = comment,
        )

        val request = Post(
          baseApiUrl + s"/admin/lists/" + encodedListUrl,
          HttpEntity(ContentTypes.`application/json`, addSecondChildToRoot),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val received: ListNodeInfoADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListNodeInfoADM]

        // check correct node info
        val childNodeInfo = received match {
          case info: ListChildNodeInfoADM => info
          case something                  => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
        }

        // check labels
        val labels: Seq[StringLiteralV2] = childNodeInfo.labels.stringLiterals
        labels.size should be(1)
        labels.sorted should be(Seq(StringLiteralV2.from(value = label, language = Some("en"))))

        // check comments
        val comments = childNodeInfo.comments.stringLiterals
        comments.size should be(1)
        comments.sorted should be(Seq(StringLiteralV2.from(value = comment, language = Some("en"))))

        // check position
        val position = childNodeInfo.position
        position should be(1)

        // check has root node
        val rootNode = childNodeInfo.hasRootNode
        rootNode should be(newListIri.get)

        secondChildIri.set(childNodeInfo.id)
      }

      "insert new child in a specific position" in {
        val encodedListUrl = java.net.URLEncoder.encode(newListIri.get, "utf-8")

        val name    = "child with position"
        val label   = "Inserted List Node Label"
        val comment = "Inserted List Node Comment"

        val insertChild =
          s"""{
             |    "parentNodeIri": "${newListIri.get}",
             |    "projectIri": "${SharedTestDataADM.anythingProjectIri}",
             |    "name": "$name",
             |    "position": 1,
             |    "labels": [{ "value": "$label", "language": "en"}],
             |    "comments": [{ "value": "$comment", "language": "en"}]
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/lists/" + encodedListUrl,
          HttpEntity(ContentTypes.`application/json`, insertChild),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val received: ListNodeInfoADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListNodeInfoADM]

        // check correct node info
        val childNodeInfo = received match {
          case info: ListChildNodeInfoADM => info
          case something                  => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
        }

        // check labels
        val labels: Seq[StringLiteralV2] = childNodeInfo.labels.stringLiterals
        labels.size should be(1)
        labels.sorted should be(Seq(StringLiteralV2.from(value = label, language = Some("en"))))

        // check comments
        val comments = childNodeInfo.comments.stringLiterals
        comments.size should be(1)
        comments.sorted should be(Seq(StringLiteralV2.from(value = comment, language = Some("en"))))

        // check position
        val position = childNodeInfo.position
        position should be(1)

        // check has root node
        val rootNode = childNodeInfo.hasRootNode
        rootNode should be(newListIri.get)

        secondChildIri.set(childNodeInfo.id)
      }

      "add child to second child node" in {
        val encodedListUrl = java.net.URLEncoder.encode(secondChildIri.get, "utf-8")

        val name    = "third"
        val label   = "New Third Child List Node Value"
        val comment = "New Third Child List Node Comment"

        val addChildToSecondChild = addChildListNodeRequest(
          parentNodeIri = secondChildIri.get,
          name = name,
          label = label,
          comment = comment,
        )

        val request = Post(
          baseApiUrl + s"/admin/lists/" + encodedListUrl,
          HttpEntity(ContentTypes.`application/json`, addChildToSecondChild),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val received: ListNodeInfoADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListNodeInfoADM]

        // check correct node info
        val childNodeInfo = received match {
          case info: ListChildNodeInfoADM => info
          case something                  => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
        }

        // check labels
        val labels: Seq[StringLiteralV2] = childNodeInfo.labels.stringLiterals
        labels.size should be(1)
        labels.sorted should be(Seq(StringLiteralV2.from(value = label, language = Some("en"))))

        // check comments
        val comments = childNodeInfo.comments.stringLiterals
        comments.size should be(1)
        comments.sorted should be(Seq(StringLiteralV2.from(value = comment, language = Some("en"))))

        // check position
        val position = childNodeInfo.position
        position should be(0)

        // check has root node
        val rootNode = childNodeInfo.hasRootNode
        rootNode should be(newListIri.get)

        thirdChildIri.set(childNodeInfo.id)
      }
    }
  }
}
