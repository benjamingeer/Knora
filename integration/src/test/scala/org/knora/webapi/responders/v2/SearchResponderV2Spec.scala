/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import dsp.errors.BadRequestException
import org.knora.webapi.SchemaAndOptions.apiV2SchemaWithOption
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageFileValueContentV2
import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.anonymousUser
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA

class SearchResponderV2Spec extends CoreSpec {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula"
    ),
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything"
    ),
    RdfDataObject(
      path = "test_data/project_ontologies/books-onto.ttl",
      name = "http://www.knora.org/ontology/0001/books"
    ),
    RdfDataObject(path = "test_data/project_data/books-data.ttl", name = "http://www.knora.org/data/0001/books")
  )
  private val searchResponderV2SpecFullData = new SearchResponderV2SpecFullData

  "The search responder v2" should {

    "perform a fulltext search for 'Narr'" in {

      val result = UnsafeZioRun.runOrThrow(
        SearchResponderV2.fulltextSearchV2(
          searchValue = "Narr",
          offset = 0,
          limitToProject = None,
          limitToResourceClass = None,
          limitToStandoffClass = None,
          returnFiles = false,
          apiV2SchemaWithOption(MarkupAsXml),
          requestingUser = anonymousUser
        )
      )

      assert(result.resources.size == 25)
    }

    "perform a fulltext search for 'Dinge'" in {
      val result = UnsafeZioRun.runOrThrow(
        SearchResponderV2.fulltextSearchV2(
          searchValue = "Dinge",
          offset = 0,
          limitToProject = None,
          limitToResourceClass = None,
          limitToStandoffClass = None,
          returnFiles = false,
          apiV2SchemaWithOption(MarkupAsXml),
          requestingUser = SharedTestDataADM.anythingUser1
        )
      )

      assert(result.resources.size == 1)
    }

    "return a Bad Request error if fulltext search input is invalid" in {
      val result = UnsafeZioRun.run(
        SearchResponderV2.fulltextSearchV2(
          searchValue = "qin(",
          offset = 0,
          limitToProject = None,
          limitToResourceClass = None,
          limitToStandoffClass = None,
          returnFiles = false,
          apiV2SchemaWithOption(MarkupAsXml),
          requestingUser = SharedTestDataADM.anythingUser1
        )
      )
      assertFailsWithA[BadRequestException](result)
    }

    "return files attached to full-text search results" in {

      val result: ReadResourcesSequenceV2 = UnsafeZioRun.runOrThrow(
        SearchResponderV2.fulltextSearchV2(
          searchValue = "p7v",
          offset = 0,
          limitToProject = None,
          limitToResourceClass = None,
          limitToStandoffClass = None,
          returnFiles = true,
          apiV2SchemaWithOption(MarkupAsXml),
          requestingUser = SharedTestDataADM.anythingUser1
        )
      )

      val hasImageFileValues: Boolean =
        result.resources.flatMap(_.values.values.flatten).exists { readValueV2: ReadValueV2 =>
          readValueV2.valueContent match {
            case _: StillImageFileValueContentV2 => true
            case _                               => false
          }
        }

      assert(hasImageFileValues)

    }

    "perform an extended search for books that have the title 'Zeitglöcklein des Lebens'" in {

      val searchResult = UnsafeZioRun.runOrThrow(
        SearchResponderV2.gravsearchV2(
          searchResponderV2SpecFullData.constructQueryForBooksWithTitleZeitgloecklein,
          apiV2SchemaWithOption(MarkupAsXml),
          anonymousUser
        )
      )

      // extended search sort by resource Iri by default if no order criterion is indicated
      compareReadResourcesSequenceV2Response(
        expected = searchResponderV2SpecFullData.booksWithTitleZeitgloeckleinResponse,
        received = searchResult
      )
    }

    "perform an extended search for books that do not have the title 'Zeitglöcklein des Lebens'" in {
      val searchResult = UnsafeZioRun.runOrThrow(
        SearchResponderV2.gravsearchV2(
          searchResponderV2SpecFullData.constructQueryForBooksWithoutTitleZeitgloecklein,
          apiV2SchemaWithOption(MarkupAsXml),
          anonymousUser
        )
      )

      // extended search sort by resource Iri by default if no order criterion is indicated
      assert(searchResult.resources.size == 18)
    }

    "perform a search by label for incunabula:book that contain 'Narrenschiff'" in {
      val result = UnsafeZioRun.runOrThrow(
        SearchResponderV2.searchResourcesByLabelV2(
          searchValue = "Narrenschiff",
          offset = 0,
          limitToProject = None,
          limitToResourceClass = Some("http://www.knora.org/ontology/0803/incunabula#book".toSmartIri), // internal Iri!
          targetSchema = ApiV2Complex,
          requestingUser = anonymousUser
        )
      )

      assert(result.resources.size == 3)
    }

    "perform a search by label for incunabula:book that contain 'Das Narrenschiff'" in {
      val result = UnsafeZioRun.runOrThrow(
        SearchResponderV2.searchResourcesByLabelV2(
          searchValue = "Narrenschiff",
          offset = 0,
          limitToProject = None,
          limitToResourceClass = Some("http://www.knora.org/ontology/0803/incunabula#book".toSmartIri), // internal Iri!
          targetSchema = ApiV2Complex,
          requestingUser = anonymousUser
        )
      )

      assert(result.resources.size == 3)
    }

    "perform a count search query by label for incunabula:book that contain 'Narrenschiff'" in {

      val result = UnsafeZioRun.runOrThrow(
        SearchResponderV2.searchResourcesByLabelCountV2(
          searchValue = "Narrenschiff",
          limitToProject = None,
          limitToResourceClass = Some("http://www.knora.org/ontology/0803/incunabula#book".toSmartIri)
        )
      )

      assert(result.numberOfResources == 3)

    }

    "perform a a count search query by label for incunabula:book that contain 'Passio sancti Meynrhadi martyris et heremite'" in {

      val result = UnsafeZioRun.runOrThrow(
        SearchResponderV2.searchResourcesByLabelCountV2(
          searchValue = "Passio sancti Meynrhadi martyris et heremite",
          limitToProject = None,
          limitToResourceClass = Some("http://www.knora.org/ontology/0803/incunabula#book".toSmartIri)
        )
      )

      assert(result.numberOfResources == 1)
    }

    "search by project and resource class" in {
      val result = UnsafeZioRun.runOrThrow(
        SearchResponderV2.searchResourcesByProjectAndClassV2(
          projectIri = SharedTestDataADM.incunabulaProject.id.toSmartIri,
          resourceClass = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          orderByProperty = Some("http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri),
          page = 0,
          schemaAndOptions = SchemaAndOptions.apiV2SchemaWithOption(MarkupAsXml),
          requestingUser = SharedTestDataADM.incunabulaProjectAdminUser
        )
      )
      result.resources.size should ===(19)
    }

    "search for list label" in {

      val result = UnsafeZioRun.runOrThrow(
        SearchResponderV2.fulltextSearchV2(
          searchValue = "non fiction",
          offset = 0,
          limitToProject = None,
          limitToResourceClass = None,
          limitToStandoffClass = None,
          returnFiles = false,
          apiV2SchemaWithOption(MarkupAsXml),
          requestingUser = SharedTestDataADM.anythingUser1
        )
      )

      compareReadResourcesSequenceV2Response(
        expected = searchResponderV2SpecFullData.expectedResultFulltextSearchForListNodeLabel,
        received = result
      )
    }

    "search for list label and find sub-nodes" in {
      val result = UnsafeZioRun.runOrThrow(
        SearchResponderV2.fulltextSearchV2(
          searchValue = "novel",
          offset = 0,
          limitToProject = None,
          limitToResourceClass = None,
          limitToStandoffClass = None,
          returnFiles = false,
          apiV2SchemaWithOption(MarkupAsXml),
          requestingUser = SharedTestDataADM.anythingUser1
        )
      )

      compareReadResourcesSequenceV2Response(
        expected = searchResponderV2SpecFullData.expectedResultFulltextSearchForListNodeLabelWithSubnodes,
        received = result
      )
    }

    "perform an extended search for a particular compound object (book)" in {
      val searchResult = UnsafeZioRun.runOrThrow(
        SearchResponderV2.gravsearchV2(
          searchResponderV2SpecFullData.constructQueryForIncunabulaCompundObject,
          apiV2SchemaWithOption(MarkupAsXml),
          anonymousUser
        )
      )
      searchResult.resources.length should equal(25)
    }

    "perform an extended search ordered asc by label" in {
      val queryAsc = searchResponderV2SpecFullData.constructQuerySortByLabel
      val ascResult = UnsafeZioRun.runOrThrow(
        SearchResponderV2.gravsearchV2(queryAsc, apiV2SchemaWithOption(MarkupAsXml), anonymousUser)
      )
      assert(ascResult.resources.head.label == "A blue thing")
    }

    "perform an extended search ordered desc by label" in {
      val queryDesc = searchResponderV2SpecFullData.constructQuerySortByLabelDesc
      val descResult = UnsafeZioRun.runOrThrow(
        SearchResponderV2.gravsearchV2(queryDesc, apiV2SchemaWithOption(MarkupAsXml), anonymousUser)
      )
      assert(descResult.resources.head.label == "visible thing with hidden int values")
    }
  }
}
