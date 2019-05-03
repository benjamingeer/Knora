package org.knora.webapi.e2e.v2

import java.io.File
import java.net.URLEncoder
import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Accept, BasicHttpCredentials}
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v2.responder.ontologymessages.{InputOntologyV2, TestResponseParsingModeV2}
import org.knora.webapi.routing.v2.OntologiesRouteV2
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util._
import org.knora.webapi.util.jsonld._
import spray.json._

import scala.concurrent.ExecutionContextExecutor

object OntologyV2R2RSpec {
    private val imagesUserProfile = SharedTestDataADM.imagesUser01
    private val imagesUsername = imagesUserProfile.email
    private val imagesProjectIri = SharedTestDataADM.IMAGES_PROJECT_IRI

    private val anythingUserProfile = SharedTestDataADM.anythingAdminUser
    private val anythingUsername = anythingUserProfile.email

    private val superUserProfile = SharedTestDataADM.superUser
    private val superUsername = superUserProfile.email

    private val password = "test"
}

/**
  * End-to-end test specification for API v2 ontology routes.
  */
class OntologyV2R2RSpec extends R2RSpec {

    import OntologyV2R2RSpec._

    override def testConfigSource: String =
        """
          |# akka.loglevel = "DEBUG"
          |# akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val ontologiesPath = new OntologiesRouteV2(routeData).knoraApiPath

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    // If true, the existing expected response files are overwritten with the HTTP GET responses from the server.
    // If false, the responses from the server are compared to the contents fo the expected response files.
    private val writeGetTestResponses = false

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/ontologies/example-box.ttl", name = "http://www.knora.org/ontology/shared/example-box")
    )

    /**
      * Represents an HTTP GET test that requests ontology information.
      *
      * @param urlPath      the URL path to be used in the request.
      * @param fileBasename the basename of the test data file containing the expected response.
      * @param disableWrite if true, this [[HttpGetTest]] will not write the expected response file when `writeFile` is called.
      *                     This is useful if two tests share the same file.
      */
    private case class HttpGetTest(urlPath: String, fileBasename: String, disableWrite: Boolean = false) {
        private def makeFile(mediaType: MediaType.NonBinary): File = {
            val fileSuffix = mediaType.fileExtensions.head
            new File(s"src/test/resources/test-data/ontologyR2RV2/$fileBasename.$fileSuffix")
        }

        /**
          * Writes the expected response file.
          *
          * @param responseStr the contents of the file to be written.
          * @param mediaType   the media type of the response.
          */
        def writeFile(responseStr: String, mediaType: MediaType.NonBinary): Unit = {
            if (!disableWrite) {
                FileUtil.writeTextFile(makeFile(mediaType), responseStr)
            }
        }

        /**
          * Reads the expected response file.
          *
          * @param mediaType the media type of the response.
          * @return the contents of the file.
          */
        def readFile(mediaType: MediaType.NonBinary): String = {
            FileUtil.readTextFile(makeFile(mediaType))
        }
    }

    // URL-encoded IRIs for use as URL segments in HTTP GET tests.
    private val imagesProjectSegment = URLEncoder.encode(imagesProjectIri, "UTF-8")
    private val knoraApiSimpleOntologySegment = URLEncoder.encode(OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri, "UTF-8")
    private val knoraApiWithValueObjectsOntologySegment = URLEncoder.encode(OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri, "UTF-8")
    private val incunabulaOntologySimpleSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2", "UTF-8")
    private val incunabulaOntologyWithValueObjectsSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2", "UTF-8")
    private val knoraApiDateSegment = URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2#Date", "UTF-8")
    private val knoraApiDateValueSegment = URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2#DateValue", "UTF-8")
    private val knoraApiSimpleHasColorSegment = URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2#hasColor", "UTF-8")
    private val knoraApiWithValueObjectsHasColorSegment = URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2#hasColor", "UTF-8")
    private val incunabulaSimplePubdateSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate", "UTF-8")
    private val incunabulaWithValueObjectsPubDateSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#pubdate", "UTF-8")
    private val incunabulaWithValueObjectsPageSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#page", "UTF-8")
    private val incunabulaWithValueObjectsBookSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book", "UTF-8")
    private val boxOntologyWithValueObjectsSegment = URLEncoder.encode("http://api.knora.org/ontology/shared/example-box/v2", "UTF-8")

    // The URLs and expected response files for each HTTP GET test.
    private val httpGetTests = Seq(
        HttpGetTest("/v2/ontologies/metadata", "allOntologyMetadata"),
        HttpGetTest(s"/v2/ontologies/metadata/$imagesProjectSegment", "imagesOntologyMetadata"),
        HttpGetTest(s"/v2/ontologies/allentities/$knoraApiSimpleOntologySegment", "knoraApiOntologySimple"),
        HttpGetTest("/ontology/knora-api/simple/v2", "knoraApiOntologySimple", disableWrite = true),
        HttpGetTest(s"/v2/ontologies/allentities/$knoraApiWithValueObjectsOntologySegment", "knoraApiOntologyWithValueObjects"),
        HttpGetTest("/ontology/knora-api/v2", "knoraApiOntologyWithValueObjects", disableWrite = true),
        HttpGetTest("/ontology/salsah-gui/v2", "salsahGuiOntology"),
        HttpGetTest("/ontology/standoff/v2", "standoffOntologyWithValueObjects"),
        HttpGetTest(s"/v2/ontologies/allentities/$incunabulaOntologySimpleSegment", "incunabulaOntologySimple"),
        HttpGetTest(s"/v2/ontologies/allentities/$incunabulaOntologyWithValueObjectsSegment", "incunabulaOntologyWithValueObjects"),
        HttpGetTest(s"/v2/ontologies/classes/$knoraApiDateSegment", "knoraApiDate"),
        HttpGetTest(s"/v2/ontologies/classes/$knoraApiDateValueSegment", "knoraApiDateValue"),
        HttpGetTest(s"/v2/ontologies/properties/$knoraApiSimpleHasColorSegment", "knoraApiSimpleHasColor"),
        HttpGetTest(s"/v2/ontologies/properties/$knoraApiWithValueObjectsHasColorSegment", "knoraApiWithValueObjectsHasColor"),
        HttpGetTest(s"/v2/ontologies/properties/$incunabulaSimplePubdateSegment", "incunabulaSimplePubDate"),
        HttpGetTest(s"/v2/ontologies/properties/$incunabulaWithValueObjectsPubDateSegment", "incunabulaWithValueObjectsPubDate"),
        HttpGetTest(s"/v2/ontologies/classes/$incunabulaWithValueObjectsPageSegment/$incunabulaWithValueObjectsBookSegment", "incunabulaPageAndBookWithValueObjects"),
        HttpGetTest(s"/v2/ontologies/allentities/$boxOntologyWithValueObjectsSegment", "boxOntologyWithValueObjects")
    )

    // The media types that will be used in HTTP Accept headers in HTTP GET tests.
    private val mediaTypesForGetTests = Seq(
        RdfMediaTypes.`application/ld+json`,
        RdfMediaTypes.`text/turtle`,
        RdfMediaTypes.`application/rdf+xml`
    )

    private val fooIri = new MutableTestIri
    private var fooLastModDate: Instant = Instant.now

    private val AnythingOntologyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri
    private var anythingLastModDate: Instant = Instant.parse("2017-12-19T15:23:42.166Z")

    private val uselessIri = new MutableTestIri
    private var uselessLastModDate: Instant = Instant.now

    private def getPropertyIrisFromResourceClassResponse(responseJsonDoc: JsonLDDocument): Set[SmartIri] = {
        val classDef = responseJsonDoc.body.requireArray("@graph").value.head.asInstanceOf[JsonLDObject]

        classDef.value(OntologyConstants.Rdfs.SubClassOf).asInstanceOf[JsonLDArray].value.collect {
            case obj: JsonLDObject if !obj.isIri => obj.requireIriInObject(OntologyConstants.Owl.OnProperty, stringFormatter.toSmartIriWithErr)
        }.toSet
    }

    "The Ontologies v2 Endpoint" should {

        "serve ontology data in different formats" in {
            // Iterate over the HTTP GET tests.
            for (httpGetTest <- httpGetTests) {

                // Do each test with each media type.
                for (mediaType <- mediaTypesForGetTests) {

                    Get(httpGetTest.urlPath).addHeader(Accept(mediaType)) ~> ontologiesPath ~> check {

                        // Are we writing expected response files?
                        if (writeGetTestResponses) {
                            // Yes.
                            httpGetTest.writeFile(responseAs[String], mediaType)
                        } else {
                            // No. Compare the received response with the expected response.
                            mediaType match {
                                case RdfMediaTypes.`application/ld+json` =>
                                    assert(JsonParser(responseAs[String]) == JsonParser(httpGetTest.readFile(mediaType)))

                                case RdfMediaTypes.`text/turtle` =>
                                    assert(parseTurtle(responseAs[String]) == parseTurtle(httpGetTest.readFile(mediaType)))

                                case RdfMediaTypes.`application/rdf+xml` => ()
                                    assert(parseRdfXml(responseAs[String]) == parseRdfXml(httpGetTest.readFile(mediaType)))

                                case _ => throw AssertionException(s"Unsupported media type for test: $mediaType")
                            }
                        }
                    }
                }
            }
        }

        "create an empty ontology called 'foo' with a project code" in {
            val label = "The foo ontology"

            val params =
                s"""
                   |{
                   |    "knora-api:ontologyName": "foo",
                   |    "knora-api:attachedToProject": {
                   |      "@id": "$imagesProjectIri"
                   |    },
                   |    "rdfs:label": "$label",
                   |    "@context": {
                   |        "rdfs": "${OntologyConstants.Rdfs.RdfsPrefixExpansion}",
                   |        "knora-api": "${OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion}"
                   |    }
                   |}
                """.stripMargin

            Post("/v2/ontologies", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(imagesUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                val metadata = responseJsonDoc.body
                val ontologyIri = metadata.value("@id").asInstanceOf[JsonLDString].value
                assert(ontologyIri == "http://0.0.0.0:3333/ontology/00FF/foo/v2")
                fooIri.set(ontologyIri)
                assert(metadata.value(OntologyConstants.Rdfs.Label) == JsonLDString(label))
                val lastModDate = Instant.parse(metadata.value(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate).asInstanceOf[JsonLDString].value)
                fooLastModDate = lastModDate
            }
        }

        "change the metadata of 'foo'" in {
            val newLabel = "The modified foo ontology"

            val params =
                s"""
                   |{
                   |  "@id": "${fooIri.get}",
                   |  "rdfs:label": "$newLabel",
                   |  "knora-api:lastModificationDate": "$fooLastModDate",
                   |  "@context": {
                   |    "rdfs": "${OntologyConstants.Rdfs.RdfsPrefixExpansion}",
                   |    "knora-api": "${OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion}"
                   |  }
                   |}
                """.stripMargin

            Put("/v2/ontologies/metadata", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(imagesUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                val metadata = responseJsonDoc.body
                val ontologyIri = metadata.value("@id").asInstanceOf[JsonLDString].value
                assert(ontologyIri == fooIri.get)
                assert(metadata.value(OntologyConstants.Rdfs.Label) == JsonLDString(newLabel))
                val lastModDate = Instant.parse(metadata.value(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate).asInstanceOf[JsonLDString].value)
                assert(lastModDate.isAfter(fooLastModDate))
                fooLastModDate = lastModDate
            }
        }

        "delete the 'foo' ontology" in {
            val fooIriEncoded = URLEncoder.encode(fooIri.get, "UTF-8")
            val lastModificationDate = URLEncoder.encode(fooLastModDate.toString, "UTF-8")

            Delete(s"/v2/ontologies/$fooIriEncoded?lastModificationDate=$lastModificationDate") ~> addCredentials(BasicHttpCredentials(imagesUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
            }
        }

        "create a property anything:hasName as a subproperty of knora-api:hasValue and schema:name" in {
            val params =
                """
                  |{
                  |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                  |  "@type" : "owl:Ontology",
                  |  "knora-api:lastModificationDate" : "2017-12-19T15:23:42.166Z",
                  |  "@graph" : [ {
                  |      "@id" : "anything:hasName",
                  |      "@type" : "owl:ObjectProperty",
                  |      "knora-api:subjectType" : {
                  |        "@id" : "anything:Thing"
                  |      },
                  |      "knora-api:objectType" : {
                  |        "@id" : "knora-api:TextValue"
                  |      },
                  |      "rdfs:comment" : [ {
                  |        "@language" : "en",
                  |        "@value" : "The name of a Thing"
                  |      }, {
                  |        "@language" : "de",
                  |        "@value" : "Der Name eines Dinges"
                  |      } ],
                  |      "rdfs:label" : [ {
                  |        "@language" : "en",
                  |        "@value" : "has name"
                  |      }, {
                  |        "@language" : "de",
                  |        "@value" : "hat Namen"
                  |      } ],
                  |      "rdfs:subPropertyOf" : [ {
                  |        "@id" : "knora-api:hasValue"
                  |      }, {
                  |        "@id" : "http://schema.org/name"
                  |      } ],
                  |      "salsah-gui:guiElement" : {
                  |        "@id" : "salsah-gui:SimpleText"
                  |      },
                  |      "salsah-gui:guiAttribute" : [ "size=80", "maxlength=100" ]
                  |  } ],
                  |  "@context" : {
                  |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                  |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                  |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
                  |    "owl" : "http://www.w3.org/2002/07/owl#",
                  |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                  |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                  |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                  |  }
                  |}
                """.stripMargin

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.properties should ===(paramsAsInput.properties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the labels of a property" in {
            val params =
                s"""
                   |{
                   |  "@id" : "$AnythingOntologyIri",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:hasName",
                   |    "@type" : "owl:ObjectProperty",
                   |    "rdfs:label" : [ {
                   |      "@language" : "en",
                   |      "@value" : "has name"
                   |    }, {
                   |      "@language" : "fr",
                   |      "@value" : "a nom"
                   |    }, {
                   |      "@language" : "de",
                   |      "@value" : "hat Namen"
                   |    } ]
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "$AnythingOntologyIri#"
                   |  }
                   |}
                """.stripMargin

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects should ===(paramsAsInput.properties.head._2.predicates.head._2.objects)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the comments of a property" in {
            val params =
                s"""
                   |{
                   |  "@id" : "$AnythingOntologyIri",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:hasName",
                   |    "@type" : "owl:ObjectProperty",
                   |    "rdfs:comment" : [ {
                   |      "@language" : "en",
                   |      "@value" : "The name of a Thing"
                   |    }, {
                   |      "@language" : "fr",
                   |      "@value" : "Le nom d'une chose"
                   |    }, {
                   |      "@language" : "de",
                   |      "@value" : "Der Name eines Dinges"
                   |    } ]
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "$AnythingOntologyIri#"
                   |  }
                   |}
                """.stripMargin

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(paramsAsInput.properties.head._2.predicates.head._2.objects)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "create a class anything:WildThing that is a subclass of anything:Thing, with a direct cardinality for anything:hasName" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:WildThing",
                   |    "@type" : "owl:Class",
                   |    "rdfs:label" : {
                   |      "@language" : "en",
                   |      "@value" : "wild thing"
                   |    },
                   |    "rdfs:comment" : {
                   |      "@language" : "en",
                   |      "@value" : "A thing that is wild"
                   |    },
                   |    "rdfs:subClassOf" : [
                   |      {
                   |        "@id": "anything:Thing"
                   |      },
                   |      {
                   |        "@type": "http://www.w3.org/2002/07/owl#Restriction",
                   |        "owl:maxCardinality": 1,
                   |        "owl:onProperty": {
                   |          "@id": "anything:hasName"
                   |        },
                   |        "salsah-gui:guiOrder": 1
                   |      }
                   |    ]
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}
            """.stripMargin

            val expectedProperties: Set[SmartIri] = Set(
                "http://0.0.0.0:3333/ontology/0001/anything/v2#isPartOfOtherThing".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasIncomingLinkValue".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#isPartOfOtherThingValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#isDeleted".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasThingPictureValue".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasThingPicture".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasName".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherListItem".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri,
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#versionDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#lastModificationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#creationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#arkUrl".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#versionArkUrl".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deletedBy".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasPermissions".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deleteComment".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deleteDate".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#userHasPermission".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToProject".toSmartIri
            )

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes should ===(paramsAsInput.classes)

                // Check that cardinalities were inherited from anything:Thing.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "create a class anything:Nothing with no properties" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:Nothing",
                   |    "@type" : "owl:Class",
                   |    "rdfs:label" : {
                   |      "@language" : "en",
                   |      "@value" : "nothing"
                   |    },
                   |    "rdfs:comment" : {
                   |      "@language" : "en",
                   |      "@value" : "Represents nothing"
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
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}
            """.stripMargin

            val expectedProperties: Set[SmartIri] = Set(
                "http://api.knora.org/ontology/knora-api/v2#hasIncomingLinkValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#isDeleted".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#versionDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#lastModificationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#creationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#arkUrl".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#versionArkUrl".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deletedBy".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasPermissions".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deleteComment".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deleteDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#userHasPermission".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToProject".toSmartIri
            )

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes should ===(paramsAsInput.classes)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the labels of a class" in {
            val params =
                s"""
                   |{
                   |  "@id" : "$AnythingOntologyIri",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:Nothing",
                   |    "@type" : "owl:Class",
                   |    "rdfs:label" : [ {
                   |      "@language" : "en",
                   |      "@value" : "nothing"
                   |    }, {
                   |      "@language" : "fr",
                   |      "@value" : "rien"
                   |    } ]
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "$AnythingOntologyIri#"
                   |  }
                   |}
                """.stripMargin

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes.head._2.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects should ===(paramsAsInput.classes.head._2.predicates.head._2.objects)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the comments of a class" in {
            val params =
                s"""
                   |{
                   |  "@id" : "$AnythingOntologyIri",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:Nothing",
                   |    "@type" : "owl:Class",
                   |    "rdfs:comment" : [ {
                   |      "@language" : "en",
                   |      "@value" : "Represents nothing"
                   |    }, {
                   |      "@language" : "fr",
                   |      "@value" : "ne représente rien"
                   |    } ]
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "$AnythingOntologyIri#"
                   |  }
                   |}
                """.stripMargin

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(paramsAsInput.classes.head._2.predicates.head._2.objects)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "create a property anything:hasOtherNothing with knora-api:objectType anything:Nothing" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:hasOtherNothing",
                   |    "@type" : "owl:ObjectProperty",
                   |    "knora-api:subjectType" : {
                   |      "@id" : "anything:Nothing"
                   |    },
                   |    "knora-api:objectType" : {
                   |      "@id" : "anything:Nothing"
                   |    },
                   |    "rdfs:comment" : {
                   |      "@language" : "en",
                   |      "@value" : "Refers to the other Nothing of a Nothing"
                   |    },
                   |    "rdfs:label" : {
                   |      "@language" : "en",
                   |      "@value" : "has nothingness"
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
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}
            """.stripMargin

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.properties should ===(paramsAsInput.properties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "add a cardinality for the property anything:hasOtherNothing to the class anything:Nothing" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:Nothing",
                   |    "@type" : "owl:Class",
                   |    "rdfs:subClassOf" : {
                   |      "@type": "owl:Restriction",
                   |      "owl:maxCardinality" : 1,
                   |      "owl:onProperty" : {
                   |        "@id" : "anything:hasOtherNothing"
                   |      }
                   |    }
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}
            """.stripMargin

            val expectedProperties: Set[SmartIri] = Set(
                "http://api.knora.org/ontology/knora-api/v2#hasIncomingLinkValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#isDeleted".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#versionDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#lastModificationDate".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothing".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#creationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#arkUrl".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothingValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#versionArkUrl".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deletedBy".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasPermissions".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deleteComment".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deleteDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#userHasPermission".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToProject".toSmartIri
            )

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.

            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            val paramsWithAddedLinkValueCardinality = paramsAsInput.copy(
                classes = paramsAsInput.classes.map {
                    case (classIri, classDef) =>
                        val hasOtherNothingValueCardinality = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothingValue".toSmartIri ->
                            classDef.directCardinalities("http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothing".toSmartIri)

                        classIri -> classDef.copy(
                            directCardinalities = classDef.directCardinalities + hasOtherNothingValueCardinality
                        )
                }
            )

            Post("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes.head._2.directCardinalities should ===(paramsWithAddedLinkValueCardinality.classes.head._2.directCardinalities)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "remove the cardinality for the property anything:hasOtherNothing from the class anything:Nothing" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate" ,
                   |  "@graph" : [ {
                   |    "@id" : "anything:Nothing",
                   |    "@type" : "owl:Class"
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}
            """.stripMargin

            val expectedProperties: Set[SmartIri] = Set(
                "http://api.knora.org/ontology/knora-api/v2#hasIncomingLinkValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#isDeleted".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#versionDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#lastModificationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#creationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#arkUrl".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#versionArkUrl".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deletedBy".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasPermissions".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deleteComment".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deleteDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#userHasPermission".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToProject".toSmartIri
            )

            Put("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes.head._2.directCardinalities.isEmpty should ===(true)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "delete the property anything:hasOtherNothing" in {
            val propertySegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothing", "UTF-8")
            val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

            Delete(s"/v2/ontologies/properties/$propertySegment?lastModificationDate=$lastModificationDate") ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                responseJsonDoc.requireStringWithValidation("@id", stringFormatter.toSmartIriWithErr) should ===("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
                val newAnythingLastModDate = responseJsonDoc.requireStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate, stringFormatter.xsdDateTimeStampToInstant)
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "create a property anything:hasNothingness with knora-api:subjectType anything:Nothing" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:hasNothingness",
                   |    "@type" : "owl:ObjectProperty",
                   |    "knora-api:subjectType" : {
                   |      "@id" : "anything:Nothing"
                   |    },
                   |    "knora-api:objectType" : {
                   |      "@id" : "knora-api:BooleanValue"
                   |    },
                   |    "rdfs:comment" : {
                   |      "@language" : "en",
                   |      "@value" : "Indicates whether a Nothing has nothingness"
                   |    },
                   |    "rdfs:label" : {
                   |      "@language" : "en",
                   |      "@value" : "has nothingness"
                   |    },
                   |    "rdfs:subPropertyOf" : {
                   |      "@id" : "knora-api:hasValue"
                   |    }
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}
            """.stripMargin

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.properties should ===(paramsAsInput.properties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "add a cardinality for the property anything:hasNothingness to the class anything:Nothing" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:Nothing",
                   |    "@type" : "owl:Class",
                   |    "rdfs:subClassOf" : {
                   |      "@type": "owl:Restriction",
                   |      "owl:maxCardinality" : 1,
                   |      "owl:onProperty" : {
                   |        "@id" : "anything:hasNothingness"
                   |      }
                   |    }
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}
            """.stripMargin

            val expectedProperties: Set[SmartIri] = Set(
                "http://api.knora.org/ontology/knora-api/v2#hasIncomingLinkValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#isDeleted".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#versionDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#lastModificationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#creationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#arkUrl".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#versionArkUrl".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasNothingness".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deletedBy".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasPermissions".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deleteComment".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deleteDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#userHasPermission".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToProject".toSmartIri
            )

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes.head._2.directCardinalities should ===(paramsAsInput.classes.head._2.directCardinalities)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "create a property anything:hasEmptiness with knora-api:subjectType anything:Nothing" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:hasEmptiness",
                   |    "@type" : "owl:ObjectProperty",
                   |    "knora-api:subjectType" : {
                   |      "@id" : "anything:Nothing"
                   |    },
                   |    "knora-api:objectType" : {
                   |      "@id" : "knora-api:BooleanValue"
                   |    },
                   |    "rdfs:comment" : {
                   |      "@language" : "en",
                   |      "@value" : "Indicates whether a Nothing has emptiness"
                   |    },
                   |    "rdfs:label" : {
                   |      "@language" : "en",
                   |      "@value" : "has emptiness"
                   |    },
                   |    "rdfs:subPropertyOf" : {
                   |      "@id" : "knora-api:hasValue"
                   |    }
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}
            """.stripMargin

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.properties should ===(paramsAsInput.properties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "change the cardinalities of the class anything:Nothing, replacing anything:hasNothingness with anything:hasEmptiness" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "anything:Nothing",
                   |    "@type" : "owl:Class",
                   |    "rdfs:subClassOf" : {
                   |      "@type": "owl:Restriction",
                   |      "owl:maxCardinality": 1,
                   |      "owl:onProperty": {
                   |        "@id" : "anything:hasEmptiness"
                   |      }
                   |    }
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}
            """.stripMargin

            val expectedProperties: Set[SmartIri] = Set(
                "http://api.knora.org/ontology/knora-api/v2#hasIncomingLinkValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#isDeleted".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasEmptiness".toSmartIri,
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#versionDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#lastModificationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#creationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#arkUrl".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#versionArkUrl".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deletedBy".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasPermissions".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deleteComment".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deleteDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#userHasPermission".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToProject".toSmartIri
            )

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes.head._2.directCardinalities should ===(paramsAsInput.classes.head._2.directCardinalities)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "delete the property anything:hasNothingness" in {
            val propertySegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasNothingness", "UTF-8")
            val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

            Delete(s"/v2/ontologies/properties/$propertySegment?lastModificationDate=$lastModificationDate") ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                responseJsonDoc.requireStringWithValidation("@id", stringFormatter.toSmartIriWithErr) should ===("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
                val newAnythingLastModDate = responseJsonDoc.requireStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate, stringFormatter.xsdDateTimeStampToInstant)
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "remove all cardinalities from the class anything:Nothing" in {
            val params =
                s"""
                   |{
                   |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$anythingLastModDate" ,
                   |  "@graph" : [ {
                   |    "@id" : "anything:Nothing",
                   |    "@type" : "owl:Class"
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}
            """.stripMargin

            val expectedProperties: Set[SmartIri] = Set(
                "http://api.knora.org/ontology/knora-api/v2#hasIncomingLinkValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#isDeleted".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#versionDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#lastModificationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#creationDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#arkUrl".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#versionArkUrl".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deletedBy".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#hasPermissions".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deleteComment".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#deleteDate".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#userHasPermission".toSmartIri,
                "http://api.knora.org/ontology/knora-api/v2#attachedToProject".toSmartIri
            )

            Put("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes.head._2.directCardinalities.isEmpty should ===(true)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should ===(expectedProperties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "delete the property anything:hasEmptiness" in {
            val propertySegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasEmptiness", "UTF-8")
            val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

            Delete(s"/v2/ontologies/properties/$propertySegment?lastModificationDate=$lastModificationDate") ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                responseJsonDoc.requireStringWithValidation("@id", stringFormatter.toSmartIriWithErr) should ===("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
                val newAnythingLastModDate = responseJsonDoc.requireStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate, stringFormatter.xsdDateTimeStampToInstant)
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "delete the class anything:Nothing" in {
            val classSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#Nothing", "UTF-8")
            val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

            Delete(s"/v2/ontologies/classes/$classSegment?lastModificationDate=$lastModificationDate") ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                responseJsonDoc.requireStringWithValidation("@id", stringFormatter.toSmartIriWithErr) should ===("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
                val newAnythingLastModDate = responseJsonDoc.requireStringWithValidation(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate, stringFormatter.xsdDateTimeStampToInstant)
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "create a shared ontology and put a property in it" in {
            val label = "The useless ontology"

            val createOntologyJson =
                s"""
                   |{
                   |    "knora-api:ontologyName": "useless",
                   |    "knora-api:attachedToProject": {
                   |      "@id": "${OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject}"
                   |    },
                   |    "knora-api:isShared": true,
                   |    "rdfs:label": "$label",
                   |    "@context": {
                   |        "rdfs": "${OntologyConstants.Rdfs.RdfsPrefixExpansion}",
                   |        "knora-api": "${OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion}"
                   |    }
                   |}
                """.stripMargin

            Post("/v2/ontologies", HttpEntity(RdfMediaTypes.`application/ld+json`, createOntologyJson)) ~> addCredentials(BasicHttpCredentials(superUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                val metadata = responseJsonDoc.body
                val ontologyIri = metadata.value("@id").asInstanceOf[JsonLDString].value
                assert(ontologyIri == "http://api.knora.org/ontology/shared/useless/v2")
                uselessIri.set(ontologyIri)
                assert(metadata.value(OntologyConstants.Rdfs.Label) == JsonLDString(label))
                val lastModDate = Instant.parse(metadata.value(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate).asInstanceOf[JsonLDString].value)
                uselessLastModDate = lastModDate
            }

            val createPropertyJson =
                s"""
                   |{
                   |  "@id" : "${uselessIri.get}",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : "$uselessLastModDate",
                   |  "@graph" : [ {
                   |    "@id" : "useless:hasSharedName",
                   |    "@type" : "owl:ObjectProperty",
                   |    "knora-api:objectType" : {
                   |      "@id" : "knora-api:TextValue"
                   |    },
                   |    "rdfs:comment" : {
                   |      "@language" : "en",
                   |      "@value" : "Represents a name"
                   |    },
                   |    "rdfs:label" : {
                   |      "@language" : "en",
                   |      "@value" : "has shared name"
                   |    },
                   |    "rdfs:subPropertyOf" : {
                   |      "@id" : "knora-api:hasValue"
                   |    }
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "useless" : "${uselessIri.get}#"
                   |  }
                   |}
            """.stripMargin

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(createPropertyJson)).unescape

            Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, createPropertyJson)) ~> addCredentials(BasicHttpCredentials(superUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.properties should ===(paramsAsInput.properties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(uselessLastModDate))
                uselessLastModDate = newAnythingLastModDate
            }
        }
    }
}
