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

package org.knora.webapi.e2e.admin.lists

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, RawHeader}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.{E2ESpec, IRI}
import org.knora.webapi.e2e.{ClientTestDataCollector, TestDataFileContent, TestDataFilePath}
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, StringLiteralV2, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.routing.authenticationmessages.CredentialsADM
import org.knora.webapi.sharedtestdata.{SharedListsTestDataADM, SharedTestDataADM}
import org.knora.webapi.util.AkkaHttpUtils

import scala.concurrent.duration._

object UpdateListItemsRouteADME2ESpec {
    val config: Config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * End-to-End (E2E) test specification for testing update node props routes.
 */
class UpdateListItemsRouteADME2ESpec extends E2ESpec(UpdateListItemsRouteADME2ESpec.config) with SessionJsonProtocol with TriplestoreJsonProtocol with ListADMJsonProtocol {

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
    private val treeChildNode = treeListNodes.head

    "The List Items Route (/admin/lists)" when {
        "update list root" should {
            "update only node name" in {
                val updateNodeName =
                    s"""{
                       |    "name": "updated root node name"
                       |}""".stripMargin
                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-rootNode-name-request",
                            fileExtension = "json"
                        ),
                        text = updateNodeName
                    )
                )
                val encodedListUrl = java.net.URLEncoder.encode(treeListInfo.id, "utf-8")

                val request = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/name", HttpEntity(ContentTypes.`application/json`, updateNodeName)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)
                val receivedListInfo: ListRootNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

                receivedListInfo.projectIri should be(SharedTestDataADM.ANYTHING_PROJECT_IRI)

