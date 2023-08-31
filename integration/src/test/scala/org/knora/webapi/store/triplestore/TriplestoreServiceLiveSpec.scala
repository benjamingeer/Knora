/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore

import akka.testkit.ImplicitSender

import scala.concurrent.duration._

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.rdf.SparqlSelectResult

class TriplestoreServiceLiveSpec extends CoreSpec with ImplicitSender {

  override implicit val timeout: FiniteDuration = 30.seconds

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject("test_data/project_data/anything-data.ttl", "http://www.knora.org/data/0001/anything")
  )

  val countTriplesQuery: String =
    """
        SELECT (COUNT(*) AS ?no)
        WHERE
            {
                ?s ?p ?o .
            }
        """

  val namedGraphQuery: String =
    """
        SELECT ?namedGraph ?s ?p ?o ?lang
        WHERE {
                {
              GRAPH ?namedGraph {
                BIND(IRI("http://www.knora.org/ontology/0001/anything#Thing") as ?s)
                ?s ?p ?obj
                BIND(str(?obj) as ?o)
                BIND(lang(?obj) as ?lang)
              }
            }
        }
        """.stripMargin

  val insertQuery: String =
    """
        prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        prefix sub: <http://subotic.org/#>

        INSERT DATA
        {
            GRAPH <http://subotic.org/graph>
            {
                <http://ivan> sub:tries "something" ;
                              sub:hopes "success" ;
                              rdf:type sub:Me .
            }
        }
        """

  val graphDataContent: String =
    """
        prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        prefix jedi: <http://jedi.org/#>

        <http://luke> jedi:tries "force for the first time" ;
                      jedi:hopes "to power the lightsaber" ;
                      rdf:type jedi:Skywalker .
        """

  val checkInsertQuery: String =
    """
        prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        prefix sub: <http://subotic.org/#>

        SELECT *
        WHERE {
            GRAPH <http://subotic.org/graph>
            {
                ?s rdf:type sub:Me .
                ?s ?p ?o .
            }
        }
        """

  val revertInsertQuery: String =
    """
        prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        prefix sub: <http://subotic.org/#>

        WITH <http://subotic.org/graph>
        DELETE { ?s ?p ?o }
        WHERE
        {
            ?s rdf:type sub:Me .
            ?s ?p ?o .
        }
        """

  val searchURI: String = "<http://jena.apache.org/text#query>"

  val textSearchQueryFusekiValueHasString: String =
    s"""
        PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

        SELECT DISTINCT *
        WHERE {
            ?iri <http://jena.apache.org/text#query> 'test' .
            ?iri knora-base:valueHasString ?literal .
        }
    """

  val textSearchQueryFusekiDRFLabel: String =
    s"""
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

        SELECT DISTINCT *
        WHERE {
            ?iri <http://jena.apache.org/text#query> 'Papa' .
            ?iri rdfs:label ?literal .
        }
    """

  var afterLoadCount: Int         = -1
  var afterChangeCount: Int       = -1
  var afterChangeRevertCount: Int = -1

  "The TriplestoreServiceManager" should {

    "reset the data after receiving a 'ResetTriplestoreContent' request" in {
      appActor ! ResetRepositoryContent(rdfDataObjects)
      expectMsg(5.minutes, ())

      appActor ! SparqlSelectRequest(countTriplesQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        afterLoadCount = msg.results.bindings.head.rowMap("no").toInt
        (afterLoadCount > 0) should ===(true)
      }
    }

    "provide data receiving a Named Graph request" in {
      appActor ! SparqlSelectRequest(namedGraphQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        msg.results.bindings.nonEmpty should ===(true)
      }
    }

    "execute an update" in {
      appActor ! SparqlSelectRequest(countTriplesQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        msg.results.bindings.head.rowMap("no").toInt should ===(afterLoadCount)
      }

      appActor ! SparqlUpdateRequest(insertQuery)
      expectMsg(())

      appActor ! SparqlSelectRequest(checkInsertQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        msg.results.bindings.size should ===(3)
      }

      appActor ! SparqlSelectRequest(countTriplesQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        afterChangeCount = msg.results.bindings.head.rowMap("no").toInt
        (afterChangeCount - afterLoadCount) should ===(3)
      }
    }

    "revert back " in {
      appActor ! SparqlSelectRequest(countTriplesQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        msg.results.bindings.head.rowMap("no").toInt should ===(afterChangeCount)
      }

      appActor ! SparqlUpdateRequest(revertInsertQuery)
      expectMsg(())

      appActor ! SparqlSelectRequest(countTriplesQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        msg.results.bindings.head.rowMap("no").toInt should ===(afterLoadCount)
      }

      appActor ! SparqlSelectRequest(checkInsertQuery)
      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        msg.results.bindings.size should ===(0)
      }
    }

    "execute the search with the lucene index for 'knora-base:valueHasString' properties" in {
      within(1000.millis) {
        appActor ! SparqlSelectRequest(textSearchQueryFusekiValueHasString)
        expectMsgPF(timeout) { case msg: SparqlSelectResult =>
          msg.results.bindings.size should ===(3)
        }
      }
    }

    "execute the search with the lucene index for 'rdfs:label' properties" in {
      within(1000.millis) {
        appActor ! SparqlSelectRequest(textSearchQueryFusekiDRFLabel)
        expectMsgPF(timeout) { case msg: SparqlSelectResult =>
          msg.results.bindings.size should ===(1)
        }
      }
    }

    "insert RDF DataObjects" in {
      appActor ! InsertRepositoryContent(rdfDataObjects)
      expectMsg(5.minutes, ())
    }

    "put the graph data as turtle" in {
      appActor ! InsertGraphDataContentRequest(graphContent = graphDataContent, "http://jedi.org/graph")
      expectMsgType[Unit](10.second)
    }

    "read the graph data as turtle" in {
      appActor ! NamedGraphDataRequest(graphIri = "http://jedi.org/graph")
      val response = expectMsgType[NamedGraphDataResponse](1.second)
      response.turtle.length should be > 0
    }
  }
}
