package org.knora.webapi.e2e.v2

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.knora.webapi._
import org.knora.webapi.e2e.{ClientTestDataCollector, TestDataFileContent, TestDataFilePath}
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.v2.responder.ontologymessages.{InputOntologyV2, TestResponseParsingModeV2}
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.routing.v2.{OntologiesRouteV2, ResourcesRouteV2}
import org.knora.webapi.sharedtestdata.{SharedOntologyTestDataADM, SharedTestDataADM}
import org.knora.webapi.util._

import java.net.URLEncoder
import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import scala.concurrent.ExecutionContextExecutor

object tempE2eSpec {
  private val anythingUserProfile = SharedTestDataADM.anythingAdminUser
  private val anythingUsername    = anythingUserProfile.email
  private val password            = SharedTestDataADM.testPass
}

/**
 * End-to-end test specification for API v2 ontology routes.
 */
class tempE2eSpec extends R2RSpec {

  import tempE2eSpec._
  override lazy val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/ontologies/example-box.ttl",
      name = "http://www.knora.org/ontology/shared/example-box"
    ),
    RdfDataObject(path = "test_data/ontologies/minimal-onto.ttl", name = "http://www.knora.org/ontology/0001/minimal"),
    RdfDataObject(
      path = "test_data/ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest"
    ),
    RdfDataObject(path = "test_data/all_data/freetest-data.ttl", name = "http://www.knora.org/data/0001/freetest")
  )

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  private val ontologiesPath                            = DSPApiDirectives.handleErrors(system)(new OntologiesRouteV2(routeData).knoraApiPath)

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("v2", "ontologies")
  // Collects client test data
  private val clientTestDataCollector = new ClientTestDataCollector(settings)
  // URL-encoded IRIs for use as URL segments in HTTP GET tests.
  private var anythingLastModDate: Instant = Instant.parse("2017-12-19T15:23:42.166Z")

  override def testConfigSource: String =
    """
      |# akka.loglevel = "DEBUG"
      |# akka.stdout-loglevel = "DEBUG"
        """.stripMargin

  private def getPropertyIrisFromResourceClassResponse(responseJsonDoc: JsonLDDocument): Set[SmartIri] = {
    val classDef = responseJsonDoc.body.requireArray("@graph").value.head.asInstanceOf[JsonLDObject]

    classDef
      .value(OntologyConstants.Rdfs.SubClassOf)
      .asInstanceOf[JsonLDArray]
      .value
      .collect {
        case obj: JsonLDObject if !obj.isIri =>
          obj.requireIriInObject(OntologyConstants.Owl.OnProperty, stringFormatter.toSmartIriWithErr)
      }
      .toSet
  }

  /**
   * Represents an HTTP GET test that requests ontology information.
   *
   * @param urlPath                     the URL path to be used in the request.
   * @param fileBasename                the basename of the test data file containing the expected response.
   * @param maybeClientTestDataBasename the basename of the client test data file, if any, to be collected by
   *                                    [[org.knora.webapi.e2e.ClientTestDataCollector]].
   * @param disableWrite                if true, this [[HttpGetTest]] will not write the expected response file when `writeFile` is called.
   *                                    This is useful if two tests share the same file.
   */
  private case class HttpGetTest(
    urlPath: String,
    fileBasename: String,
    maybeClientTestDataBasename: Option[String] = None,
    disableWrite: Boolean = false
  ) {}

  "The Ontologies v2 Endpoint" should {

    "create a class anything:Book with no properties" in {
      val params =
        s"""
           |{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Book",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "book"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "Represents a book"
           |    },
           |    "rdfs:subClassOf" : {
           |      "@id" : "knora-api:Resource"
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}
            """.stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-class-without-cardinalities-request",
            fileExtension = "json"
          ),
          text = params
        )
      )

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes should ===(paramsAsInput.classes)

        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-class-without-cardinalities-response",
              fileExtension = "json"
            ),
            text = responseStr
          )
        )
      }
    }

    "create a class anything:Page with no properties" in {
      val params =
        s"""
           |{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Page",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "Page"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "Represents a page"
           |    },
           |    "rdfs:subClassOf" : {
           |      "@id" : "knora-api:Resource"
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}
            """.stripMargin

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-class-without-cardinalities-request",
            fileExtension = "json"
          ),
          text = params
        )
      )

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes should ===(paramsAsInput.classes)

        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-class-without-cardinalities-response",
              fileExtension = "json"
            ),
            text = responseStr
          )
        )
      }
    }

    "create a property anything:hasPage with knora-api:objectType anything:Book" in {

      val params =
        s"""
           |{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasPage",
           |    "@type" : "owl:ObjectProperty",
           |    "knora-api:subjectType" : {
           |      "@id" : "anything:Book"
           |    },
           |    "knora-api:objectType" : {
           |      "@id" : "anything:Page"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "A book has a page."
           |    },
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "Has page"
           |    },
           |    "rdfs:subPropertyOf" : {
           |      "@id" : "knora-api:hasLinkTo"
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}
            """.stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.properties should ===(paramsAsInput.properties)

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "add a cardinality for the property anything:hasPage to the class anything:Book" in {
      val params =
        s"""
           |{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Book",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : {
           |      "@type": "owl:Restriction",
           |      "owl:minCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:hasPage"
           |      }
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}
            """.stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      val paramsWithAddedLinkValueCardinality = paramsAsInput.copy(
        classes = paramsAsInput.classes.map { case (classIri, classDef) =>
          val hasPageValueCardinality =
            "http://0.0.0.0:3333/ontology/0001/anything/v2#hasPageValue".toSmartIri ->
              classDef.directCardinalities("http://0.0.0.0:3333/ontology/0001/anything/v2#hasPage".toSmartIri)

          classIri -> classDef.copy(
            directCardinalities = classDef.directCardinalities + hasPageValueCardinality
          )
        }
      )

      Post("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.directCardinalities should ===(
          paramsWithAddedLinkValueCardinality.classes.head._2.directCardinalities
        )

        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "should have added isEditable in newly added link property value" in {
      val url = URLEncoder.encode(s"${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}", "UTF-8")
      Get(
        s"/v2/ontologies/allentities/${url}"
      ) ~> ontologiesPath ~> check {
        val responseStr: String = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        val graph = responseJsonDoc.body.requireArray("@graph").value //.head.asInstanceOf[JsonLDObject]

        val hasPageValue = graph
          .filter(
            _.asInstanceOf[JsonLDObject]
              .value("@id")
              .asInstanceOf[JsonLDString]
              .value == "http://0.0.0.0:3333/ontology/0001/anything/v2#hasPageValue"
          )
          .head
          .asInstanceOf[JsonLDObject]

        val iris = hasPageValue.value.keySet

        val expectedIris = Set(
          OntologyConstants.Rdfs.Comment,
          OntologyConstants.Rdfs.Label,
          OntologyConstants.Rdfs.SubPropertyOf,
          OntologyConstants.KnoraApiV2Complex.IsEditable,
          OntologyConstants.KnoraApiV2Complex.IsResourceProperty,
          OntologyConstants.KnoraApiV2Complex.IsLinkValueProperty,
          OntologyConstants.KnoraApiV2Complex.ObjectType,
          OntologyConstants.KnoraApiV2Complex.SubjectType,
          "@id",
          "@type"
        )

        iris should equal(expectedIris)
      }
    }

  }
}
