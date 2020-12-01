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

package org.knora.webapi.responders.admin

import java.util.UUID

import akka.actor.Status.Failure
import akka.testkit._
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.sharedtestdata.SharedTestDataV1._
import org.knora.webapi._
import org.knora.webapi.exceptions.{BadRequestException, DuplicateValueException}
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, StringLiteralV2}
import org.knora.webapi.sharedtestdata.{SharedListsTestDataADM, SharedTestDataADM}
import org.knora.webapi.util.MutableTestIri

import scala.concurrent.duration._

/**
 * Static data for testing [[ListsResponderADM]].
 */
object ListsResponderADMSpec {
    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * Tests [[ListsResponderADM]].
 */
class ListsResponderADMSpec extends CoreSpec(ListsResponderADMSpec.config) with ImplicitSender {

    // The default timeout for receiving reply messages from actors.
    implicit private val timeout = 5.seconds

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    private val treeListInfo: ListRootNodeInfoADM = SharedListsTestDataADM.treeListInfo

    private val summerNodeInfo: ListNodeInfoADM = SharedListsTestDataADM.summerNodeInfo

    private val otherTreeListInfo: ListRootNodeInfoADM = SharedListsTestDataADM.otherTreeListInfo

    private val treeListChildNodes: Seq[ListNodeADM] = SharedListsTestDataADM.treeListChildNodes

