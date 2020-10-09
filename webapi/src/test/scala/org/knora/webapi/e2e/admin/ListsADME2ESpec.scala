/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.e2e.admin

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.e2e.{ClientTestDataCollector, TestDataFileContent, TestDataFilePath}
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, StringLiteralV2, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.routing.authenticationmessages.CredentialsADM
import org.knora.webapi.sharedtestdata.{SharedListsTestDataADM, SharedTestDataADM}
import org.knora.webapi.util.{AkkaHttpUtils, MutableTestIri}
import org.knora.webapi.{E2ESpec, IRI}

import scala.concurrent.Await
import scala.concurrent.duration._

object ListsADME2ESpec {
    val config: Config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing users endpoint.
  */
class ListsADME2ESpec extends E2ESpec(ListsADME2ESpec.config) with SessionJsonProtocol with TriplestoreJsonProtocol with ListADMJsonProtocol {

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(5.seconds)

    // Directory path for generated client test data
    private val clientTestDataPath: Seq[String] = Seq("admin", "lists")

    // Collects client test data
    private val clientTestDataCollector = new ClientTestDataCollector(settings)

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    val rootCreds: CredentialsADM = CredentialsADM(
        SharedTestDataADM.rootUser,
        "test"
    )

    val normalUserCreds: CredentialsADM = CredentialsADM(
        SharedTestDataADM.normalUser,
        "test"
    )

    val anythingUserCreds: CredentialsADM = CredentialsADM(
        SharedTestDataADM.anythingUser1,
        "test"
    )

    val anythingAdminUserCreds: CredentialsADM = CredentialsADM(
        SharedTestDataADM.anythingAdminUser,
        "test"
    )

    private val treeListInfo: ListRootNodeInfoADM = SharedListsTestDataADM.treeListInfo
    private val treeListNodes: Seq[ListChildNodeADM] = SharedListsTestDataADM.treeListChildNodes

    def addChildListNodeRequest(parentNodeIri: IRI,
                                name: String,
                                label: String,
                                comment: String): String = {
        s"""{
           |    "parentNodeIri": "$parentNodeIri",
           |    "projectIri": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
           |    "name": "$name",
           |    "labels": [{ "value": "$label", "language": "en"}],
           |    "comments": [{ "value": "$comment", "language": "en"}]
           |}""".stripMargin
    }

