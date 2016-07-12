/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.responders.v1

import java.util.UUID

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActorRef}
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.sipimessages.SipiResponderConversionFileRequestV1
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.messages.v1.responder.resourcemessages.{LocationV1, ResourceFullGetRequestV1, ResourceFullResponseV1}
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.store.triplestoremessages._
import org.knora.webapi.responders._
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.{DateUtilV1, MutableTestIri}
import org.knora.webapi.util.ScalaPrettyPrinter

import scala.concurrent.duration._

/**
  * Static data for testing [[ValuesResponderV1]].
  */
object ValuesResponderV1Spec {
    private val incunabulaProjectIri = "http://data.knora.org/projects/77275339"
    private val anythingProjectIri = "http://data.knora.org/projects/anything"

    private val zeitglöckleinIri = "http://data.knora.org/c5058f3a"
    private val miscResourceIri = "http://data.knora.org/miscResource"
    private val aThingIri = "http://data.knora.org/a-thing"

    private val incunabulaUserData = UserDataV1(
        email = Some("test@test.ch"),
        lastname = Some("Test"),
        firstname = Some("User"),
        username = Some("testuser"),
        token = None,
        user_id = Some("http://data.knora.org/users/b83acc5f05"),
        lang = "de"
    )

    private val incunabulaUser = UserProfileV1(
        projects = Vector("http://data.knora.org/projects/77275339"),
        groups = Nil,
        userData = incunabulaUserData
    )

    private val imagesUser = UserProfileV1(
        projects = Vector("http://data.knora.org/projects/images"),
        groups = Nil,
        userData = UserDataV1(
            user_id = Some("http://data.knora.org/users/91e19f1e01"),
            lang = "de"
        )
    )

    private val anythingUser = UserProfileV1(
        projects = Vector("http://data.knora.org/projects/anything"),
        groups = Nil,
        userData = UserDataV1(
            user_id = Some("http://data.knora.org/users/9XBCrDV3SRa7kS1WwynB4Q"),
            lang = "de"
        )
    )

    private val versionHistoryWithHiddenVersion = ValueVersionHistoryGetResponseV1(
        userdata = incunabulaUserData,
        valueVersions = Vector(
            ValueVersionV1(
                previousValue = None, // The user doesn't have permission to see the previous value.
                valueCreationDate = Some("2016-01-22T11:31:24Z"),
                valueObjectIri = "http://data.knora.org/21abac2162/values/f76660458201"
            ),
            ValueVersionV1(
                previousValue = None,
                valueCreationDate = Some("2016-01-20T11:31:24Z"),
                valueObjectIri = "http://data.knora.org/21abac2162/values/11111111"
            )
        )
    )
}

/**
  * Tests [[ValuesResponderV1]].
  */
class ValuesResponderV1Spec extends CoreSpec() with ImplicitSender {
    import ValuesResponderV1Spec._

    private val actorUnderTest = TestActorRef[ValuesResponderV1]

    val responderManager = system.actorOf(Props(new TestResponderManagerV1(Map(SIPI_ROUTER_ACTOR_NAME -> system.actorOf(Props(new MockSipiResponderV1))))), name = RESPONDER_MANAGER_ACTOR_NAME)

    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = Vector(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/responders.v1.ValuesResponderV1Spec/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/images-demo-onto.ttl", name = "http://www.knora.org/ontology/images"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images"),
        RdfDataObject(path = "_test_data/ontologies/anything-onto.ttl", name = "http://www.knora.org/ontology/anything"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    // The default timeout for receiving reply messages from actors.
    private val timeout = 30.seconds

    // IRIs that are generated by tests and used by subsequent tests.
    private val commentIri = new MutableTestIri
    private val firstValueIriWithResourceRef = new MutableTestIri
    private val secondValueIriWithResourceRef = new MutableTestIri
    private val standoffLinkValueIri = new MutableTestIri
    private val currentSeqnumValueIri = new MutableTestIri
    private val currentPubdateValueIri = new MutableTestIri
    private val linkObjLinkValueIri = new MutableTestIri
    private val currentColorValueIri = new MutableTestIri
    private val currentGeomValueIri = new MutableTestIri
    private val partOfLinkValueIri = new MutableTestIri

    private def checkComment1aResponse(response: CreateValueResponseV1, utf8str: String, textattr: Map[String, Seq[StandoffPositionV1]] = Map.empty[String, Seq[StandoffPositionV1]]): Unit = {
        assert(response.rights == 8, "rights was not 8")
        assert(response.value.asInstanceOf[TextValueV1].utf8str == utf8str, "comment value did not match")
        assert(response.value.asInstanceOf[TextValueV1].textattr == textattr, "textattr did not match")
        commentIri.set(response.id)
    }

    private def checkValueGetResponse(response: ValueGetResponseV1): Unit = {
        assert(response.rights == 8, "rights was not 8")
        assert(response.value.asInstanceOf[TextValueV1].utf8str == "Comment 1a\r", "comment value did not match")
    }

    private def checkValueGetResponseWithStandoff(response: ValueGetResponseV1): Unit = {
        assert(response.rights == 6, "rights was not 6")
        assert(response.value.asInstanceOf[TextValueV1].utf8str == "Zusammengebunden mit zwei weiteren Drucken von Johann Amerbach\n", "comment utf8str value did not match")

        // expected Standoff information for <http://data.knora.org/e41ab5695c/values/d3398239089e04> in incunabula-data.ttl
        val textattr = Map(
            "bold" -> Vector(StandoffPositionV1(
                start = 21,
                end = 25
            ))
        )

        assert(response.value.asInstanceOf[TextValueV1].textattr == textattr, "textattr did not match")
    }

    private def checkComment1bResponse(response: ChangeValueResponseV1, utf8str: String, textattr: Map[String, Seq[StandoffPositionV1]] = Map.empty[String, Seq[StandoffPositionV1]]): Unit = {
        assert(response.rights == 8, "rights was not 8")
        assert(response.value.asInstanceOf[TextValueV1].utf8str == utf8str, "comment value did not match")
        assert(response.value.asInstanceOf[TextValueV1].textattr == textattr, "textattr did not match")
        commentIri.set(response.id)
    }

    private def checkOrderInResource(response: ResourceFullResponseV1): Unit = {
        val comments = response.props.get.properties.filter(_.pid == "http://www.knora.org/ontology/incunabula#book_comment").head

        assert(comments.values == Vector(
            TextValueV1(utf8str = "Comment 1b"),
            TextValueV1("Comment 2")
        ), "Values of book_comment did not match")
    }

    private def checkDeletion(response: DeleteValueResponseV1): Unit = {
        commentIri.set(response.id)
    }

    private def checkTextValue(received: TextValueV1, expected: TextValueV1): Unit = {
        def orderPositions(left: StandoffPositionV1, right: StandoffPositionV1): Boolean = {
            if (left.start != right.start) {
                left.start < right.start
            } else {
                left.end < right.end
            }
        }

        assert(received.utf8str == expected.utf8str)
        assert(received.resource_reference == expected.resource_reference)
        assert(received.textattr.keys == expected.textattr.keys)

        for (attribute <- expected.textattr.keys) {
            val expectedPositions = expected.textattr(attribute).sortWith(orderPositions)
            val receivedPositions = received.textattr(attribute).sortWith(orderPositions)

            assert(receivedPositions.length == expectedPositions.length)

            for ((receivedPosition, expectedPosition) <- receivedPositions.zip(expectedPositions)) {
                assert(receivedPosition.start == expectedPosition.start)
                assert(receivedPosition.end == expectedPosition.end)

                assert(receivedPosition.resid == expectedPosition.resid)

                if (expectedPosition.resid.isEmpty) {
                    assert(receivedPosition.href == expectedPosition.href)
                }
            }
        }
    }

    private def getLastModificationDate(resourceIri: IRI): Option[String] = {
        val lastModSparqlQuery = queries.sparql.v1.txt.getLastModificationDate(
            triplestore = settings.triplestoreType,
            resourceIri = resourceIri
        ).toString()

        storeManager ! SparqlSelectRequest(lastModSparqlQuery)

        expectMsgPF(timeout) {
            case response: SparqlSelectResponse =>
                val rows = response.results.bindings
                assert(rows.size <= 1, s"Resource $resourceIri has more than one instance of knora-base:lastModificationDate")

                if (rows.size == 1) {
                    Some(rows.head.rowMap("lastModificationDate"))
                } else {
                    None
                }
        }
    }

    // a sample set of text attributes
    private val sampleTextattr = Map(
        "bold" -> Vector(StandoffPositionV1(
            start = 0,
            end = 7
        )),
        "p" -> Vector(StandoffPositionV1(
            start = 0,
            end = 10
        ))
    )

    private def checkImageFileValueChange(received: ChangeFileValueResponseV1, request: ChangeFileValueRequestV1): Unit = {
        assert(received.locations.size == 2, "Expected two file values to have been changed (thumb and full quality)")

        received.locations.foreach {
            location: LocationV1 => assert(location.origname == request.file.originalFilename, "wrong original file name")
        }
    }

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())
    }