                receivedListInfo.name should be(Some("updated root node name"))

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-rootNode-name-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "update only node labels" in {
                val updateNodeLabels =
                    s"""{
                       |    "labels": [{"language": "se", "value": "nya märkningen"}]
                       |}""".stripMargin
                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-rootNode-labels-request",
                            fileExtension = "json"
                        ),
                        text = updateNodeLabels
                    )
                )
                val encodedListUrl = java.net.URLEncoder.encode(treeListInfo.id, "utf-8")

                val request = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/labels", HttpEntity(ContentTypes.`application/json`, updateNodeLabels)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)
                val receivedListInfo: ListRootNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

                receivedListInfo.projectIri should be(SharedTestDataADM.ANYTHING_PROJECT_IRI)

                val labels: Seq[StringLiteralV2] = receivedListInfo.labels.stringLiterals
                labels.size should be(1)
                labels should contain(StringLiteralV2(value = "nya märkningen", language = Some("se")))

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-rootNode-labels-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "update node comments" in {
                val updateCommentsLabels =
                    s"""{
                       |    "comments": [{"language": "se", "value": "nya kommentarer"}]
                       |}""".stripMargin
                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-rootNode-comments-request",
                            fileExtension = "json"
                        ),
                        text = updateCommentsLabels
                    )
                )
                val encodedListUrl = java.net.URLEncoder.encode(treeListInfo.id, "utf-8")

                val request = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/comments", HttpEntity(ContentTypes.`application/json`, updateCommentsLabels)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)
                val receivedListInfo: ListRootNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

                receivedListInfo.projectIri should be(SharedTestDataADM.ANYTHING_PROJECT_IRI)

                val comments: Seq[StringLiteralV2] = receivedListInfo.comments.stringLiterals
                comments.size should be(1)
                comments should contain(StringLiteralV2(value = "nya kommentarer", language = Some("se")))

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-rootNode-comments-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }
        }

        "updating child nodes" should {
            "update only the name of the child node" in {
                val newName = "updated third child name"
                val updateNodeName =
                    s"""{
                       |    "name": "$newName"
                       |}""".stripMargin

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-childNode-name-request",
                            fileExtension = "json"
                        ),
                        text = updateNodeName
                    )
                )

                val encodedListUrl = java.net.URLEncoder.encode(treeChildNode.id, "utf-8")

                val request = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/name", HttpEntity(ContentTypes.`application/json`, updateNodeName)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)

                response.status should be(StatusCodes.OK)

                val receivedNodeInfo: ListChildNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListChildNodeInfoADM]
                receivedNodeInfo.name.get should be(newName)

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-childNode-name-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "update only the labels of the child node" in {
                val updateNodeLabels =
                    s"""{
                       |    "labels": [{"language": "se", "value": "nya märkningen för nod"}]
                       |}""".stripMargin

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-childNode-labels-request",
                            fileExtension = "json"
                        ),
                        text = updateNodeLabels
                    )
                )

                val encodedListUrl = java.net.URLEncoder.encode(treeChildNode.id, "utf-8")

                val request = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/labels", HttpEntity(ContentTypes.`application/json`, updateNodeLabels)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)

                response.status should be(StatusCodes.OK)

                val receivedNodeInfo: ListChildNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListChildNodeInfoADM]
                val labels: Seq[StringLiteralV2] = receivedNodeInfo.labels.stringLiterals
                labels.size should be(1)
                labels should contain(StringLiteralV2(value = "nya märkningen för nod", language = Some("se")))


                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-childNode-labels-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "update only comments of the child node" in {
                val updateNodeComments =
                    s"""{
                       |    "comments": [{"language": "se", "value": "nya kommentarer för nod"}]
                       |}""".stripMargin

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-childNode-comments-request",
                            fileExtension = "json"
                        ),
                        text = updateNodeComments
                    )
                )

                val encodedListUrl = java.net.URLEncoder.encode(treeChildNode.id, "utf-8")

                val request = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/comments", HttpEntity(ContentTypes.`application/json`, updateNodeComments)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)

                response.status should be(StatusCodes.OK)

                val receivedNodeInfo: ListChildNodeInfoADM = AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListChildNodeInfoADM]
                val comments: Seq[StringLiteralV2] = receivedNodeInfo.comments.stringLiterals
                comments.size should be(1)
                comments should contain(StringLiteralV2(value = "nya kommentarer för nod", language = Some("se")))


                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-childNode-comments-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "not update the position of a node if given IRI is invalid" in {
                val parentIri = "http://rdfh.ch/lists/0001/notUsedList01"
                val newPosition = 1
                val nodeIri = "invalid-iri"
                val updateNodeName =
                    s"""{
                       |    "parentNodeIri": "$parentIri",
                       |    "position": $newPosition
                       |}""".stripMargin

                val encodedListUrl = java.net.URLEncoder.encode(nodeIri, "utf-8")

                val request = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/position", HttpEntity(ContentTypes.`application/json`, updateNodeName)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)

                response.status should be(StatusCodes.BadRequest)
            }

            "update only the position of the child node within same parent" in {
                val parentIri = "http://rdfh.ch/lists/0001/notUsedList01"
                val newPosition = 1
                val nodeIri = "http://rdfh.ch/lists/0001/notUsedList014"
                val updateNodeName =
                    s"""{
                       |    "parentNodeIri": "$parentIri",
                       |    "position": $newPosition
                       |}""".stripMargin

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-childNode-position-request",
                            fileExtension = "json"
                        ),
                        text = updateNodeName
                    )
                )

                val encodedListUrl = java.net.URLEncoder.encode(nodeIri, "utf-8")

                val request = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/position", HttpEntity(ContentTypes.`application/json`, updateNodeName)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)

                response.status should be(StatusCodes.OK)

                val receivedNode: ListNodeADM = AkkaHttpUtils.httpResponseToJson(response).fields("node").convertTo[ListNodeADM]
                receivedNode.getNodeId should be(parentIri)

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-childNode-position-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "reposition child node to the end of its parent's children" in {
                val parentIri = "http://rdfh.ch/lists/0001/notUsedList01"
                val newPosition = -1
                val nodeIri = "http://rdfh.ch/lists/0001/notUsedList012"
                val updateNodeName =
                    s"""{
                       |    "parentNodeIri": "$parentIri",
                       |    "position": $newPosition
                       |}""".stripMargin

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-childNode-position-to-end-request",
                            fileExtension = "json"
                        ),
                        text = updateNodeName
                    )
                )

                val encodedListUrl = java.net.URLEncoder.encode(nodeIri, "utf-8")

                val request = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/position", HttpEntity(ContentTypes.`application/json`, updateNodeName)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)

                response.status should be(StatusCodes.OK)

                val receivedNode: ListNodeADM = AkkaHttpUtils.httpResponseToJson(response).fields("node").convertTo[ListNodeADM]
                receivedNode.getNodeId should be(parentIri)

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-childNode-position-to-end-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "update parent and position of the child node" in {
                val parentIri = "http://rdfh.ch/lists/0001/notUsedList"
                val newPosition = 2
                val nodeIri = "http://rdfh.ch/lists/0001/notUsedList015"
                val updateNodeName =
                    s"""{
                       |    "parentNodeIri": "$parentIri",
                       |    "position": $newPosition
                       |}""".stripMargin

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-childNode-position-new-parent-request",
                            fileExtension = "json"
                        ),
                        text = updateNodeName
                    )
                )

                val encodedListUrl = java.net.URLEncoder.encode(nodeIri, "utf-8")

                val request = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/position", HttpEntity(ContentTypes.`application/json`, updateNodeName)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)

                response.status should be(StatusCodes.OK)

                val receivedNode: ListNodeADM = AkkaHttpUtils.httpResponseToJson(response).fields("node").convertTo[ListNodeADM]
                receivedNode.getNodeId should be(parentIri)

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-childNode-position-new-parent-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }

            "reposition child node to end of another parent's children" in {
                val parentIri = "http://rdfh.ch/lists/0001/notUsedList"
                val newPosition = -1
                val nodeIri = "http://rdfh.ch/lists/0001/notUsedList015"
                val updateNodeName =
                    s"""{
                       |    "parentNodeIri": "$parentIri",
                       |    "position": $newPosition
                       |}""".stripMargin

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-childNode-position-new-parent-to-end-request",
                            fileExtension = "json"
                        ),
                        text = updateNodeName
                    )
                )

                val encodedListUrl = java.net.URLEncoder.encode(nodeIri, "utf-8")

                val request = Put(baseApiUrl + s"/admin/lists/" + encodedListUrl + "/position", HttpEntity(ContentTypes.`application/json`, updateNodeName)) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
                val response: HttpResponse = singleAwaitingRequest(request)

                response.status should be(StatusCodes.OK)

                val receivedNode: ListNodeADM = AkkaHttpUtils.httpResponseToJson(response).fields("node").convertTo[ListNodeADM]
                receivedNode.getNodeId should be(parentIri)

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "update-childNode-position-new-parent-to-end-response",
                            fileExtension = "json"
                        ),
                        text = responseToString(response)
                    )
                )
            }
        }
    }
}