    "The Lists Route (/admin/lists)" when {

        "used to query information about lists" should {

            "return all lists" in {
                val request = Get(baseApiUrl + s"/admin/lists") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)

                // println(s"response: ${response.toString}")

                response.status should be(StatusCodes.OK)

                val lists: Seq[ListNodeInfoADM] = AkkaHttpUtils.httpResponseToJson(response).fields("lists").convertTo[Seq[ListNodeInfoADM]]

                // log.debug("lists: {}", lists)

                lists.size should be (7)
                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "get-lists-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "return all lists belonging to the images project" in {
                val request = Get(baseApiUrl + s"/admin/lists?projectIri=http%3A%2F%2Frdfh.ch%2Fprojects%2F00FF") ~> addCredentials(rootCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")

                response.status should be(StatusCodes.OK)

                val lists: Seq[ListNodeInfoADM] = AkkaHttpUtils.httpResponseToJson(response).fields("lists").convertTo[Seq[ListNodeInfoADM]]

                // log.debug("lists: {}", lists)

                lists.size should be (4)

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "get-image-project-lists-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "return all lists belonging to the anything project" in {
                val request = Get(baseApiUrl + s"/admin/lists?projectIri=http%3A%2F%2Frdfh.ch%2Fprojects%2F0001") ~> addCredentials(rootCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")

                response.status should be(StatusCodes.OK)

                val lists: Seq[ListNodeInfoADM] = AkkaHttpUtils.httpResponseToJson(response).fields("lists").convertTo[Seq[ListNodeInfoADM]]

                // log.debug("lists: {}", lists)

                lists.size should be (2)

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "get-anything-project-lists-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "return basic list information" in {
                val request = Get(baseApiUrl + s"/admin/lists/infos/http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FtreeList") ~> addCredentials(rootCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")

                response.status should be(StatusCodes.OK)

                val receivedListInfo: ListRootNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

                val expectedListInfo: ListRootNodeInfoADM = SharedListsTestDataADM.treeListInfo

                receivedListInfo.sorted should be (expectedListInfo.sorted)

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "get-list-info-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "return a complete list" in {
                val request = Get(baseApiUrl + s"/admin/lists/http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FtreeList") ~> addCredentials(rootCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // println(s"response: ${response.toString}")

                response.status should be(StatusCodes.OK)

                val receivedList: ListADM = AkkaHttpUtils.httpResponseToJson(response).fields("list").convertTo[ListADM]
                receivedList.listinfo.sorted should be (treeListInfo.sorted)
                receivedList.children.map(_.sorted) should be (treeListNodes.map(_.sorted))

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "get-list-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "return node info without children" in {
                val request = Get(baseApiUrl + s"/admin/lists/nodes/http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FtreeList01") ~> addCredentials(rootCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")

                response.status should be(StatusCodes.OK)

                val receivedListInfo: ListChildNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListChildNodeInfoADM]

                val expectedListInfo: ListChildNodeInfoADM = SharedListsTestDataADM.treeListNode01Info

                receivedListInfo.sorted should be (expectedListInfo.sorted)

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "get-list-node-info-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }
        }

        "given a custom Iri" should {

            "create a list with the provided custom Iri" in {
                val createListWithCustomIriRequest: String =
                    s"""{
                       |    "id": "${SharedTestDataADM.customListIRI}",
                       |    "projectIri": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
                       |    "labels": [{ "value": "New list with a custom IRI", "language": "en"}],
                       |    "comments": []
                       |}""".stripMargin

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "create-list-with-custom-IRI-request",
                            fileExtension = "json"
                        ),
                        text = createListWithCustomIriRequest
                    )
                )
                val request = Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, createListWithCustomIriRequest)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                response.status should be(StatusCodes.OK)

                val receivedList: ListADM = AkkaHttpUtils.httpResponseToJson(response).fields("list").convertTo[ListADM]

                val listInfo = receivedList.listinfo
                listInfo.id should be (SharedTestDataADM.customListIRI)

                val labels: Seq[StringLiteralV2] = listInfo.labels.stringLiterals
                labels.size should be (1)
                labels.head should be (StringLiteralV2(value = "New list with a custom IRI", language = Some("en")))

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "create-list-with-custom-IRI-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "return a DuplicateValueException during list creation when the supplied list IRI is not unique" in {

                // duplicate list IRI
                val params =
                    s"""
                       |{
                       |    "id": "${SharedTestDataADM.customListIRI}",
                       |    "projectIri": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
                       |    "labels": [{ "value": "New List", "language": "en"}],
                       |    "comments": []
                       |}
                """.stripMargin

                val request = Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, params))  ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                response.status should be(StatusCodes.BadRequest)

                val errorMessage : String = Await.result(Unmarshal(response.entity).to[String], 1.second)
                val invalidIri: Boolean = errorMessage.contains(s"IRI: '${SharedTestDataADM.customListIRI}' already exists, try another one.")
                invalidIri should be(true)
            }
        }