    "The Lists Responder" when {

        "used to query information about lists" should {

            "return all lists" in {
                responderManager ! ListsGetRequestADM(
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.imagesUser01
                )

                val received: ListsGetResponseADM = expectMsgType[ListsGetResponseADM](timeout)

                received.lists.size should be(8)
            }

            "return all lists belonging to the images project" in {
                responderManager ! ListsGetRequestADM(
                    projectIri = Some(IMAGES_PROJECT_IRI),
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.imagesUser01
                )

                val received: ListsGetResponseADM = expectMsgType[ListsGetResponseADM](timeout)

                // log.debug("received: " + received)

                received.lists.size should be(4)
            }

            "return all lists belonging to the anything project" in {
                responderManager ! ListsGetRequestADM(
                    projectIri = Some(ANYTHING_PROJECT_IRI),
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.imagesUser01
                )

                val received: ListsGetResponseADM = expectMsgType[ListsGetResponseADM](timeout)

                // log.debug("received: " + received)

                received.lists.size should be(3)
            }

            "return basic list information (anything list)" in {
                responderManager ! ListNodeInfoGetRequestADM(
                    iri = "http://rdfh.ch/lists/0001/treeList",
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.anythingUser1
                )

                val received: RootNodeInfoGetResponseADM = expectMsgType[RootNodeInfoGetResponseADM](timeout)

                // log.debug("returned basic keyword list information: {}", MessageUtil.toSource(received.items.head))

                received.listinfo.sorted should be(treeListInfo.sorted)
            }

            "return basic list information (anything other list)" in {
                responderManager ! ListNodeInfoGetRequestADM(
                    iri = "http://rdfh.ch/lists/0001/otherTreeList",
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.anythingUser1
                )

                val received: RootNodeInfoGetResponseADM = expectMsgType[RootNodeInfoGetResponseADM](timeout)

                // log.debug("returned basic keyword list information: {}", MessageUtil.toSource(received.items.head))

                received.listinfo.sorted should be(otherTreeListInfo.sorted)
            }

            "return basic node information (images list - sommer)" in {
                responderManager ! ListNodeInfoGetRequestADM(
                    iri = "http://rdfh.ch/lists/00FF/526f26ed04",
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.imagesUser01
                )

                val received: ChildNodeInfoGetResponseADM = expectMsgType[ChildNodeInfoGetResponseADM](timeout)

                // log.debug("returned basic keyword list information: {}", MessageUtil.toSource(received.items.head))

                received.nodeinfo.sorted should be(summerNodeInfo.sorted)
            }

            "return a full list response" in {
                responderManager ! ListGetRequestADM(
                    iri = "http://rdfh.ch/lists/0001/treeList",
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.anythingUser1
                )

                val received: ListGetResponseADM = expectMsgType[ListGetResponseADM](timeout)

                // log.debug("returned whole keyword list: {}", MessageUtil.toSource(received.items.head))

                received.list.listinfo.sorted should be(treeListInfo.sorted)

                received.list.children.map(_.sorted) should be(treeListChildNodes.map(_.sorted))
            }
        }
        val newListIri = new MutableTestIri
        val firstChildIri = new MutableTestIri
        val secondChildIri = new MutableTestIri
        val thirdChildIri = new MutableTestIri

        "used to modify lists" should {
            "create a list" in {
                responderManager ! ListCreateRequestADM(
                    createRootNode = CreateNodeApiRequestADM(
                        projectIri = IMAGES_PROJECT_IRI,
                        name = Some("neuelistename"),
                        labels = Seq(StringLiteralV2(value = "Neue Liste", language = Some("de"))),
                        comments = Seq.empty[StringLiteralV2]
                    ),
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.imagesUser01,
                    apiRequestID = UUID.randomUUID
                )

                val received: ListGetResponseADM = expectMsgType[ListGetResponseADM](timeout)

                val listInfo = received.list.listinfo
                listInfo.projectIri should be(IMAGES_PROJECT_IRI)

                listInfo.name should be(Some("neuelistename"))

                val labels: Seq[StringLiteralV2] = listInfo.labels.stringLiterals
                labels.size should be(1)
                labels.head should be(StringLiteralV2(value = "Neue Liste", language = Some("de")))

                val comments = received.list.listinfo.comments.stringLiterals
                comments.isEmpty should be(true)

                val children = received.list.children
                children.size should be(0)

                // store list IRI for next test
                newListIri.set(listInfo.id)
            }

            "update basic list information" in {
                responderManager ! NodeInfoChangeRequestADM(
                    listIri = newListIri.get,
                    changeNodeRequest = ChangeNodeInfoApiRequestADM(
                        listIri = newListIri.get,
                        projectIri = IMAGES_PROJECT_IRI,
                        name = Some("updated name"),
                        labels = Some(Seq(
                            StringLiteralV2(value = "Neue geänderte Liste", language = Some("de")),
                            StringLiteralV2(value = "Changed list", language = Some("en"))
                        )),
                        comments = Some(Seq(
                            StringLiteralV2(value = "Neuer Kommentar", language = Some("de")),
                            StringLiteralV2(value = "New comment", language = Some("en"))
                        )
                        )),
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.imagesUser01,
                    apiRequestID = UUID.randomUUID
                )

                val received: RootNodeInfoGetResponseADM = expectMsgType[RootNodeInfoGetResponseADM](timeout)

                val listInfo = received.listinfo
                listInfo.projectIri should be(IMAGES_PROJECT_IRI)
                listInfo.name should be(Some("updated name"))
                val labels: Seq[StringLiteralV2] = listInfo.labels.stringLiterals
                labels.size should be(2)
                labels.sorted should be(Seq(
                    StringLiteralV2(value = "Neue geänderte Liste", language = Some("de")),
                    StringLiteralV2(value = "Changed list", language = Some("en"))
                ).sorted)

                val comments = listInfo.comments.stringLiterals
                comments.size should be(2)
                comments.sorted should be(Seq(
                    StringLiteralV2(value = "Neuer Kommentar", language = Some("de")),
                    StringLiteralV2(value = "New comment", language = Some("en"))
                ).sorted)
            }

            "not update basic list information if name is duplicate" in {
                responderManager ! NodeInfoChangeRequestADM(
                    listIri = newListIri.get,
                    changeNodeRequest = ChangeNodeInfoApiRequestADM(
                        listIri = newListIri.get,
                        projectIri = IMAGES_PROJECT_IRI,
                        name = Some("sommer")),
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.imagesUser01,
                    apiRequestID = UUID.randomUUID
                )
                expectMsg(Failure(DuplicateValueException("The name sommer is already used by a list inside the project http://rdfh.ch/projects/00FF.")))
            }

            "add child to list - to the root node" in {
                responderManager ! ListChildNodeCreateRequestADM(
                    createChildNodeRequest = CreateNodeApiRequestADM(
                        parentNodeIri = Some(newListIri.get),
                        projectIri = IMAGES_PROJECT_IRI,
                        name = Some("first"),
                        labels = Seq(StringLiteralV2(value = "New First Child List Node Value", language = Some("en"))),
                        comments = Seq(StringLiteralV2(value = "New First Child List Node Comment", language = Some("en")))
                    ),
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.imagesUser01,
                    apiRequestID = UUID.randomUUID
                )

                val received: ChildNodeInfoGetResponseADM = expectMsgType[ChildNodeInfoGetResponseADM](timeout)
                val nodeInfo = received.nodeinfo

                // check correct node info
                val childNodeInfo = nodeInfo match {
                    case info: ListChildNodeInfoADM => info
                    case something => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
                }

                // check labels
                val labels: Seq[StringLiteralV2] = childNodeInfo.labels.stringLiterals
                labels.size should be(1)
                labels.sorted should be(Seq(StringLiteralV2(value = "New First Child List Node Value", language = Some("en"))))

                // check comments
                val comments = childNodeInfo.comments.stringLiterals
                comments.size should be(1)
                comments.sorted should be(Seq(StringLiteralV2(value = "New First Child List Node Comment", language = Some("en"))))

                // check position
                val position = childNodeInfo.position
                position should be(0)

                // check has root node
                val rootNode = childNodeInfo.hasRootNode
                rootNode should be(newListIri.get)

                firstChildIri.set(childNodeInfo.id)
            }

            "add second child to list - to the root node" in {
                responderManager ! ListChildNodeCreateRequestADM(
                       createChildNodeRequest = CreateNodeApiRequestADM(
                           parentNodeIri = Some(newListIri.get),
                           projectIri = IMAGES_PROJECT_IRI,
                           name = Some("second"),
                           labels = Seq(StringLiteralV2(value = "New Second Child List Node Value", language = Some("en"))),
                           comments = Seq(StringLiteralV2(value = "New Second Child List Node Comment", language = Some("en")))
                       ),
                        featureFactoryConfig = defaultFeatureFactoryConfig,
                        requestingUser = SharedTestDataADM.imagesUser01,
                        apiRequestID = UUID.randomUUID
               )

               val received: ChildNodeInfoGetResponseADM = expectMsgType[ChildNodeInfoGetResponseADM](timeout)
               val nodeInfo = received.nodeinfo

               // check correct node info
               val childNodeInfo = nodeInfo match {
                   case info: ListChildNodeInfoADM => info
                   case something => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
               }

               // check labels
               val labels: Seq[StringLiteralV2] = childNodeInfo.labels.stringLiterals
               labels.size should be(1)
               labels.sorted should be(Seq(StringLiteralV2(value = "New Second Child List Node Value", language = Some("en"))))


               // check comments
               val comments = childNodeInfo.comments.stringLiterals
               comments.size should be(1)
               comments.sorted should be(Seq(StringLiteralV2(value = "New Second Child List Node Comment", language = Some("en"))))

               // check position
               val position = childNodeInfo.position
               position should be(1)

               // check has root node
               val rootNode = childNodeInfo.hasRootNode
               rootNode should be(newListIri.get)

               secondChildIri.set(childNodeInfo.id)
           }

           "add child to second child node" in {
               responderManager ! ListChildNodeCreateRequestADM(
                   createChildNodeRequest = CreateNodeApiRequestADM(
                       parentNodeIri = Some(secondChildIri.get),
                       projectIri = IMAGES_PROJECT_IRI,
                       name = Some("third"),
                       labels = Seq(StringLiteralV2(value = "New Third Child List Node Value", language = Some("en"))),
                       comments = Seq(StringLiteralV2(value = "New Third Child List Node Comment", language = Some("en")))
                   ),
                   featureFactoryConfig = defaultFeatureFactoryConfig,
                   requestingUser = SharedTestDataADM.imagesUser01,
                   apiRequestID = UUID.randomUUID
               )

               val received: ChildNodeInfoGetResponseADM = expectMsgType[ChildNodeInfoGetResponseADM](timeout)
               val nodeInfo = received.nodeinfo

               // check correct node info
               val childNodeInfo = nodeInfo match {
                   case info: ListChildNodeInfoADM => info
                   case something => fail(s"expecting ListChildNodeInfoADM but got ${something.getClass.toString} instead.")
               }

               // check labels
               val labels: Seq[StringLiteralV2] = childNodeInfo.labels.stringLiterals
               labels.size should be(1)
               labels.sorted should be(Seq(StringLiteralV2(value = "New Third Child List Node Value", language = Some("en"))))


               // check comments
               val comments = childNodeInfo.comments.stringLiterals
               comments.size should be(1)
               comments.sorted should be(Seq(StringLiteralV2(value = "New Third Child List Node Comment", language = Some("en"))))

               // check position
               val position = childNodeInfo.position
               position should be(0)

               // check has root node
               val rootNode = childNodeInfo.hasRootNode
               rootNode should be(newListIri.get)

               thirdChildIri.set(childNodeInfo.id)
           }
        }
        "used to delete list items" should {
            "not delete a node that is in use" in {
                val nodeInUseIri = "http://rdfh.ch/lists/0001/treeList01"
                responderManager ! ListItemDeleteRequestADM(
                    nodeIri = nodeInUseIri,
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.anythingAdminUser,
                    apiRequestID = UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException(s"Node ${nodeInUseIri} cannot be deleted, because it is in use.")))

            }

            "not delete a node that has a child which is used (node itself not in use, but its child is)" in {
                val nodeIri = "http://rdfh.ch/lists/0001/treeList03"
                responderManager ! ListItemDeleteRequestADM(
                    nodeIri = nodeIri,
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.anythingAdminUser,
                    apiRequestID = UUID.randomUUID
                )
                val usedChild = "http://rdfh.ch/lists/0001/treeList10"
                expectMsg(Failure(BadRequestException(s"Node ${nodeIri} cannot be deleted, because its child ${usedChild} is in use.")))

            }

            "not delete a node used as object of salsah-gui:guiAttribute (i.e. 'hlist=<nodeIri>') but not as object of knora-base:valueHasListNode" in {
                val nodeInUseInOntologyIri = "http://rdfh.ch/lists/0001/treeList"
                responderManager ! ListItemDeleteRequestADM(
                    nodeIri = nodeInUseInOntologyIri,
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.anythingAdminUser,
                    apiRequestID = UUID.randomUUID
                )
                expectMsg(Failure(BadRequestException(s"Node ${nodeInUseInOntologyIri} cannot be deleted, because it is in use.")))

            }

            "delete a middle child node that is not in use" in {
                val nodeIri = "http://rdfh.ch/lists/0001/notUsedList02"
                responderManager ! ListItemDeleteRequestADM(
                    nodeIri = nodeIri,
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.anythingAdminUser,
                    apiRequestID = UUID.randomUUID
                )
                val received: ChildNodeDeleteResponseADM = expectMsgType[ChildNodeDeleteResponseADM](timeout)
                val parentNode = received.node
                val remainingChildren = parentNode.getChildren
                remainingChildren.size should be (2)
                //last child should be shifted to left
                remainingChildren.last.position should be (1)

                // first node should still have its child
                val firstChild = remainingChildren.head
                firstChild.id should be ("http://rdfh.ch/lists/0001/notUsedList01")
                firstChild.position should be (0)
                firstChild.children.size should be (1)
            }

            "delete a child node that is not in use" in {
                val nodeIri = "http://rdfh.ch/lists/0001/notUsedList01"
                responderManager ! ListItemDeleteRequestADM(
                    nodeIri = nodeIri,
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.anythingAdminUser,
                    apiRequestID = UUID.randomUUID
                )
                val received: ChildNodeDeleteResponseADM = expectMsgType[ChildNodeDeleteResponseADM](timeout)
                val parentNode = received.node
                val remainingChildren = parentNode.getChildren
                remainingChildren.size should be (1)
                val firstChild = remainingChildren.head
                firstChild.id should be ("http://rdfh.ch/lists/0001/notUsedList03")
                firstChild.position should be (0)
            }

            "delete a list (i.e. root node) that is not in use in ontology" in {
                val listIri = "http://rdfh.ch/lists/0001/notUsedList"
                responderManager ! ListItemDeleteRequestADM(
                    nodeIri = listIri,
                    featureFactoryConfig = defaultFeatureFactoryConfig,
                    requestingUser = SharedTestDataADM.anythingAdminUser,
                    apiRequestID = UUID.randomUUID
                )
                val received: ListDeleteResponseADM = expectMsgType[ListDeleteResponseADM](timeout)
                received.iri should be (listIri)
                received.deleted should be (true)
            }
        }

    }
}
