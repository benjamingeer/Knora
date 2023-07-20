/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v1

import akka.testkit._

import scala.concurrent.duration._

import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v1.responder.searchmessages._
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM._
import org.knora.webapi.sharedtestdata.SharedTestDataADM

/**
 * Static data for testing [[SearchResponderV1]].
 */
object SearchResponderV1Spec {

  private val incunabulaUser = SharedTestDataADM.incunabulaMemberUser

  private val anythingUser1 = SharedTestDataADM.anythingUser1

  private val anythingUser2 = SharedTestDataADM.anythingUser2

  private val fulltextThingResultsForUser1 = Vector(
    SearchResultRowV1(
      rights = Some(8),
      preview_ny = 32,
      preview_nx = 32,
      value = Vector(
        "Ein Ding f\u00FCr jemanden, dem die Dinge gefallen",
        "Na ja, die Dinge sind OK."
      ),
      valuelabel = Vector(
        "Label",
        "Text"
      ),
      valuetype_id = Vector(
        "http://www.w3.org/2000/01/rdf-schema#label",
        "http://www.knora.org/ontology/knora-base#TextValue"
      ),
      iconlabel = Some("Ding"),
      icontitle = Some("Ding"),
      iconsrc = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      preview_path = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      obj_id = "http://rdfh.ch/0001/a-thing-with-text-values"
    )
  )

  private val fulltextValueInThingResultsForUser1 = Vector(
    SearchResultRowV1(
      rights = Some(8),
      preview_ny = 32,
      preview_nx = 32,
      value = Vector(
        "Ein Ding f\u00FCr jemanden, dem die Dinge gefallen",
        "Ich liebe die Dinge, sie sind alles f\u00FCr mich."
      ),
      valuelabel = Vector(
        "Label",
        "Text"
      ),
      valuetype_id = Vector(
        "http://www.w3.org/2000/01/rdf-schema#label",
        "http://www.knora.org/ontology/knora-base#TextValue"
      ),
      iconlabel = Some("Ding"),
      icontitle = Some("Ding"),
      iconsrc = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      preview_path = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      obj_id = "http://rdfh.ch/0001/a-thing-with-text-values"
    )
  )

  private val fulltextThingResultsForUser2 = Vector(
    SearchResultRowV1(
      rights = Some(2),
      preview_ny = 32,
      preview_nx = 32,
      value = Vector("Ein Ding f\u00FCr jemanden, dem die Dinge gefallen"),
      valuelabel = Vector("Label"),
      valuetype_id = Vector("http://www.w3.org/2000/01/rdf-schema#label"),
      iconlabel = Some("Ding"),
      icontitle = Some("Ding"),
      iconsrc = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      preview_path = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      obj_id = "http://rdfh.ch/0001/a-thing-with-text-values"
    )
  )

  private val hasOtherThingResultsForUser1 = Vector(
    SearchResultRowV1(
      rights = Some(8),
      preview_ny = 32,
      preview_nx = 32,
      value = Vector(
        "A thing that only project members can see",
        "Another thing that only project members can see"
      ),
      valuelabel = Vector(
        "Label",
        "Ein anderes Ding"
      ),
      valuetype_id = Vector(
        "http://www.w3.org/2000/01/rdf-schema#label",
        "http://www.knora.org/ontology/knora-base#Resource"
      ),
      iconlabel = Some("Ding"),
      icontitle = Some("Ding"),
      iconsrc = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      preview_path = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      obj_id = "http://rdfh.ch/0001/project-thing-1"
    )
  )

  private val hasStandoffLinkToResultsForUser1 = Vector(
    SearchResultRowV1(
      rights = Some(8),
      preview_ny = 32,
      preview_nx = 32,
      value = Vector(
        "A thing that only project members can see",
        "Another thing that only project members can see"
      ),
      valuelabel = Vector(
        "Label",
        "hat Standoff Link zu"
      ),
      valuetype_id = Vector(
        "http://www.w3.org/2000/01/rdf-schema#label",
        "http://www.knora.org/ontology/knora-base#Resource"
      ),
      iconlabel = Some("Ding"),
      icontitle = Some("Ding"),
      iconsrc = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      preview_path = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      obj_id = "http://rdfh.ch/0001/project-thing-1"
    )
  )