        "used to modify list information" should {

            val newListIri = new MutableTestIri
            val firstChildIri = new MutableTestIri
            val secondChildIri = new MutableTestIri
            val thirdChildIri = new MutableTestIri

            "create a list" in {
                val createListRequest: String =
                    s"""{
                       |    "projectIri": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
                       |    "labels": [{ "value": "Neue Liste", "language": "de"}],
                       |    "comments": []
                       |}""".stripMargin

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "create-list-request",
                            fileExtension = "json"
                        ),
                        text = createListRequest
                    )
                )
                val request = Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, createListRequest)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val receivedList: ListADM = AkkaHttpUtils.httpResponseToJson(response).fields("list").convertTo[ListADM]

                val listInfo = receivedList.listinfo
                listInfo.projectIri should be (SharedTestDataADM.ANYTHING_PROJECT_IRI)

                val labels: Seq[StringLiteralV2] = listInfo.labels.stringLiterals
                labels.size should be (1)
                labels.head should be (StringLiteralV2(value = "Neue Liste", language = Some("de")))

                val comments = receivedList.listinfo.comments.stringLiterals
                comments.isEmpty should be (true)

                val children = receivedList.children
                children.size should be (0)

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "create-list-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
                // store list IRI for next test
                newListIri.set(listInfo.id)
            }

            "return a ForbiddenException if the user creating the list is not project or system admin" in {
                val params =
                    s"""
                       |{
                       |    "projectIri": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
                       |    "labels": [{ "value": "Neue Liste", "language": "de"}],
                       |    "comments": []
                       |}
                """.stripMargin

                val request = Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(anythingUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.Forbidden)
            }

            "return a BadRequestException during list creation when payload is not correct" in {

                // no project IRI
                val params01 =
                    s"""
                       |{
                       |    "projectIri": "",
                       |    "labels": [{ "value": "Neue Liste", "language": "de"}],
                       |    "comments": []
                       |}
                """.stripMargin

                val request01 = Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, params01))
                val response01: HttpResponse = singleAwaitingRequest(request01)
                // println(s"response: ${response01.toString}")
                response01.status should be(StatusCodes.BadRequest)


                // invalid project IRI
                val params02 =
                    s"""
                       |{
                       |    "projectIri": "notvalidIRI",
                       |    "labels": [{ "value": "Neue Liste", "language": "de"}],
                       |    "comments": []
                       |}
                """.stripMargin

                val request02 = Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, params02))
                val response02: HttpResponse = singleAwaitingRequest(request02)
                // println(s"response: ${response02.toString}")
                response02.status should be(StatusCodes.BadRequest)


                // missing label
                val params03 =
                    s"""
                       |{
                       |    "projectIri": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
                       |    "labels": [],
                       |    "comments": []
                       |}
                """.stripMargin

                val request03 = Post(baseApiUrl + s"/admin/lists", HttpEntity(ContentTypes.`application/json`, params03))
                val response03: HttpResponse = singleAwaitingRequest(request03)
                // println(s"response: ${response03.toString}")
                response03.status should be(StatusCodes.BadRequest)

            }

            "update basic list information" in {

                val updateListInfo: String =
                    s"""{
                       |    "listIri": "${newListIri.get}",
                       |    "projectIri": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
                       |    "labels": [{ "value": "Neue geänderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
                       |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
                       |}""".stripMargin

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-list-info-request",
                            fileExtension = "json"
                        ),
                        text = updateListInfo
                    )
                )
                val encodedListUrl = java.net.URLEncoder.encode(newListIri.get, "utf-8")

                val request = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl, HttpEntity(ContentTypes.`application/json`, updateListInfo)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val receivedListInfo: ListRootNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

                receivedListInfo.projectIri should be (SharedTestDataADM.ANYTHING_PROJECT_IRI)

                val labels: Seq[StringLiteralV2] = receivedListInfo.labels.stringLiterals
                labels.size should be (2)

                val comments = receivedListInfo.comments.stringLiterals
                comments.size should be (2)

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-list-info-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "update basic list information with a new name" in {
                val updateListName =
                    s"""{
                       |    "listIri": "${newListIri.get}",
                       |    "projectIri": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
                       |    "name": "a totally new name"
                       |}""".stripMargin
                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-list-name-request",
                            fileExtension = "json"
                        ),
                        text = updateListName
                    )
                )
                val encodedListUrl = java.net.URLEncoder.encode(newListIri.get, "utf-8")

                val request = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl, HttpEntity(ContentTypes.`application/json`, updateListName)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val receivedListInfo: ListRootNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

                receivedListInfo.projectIri should be (SharedTestDataADM.ANYTHING_PROJECT_IRI)

                receivedListInfo.name should be (Some("a totally new name"))

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-list-name-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "update basic list information with repeated comment and label in different languages" in {

                val updateListInfoWithRepeatedCommentAndLabelValuesRequest: String =
                    s"""{
                       |    "listIri": "http://rdfh.ch/lists/0001/treeList",
                       |    "projectIri": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
                       |  "labels": [
                       |    {"language": "en", "value": "Test List"},
                       |    {"language": "se", "value": "Test List"}
                       |  ],
                       |  "comments": [
                       |    {"language": "en", "value": "test"},
                       |    {"language": "de", "value": "test"},
                       |    {"language": "fr", "value": "test"},
                       |     {"language": "it", "value": "test"}
                       |  ]
                       |}""".stripMargin

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-list-info-comment-label-multiple-languages-request",
                            fileExtension = "json"
                        ),
                        text = updateListInfoWithRepeatedCommentAndLabelValuesRequest
                    )
                )

                val encodedListUrl = java.net.URLEncoder.encode("http://rdfh.ch/lists/0001/treeList", "utf-8")

                val request = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl, HttpEntity(ContentTypes.`application/json`, updateListInfoWithRepeatedCommentAndLabelValuesRequest)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val receivedListInfo: ListRootNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

                receivedListInfo.projectIri should be (SharedTestDataADM.ANYTHING_PROJECT_IRI)

                val labels: Seq[StringLiteralV2] = receivedListInfo.labels.stringLiterals
                labels.size should be (2)

                val comments = receivedListInfo.comments.stringLiterals
                comments.size should be (4)

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-list-info-comment-label-multiple-languages-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "return a ForbiddenException if the user updating the list is not project or system admin" in {
                val params =
                    s"""
                       |{
                       |    "listIri": "${newListIri.get}",
                       |    "projectIri": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
                       |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
                       |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
                       |}
                """.stripMargin

                val encodedListUrl = java.net.URLEncoder.encode(newListIri.get, "utf-8")

                val request = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl, HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(anythingUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.Forbidden)
            }

            "return a BadRequestException during list change when payload is not correct" in {

                val encodedListUrl = java.net.URLEncoder.encode(newListIri.get, "utf-8")

                // empty list IRI
                val params01 =
                    s"""
                       |{
                       |    "listIri": "",
                       |    "projectIri": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
                       |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
                       |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
                       |}
                """.stripMargin

                val request01 = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl, HttpEntity(ContentTypes.`application/json`, params01)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response01: HttpResponse = singleAwaitingRequest(request01)
                // log.debug(s"response: ${response.toString}")
                response01.status should be(StatusCodes.BadRequest)

                // empty project
                val params02 =
                s"""
                   |{
                   |    "listIri": "${newListIri.get}",
                   |    "projectIri": "",
                   |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
                   |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
                   |}
                """.stripMargin

                val request02 = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl, HttpEntity(ContentTypes.`application/json`, params02)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response02: HttpResponse = singleAwaitingRequest(request02)
                // log.debug(s"response: ${response.toString}")
                response02.status should be(StatusCodes.BadRequest)

                // empty parameters
                val params03 =
                    s"""
                       |{
                       |    "listIri": "${newListIri.get}",
                       |    "projectIri": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
                       |    "labels": [],
                       |    "comments": []
                       |}
                """.stripMargin

                val request03 = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl, HttpEntity(ContentTypes.`application/json`, params03)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response03: HttpResponse = singleAwaitingRequest(request03)
                // log.debug(s"response: ${response.toString}")
                response03.status should be(StatusCodes.BadRequest)

            }

            "add child to list - to the root node" in {

                val encodedListUrl = java.net.URLEncoder.encode(newListIri.get, "utf-8")

                val name = "first"
                val label = "New First Child List Node Value"
                val comment = "New First Child List Node Comment"

                val addChildToRoot = addChildListNodeRequest(
                    parentNodeIri = newListIri.get,
                    name = name,
                    label = label,
                    comment = comment
                )

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "create-child-node-request",
                            fileExtension = "json"
                        ),
                        text = addChildToRoot
                    )
                )

                val request = Post(baseApiUrl + s"/admin/lists/" + encodedListUrl, HttpEntity(ContentTypes.`application/json`, addChildToRoot)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // println(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val received: ListNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListNodeInfoADM]

                // check correct node info
                val childNodeInfo = received match {
                    case info: ListChildNodeInfoADM => info
                    case something => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
                }

                // check labels
                val labels: Seq[StringLiteralV2] = childNodeInfo.labels.stringLiterals
                labels.size should be (1)
                labels.sorted should be (Seq(StringLiteralV2(value = label, language = Some("en"))))

                // check comments
                val comments = childNodeInfo.comments.stringLiterals
                comments.size should be (1)
                comments.sorted should be (Seq(StringLiteralV2(value = comment, language = Some("en"))))

                // check position
                val position = childNodeInfo.position
                position should be (0)

                // check has root node
                val rootNode = childNodeInfo.hasRootNode
                rootNode should be (newListIri.get)
                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "create-child-node-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
                firstChildIri.set(childNodeInfo.id)
            }

            "add second child to list - to the root node" in {

                val encodedListUrl = java.net.URLEncoder.encode(newListIri.get, "utf-8")

                val name = "second"
                val label = "New Second Child List Node Value"
                val comment = "New Second Child List Node Comment"

                val addSecondChildToRoot = addChildListNodeRequest(
                    parentNodeIri = newListIri.get,
                    name = name,
                    label = label,
                    comment = comment
                )
                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "add-second-child-to-root-request",
                            fileExtension = "json"
                        ),
                        text = addSecondChildToRoot
                    )
                )
                val request = Post(baseApiUrl + s"/admin/lists/" + encodedListUrl, HttpEntity(ContentTypes.`application/json`, addSecondChildToRoot)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // println(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val received: ListNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListNodeInfoADM]

                // check correct node info
                val childNodeInfo = received match {
                    case info: ListChildNodeInfoADM => info
                    case something => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
                }

                // check labels
                val labels: Seq[StringLiteralV2] = childNodeInfo.labels.stringLiterals
                labels.size should be (1)
                labels.sorted should be (Seq(StringLiteralV2(value = label, language = Some("en"))))

                // check comments
                val comments = childNodeInfo.comments.stringLiterals
                comments.size should be (1)
                comments.sorted should be (Seq(StringLiteralV2(value = comment, language = Some("en"))))

                // check position
                val position = childNodeInfo.position
                position should be (1)

                // check has root node
                val rootNode = childNodeInfo.hasRootNode
                rootNode should be (newListIri.get)

                secondChildIri.set(childNodeInfo.id)

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "add-second-child-to-root-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "add child to second child node" in {

                val encodedListUrl = java.net.URLEncoder.encode(secondChildIri.get, "utf-8")

                val name = "third"
                val label = "New Third Child List Node Value"
                val comment = "New Third Child List Node Comment"

                val addChildToSecondChild = addChildListNodeRequest(
                    parentNodeIri = secondChildIri.get,
                    name = name,
                    label = label,
                    comment = comment
                )
                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "add-second-child-to-root-request",
                            fileExtension = "json"
                        ),
                        text = addChildToSecondChild
                    )
                )
                val request = Post(baseApiUrl + s"/admin/lists/" + encodedListUrl, HttpEntity(ContentTypes.`application/json`, addChildToSecondChild)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // println(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val received: ListNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListNodeInfoADM]

                // check correct node info
                val childNodeInfo = received match {
                    case info: ListChildNodeInfoADM => info
                    case something => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
                }

                // check labels
                val labels: Seq[StringLiteralV2] = childNodeInfo.labels.stringLiterals
                labels.size should be (1)
                labels.sorted should be (Seq(StringLiteralV2(value = label, language = Some("en"))))

                // check comments
                val comments = childNodeInfo.comments.stringLiterals
                comments.size should be (1)
                comments.sorted should be (Seq(StringLiteralV2(value = comment, language = Some("en"))))

                // check position
                val position = childNodeInfo.position
                position should be (0)

                // check has root node
                val rootNode = childNodeInfo.hasRootNode
                rootNode should be (newListIri.get)

                thirdChildIri.set(childNodeInfo.id)

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "add-second-child-to-root-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "add flat nodes" ignore {

            }

            "add hierarchical nodes" ignore {

            }

            "change node order" ignore {

            }

            "delete node if not in use" ignore {

            }
        }
    }
}