    "The values responder" should {
        "add a new text value without Standoff" in {
            val lastModBeforeUpdate = getLastModificationDate(zeitglöckleinIri)

            val utf8str = "Comment 1a\r"

            actorUnderTest ! CreateValueRequestV1(
                projectIri = incunabulaProjectIri,
                resourceIri = zeitglöckleinIri,
                propertyIri = "http://www.knora.org/ontology/incunabula#book_comment",
                value = TextValueV1(utf8str = utf8str),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: CreateValueResponseV1 => checkComment1aResponse(msg, utf8str)
            }

            // Check that the resource's last modification date got updated.
            val lastModAfterUpdate = getLastModificationDate(zeitglöckleinIri)
            lastModBeforeUpdate != lastModAfterUpdate should ===(true)
        }

        "query a text value without Standoff" in {
            actorUnderTest ! ValueGetRequestV1(
                valueIri = commentIri.get,
                userProfile = incunabulaUser
            )

            expectMsgPF(timeout) {
                case msg: ValueGetResponseV1 => checkValueGetResponse(msg)
            }
        }

        "query a text value containing Standoff (disabled because of issue 17)" ignore {
            actorUnderTest ! ValueGetRequestV1(
                valueIri = "http://data.knora.org/e41ab5695c/values/d3398239089e04",
                userProfile = incunabulaUser
            )

            expectMsgPF(timeout) {
                case msg: ValueGetResponseV1 =>
                    checkValueGetResponseWithStandoff(msg)
            }
        }

        "query a LinkValue" in {
            actorUnderTest ! LinkValueGetRequestV1(
                subjectIri = "http://data.knora.org/8a0b1e75",
                predicateIri = "http://www.knora.org/ontology/incunabula#partOf",
                objectIri = zeitglöckleinIri,
                userProfile = incunabulaUser
            )

            expectMsg(
                timeout,
                ValueGetResponseV1(
                    valuetype = OntologyConstants.KnoraBase.LinkValue,
                    value = LinkValueV1(
                        subjectIri = "http://data.knora.org/8a0b1e75",
                        predicateIri = "http://www.knora.org/ontology/incunabula#partOf",
                        objectIri = zeitglöckleinIri,
                        referenceCount = 1
                    ),
                    rights = 2,
                    userdata = incunabulaUserData
                )
            )
        }

        "add a new version of a text value without Standoff" in {
            val lastModBeforeUpdate = getLastModificationDate(zeitglöckleinIri)

            val utf8str = "Comment 1b"

            actorUnderTest ! ChangeValueRequestV1(
                valueIri = commentIri.get,
                value = TextValueV1(utf8str = utf8str),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: ChangeValueResponseV1 => checkComment1bResponse(msg, utf8str)
            }

            // Check that the resource's last modification date got updated.
            val lastModAfterUpdate = getLastModificationDate(zeitglöckleinIri)
            lastModBeforeUpdate != lastModAfterUpdate should ===(true)
        }

        "not add a new version of a value that's exactly the same as the current version" in {
            val utf8str = "Comment 1b"

            actorUnderTest ! ChangeValueRequestV1(
                valueIri = commentIri.get,
                value = TextValueV1(utf8str = utf8str),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "not create a new value that would duplicate an existing value" in {
            val utf8str = "Comment 1b"

            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = zeitglöckleinIri,
                propertyIri = "http://www.knora.org/ontology/incunabula#book_comment",
                value = TextValueV1(utf8str = utf8str),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "not add a new version of a value that would duplicate an existing value" in {
            val utf8str = "GW 4168"

            actorUnderTest ! ChangeValueRequestV1(
                valueIri = "http://data.knora.org/c5058f3a/values/184e99ca01",
                value = TextValueV1(utf8str = utf8str),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "insert valueHasOrder correctly for each value" in {
            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = zeitglöckleinIri,
                propertyIri = "http://www.knora.org/ontology/incunabula#book_comment",
                value = TextValueV1("Comment 2"),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: CreateValueResponseV1 => ()
            }

            responderManager ! ResourceFullGetRequestV1(
                iri = zeitglöckleinIri,
                userProfile = incunabulaUser
            )

            expectMsgPF(timeout) {
                case msg: ResourceFullResponseV1 => checkOrderInResource(msg)
            }
        }

        "return the version history of a value" in {
            actorUnderTest ! ValueVersionHistoryGetRequestV1(
                resourceIri = zeitglöckleinIri,
                propertyIri = "http://www.knora.org/ontology/incunabula#book_comment",
                currentValueIri = commentIri.get,
                userProfile = incunabulaUser
            )

            expectMsgPF(timeout) {
                case msg: ValueVersionHistoryGetResponseV1 => msg.valueVersions.length should ===(2)
            }
        }

        "mark a value as deleted" in {
            val lastModBeforeUpdate = getLastModificationDate(zeitglöckleinIri)

            actorUnderTest ! DeleteValueRequestV1(
                valueIri = commentIri.get,
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: DeleteValueResponseV1 => checkDeletion(msg)
            }

            actorUnderTest ! ValueGetRequestV1(
                valueIri = commentIri.get,
                userProfile = incunabulaUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }

            // Check that the resource's last modification date got updated.
            val lastModAfterUpdate = getLastModificationDate(zeitglöckleinIri)
            lastModBeforeUpdate != lastModAfterUpdate should ===(true)
        }

        "not add a new value to a nonexistent resource" in {
            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = "http://data.knora.org/nonexistent",
                propertyIri = "http://www.knora.org/ontology/incunabula#book_comment",
                value = TextValueV1("Comment 1"),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "not add a new value to a deleted resource" in {
            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = "http://data.knora.org/9935159f67",
                propertyIri = "http://www.knora.org/ontology/incunabula#book_comment",
                value = TextValueV1("Comment 1"),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "not add a new version of a deleted value" in {
            actorUnderTest ! ChangeValueRequestV1(
                valueIri = commentIri.get,
                value = TextValueV1("Comment 1c"),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "not add a new value to a resource that the user doesn't have permission to modify" in {
            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = "http://data.knora.org/e41ab5695c",
                propertyIri = "http://www.knora.org/ontology/incunabula#book_comment",
                value = TextValueV1("Comment 1"),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }
        }

        "not add a new value of the wrong type" in {
            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = "http://data.knora.org/21abac2162",
                propertyIri = "http://www.knora.org/ontology/incunabula#pubdate",
                value = TextValueV1("this is not a date"),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

        "not add a new version to a value that the user doesn't have permission to modify" in {
            actorUnderTest ! ChangeValueRequestV1(
                valueIri = "http://data.knora.org/c5058f3a/values/c3295339",
                value = TextValueV1("Zeitglöcklein des Lebens und Leidens Christi modified"),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }
        }

        "not add a new version of a value of the wrong type" in {
            actorUnderTest ! ChangeValueRequestV1(
                valueIri = "http://data.knora.org/c5058f3a/values/cfd09f1e01",
                value = TextValueV1("this is not a date"),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

        "not add a new value that would violate a cardinality restriction" in {
            // The cardinality of incunabula:partOf in incunabula:page is 1, and page http://data.knora.org/4f11adaf is already part of a book.
            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = "http://data.knora.org/4f11adaf",
                propertyIri = "http://www.knora.org/ontology/incunabula#partOf",
                value = LinkUpdateV1(targetResourceIri = "http://data.knora.org/e41ab5695c"),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }

            // The cardinality of incunabula:seqnum in incunabula:page is 0-1, and page http://data.knora.org/4f11adaf already has a seqnum.
            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = "http://data.knora.org/4f11adaf",
                propertyIri = "http://www.knora.org/ontology/incunabula#seqnum",
                value = IntegerValueV1(1),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

        "hide versions the user doesn't have permission to see" in {
            actorUnderTest ! ValueVersionHistoryGetRequestV1(
                resourceIri = "http://data.knora.org/21abac2162",
                propertyIri = "http://www.knora.org/ontology/incunabula#title",
                currentValueIri = "http://data.knora.org/21abac2162/values/f76660458201",
                userProfile = incunabulaUser
            )

            expectMsg(timeout, versionHistoryWithHiddenVersion)
        }


        "create a color value" in {

            val color = "#000000"

            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = miscResourceIri,
                propertyIri = "http://www.knora.org/ontology/incunabula#miscHasColor",
                value = ColorValueV1(color),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID)

            expectMsgPF(timeout) {
                case msg: CreateValueResponseV1 =>
                    currentColorValueIri.set(msg.id)
                    msg.value should ===(ColorValueV1(color))
            }
        }

        "change an existing color value" in {

            val color = "#FFFFFF"

            actorUnderTest ! ChangeValueRequestV1(
                value = ColorValueV1(color),
                userProfile = incunabulaUser,
                valueIri = currentColorValueIri.get,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: ChangeValueResponseV1 =>
                    currentColorValueIri.set(msg.id)
                    msg.value should ===(ColorValueV1(color))
            }
        }

        "create a geometry value" in {

            val geom = "{\"status\":\"active\",\"lineColor\":\"#ff3333\",\"lineWidth\":2,\"points\":[{\"x\":0.5516074450084602,\"y\":0.4444444444444444},{\"x\":0.2791878172588832,\"y\":0.5}],\"type\":\"rectangle\",\"original_index\":0}"

            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = miscResourceIri,
                propertyIri = "http://www.knora.org/ontology/incunabula#miscHasGeometry",
                value = GeomValueV1(geom),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID)

            expectMsgPF(timeout) {
                case msg: CreateValueResponseV1 =>
                    currentGeomValueIri.set(msg.id)
                    msg.value should ===(GeomValueV1(geom))
            }

        }

        "change a geometry value for a region" in {

            val geom = "{\"status\":\"active\",\"lineColor\":\"#ff4433\",\"lineWidth\":1,\"points\":[{\"x\":0.5516074450084602,\"y\":0.4444444444444444},{\"x\":0.2791878172588832,\"y\":0.5}],\"type\":\"rectangle\",\"original_index\":0}"

            actorUnderTest ! ChangeValueRequestV1(
                value = GeomValueV1(geom),
                valueIri = currentGeomValueIri.get,
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: ChangeValueResponseV1 =>
                    currentGeomValueIri.set(msg.id)
                    msg.value should ===(GeomValueV1(geom))
            }
        }

        "add a new text value with Standoff" in {

            val utf8str = "Comment 1aa\r"

            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = zeitglöckleinIri,
                propertyIri = "http://www.knora.org/ontology/incunabula#book_comment",
                value = TextValueV1(utf8str = utf8str, textattr = sampleTextattr),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: CreateValueResponseV1 => checkComment1aResponse(msg, utf8str, sampleTextattr)
            }
        }

        "add a new version of a text value with Standoff" in {

            val utf8str = "Comment 1bb\r"

            actorUnderTest ! ChangeValueRequestV1(
                valueIri = commentIri.get,
                value = TextValueV1(utf8str = utf8str, textattr = sampleTextattr),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: ChangeValueResponseV1 => checkComment1bResponse(msg, utf8str, sampleTextattr)
            }
        }

        "add a new text value containing a Standoff resource reference, and create a hasStandoffLinkTo direct link and a corresponding LinkValue" in {
            val textValueWithResourceRef = TextValueV1(
                utf8str = "This comment refers to another resource",
                textattr = Map(
                    StandoffConstantsV1.LINK_ATTR -> Vector(StandoffPositionV1(
                        start = 31,
                        end = 39,
                        resid = Some(zeitglöckleinIri)
                    ))
                ),
                resource_reference = Vector(zeitglöckleinIri)
            )

            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = "http://data.knora.org/21abac2162",
                propertyIri = "http://www.knora.org/ontology/incunabula#book_comment",
                value = textValueWithResourceRef,
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case CreateValueResponseV1(newValue: TextValueV1, _, newValueIri: IRI, _, _) =>
                    firstValueIriWithResourceRef.set(newValueIri)
                    checkTextValue(received = newValue, expected = textValueWithResourceRef)
            }

            actorUnderTest ! LinkValueGetRequestV1(
                subjectIri = "http://data.knora.org/21abac2162",
                predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                objectIri = zeitglöckleinIri,
                userProfile = incunabulaUser
            )

            // Since this is the first Standoff resource reference between the source and target resources, we should
            // now have version 1 of a LinkValue, with a reference count of 1.

            expectMsg(
                timeout,
                ValueGetResponseV1(
                    valuetype = OntologyConstants.KnoraBase.LinkValue,
                    value = LinkValueV1(
                        subjectIri = "http://data.knora.org/21abac2162",
                        predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                        objectIri = zeitglöckleinIri,
                        referenceCount = 1
                    ),
                    rights = 8,
                    userdata = incunabulaUserData
                )
            )

            val sparqlQuery = queries.sparql.v1.txt.findLinkValueByObject(
                triplestore = settings.triplestoreType,
                subjectIri = "http://data.knora.org/21abac2162",
                predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                objectIri = zeitglöckleinIri
            ).toString()

            storeManager ! SparqlSelectRequest(sparqlQuery)

            // The new LinkValue should have no previous version, and there should be a direct link between the resources.

            expectMsgPF(timeout) {
                case response: SparqlSelectResponse =>
                    val rows = response.results.bindings
                    rows.groupBy(_.rowMap("linkValue")).size should ===(1)
                    rows.exists(_.rowMap("objPred") == OntologyConstants.KnoraBase.PreviousValue) should ===(false)
                    rows.head.rowMap.get("directLinkExists").exists(_.toBoolean) should ===(true)
            }

        }

        "add a new version of a text value containing a Standoff resource reference, without needlessly making a new version of the LinkValue" in {
            // The new version contains two references to the same resource.
            val textValueWithResourceRef = TextValueV1(
                utf8str = "This updated comment refers to another resource",
                textattr = Map(
                    StandoffConstantsV1.LINK_ATTR -> Vector(
                        StandoffPositionV1(
                            start = 39,
                            end = 47,
                            resid = Some(zeitglöckleinIri)
                        ),
                        StandoffPositionV1(
                            start = 0,
                            end = 4,
                            resid = Some(zeitglöckleinIri)
                        )
                    )
                ),
                resource_reference = Vector(zeitglöckleinIri)
            )

            actorUnderTest ! ChangeValueRequestV1(
                valueIri = firstValueIriWithResourceRef.get,
                value = textValueWithResourceRef,
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case ChangeValueResponseV1(newValue: TextValueV1, _, newValueIri: IRI, _, _) =>
                    firstValueIriWithResourceRef.set(newValueIri)
                    checkTextValue(received = newValue, expected = textValueWithResourceRef)
            }

            actorUnderTest ! LinkValueGetRequestV1(
                subjectIri = "http://data.knora.org/21abac2162",
                predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                objectIri = zeitglöckleinIri,
                userProfile = incunabulaUser
            )

            // Since the new version still refers to the same resource, the reference count of the LinkValue should not
            // change.

            expectMsg(
                timeout,
                ValueGetResponseV1(
                    valuetype = OntologyConstants.KnoraBase.LinkValue,
                    value = LinkValueV1(
                        subjectIri = "http://data.knora.org/21abac2162",
                        predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                        objectIri = zeitglöckleinIri,
                        referenceCount = 1
                    ),
                    rights = 8,
                    userdata = incunabulaUserData
                )
            )

            val sparqlQuery = queries.sparql.v1.txt.findLinkValueByObject(
                triplestore = settings.triplestoreType,
                subjectIri = "http://data.knora.org/21abac2162",
                predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                objectIri = zeitglöckleinIri
            ).toString()

            storeManager ! SparqlSelectRequest(sparqlQuery)

            // There should be no new version of the LinkValue, and the direct link should still be there.

            expectMsgPF(timeout) {
                case response: SparqlSelectResponse =>
                    val rows = response.results.bindings
                    rows.groupBy(_.rowMap("linkValue")).size should ===(1)
                    rows.exists(_.rowMap("objPred") == OntologyConstants.KnoraBase.PreviousValue) should ===(false)
                    rows.head.rowMap.get("directLinkExists").exists(_.toBoolean) should ===(true)
            }
        }

        "add another new text value containing a Standoff resource reference, and make a new version of the LinkValue" in {
            val textValueWithResourceRef = TextValueV1(
                utf8str = "This remark refers to another resource",
                textattr = Map(
                    StandoffConstantsV1.LINK_ATTR -> Vector(StandoffPositionV1(
                        start = 30,
                        end = 38,
                        resid = Some(zeitglöckleinIri)
                    ))
                ),
                resource_reference = Vector(zeitglöckleinIri)
            )

            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = "http://data.knora.org/21abac2162",
                propertyIri = "http://www.knora.org/ontology/incunabula#book_comment",
                value = textValueWithResourceRef,
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case CreateValueResponseV1(newValue: TextValueV1, _, newValueIri: IRI, _, _) =>
                    secondValueIriWithResourceRef.set(newValueIri)
                    checkTextValue(received = newValue, expected = textValueWithResourceRef)
            }

            actorUnderTest ! LinkValueGetRequestV1(
                subjectIri = "http://data.knora.org/21abac2162",
                predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                objectIri = zeitglöckleinIri,
                userProfile = incunabulaUser
            )

            // Now that we've added a different TextValue that refers to the same resource, we should have version 2
            // of the LinkValue, with a reference count of 2.

            expectMsg(
                timeout,
                ValueGetResponseV1(
                    valuetype = OntologyConstants.KnoraBase.LinkValue,
                    value = LinkValueV1(
                        subjectIri = "http://data.knora.org/21abac2162",
                        predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                        objectIri = zeitglöckleinIri,
                        referenceCount = 2
                    ),
                    rights = 8,
                    userdata = incunabulaUserData
                )
            )

            val sparqlQuery = queries.sparql.v1.txt.findLinkValueByObject(
                triplestore = settings.triplestoreType,
                subjectIri = "http://data.knora.org/21abac2162",
                predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                objectIri = zeitglöckleinIri
            ).toString()

            storeManager ! SparqlSelectRequest(sparqlQuery)

            // It should have a previousValue pointing to the previous version, and the direct link should
            // still be there.

            expectMsgPF(timeout) {
                case response: SparqlSelectResponse =>
                    val rows = response.results.bindings
                    rows.groupBy(_.rowMap("linkValue")).size should ===(1)
                    rows.exists(_.rowMap("objPred") == OntologyConstants.KnoraBase.PreviousValue) should ===(true)
                    rows.head.rowMap.get("directLinkExists").exists(_.toBoolean) should ===(true)
            }
        }

        "add a new version of a text value with the Standoff resource reference removed, and make a new version of the LinkValue" in {
            val textValue = TextValueV1(utf8str = "No resource reference here")

            actorUnderTest ! ChangeValueRequestV1(
                valueIri = firstValueIriWithResourceRef.get,
                value = textValue,
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case ChangeValueResponseV1(newValue: TextValueV1, _, newValueIri: IRI, _, _) =>
                    firstValueIriWithResourceRef.set(newValueIri)
                    checkTextValue(received = textValue, expected = newValue)
            }

            actorUnderTest ! LinkValueGetRequestV1(
                subjectIri = "http://data.knora.org/21abac2162",
                predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                objectIri = zeitglöckleinIri,
                userProfile = incunabulaUser
            )

            // Version 3 of the LinkValue should have a reference count of 1.

            expectMsg(
                timeout,
                ValueGetResponseV1(
                    valuetype = OntologyConstants.KnoraBase.LinkValue,
                    value = LinkValueV1(
                        subjectIri = "http://data.knora.org/21abac2162",
                        predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                        objectIri = zeitglöckleinIri,
                        referenceCount = 1
                    ),
                    rights = 8,
                    userdata = incunabulaUserData
                )
            )

            val sparqlQuery = queries.sparql.v1.txt.findLinkValueByObject(
                triplestore = settings.triplestoreType,
                subjectIri = "http://data.knora.org/21abac2162",
                predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                objectIri = zeitglöckleinIri
            ).toString()

            storeManager ! SparqlSelectRequest(sparqlQuery)

            // The LinkValue should point to its previous version, and the direct link should still be there.

            expectMsgPF(timeout) {
                case response: SparqlSelectResponse =>
                    standoffLinkValueIri.set(response.results.bindings.head.rowMap("linkValue"))
                    val rows = response.results.bindings
                    rows.groupBy(_.rowMap("linkValue")).size should ===(1)
                    rows.exists(_.rowMap("objPred") == OntologyConstants.KnoraBase.PreviousValue) should ===(true)
                    rows.head.rowMap.get("directLinkExists").exists(_.toBoolean) should ===(true)
            }

            // The LinkValue should have 3 versions in its version history.

            actorUnderTest ! ValueVersionHistoryGetRequestV1(
                resourceIri = "http://data.knora.org/21abac2162",
                propertyIri = OntologyConstants.KnoraBase.HasStandoffLinkToValue,
                currentValueIri = standoffLinkValueIri.get,
                userProfile = incunabulaUser
            )

            expectMsgPF(timeout) {
                case msg: ValueVersionHistoryGetResponseV1 => msg.valueVersions.length should ===(3)
            }
        }

        "delete a hasStandoffLinkTo direct link when the reference count of the corresponding LinkValue reaches 0" in {
            val textValue = TextValueV1(utf8str = "No resource reference here either")

            actorUnderTest ! ChangeValueRequestV1(
                valueIri = secondValueIriWithResourceRef.get,
                value = textValue,
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case ChangeValueResponseV1(newValue: TextValueV1, _, newValueIri: IRI, _, _) =>
                    secondValueIriWithResourceRef.set(newValueIri)
                    checkTextValue(received = newValue, expected = textValue)
            }

            // The new version of the LinkValue should be marked as deleted.

            actorUnderTest ! LinkValueGetRequestV1(
                subjectIri = "http://data.knora.org/21abac2162",
                predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                objectIri = zeitglöckleinIri,
                userProfile = incunabulaUser
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }

            val sparqlQuery = queries.sparql.v1.txt.findLinkValueByObject(
                triplestore = settings.triplestoreType,
                subjectIri = "http://data.knora.org/21abac2162",
                predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                objectIri = zeitglöckleinIri,
                includeDeleted = true
            ).toString()

            storeManager ! SparqlSelectRequest(sparqlQuery)

            // The LinkValue should point to its previous version. There should be no direct link.

            expectMsgPF(timeout) {
                case response: SparqlSelectResponse =>
                    standoffLinkValueIri.unset()
                    val rows = response.results.bindings
                    rows.groupBy(_.rowMap("linkValue")).size should ===(1)
                    rows.exists(row => row.rowMap("objPred") == OntologyConstants.KnoraBase.IsDeleted && row.rowMap("objObj").toBoolean) should ===(true)
                    rows.exists(_.rowMap("objPred") == OntologyConstants.KnoraBase.PreviousValue) should ===(true)
                    rows.head.rowMap.get("directLinkExists").exists(_.toBoolean) should ===(false)
            }
        }

        "recreate the hasStandoffLinkTo direct link when a new standoff resource reference is added" in {
            val textValueWithResourceRef = TextValueV1(
                utf8str = "This updated comment refers again to another resource",
                textattr = Map(
                    StandoffConstantsV1.LINK_ATTR -> Vector(
                        StandoffPositionV1(
                            start = 45,
                            end = 53,
                            resid = Some(zeitglöckleinIri)
                        )
                    )
                ),
                resource_reference = Vector(zeitglöckleinIri)
            )

            actorUnderTest ! ChangeValueRequestV1(
                valueIri = firstValueIriWithResourceRef.get,
                value = textValueWithResourceRef,
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case ChangeValueResponseV1(newValue: TextValueV1, _, newValueIri: IRI, _, _) =>
                    firstValueIriWithResourceRef.set(newValueIri)
                    checkTextValue(received = newValue, expected = textValueWithResourceRef)
            }

            actorUnderTest ! LinkValueGetRequestV1(
                subjectIri = "http://data.knora.org/21abac2162",
                predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                objectIri = zeitglöckleinIri,
                userProfile = incunabulaUser
            )

            // There should now be a new LinkValue with no previous versions and a reference count of 1, and
            // there should once again be a direct link.

            expectMsg(
                timeout,
                ValueGetResponseV1(
                    valuetype = OntologyConstants.KnoraBase.LinkValue,
                    value = LinkValueV1(
                        subjectIri = "http://data.knora.org/21abac2162",
                        predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                        objectIri = zeitglöckleinIri,
                        referenceCount = 1
                    ),
                    rights = 8,
                    userdata = incunabulaUserData
                )
            )

            val sparqlQuery = queries.sparql.v1.txt.findLinkValueByObject(
                triplestore = settings.triplestoreType,
                subjectIri = "http://data.knora.org/21abac2162",
                predicateIri = OntologyConstants.KnoraBase.HasStandoffLinkTo,
                objectIri = zeitglöckleinIri
            ).toString()

            storeManager ! SparqlSelectRequest(sparqlQuery)

            expectMsgPF(timeout) {
                case response: SparqlSelectResponse =>
                    val rows = response.results.bindings
                    rows.groupBy(_.rowMap("linkValue")).size should ===(1)
                    rows.exists(_.rowMap("objPred") == OntologyConstants.KnoraBase.PreviousValue) should ===(false)
                    rows.head.rowMap.get("directLinkExists").exists(_.toBoolean) should ===(true)
            }
        }

        "add a new Integer value (seqnum of a page)" in {

            val seqnum = 4

            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = "http://data.knora.org/8a0b1e75",
                propertyIri = "http://www.knora.org/ontology/incunabula#seqnum",
                value = IntegerValueV1(seqnum),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case CreateValueResponseV1(newValue: IntegerValueV1, _, newValueIri: IRI, _, incunabulaUserData) =>
                    currentSeqnumValueIri.set(newValueIri)
                    newValue should ===(IntegerValueV1(seqnum))
            }
        }

        "change an existing Integer value (seqnum of a page)" in {

            val seqnum = 8

            actorUnderTest ! ChangeValueRequestV1(
                value = IntegerValueV1(seqnum),
                userProfile = incunabulaUser,
                valueIri = currentSeqnumValueIri.get,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case ChangeValueResponseV1(newValue: IntegerValueV1, _, newValueIri: IRI, _, incunabulaUserData) =>
                    newValue should ===(IntegerValueV1(seqnum))
            }
        }

        "add a new Date value (pubdate of a book)" in {

            // great resource to verify that expected conversion result from and to JDC is correct:
            // https://www.fourmilab.ch/documents/calendar/
            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = "http://data.knora.org/21abac2162",
                propertyIri = "http://www.knora.org/ontology/incunabula#pubdate",
                value = JulianDayCountValueV1(
                    dateval1 = 2451545,
                    dateval2 = 2457044,
                    dateprecision1 = KnoraPrecisionV1.YEAR,
                    dateprecision2 = KnoraPrecisionV1.DAY,
                    calendar = KnoraCalendarV1.GREGORIAN
                ),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: CreateValueResponseV1 =>
                    currentPubdateValueIri.set(msg.id)
                    msg.value should ===(DateValueV1("2000", "2015-01-21", KnoraCalendarV1.GREGORIAN))
            }
        }

        "change an existing date (pubdate of a book)" in {

            actorUnderTest ! ChangeValueRequestV1(
                value = JulianDayCountValueV1(
                    dateval1 = 2265854,
                    dateval2 = 2265854,
                    dateprecision1 = KnoraPrecisionV1.DAY,
                    dateprecision2 = KnoraPrecisionV1.DAY,
                    calendar = KnoraCalendarV1.JULIAN
                ),
                userProfile = incunabulaUser,
                valueIri = currentPubdateValueIri.get,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: ChangeValueResponseV1 =>
                    currentPubdateValueIri.set(msg.id)
                    msg.value should ===(DateValueV1("1491-07-28", "1491-07-28", KnoraCalendarV1.JULIAN))
            }

        }

        "create a link between two resources" in {
            val createValueRequest = CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = "http://data.knora.org/cb1a74e3e2f6",
                propertyIri = OntologyConstants.KnoraBase.HasLinkTo,
                value = LinkUpdateV1(
                    targetResourceIri = zeitglöckleinIri
                ),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! createValueRequest

            expectMsgPF(timeout) {
                case CreateValueResponseV1(linkV1: LinkV1, _, newLinkValueIri: IRI, _, _) =>
                    linkObjLinkValueIri.set(newLinkValueIri)
                    linkV1.targetResourceIri should ===(zeitglöckleinIri)
                    linkV1.valueResourceClass should ===(Some("http://www.knora.org/ontology/incunabula#book"))
            }

            // The new LinkValue should have no previous version, and there should be a direct link between the resources.

            val sparqlQuery = queries.sparql.v1.txt.findLinkValueByObject(
                triplestore = settings.triplestoreType,
                subjectIri = "http://data.knora.org/cb1a74e3e2f6",
                predicateIri = OntologyConstants.KnoraBase.HasLinkTo,
                objectIri = zeitglöckleinIri
            ).toString()

            storeManager ! SparqlSelectRequest(sparqlQuery)

            expectMsgPF(timeout) {
                case response: SparqlSelectResponse =>
                    val rows = response.results.bindings
                    rows.groupBy(_.rowMap("linkValue")).size should ===(1)
                    rows.exists(_.rowMap("objPred") == OntologyConstants.KnoraBase.PreviousValue) should ===(false)
                    rows.head.rowMap.get("directLinkExists").exists(_.toBoolean) should ===(true)
            }
        }

        "not create a duplicate link" in {
            val createValueRequest = CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = "http://data.knora.org/cb1a74e3e2f6",
                propertyIri = OntologyConstants.KnoraBase.HasLinkTo,
                value = LinkUpdateV1(
                    targetResourceIri = zeitglöckleinIri
                ),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! createValueRequest

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "not create a link that points to a resource of the wrong class" in {
            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = miscResourceIri,
                propertyIri = "http://www.knora.org/ontology/incunabula#miscHasBook", // can only point to an incunabula:book
                value = LinkUpdateV1(
                    targetResourceIri = "http://data.knora.org/8a0b1e75" // an incunabula:page, not an incunabula:book
                ),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

        "change a link" in {
            val linkSourceIri = "http://data.knora.org/cb1a74e3e2f6"
            val linkTargetIri = "http://data.knora.org/21abac2162"
            val lastModBeforeUpdate = getLastModificationDate(linkSourceIri)

            val changeValueRequest = ChangeValueRequestV1(
                value = LinkUpdateV1(
                    targetResourceIri = linkTargetIri
                ),
                userProfile = incunabulaUser,
                valueIri = linkObjLinkValueIri.get,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! changeValueRequest

            expectMsgPF(timeout) {
                case ChangeValueResponseV1(linkValue: LinkV1, _, newLinkValueIri: IRI, _, _) =>
                    linkObjLinkValueIri.set(newLinkValueIri)
                    linkValue.targetResourceIri should ===(linkTargetIri)
            }

            // The old LinkValue should be deleted now, and the old direct link should have been removed.

            val oldLinkValueSparqlQuery = queries.sparql.v1.txt.findLinkValueByObject(
                triplestore = settings.triplestoreType,
                subjectIri = linkSourceIri,
                predicateIri = OntologyConstants.KnoraBase.HasLinkTo,
                objectIri = zeitglöckleinIri,
                includeDeleted = true
            ).toString()

            storeManager ! SparqlSelectRequest(oldLinkValueSparqlQuery)

            expectMsgPF(timeout) {
                case response: SparqlSelectResponse =>
                    val rows = response.results.bindings
                    rows.groupBy(_.rowMap("linkValue")).size should ===(1)
                    rows.exists(row => row.rowMap("objPred") == OntologyConstants.KnoraBase.IsDeleted && row.rowMap("objObj").toBoolean) should ===(true)
                    rows.exists(_.rowMap("objPred") == OntologyConstants.KnoraBase.PreviousValue) should ===(true)
                    rows.head.rowMap.get("directLinkExists").exists(_.toBoolean) should ===(false)
            }

            // The new LinkValue should have no previous version, and there should be a direct link between the resources.

            val newLinkValueSparqlQuery = queries.sparql.v1.txt.findLinkValueByObject(
                triplestore = settings.triplestoreType,
                subjectIri = linkSourceIri,
                predicateIri = OntologyConstants.KnoraBase.HasLinkTo,
                objectIri = linkTargetIri
            ).toString()

            storeManager ! SparqlSelectRequest(newLinkValueSparqlQuery)

            expectMsgPF(timeout) {
                case response: SparqlSelectResponse =>
                    val rows = response.results.bindings
                    rows.groupBy(_.rowMap("linkValue")).size should ===(1)
                    rows.exists(_.rowMap("objPred") == OntologyConstants.KnoraBase.PreviousValue) should ===(false)
                    rows.head.rowMap.get("directLinkExists").exists(_.toBoolean) should ===(true)
            }

            // Check that the link source's last modification date got updated.
            val lastModAfterUpdate = getLastModificationDate(linkSourceIri)
            lastModBeforeUpdate != lastModAfterUpdate should ===(true)
        }

        "delete a link between two resources" in {

            val linkSourceIri = "http://data.knora.org/cb1a74e3e2f6"
            val linkTargetIri = "http://data.knora.org/21abac2162"
            val lastModBeforeUpdate = getLastModificationDate(linkSourceIri)

            val comment = "This link is no longer needed"

            actorUnderTest ! DeleteValueRequestV1(
                valueIri = linkObjLinkValueIri.get,
                comment = Some(comment),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case DeleteValueResponseV1(newLinkValueIri: IRI, _) =>
                    linkObjLinkValueIri.set(newLinkValueIri)
            }

            val deletedLinkValueSparqlQuery = queries.sparql.v1.txt.findLinkValueByObject(
                triplestore = settings.triplestoreType,
                subjectIri = linkSourceIri,
                predicateIri = OntologyConstants.KnoraBase.HasLinkTo,
                objectIri = linkTargetIri,
                includeDeleted = true
            ).toString()

            storeManager ! SparqlSelectRequest(deletedLinkValueSparqlQuery)

            expectMsgPF(timeout) {
                case response: SparqlSelectResponse =>
                    val rows = response.results.bindings
                    rows.groupBy(_.rowMap("linkValue")).size should ===(1)
                    rows.exists(row => row.rowMap("objPred") == OntologyConstants.KnoraBase.IsDeleted && row.rowMap("objObj").toBoolean) should ===(true)
                    rows.exists(_.rowMap("objPred") == OntologyConstants.KnoraBase.PreviousValue) should ===(true)
                    rows.head.rowMap.get("directLinkExists").exists(_.toBoolean) should ===(false)
                    rows.exists(row => row.rowMap("objPred") == OntologyConstants.KnoraBase.ValueHasComment && row.rowMap("objObj") == comment) should ===(true)
            }

            // Check that the link source's last modification date got updated.
            val lastModAfterUpdate = getLastModificationDate(linkSourceIri)
            lastModBeforeUpdate != lastModAfterUpdate should ===(true)
        }

        "change the partOf property of a page" in {
            // A test UserDataV1.
            val userData = UserDataV1(
                email = Some("test@test.ch"),
                lastname = Some("Test"),
                firstname = Some("User"),
                username = Some("testuser"),
                token = None,
                user_id = Some("http://data.knora.org/users/91e19f1e01"),
                lang = "de"
            )

            // A test UserProfileV1.
            val userProfile = UserProfileV1(
                projects = Vector("http://data.knora.org/projects/77275339"),
                groups = Nil,
                userData = userData
            )

            val linkTargetIri = "http://data.knora.org/e41ab5695c"

            partOfLinkValueIri.set("http://data.knora.org/8a0b1e75/values/3a7b5130-22c2-4400-a794-062b7a3e3436")

            val changeValueRequest = ChangeValueRequestV1(
                value = LinkUpdateV1(
                    targetResourceIri = linkTargetIri
                ),
                userProfile = userProfile,
                valueIri = partOfLinkValueIri.get,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! changeValueRequest

            expectMsgPF(timeout) {
                case ChangeValueResponseV1(linkValue: LinkV1, _, newLinkValueIri: IRI, _, _) =>
                    // save valueIri for next test
                    partOfLinkValueIri.set(newLinkValueIri)
                    linkValue.targetResourceIri should ===(linkTargetIri)
            }

        }

        "try to change the partOf property of a page, but submit the current target Iri" in {
            // A test UserDataV1.
            val userData = UserDataV1(
                email = Some("test@test.ch"),
                lastname = Some("Test"),
                firstname = Some("User"),
                username = Some("testuser"),
                token = None,
                user_id = Some("http://data.knora.org/users/91e19f1e01"),
                lang = "de"
            )

            // A test UserProfileV1.
            val userProfile = UserProfileV1(
                projects = Vector("http://data.knora.org/projects/77275339"),
                groups = Nil,
                userData = userData
            )

            val linkTargetIri = "http://data.knora.org/e41ab5695c"

            val changeValueRequest = ChangeValueRequestV1(
                value = LinkUpdateV1(
                    targetResourceIri = linkTargetIri
                ),
                userProfile = userProfile,
                valueIri = partOfLinkValueIri.get, // use valueIri from previous test
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! changeValueRequest

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }

        }

        "add a new text value with a comment" in {
            val comment = "This is a comment"
            val metaComment = "This is a metacomment"

            actorUnderTest ! CreateValueRequestV1(
                projectIri = "http://data.knora.org/projects/77275339",
                resourceIri = zeitglöckleinIri,
                propertyIri = "http://www.knora.org/ontology/incunabula#book_comment",
                value = TextValueV1(utf8str = comment),
                comment = Some(metaComment),
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: CreateValueResponseV1 =>
                    msg.value.toString should ===(comment)
                    msg.comment should ===(Some(metaComment))
            }
        }

        "add a comment to a value" in {
            val lastModBeforeUpdate = getLastModificationDate(zeitglöckleinIri)

            val comment = "This is wrong. I am the author!"

            val changeCommentRequest = ChangeCommentRequestV1(
                valueIri = "http://data.knora.org/c5058f3a/values/8653a672",
                comment = comment,
                userProfile = incunabulaUser,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! changeCommentRequest

            expectMsgPF(timeout) {
                case msg: ChangeValueResponseV1 =>
                    msg.value should ===(TextValueV1(utf8str = "Berthold, der Bruder"))
                    msg.comment should ===(Some(comment))
            }

            // Check that the resource's last modification date got updated.
            val lastModAfterUpdate = getLastModificationDate(zeitglöckleinIri)
            lastModBeforeUpdate != lastModAfterUpdate should ===(true)
        }

        "add a new image file value to an incunabula:page" in {

            val fileRequest = SipiResponderConversionFileRequestV1(
                originalFilename = "Chlaus.jpg",
                originalMimeType = "image/jpeg",
                filename = "./test_server/images/Chlaus.jpg",
                userProfile = incunabulaUser
            )

            val fileChangeRequest = ChangeFileValueRequestV1(
                resourceIri = "http://data.knora.org/8a0b1e75",
                file = fileRequest,
                apiRequestID = UUID.randomUUID,
                userProfile = incunabulaUser)

            actorUnderTest ! fileChangeRequest

            expectMsgPF(timeout) {
                case msg: ChangeFileValueResponseV1 => checkImageFileValueChange(msg, fileChangeRequest)
            }

        }

        "change the season of a image:bild from summer to winter" in {

            val winter = "http://data.knora.org/lists/eda2792605"

            actorUnderTest ! ChangeValueRequestV1(
                value = HierarchicalListValueV1(winter),
                userProfile = imagesUser,
                valueIri = "http://data.knora.org/d208fb9357d5/values/bc90a9c5091004",
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case ChangeValueResponseV1(newListValue: HierarchicalListValueV1, _, _, _, _) =>
                    newListValue should ===(HierarchicalListValueV1(winter))
            }

        }

        "create a season of a image:bild" in {

            val summer = "http://data.knora.org/lists/526f26ed04"

            actorUnderTest ! CreateValueRequestV1(
                value = HierarchicalListValueV1(summer),
                userProfile = imagesUser,
                propertyIri = "http://www.knora.org/ontology/images#jahreszeit",
                resourceIri = "http://data.knora.org/691e7e2244d5",
                projectIri = "http://data.knora.org/projects/images",
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case CreateValueResponseV1(newListValue: HierarchicalListValueV1, _ , _, _, _) =>
                    newListValue should ===(HierarchicalListValueV1(summer))
            }

        }

        "add a decimal value to an anything:Thing" in {
            val decimalValue = DecimalValueV1(BigDecimal("5.6"))

            actorUnderTest ! CreateValueRequestV1(
                value = decimalValue,
                userProfile = anythingUser,
                propertyIri = "http://www.knora.org/ontology/anything#hasDecimal",
                resourceIri = aThingIri,
                projectIri = anythingProjectIri,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case CreateValueResponseV1(newDecimalValue: DecimalValueV1, _ , _, _, _) =>
                    newDecimalValue should ===(decimalValue)
            }
        }

        "add an interval value to an anything:Thing" in {
            val intervalValue = IntervalValueV1(timeval1 = BigDecimal("1000000000000000.0000000000000001"), timeval2 = BigDecimal("1000000000000000.0000000000000002"))

            actorUnderTest ! CreateValueRequestV1(
                value = intervalValue,
                userProfile = anythingUser,
                propertyIri = "http://www.knora.org/ontology/anything#hasInterval",
                resourceIri = aThingIri,
                projectIri = anythingProjectIri,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case CreateValueResponseV1(newIntervalValue: IntervalValueV1, _ , _, _, _) =>
                    newIntervalValue should ===(intervalValue)
            }
        }

        "add a color value to an anything:Thing" in {
            val colorValue = ColorValueV1("#4169E1")

            actorUnderTest ! CreateValueRequestV1(
                value = colorValue,
                userProfile = anythingUser,
                propertyIri = "http://www.knora.org/ontology/anything#hasColor",
                resourceIri = aThingIri,
                projectIri = anythingProjectIri,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case CreateValueResponseV1(newColorValue: ColorValueV1, _ , _, _, _) =>
                    newColorValue should ===(colorValue)
            }
        }

        // TODO: commented out because of compaibility issues with the GUI
        /*"add a geometry value to an anything:Thing" in {
            val geomValue = GeomValueV1("{\"status\":\"active\",\"lineColor\":\"#ff3333\",\"lineWidth\":2,\"points\":[{\"x\":0.5516074450084602,\"y\":0.4444444444444444},{\"x\":0.2791878172588832,\"y\":0.5}],\"type\":\"rectangle\",\"original_index\":0}")

            actorUnderTest ! CreateValueRequestV1(
                value = geomValue,
                userProfile = anythingUser,
                propertyIri = "http://www.knora.org/ontology/anything#hasGeometry",
                resourceIri = aThingIri,
                projectIri = anythingProjectIri,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case CreateValueResponseV1(newGeomValue: GeomValueV1, _ , _, _, _) =>
                    newGeomValue should ===(geomValue)
            }
        }

        "add a geoname value to an anything:Thing" in {
            val geonameValue = GeonameValueV1("2661602")

            actorUnderTest ! CreateValueRequestV1(
                value = geonameValue,
                userProfile = anythingUser,
                propertyIri = "http://www.knora.org/ontology/anything#hasGeoname",
                resourceIri = aThingIri,
                projectIri = anythingProjectIri,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case CreateValueResponseV1(newGeonameValue: GeonameValueV1, _ , _, _, _) =>
                    newGeonameValue should ===(geonameValue)
            }
        }

        "add a boolean value to an anything:Thing" in {
            val booleanValue = BooleanValueV1(true)

            actorUnderTest ! CreateValueRequestV1(
                value = booleanValue,
                userProfile = anythingUser,
                propertyIri = "http://www.knora.org/ontology/anything#hasBoolean",
                resourceIri = aThingIri,
                projectIri = anythingProjectIri,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case CreateValueResponseV1(newBooleanValue: BooleanValueV1, _ , _, _, _) =>
                    newBooleanValue should ===(booleanValue)
            }
        }*/

        "add a URI value to an anything:Thing" in {
            val uriValue = UriValueV1("http://dhlab.unibas.ch")

            actorUnderTest ! CreateValueRequestV1(
                value = uriValue,
                userProfile = anythingUser,
                propertyIri = "http://www.knora.org/ontology/anything#hasUri",
                resourceIri = aThingIri,
                projectIri = anythingProjectIri,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case CreateValueResponseV1(newUriValue: UriValueV1, _ , _, _, _) =>
                    newUriValue should ===(uriValue)
            }
        }

    }
}