  private val hasStandoffLinkToResultsForUser2 = Vector(
    SearchResultRowV1(
      rights = Some(2),
      preview_ny = 32,
      preview_nx = 32,
      value = Vector(
        "A thing that only project members can see",
        "Another thing that only project members can see"
      ),
      valuelabel = Vector(
        "Label",
        "hat Standoff Link zu"
      ),
      valuetype_id = Vector(
        "http://www.w3.org/2000/01/rdf-schema#label",
        "http://www.knora.org/ontology/knora-base#Resource"
      ),
      iconlabel = Some("Ding"),
      icontitle = Some("Ding"),
      iconsrc = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      preview_path = Some("http://0.0.0.0:3335/project-icons/anything/thing.png"),
      obj_id = "http://rdfh.ch/0001/project-thing-1"
    )
  )

}

/**
 * Tests [[SearchResponderV1]].
 */
class SearchResponderV1Spec extends CoreSpec with ImplicitSender {

  import SearchResponderV1Spec._

  override lazy val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula"
    ),
    RdfDataObject(
      path = "test_data/project_data/images-demo-data.ttl",
      name = "http://www.knora.org/data/00FF/images"
    ),
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything"
    )
  )

  // The default timeout for receiving reply messages from actors.
  override implicit val timeout: FiniteDuration = 30.seconds

  // An expected response consisting of two books with the title "Zeitglöcklein des Lebens und Leidens Christi".
  private val twoZeitglöckleinBooksResponse = SearchGetResponseV1(
    thumb_max = SearchPreviewDimensionsV1(
      ny = 32,
      nx = 32
    ),
    paging = Vector(
      SearchResultPage(
        show_nrows = 2,
        start_at = 0,
        current = true
      )
    ),
    nhits = "2",
    subjects = Vector(
      SearchResultRowV1(
        rights = Some(6),
        preview_ny = 32,
        preview_nx = 32,
        value = Vector(
          "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
          "Zeitgl\u00F6cklein des Lebens und Leidens Christi"
        ),
        valuelabel = Vector(
          "Label",
          "Titel"
        ),
        valuetype_id = Vector(
          "http://www.w3.org/2000/01/rdf-schema#label",
          "http://www.knora.org/ontology/knora-base#TextValue"
        ),
        iconlabel = Some("Buch"),
        icontitle = Some("Buch"),
        iconsrc = Some(appConfig.salsah1.baseUrl + appConfig.salsah1.projectIconsBasepath + "incunabula/book.gif"),
        preview_path = Some(appConfig.salsah1.baseUrl + appConfig.salsah1.projectIconsBasepath + "incunabula/book.gif"),
        obj_id = "http://rdfh.ch/0803/c5058f3a"
      ),
      SearchResultRowV1(
        rights = Some(6),
        preview_ny = 32,
        preview_nx = 32,
        value = Vector(
          "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
          "Zeitgl\u00F6cklein des Lebens und Leidens Christi"
        ),
        valuelabel = Vector(
          "Label",
          "Titel"
        ),
        valuetype_id = Vector(
          "http://www.w3.org/2000/01/rdf-schema#label",
          "http://www.knora.org/ontology/knora-base#TextValue"
        ),
        iconlabel = Some("Buch"),
        icontitle = Some("Buch"),
        iconsrc = Some(appConfig.salsah1.baseUrl + appConfig.salsah1.projectIconsBasepath + "incunabula/book.gif"),
        preview_path = Some(appConfig.salsah1.baseUrl + appConfig.salsah1.projectIconsBasepath + "incunabula/book.gif"),
        obj_id = "http://rdfh.ch/0803/ff17e5ef9601"
      )
    )
  )

  "The search responder" should {
    "return 3 results when we do a simple search for the word 'Zeitglöcklein' in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/Zeitglöcklein?searchtype=fulltext
      appActor ! FulltextSearchGetRequestV1(
        searchValue = "Zeitglöcklein",
        userProfile = incunabulaUser,
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        assert(response.subjects.size == 2)
      }

    }

    "return 2 results when we do a simple search for the words 'Zeitglöcklein' and 'Lebens' in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/Zeitglöcklein%20Lebens?searchtype=fulltext
      appActor ! FulltextSearchGetRequestV1(
        searchValue = "Zeitglöcklein AND Lebens",
        userProfile = incunabulaUser,
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        assert(response.subjects.size == 2)
      }
    }

    "return 0 results when we do a simple search for the words 'Zeitglöcklein' for the type incunabula:page in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/Zeitglöcklein%20Lebens?searchtype=fulltext&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page
      appActor ! FulltextSearchGetRequestV1(
        searchValue = "Zeitglöcklein AND Lebens",
        userProfile = incunabulaUser,
        startAt = 0,
        showNRows = 25,
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#page")
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        assert(response.subjects.size == 0)
      }
    }

    "return 1 result when we do a simple search for the word 'Orationes' (the rdfs:label and title of a book) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/Orationes?searchtype=fulltext
      appActor ! FulltextSearchGetRequestV1(
        searchValue = "Orationes",
        userProfile = incunabulaUser,
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        assert(response.subjects.size == 1)
      }
    }

    "return 1 result when we do a simple search for the words 'Olympius AND Methodius' in the Incunabula test data" in {
      appActor ! FulltextSearchGetRequestV1(
        searchValue = "Olympius AND Methodius",
        userProfile = incunabulaUser,
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        assert(response.subjects.size == 1)
      }
    }

    "return 2 books with the title 'Zeitglöcklein des Lebens und Leidens Christi' when we search for book titles containing the string 'Zeitglöcklein' (using a regular expression) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=LIKE&searchval=Zeitglöcklein
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("Zeitglöcklein"),
        compareProps = Vector(SearchComparisonOperatorV1.LIKE),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#title"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25
      )

      expectMsg(timeout, twoZeitglöckleinBooksResponse)
    }

    "return 2 books with the title 'Zeitglöcklein des Lebens und Leidens Christi' when we search for book titles containing the word 'Zeitglöcklein' (using the full-text search index) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=MATCH&searchval=Zeitglöcklein
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("Zeitglöcklein"),
        compareProps = Vector(SearchComparisonOperatorV1.MATCH),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#title"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25
      )

      expectMsg(timeout, twoZeitglöckleinBooksResponse)
    }

    "return 1 book with the title 'Zeitglöcklein des Lebens und Leidens Christi' that was published in 1490 (Julian Calendar) when we search for book titles containing the word 'Zeitglöcklein' (using the full-text search index) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=MATCH&searchval=Zeitglöcklein&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23pubdate&compop=EQ&searchval=JULIAN:1490
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("Zeitglöcklein", "JULIAN:1490"),
        compareProps = Vector(SearchComparisonOperatorV1.MATCH, SearchComparisonOperatorV1.EQ),
        propertyIri = Vector(
          "http://www.knora.org/ontology/0803/incunabula#title",
          "http://www.knora.org/ontology/0803/incunabula#pubdate"
        ),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(1)
      }
    }

    "return 2 books with the title 'Zeitglöcklein des Lebens und Leidens Christi' when we search for book titles containing the word 'Lebens' but not containing the word 'walfart' (using MATCH BOOLEAN) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=MATCH_BOOLEAN&searchval=%2BLebens+-walfart&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("+Lebens -walfart"),
        compareProps = Vector(SearchComparisonOperatorV1.MATCH_BOOLEAN),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#title"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25
      )

      expectMsg(timeout, twoZeitglöckleinBooksResponse)
    }

    /*

        Previously we used a FILTER NOT EXISTS statement here, but it did not return a value for ?anyLiteral
        So now we use just a negated regex
        Problem: if a resource has two instances of the same property and one of them matches and the other does not,
        it will be contained in the search results (now we have 18 instead of 17 books returned)

     */

    "return 18 books when we search for book titles that do not include the string 'Zeitglöcklein' (using a regular expression) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=!LIKE&searchval=Zeitgl%C3%B6cklein
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("Zeitglöcklein"),
        compareProps = Vector(SearchComparisonOperatorV1.NOT_LIKE),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#title"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(18)
      }
    }

    "return 2 books with the title 'Zeitglöcklein des Lebens und Leidens Christi' when we search for exactly that book title in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=EQ&searchval=Zeitgl%C3%B6cklein%20des%20Lebens%20und%20Leidens%20Christi
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("Zeitglöcklein des Lebens und Leidens Christi"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#title"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25
      )

      expectMsg(timeout, twoZeitglöckleinBooksResponse)
    }

    "return 18 books when we search for all books that have a title that is not exactly 'Zeitglöcklein des Lebens und Leidens Christi' (although they may have another title that is) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=!EQ&searchval=Zeitgl%C3%B6cklein%20des%20Lebens%20und%20Leidens%20Christi
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("Zeitglöcklein des Lebens und Leidens Christi"),
        compareProps = Vector(SearchComparisonOperatorV1.NOT_EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#title"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(18)
      }
    }

    "return 19 books when we search for all books in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector(),
        compareProps = Vector(),
        propertyIri = Vector(),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(19)
      }
    }

    "return 19 books when we search for all books that have a title in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=EXISTS&searchval
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector(""),
        compareProps = Vector(SearchComparisonOperatorV1.EXISTS),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#title"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(19)
      }
    }

    "return 19 pages when we search for all pages that have a sequence number of 1 in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23seqnum&compop=EQ&searchval=1
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("1"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#seqnum"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#page"),
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(19)
      }
    }

    "return 79 pages when we search for all pages that have an incunabula:seqnum greater than 450 in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23seqnum&compop=GT&searchval=450
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("450"),
        compareProps = Vector(SearchComparisonOperatorV1.GT),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#seqnum"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#page"),
        startAt = 0,
        showNRows = 100
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(79)
      }
    }

    "return 79 pages when we search for all representations that have an incunabula:seqnum greater than 450 in the Incunabula test data" in {
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("450"),
        compareProps = Vector(SearchComparisonOperatorV1.GT),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#seqnum"),
        filterByRestype = Some("http://www.knora.org/ontology/knora-base#Representation"),
        startAt = 0,
        showNRows = 100
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(79)
      }
    }

    "return 2 books when we search for all books that were published in January 1495 (Julian date) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23seqnum&compop=EQ&searchval=1
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("JULIAN:1495-01"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#pubdate"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(2)
      }
    }

    "return 7 books when we search for all books whose publication date is greater than or equal to January 1495 (Julian date) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23pubdate&compop=GT_EQ&searchval=JULIAN:1495-01
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("JULIAN:1495-01"),
        compareProps = Vector(SearchComparisonOperatorV1.GT_EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#pubdate"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(7)
      }
    }

    "return 15 books when we search for all books whose publication date is less than or equal to December 1495 (Julian date) in the Incunabula test data" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23pubdate&compop=LT_EQ&searchval=JULIAN:1495-12
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("JULIAN:1495-12"),
        compareProps = Vector(SearchComparisonOperatorV1.LT_EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#pubdate"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#book"),
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(15)
      }
    }

    "return all the pages that are part of Zeitglöcklein des Lebens" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23partOf&compop=EQ&searchval=http%3A%2F%2Frdfh.ch%2Fc5058f3a
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/0803/c5058f3a"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0803/incunabula#partOf"),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#page"),
        startAt = 0,
        showNRows = 500
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(402)
      }
    }

    "return all the pages that have a sequence number of 1 and are part of some book" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23seqnum&compop=EQ&searchval=1&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23partOf&compop=EXISTS&searchval=
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("1", ""),
        compareProps = Vector(SearchComparisonOperatorV1.EQ, SearchComparisonOperatorV1.EXISTS),
        propertyIri = Vector(
          "http://www.knora.org/ontology/0803/incunabula#seqnum",
          "http://www.knora.org/ontology/0803/incunabula#partOf"
        ),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#page"),
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(19)
      }
    }

    "return all the representations that have a sequence number of 1 and are part of some book (using knora-base:isPartOf)" in {
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("1", ""),
        compareProps = Vector(SearchComparisonOperatorV1.EQ, SearchComparisonOperatorV1.EXISTS),
        propertyIri = Vector(
          "http://www.knora.org/ontology/0803/incunabula#seqnum",
          "http://www.knora.org/ontology/knora-base#isPartOf"
        ),
        filterByRestype = Some("http://www.knora.org/ontology/knora-base#Representation"),
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(19)
      }
    }

    "return all the pages that are part of Zeitglöcklein des Lebens and have a seqnum" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23partOf&compop=EQ&searchval=http%3A%2F%2Frdfh.ch%2Fc5058f3a&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23seqnum&compop=EXISTS&searchval=
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/0803/c5058f3a", ""),
        compareProps = Vector(SearchComparisonOperatorV1.EQ, SearchComparisonOperatorV1.EXISTS),
        propertyIri = Vector(
          "http://www.knora.org/ontology/0803/incunabula#partOf",
          "http://www.knora.org/ontology/0803/incunabula#seqnum"
        ),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#page"),
        startAt = 0,
        showNRows = 500
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(402)
      }

    }

    "return all the representations that are part of Zeitglöcklein des Lebens and have a seqnum (using base properties from knora-base)" in {
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/0803/c5058f3a", ""),
        compareProps = Vector(SearchComparisonOperatorV1.EQ, SearchComparisonOperatorV1.EXISTS),
        propertyIri = Vector(
          "http://www.knora.org/ontology/knora-base#isPartOf",
          "http://www.knora.org/ontology/knora-base#seqnum"
        ),
        filterByRestype = Some("http://www.knora.org/ontology/knora-base#Representation"),
        startAt = 0,
        showNRows = 500
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(402)
      }

    }

    "return all the pages that are part of Zeitglöcklein des Lebens, have a seqnum less than or equal to 200, and have a page number that is not 'a1r, Titelblatt'" in {
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/0803/c5058f3a", "200", "a1r, Titelblatt"),
        compareProps =
          Vector(SearchComparisonOperatorV1.EQ, SearchComparisonOperatorV1.LT_EQ, SearchComparisonOperatorV1.NOT_EQ),
        propertyIri = Vector(
          "http://www.knora.org/ontology/0803/incunabula#partOf",
          "http://www.knora.org/ontology/0803/incunabula#seqnum",
          "http://www.knora.org/ontology/0803/incunabula#pagenum"
        ),
        filterByRestype = Some("http://www.knora.org/ontology/0803/incunabula#page"),
        startAt = 0,
        showNRows = 300
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(199)
      }

    }

    "return all the images from the images-demo project whose title belong to the category 'Sport'" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&property_id%5B%5D=http%3A%2F%2Fwww.knora.org%2Fontology%2Fimages%23titel&compop%5B%5D=EQ&searchval%5B%5D=http%3A%2F%2Frdfh.ch%2Flists%2F71a1543cce&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fimages%23bild
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/lists/00FF/71a1543cce"), // list node SPORT
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector(IMAGES_TITEL_PROPERTY),
        filterByRestype = Some(IMAGES_BILD_RESOURCE_CLASS),
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(10)
      }

    }

    "return all the images from the images-demo project whose title belong to the category 'Spazieren'" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&property_id%5B%5D=http%3A%2F%2Fwww.knora.org%2Fontology%2Fimages%23titel&compop%5B%5D=EQ&searchval%5B%5D=http%3A%2F%2Frdfh.ch%2Flists%2F38c73482e3&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fimages%23bild
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/lists/00FF/38c73482e3"), // list node SPAZIEREN
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector(IMAGES_TITEL_PROPERTY),
        filterByRestype = Some(IMAGES_BILD_RESOURCE_CLASS),
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(1)
      }

    }

    "return all the images from the images-demo project whose title belong to the category 'Alpinismus'" in {
      // http://0.0.0.0:3333/v1/search/?searchtype=extended&property_id%5B%5D=http%3A%2F%2Fwww.knora.org%2Fontology%2Fimages%23titel&compop%5B%5D=EQ&searchval%5B%5D=http%3A%2F%2Frdfh.ch%2Flists%2F3bc59463e2&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fimages%23bild
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/lists/00FF/3bc59463e2"), // list node ALPINISMUS
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector(IMAGES_TITEL_PROPERTY),
        filterByRestype = Some(IMAGES_BILD_RESOURCE_CLASS),
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(4)
      }

    }

    "filter full-text search results using permissions on resources and values" in {
      // When the owner of the resource and its values, anythingUser1, searches for something that matches the resource's label
      // as well as both values, the search result should include the resource and show that both values matched.

      appActor ! FulltextSearchGetRequestV1(
        searchValue = "die AND Dinge",
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        userProfile = anythingUser1,
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects should ===(fulltextThingResultsForUser1)
      }

      // Another user in the same project, anythingUser2, should get the resource as a search result, but should not see the values.

      appActor ! FulltextSearchGetRequestV1(
        searchValue = "die AND Dinge",
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        userProfile = anythingUser2,
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects should ===(fulltextThingResultsForUser2)
      }

      // User anythingUser2 should also get the resource as a search result by searching for something that matches the resource's label, but not the values.

      appActor ! FulltextSearchGetRequestV1(
        searchValue = "für AND jemanden,",
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        userProfile = anythingUser2,
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects should ===(fulltextThingResultsForUser2)
      }

      // If user anythingUser1 searches for something that matches one of the values, but doesn't match the resource's label, the result should include the
      // value that matched, but not the value that didn't match.

      appActor ! FulltextSearchGetRequestV1(
        searchValue = "alles AND für AND sind",
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        userProfile = anythingUser1,
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects should ===(fulltextValueInThingResultsForUser1)
      }

      // If user anythingUser2 searches for something that matches one of the values, but doesn't match the resource's label, no results should be returned.

      appActor ! FulltextSearchGetRequestV1(
        searchValue = "alles AND für AND mich",
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        userProfile = anythingUser2,
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(0)
      }

      // A user in another project shouldn't get any results for any of those queries.

      appActor ! FulltextSearchGetRequestV1(
        searchValue = "die AND Dinge",
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        userProfile = incunabulaUser,
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(0)
      }

      appActor ! FulltextSearchGetRequestV1(
        searchValue = "für AND jemanden",
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        userProfile = incunabulaUser,
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(0)
      }

      appActor ! FulltextSearchGetRequestV1(
        searchValue = "alles AND für AND mich",
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        userProfile = incunabulaUser,
        startAt = 0,
        showNRows = 25
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(0)
      }
    }

    "should not show resources that the user doesn't have permission to see in an extended search" in {
      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/project-thing-2"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0001/anything#hasOtherThing"),
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        startAt = 0,
        showNRows = 10
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(0)
      }

      appActor ! ExtendedSearchGetRequestV1(
        userProfile = incunabulaUser,
        searchValue = Vector("http://rdfh.ch/project-thing-2"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"),
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        startAt = 0,
        showNRows = 10
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(0)
      }
    }

    "should show standoff links if the user has view permission on both resources, but show other links only if the user also has view permission on the link" in {
      // The link's owner, anythingUser1, should see the hasOtherThing link as well as the hasStandoffLinkTo link.

      appActor ! ExtendedSearchGetRequestV1(
        userProfile = anythingUser1,
        searchValue = Vector("http://rdfh.ch/0001/project-thing-2"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0001/anything#hasOtherThing"),
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        startAt = 0,
        showNRows = 10
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects should ===(hasOtherThingResultsForUser1)
      }

      appActor ! ExtendedSearchGetRequestV1(
        userProfile = anythingUser1,
        searchValue = Vector("http://rdfh.ch/0001/project-thing-2"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"),
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        startAt = 0,
        showNRows = 10
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects should ===(hasStandoffLinkToResultsForUser1)
      }

      // But another user in the Anything project should see only the hasStandoffLinkTo link.

      appActor ! ExtendedSearchGetRequestV1(
        userProfile = anythingUser2,
        searchValue = Vector("http://rdfh.ch/0001/project-thing-2"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/0001/anything#hasOtherThing"),
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        startAt = 0,
        showNRows = 10
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects.size should ===(0)
      }

      appActor ! ExtendedSearchGetRequestV1(
        userProfile = anythingUser2,
        searchValue = Vector("http://rdfh.ch/0001/project-thing-2"),
        compareProps = Vector(SearchComparisonOperatorV1.EQ),
        propertyIri = Vector("http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"),
        filterByRestype = Some("http://www.knora.org/ontology/0001/anything#Thing"),
        startAt = 0,
        showNRows = 10
      )

      expectMsgPF(timeout) { case response: SearchGetResponseV1 =>
        response.subjects should ===(hasStandoffLinkToResultsForUser2)
      }
    }

  }
}
