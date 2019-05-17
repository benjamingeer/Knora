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

package org.knora.webapi.responders.v2

import akka.testkit.ImplicitSender
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.searchmessages._
import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{ApiV2Complex, CoreSpec, MarkupAsXml, SchemaOptions, SharedTestDataADM}

import scala.concurrent.duration._

/**
  * Tests [[SearchResponderV2]].
  */
class SearchResponderV2Spec extends CoreSpec() with ImplicitSender {

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    private val searchResponderV2SpecFullData = new SearchResponderV2SpecFullData

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds

    "The search responder v2" should {

        "perform a fulltext search for 'Narr'" in {

            responderManager ! FulltextSearchRequestV2(
                searchValue = "Narr",
                offset = 0,
                limitToProject = None,
                limitToResourceClass = None,
                limitToStandoffClass = None,
                targetSchema = ApiV2Complex,
                schemaOptions = SchemaOptions.ForStandoffWithTextValues,
                requestingUser = SharedTestDataADM.anonymousUser
            )

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>

                    compareReadResourcesSequenceV2Response(
                        expected = searchResponderV2SpecFullData.fulltextSearchForNarr,
                        received = response
                    )
            }
        }

        "perform a fulltext search for 'Dinge'" in {

            responderManager ! FulltextSearchRequestV2(
                searchValue = "Dinge",
                offset = 0,
                limitToProject = None,
                limitToResourceClass = None,
                limitToStandoffClass = None,
                targetSchema = ApiV2Complex,
                schemaOptions = SchemaOptions.ForStandoffWithTextValues,
                requestingUser = SharedTestDataADM.anythingUser1
            )

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(expected = searchResponderV2SpecFullData.fulltextSearchForDinge, received = response)
            }

        }

        "perform an extended search for books that have the title 'Zeitglöcklein des Lebens'" in {


            responderManager ! GravsearchRequestV2(
                constructQuery = searchResponderV2SpecFullData.constructQueryForBooksWithTitleZeitgloecklein,
                targetSchema = ApiV2Complex,
                schemaOptions = SchemaOptions.ForStandoffWithTextValues,
                requestingUser = SharedTestDataADM.anonymousUser
            )

            // extended search sort by resource Iri by default if no order criterion is indicated
            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(expected = searchResponderV2SpecFullData.booksWithTitleZeitgloeckleinResponse, received = response)
            }

        }

        "perform an extended search for books that do not have the title 'Zeitglöcklein des Lebens'" in {

            responderManager ! GravsearchRequestV2(
                constructQuery = searchResponderV2SpecFullData.constructQueryForBooksWithoutTitleZeitgloecklein,
                targetSchema = ApiV2Complex,
                schemaOptions = SchemaOptions.ForStandoffWithTextValues,
                requestingUser = SharedTestDataADM.anonymousUser
            )

            // extended search sort by resource Iri by default if no order criterion is indicated
            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    // TODO: do better testing once JSON-LD can be converted back into case classes
                    assert(response.numberOfResources == 18, s"18 books were expected, but ${response.numberOfResources} given.")
            }

        }

        "perform a search by label for incunabula:book that contain 'Narrenschiff'" in {

            responderManager ! SearchResourceByLabelRequestV2(
                searchValue = "Narrenschiff",
                offset = 0,
                limitToProject = None,
                limitToResourceClass = Some("http://www.knora.org/ontology/0803/incunabula#book".toSmartIri), // internal Iri!
                requestingUser = SharedTestDataADM.anonymousUser
            )

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    assert(response.numberOfResources == 3, s"3 results were expected, but ${response.numberOfResources} given")
            }

        }

        "search by project and resource class" in {
            responderManager ! SearchResourcesByProjectAndClassRequestV2(
                projectIri = SharedTestDataADM.incunabulaProject.id.toSmartIri,
                resourceClass = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                orderByProperty = Some("http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri),
                schemaOptions = SchemaOptions.ForStandoffWithTextValues,
                page = 0,
                requestingUser = SharedTestDataADM.incunabulaProjectAdminUser
            )

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 => response.numberOfResources should ===(19)
            }
        }

    }


}