/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.apache.pekko
import spray.json.JsString
import zio.ZIO

import java.net.URLEncoder
import java.time.Instant
import scala.concurrent.ExecutionContextExecutor
import scala.util.Random

import dsp.constants.SalsahGui
import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import dsp.valueobjects.LangString
import dsp.valueobjects.LanguageCode
import org.knora.webapi.*
import org.knora.webapi.e2e.v2.ontology.InputOntologyParsingModeV2.TestResponseParsingModeV2
import org.knora.webapi.e2e.v2.ontology.InputOntologyV2
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.models.*
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.routing.v2.OntologiesRouteV2
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.testservices.TestClientService
import org.knora.webapi.util.*

import pekko.http.scaladsl.model.*
import pekko.http.scaladsl.model.headers.BasicHttpCredentials

object OntologyV2R2RSpec {
  private val anythingUserProfile = SharedTestDataADM.anythingAdminUser
  private val anythingUsername    = anythingUserProfile.email

  private val superUserProfile = SharedTestDataADM.superUser
  private val superUsername    = superUserProfile.email

  private val password = SharedTestDataADM.testPass
}

/**
 * End-to-end test specification for API v2 ontology routes.
 */
class OntologyV2R2RSpec extends R2RSpec {

  import OntologyV2R2RSpec.*

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val ontologiesPath =
    DSPApiDirectives.handleErrors(appConfig)(OntologiesRouteV2().makeRoute)

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override lazy val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/project_ontologies/example-box.ttl",
      name = "http://www.knora.org/ontology/shared/example-box",
    ),
    RdfDataObject(
      path = "test_data/project_ontologies/minimal-onto.ttl",
      name = "http://www.knora.org/ontology/0001/minimal",
    ),
    RdfDataObject(
      path = "test_data/project_ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest",
    ),
    RdfDataObject(path = "test_data/project_data/freetest-data.ttl", name = "http://www.knora.org/data/0001/freetest"),
    RdfDataObject(
      path = "test_data/project_ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything",
    ),
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
  )

  private val anythingOntoLocalhostIri = SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost
  private val hasOtherNothingIri       = anythingOntoLocalhostIri + "#hasOtherNothing"
  private val hasOtherNothingValueIri  = anythingOntoLocalhostIri + "#hasOtherNothingValue"

  private val fooIri                  = new MutableTestIri
  private val barIri                  = new MutableTestIri
  private var fooLastModDate: Instant = Instant.now
  private var barLastModDate: Instant = Instant.now

  private var anythingLastModDate: Instant = Instant.parse("2017-12-19T15:23:42.166Z")
  private var freetestLastModDate: Instant = Instant.parse("2012-12-12T12:12:12.12Z")

  private val uselessIri                  = new MutableTestIri
  private var uselessLastModDate: Instant = Instant.now

  private def getPropertyIrisFromResourceClassResponse(responseJsonDoc: JsonLDDocument): Set[SmartIri] = {
    val classDef = responseJsonDoc.body
      .getRequiredArray("@graph")
      .fold(msg => throw BadRequestException(msg), identity)
      .value
      .head
      .asInstanceOf[JsonLDObject]

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

  "The Ontologies v2 Endpoint" should {

    "not allow the user to request the knora-base ontology" in {
      Get(
        "/v2/ontologies/allentities/http%3A%2F%2Fapi.knora.org%2Fontology%2Fknora-base%2Fv2",
      ) ~> ontologiesPath ~> check {
        val responseStr: String = responseAs[String]
        assert(response.status == StatusCodes.BadRequest, responseStr)
      }
    }

    "not allow the user to request the knora-admin ontology" in {
      Get(
        "/v2/ontologies/allentities/http%3A%2F%2Fapi.knora.org%2Fontology%2Fknora-admin%2Fv2",
      ) ~> ontologiesPath ~> check {
        val responseStr: String = responseAs[String]
        assert(response.status == StatusCodes.BadRequest, responseStr)
      }
    }

    "create an empty ontology called 'foo' with a project code" in {
      val label = "The foo ontology"

      val params =
        s"""{
           |    "knora-api:ontologyName": "foo",
           |    "knora-api:attachedToProject": {
           |      "@id": "${SharedTestDataADM.anythingProjectIri}"
           |    },
           |    "rdfs:label": "$label",
           |    "@context": {
           |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
           |    }
           |}""".stripMargin

      Post("/v2/ontologies", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        val metadata        = responseJsonDoc.body
        val ontologyIri     = metadata.value("@id").asInstanceOf[JsonLDString].value
        assert(ontologyIri == "http://0.0.0.0:3333/ontology/0001/foo/v2")
        fooIri.set(ontologyIri)
        assert(metadata.value(OntologyConstants.Rdfs.Label) == JsonLDString(label))

        val lastModDate = metadata.requireDatatypeValueInObject(
          key = KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )

        fooLastModDate = lastModDate
      }
    }

    "create an empty ontology called 'bar' with a comment" in {
      val label   = "The bar ontology"
      val comment = "some comment"

      val params =
        s"""{
           |    "knora-api:ontologyName": "bar",
           |    "knora-api:attachedToProject": {
           |      "@id": "${SharedTestDataADM.anythingProjectIri}"
           |    },
           |    "rdfs:label": "$label",
           |    "rdfs:comment": "$comment",
           |    "@context": {
           |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
           |    }
           |}""".stripMargin

      Post("/v2/ontologies", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        val metadata        = responseJsonDoc.body
        val ontologyIri     = metadata.value("@id").asInstanceOf[JsonLDString].value
        assert(ontologyIri == "http://0.0.0.0:3333/ontology/0001/bar/v2")
        assert(
          metadata.value(OntologyConstants.Rdfs.Comment) == JsonLDString(
            Iri.fromSparqlEncodedString(comment),
          ),
        )
        barIri.set(ontologyIri)
        val lastModDate = metadata.requireDatatypeValueInObject(
          key = KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )
        barLastModDate = lastModDate
      }
    }

    "create an empty ontology called 'test' with a comment that has a special character" in {
      val label   = "The test ontology"
      val comment = "some \\\"test\\\" comment"

      val params =
        s"""{
           |    "knora-api:ontologyName": "test",
           |    "knora-api:attachedToProject": {
           |      "@id": "${SharedTestDataADM.anythingProjectIri}"
           |    },
           |    "rdfs:label": "$label",
           |    "rdfs:comment": "$comment",
           |    "@context": {
           |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
           |    }
           |}""".stripMargin

      Post("/v2/ontologies", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        val metadata        = responseJsonDoc.body
        val ontologyIri     = metadata.value("@id").asInstanceOf[JsonLDString].value
        assert(ontologyIri == "http://0.0.0.0:3333/ontology/0001/test/v2")
        assert(
          metadata.value(OntologyConstants.Rdfs.Comment) == JsonLDString(
            Iri.fromSparqlEncodedString(comment),
          ),
        )
      }
    }

    "change the metadata of 'foo'" in {
      val newLabel   = "The modified foo ontology"
      val newComment = "new comment"

      val params =
        s"""{
           |  "@id": "${fooIri.get}",
           |  "rdfs:label": "$newLabel",
           |  "rdfs:comment": "$newComment",
           |  "knora-api:lastModificationDate": {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$fooLastModDate"
           |  },
           |  "@context": {
           |    "xsd" :  "http://www.w3.org/2001/XMLSchema#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
           |  }
           |}""".stripMargin

      Put("/v2/ontologies/metadata", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        val metadata        = responseJsonDoc.body
        val ontologyIri     = metadata.value("@id").asInstanceOf[JsonLDString].value
        assert(ontologyIri == fooIri.get)
        assert(metadata.value(OntologyConstants.Rdfs.Label) == JsonLDString(newLabel))
        assert(metadata.value(OntologyConstants.Rdfs.Comment) == JsonLDString(newComment))

        val lastModDate = metadata.requireDatatypeValueInObject(
          key = KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )

        assert(lastModDate.isAfter(fooLastModDate))
        fooLastModDate = lastModDate
      }
    }

    "change the metadata of 'bar' ontology giving a comment containing a special character" in {
      val newComment = "a \\\"new\\\" comment"

      val params =
        s"""{
           |  "@id": "${barIri.get}",
           |  "rdfs:comment": "$newComment",
           |  "knora-api:lastModificationDate": {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$barLastModDate"
           |  },
           |  "@context": {
           |    "xsd" :  "http://www.w3.org/2001/XMLSchema#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
           |  }
           |}""".stripMargin

      Put("/v2/ontologies/metadata", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        val metadata        = responseJsonDoc.body
        val ontologyIri     = metadata.value("@id").asInstanceOf[JsonLDString].value
        assert(ontologyIri == barIri.get)
        assert(
          metadata.value(OntologyConstants.Rdfs.Comment) == JsonLDString(
            Iri.fromSparqlEncodedString(newComment),
          ),
        )

        val lastModDate = metadata.requireDatatypeValueInObject(
          key = KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )

        assert(lastModDate.isAfter(barLastModDate))
        barLastModDate = lastModDate
      }
    }

    "delete the comment from 'foo'" in {
      val fooIriEncoded        = URLEncoder.encode(fooIri.get, "UTF-8")
      val lastModificationDate = URLEncoder.encode(fooLastModDate.toString, "UTF-8")

      Delete(s"/v2/ontologies/comment/$fooIriEncoded?lastModificationDate=$lastModificationDate") ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        val metadata        = responseJsonDoc.body
        val ontologyIri     = metadata.value("@id").asInstanceOf[JsonLDString].value
        assert(ontologyIri == fooIri.get)
        assert(metadata.value(OntologyConstants.Rdfs.Label) == JsonLDString("The modified foo ontology"))
        assert(!metadata.value.contains(OntologyConstants.Rdfs.Comment))

        val lastModDate = metadata.requireDatatypeValueInObject(
          key = KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )

        assert(lastModDate.isAfter(fooLastModDate))
        fooLastModDate = lastModDate
      }
    }

    "determine that an ontology can be deleted" in {
      val fooIriEncoded = URLEncoder.encode(fooIri.get, "UTF-8")

      Get(s"/v2/ontologies/candeleteontology/$fooIriEncoded") ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]

        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        assert(responseJsonDoc.body.value(KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
      }
    }

    "delete the 'foo' ontology" in {
      val fooIriEncoded        = URLEncoder.encode(fooIri.get, "UTF-8")
      val lastModificationDate = URLEncoder.encode(fooLastModDate.toString, "UTF-8")

      Delete(s"/v2/ontologies/$fooIriEncoded?lastModificationDate=$lastModificationDate") ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
      }
    }

    "create a property anything:hasName as a subproperty of knora-api:hasValue and schema:name" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
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
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
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

    "change the rdfs:label of a property" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
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
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects.toSet should ===(
          paramsAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects.toSet,
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the rdfs:comment of a property" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
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
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.properties.head._2
          .predicates(OntologyConstants.Rdfs.Comment.toSmartIri)
          .objects
          .toSet should ===(
          paramsAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects.toSet,
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "add an rdfs:comment to a link property that has no rdfs:comment" in {
      val params =
        s"""{
           |    "@id": "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |    "@type": "owl:Ontology",
           |    "knora-api:lastModificationDate": {
           |        "@type": "xsd:dateTimeStamp",
           |        "@value": "$anythingLastModDate"
           |    },
           |    "@graph": [
           |        {
           |            "@id": "anything:hasBlueThing",
           |            "@type": "owl:ObjectProperty",
           |            "rdfs:comment": [
           |                {
           |                    "@language": "en",
           |                    "@value": "asdas asd as dasdasdas"
           |                }
           |            ]
           |        }
           |    ],
           |    "@context": {
           |        "anything": "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#",
           |        "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           |        "owl": "http://www.w3.org/2002/07/owl#",
           |        "xsd": "http://www.w3.org/2001/XMLSchema#",
           |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
           |        "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#"
           |    }
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(
          paramsAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects,
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the rdfs:comment of a property" in {
      val propertySegment =
        URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/freetest/v2#hasPropertyWithComment2", "UTF-8")
      val lastModificationDate = URLEncoder.encode(freetestLastModDate.toString, "UTF-8")

      Delete(
        s"/v2/ontologies/properties/comment/$propertySegment?lastModificationDate=$lastModificationDate",
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        val newFreetestLastModDate = responseJsonDoc.body.requireDatatypeValueInObject(
          key = KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )

        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate

        val expectedResponse: String =
          s"""{
             |   "knora-api:lastModificationDate": {
             |       "@value": "$newFreetestLastModDate",
             |       "@type": "xsd:dateTimeStamp"
             |   },
             |   "rdfs:label": "freetest",
             |   "@graph": [
             |      {
             |         "rdfs:label": [
             |            {
             |               "@value": "Property mit einem Kommentar 2",
             |               "@language": "de"
             |            },
             |            {
             |               "@value": "Property with a comment 2",
             |               "@language": "en"
             |            }
             |         ],
             |         "rdfs:subPropertyOf": {
             |            "@id": "knora-api:hasValue"
             |         },
             |         "@type": "owl:ObjectProperty",
             |         "knora-api:objectType": {
             |            "@id": "knora-api:TextValue"
             |         },
             |         "salsah-gui:guiElement": {
             |            "@id": "salsah-gui:SimpleText"
             |         },
             |         "@id": "freetest:hasPropertyWithComment2"
             |      }
             |   ],
             |   "knora-api:attachedToProject": {
             |      "@id": "http://rdfh.ch/projects/0001"
             |   },
             |   "@type": "owl:Ontology",
             |   "@id": "http://0.0.0.0:3333/ontology/0001/freetest/v2",
             |   "@context": {
             |      "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |      "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
             |      "freetest": "http://0.0.0.0:3333/ontology/0001/freetest/v2#",
             |      "owl": "http://www.w3.org/2002/07/owl#",
             |      "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#",
             |      "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
             |      "xsd": "http://www.w3.org/2001/XMLSchema#"
             |   }
             |}""".stripMargin

        val expectedResponseToCompare: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(expectedResponse)).unescape

        val responseFromJsonLD: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

        responseFromJsonLD.properties.head._2.predicates.toSet should ===(
          expectedResponseToCompare.properties.head._2.predicates.toSet,
        )
      }
    }

    "delete the rdfs:comment of a class" in {
      val classSegment: String =
        URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/freetest/v2#BookWithComment2", "UTF-8")
      val lastModificationDate = URLEncoder.encode(freetestLastModDate.toString, "UTF-8")

      Delete(
        s"/v2/ontologies/classes/comment/$classSegment?lastModificationDate=$lastModificationDate",
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(responseStr)
        val newFreetestLastModDate = responseJsonDoc.body.requireDatatypeValueInObject(
          key = KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )

        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate

        val expectedResponse: String =
          s"""{
             |   "knora-api:lastModificationDate": {
             |       "@value": "$newFreetestLastModDate",
             |       "@type": "xsd:dateTimeStamp"
             |   },
             |   "rdfs:label": "freetest",
             |   "@graph": [
             |      {
             |         "rdfs:label": [
             |            {
             |               "@value": "Buch 2 mit Kommentar",
             |               "@language": "de"
             |            },
             |            {
             |               "@value": "Book 2 with comment",
             |               "@language": "en"
             |            }
             |         ],
             |         "rdfs:subClassOf": [
             |            {
             |               "@id": "knora-api:Resource"
             |            }
             |         ],
             |         "@type": "owl:Class",
             |         "@id": "freetest:BookWithComment2"
             |      }
             |   ],
             |   "knora-api:attachedToProject": {
             |      "@id": "http://rdfh.ch/projects/0001"
             |   },
             |   "@type": "owl:Ontology",
             |   "@id": "http://0.0.0.0:3333/ontology/0001/freetest/v2",
             |   "@context": {
             |      "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |      "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
             |      "freetest": "http://0.0.0.0:3333/ontology/0001/freetest/v2#",
             |      "owl": "http://www.w3.org/2002/07/owl#",
             |      "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#",
             |      "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
             |      "xsd": "http://www.w3.org/2001/XMLSchema#"
             |   }
             |}""".stripMargin

        val expectedResponseToCompare: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(expectedResponse)).unescape

        val responseFromJsonLD: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

        responseFromJsonLD.classes.head._2.predicates.toSet should ===(
          expectedResponseToCompare.classes.head._2.predicates.toSet,
        )
      }
    }

    "change the salsah-gui:guiElement and salsah-gui:guiAttribute of a property" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasName",
           |    "@type" : "owl:ObjectProperty",
           |    "salsah-gui:guiElement" : {
           |      "@id" : "salsah-gui:Textarea"
           |    },
           |    "salsah-gui:guiAttribute" : [ "cols=80", "rows=24" ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put(
        "/v2/ontologies/properties/guielement",
        HttpEntity(RdfMediaTypes.`application/ld+json`, params),
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

        responseAsInput.properties.head._2
          .predicates(SalsahGui.External.GuiElementProp.toSmartIri)
          .objects
          .toSet should ===(
          paramsAsInput.properties.head._2
            .predicates(SalsahGui.External.GuiElementProp.toSmartIri)
            .objects
            .toSet,
        )

        responseAsInput.properties.head._2
          .predicates(SalsahGui.External.GuiAttribute.toSmartIri)
          .objects
          .toSet should ===(
          paramsAsInput.properties.head._2
            .predicates(SalsahGui.External.GuiAttribute.toSmartIri)
            .objects
            .toSet,
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not change the salsah-gui:guiElement and salsah-gui:guiAttribute of a property if their combination is invalid" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasName",
           |    "@type" : "owl:ObjectProperty",
           |    "salsah-gui:guiElement" : {
           |      "@id" : "salsah-gui:List"
           |    },
           |    "salsah-gui:guiAttribute" : [ "cols=80", "rows=24" ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      Put(
        "/v2/ontologies/properties/guielement",
        HttpEntity(RdfMediaTypes.`application/ld+json`, params),
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.BadRequest, responseStr)
      }
    }

    "remove the salsah-gui:guiElement and salsah-gui:guiAttribute from a property" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasName",
           |    "@type" : "owl:ObjectProperty"
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      Put(
        "/v2/ontologies/properties/guielement",
        HttpEntity(RdfMediaTypes.`application/ld+json`, params),
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

        assert(
          !responseAsInput.properties.head._2.predicates
            .contains(SalsahGui.External.GuiElementProp.toSmartIri),
        )

        assert(
          !responseAsInput.properties.head._2.predicates
            .contains(SalsahGui.External.GuiAttribute.toSmartIri),
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create a class anything:WildThing that is a subclass of anything:Thing, with a direct cardinality for anything:hasName" in {

      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
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
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes should ===(paramsAsInput.classes)

        // Check that cardinalities were inherited from anything:Thing.
        getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain(
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri,
        )

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
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
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
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}
           """.stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
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
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the labels of a class" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
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
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects should ===(
          paramsAsInput.classes.head._2.predicates.head._2.objects,
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the comments of a class" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
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
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(
          paramsAsInput.classes.head._2.predicates.head._2.objects,
        )

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
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
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
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}
                """.stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
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

    "add a cardinality for the property anything:hasOtherNothing to the class anything:Nothing" in {
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
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}
                """.stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      val paramsWithAddedLinkValueCardinality = paramsAsInput.copy(
        classes = paramsAsInput.classes.map { case (classIri, classDef) =>
          val hasOtherNothingValueCardinality =
            "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothingValue".toSmartIri ->
              classDef.directCardinalities("http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothing".toSmartIri)

          classIri -> classDef.copy(
            directCardinalities = classDef.directCardinalities + hasOtherNothingValueCardinality,
          )
        },
      )

      Post("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.directCardinalities should ===(
          paramsWithAddedLinkValueCardinality.classes.head._2.directCardinalities,
        )

        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "add all IRIs to newly created link value property again" in {
      val url = URLEncoder.encode(s"${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}", "UTF-8")
      Get(
        s"/v2/ontologies/allentities/$url",
      ) ~> ontologiesPath ~> check {
        val responseStr: String = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        val graph = responseJsonDoc.body
          .getRequiredArray("@graph")
          .fold(e => throw BadRequestException(e), identity)
          .value

        val hasOtherNothingValue = graph
          .filter(
            _.asInstanceOf[JsonLDObject]
              .value("@id")
              .asInstanceOf[JsonLDString]
              .value == "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothingValue",
          )
          .head
          .asInstanceOf[JsonLDObject]

        val iris = hasOtherNothingValue.value.keySet

        val expectedIris = Set(
          OntologyConstants.Rdfs.Comment,
          OntologyConstants.Rdfs.Label,
          OntologyConstants.Rdfs.SubPropertyOf,
          KnoraApiV2Complex.IsEditable,
          KnoraApiV2Complex.IsResourceProperty,
          KnoraApiV2Complex.IsLinkValueProperty,
          KnoraApiV2Complex.ObjectType,
          KnoraApiV2Complex.SubjectType,
          "@id",
          "@type",
        )

        iris should equal(expectedIris)

        val isEditable =
          hasOtherNothingValue
            .getRequiredBoolean(KnoraApiV2Complex.IsEditable)
            .fold(msg => throw BadRequestException(msg), identity)
        isEditable shouldBe true
      }
    }

    "update the property label and check if the property is still correctly marked as `isEditable: true`" in {
      // update label and comment of a property
      val newLabel = "updated label"
      val params =
        s"""
           |{
           |  "@id" : "$anythingOntoLocalhostIri",
           |  "@type": "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph": [
           |    {
           |      "@id": "$hasOtherNothingIri",
           |      "@type" : "owl:ObjectProperty",
           |      "rdfs:label": {
           |        "@language": "en",
           |        "@value": "$newLabel"
           |      }
           |    }
           |  ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
           |  }
           |}""".stripMargin

      Put("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]

        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc    = JsonLDUtil.parseJsonLD(responseStr)
        val updatedOntologyIri = responseJsonDoc.body.value("@id").asInstanceOf[JsonLDString].value
        assert(updatedOntologyIri == anythingOntoLocalhostIri)
        val graph =
          responseJsonDoc.body.getRequiredArray("@graph").fold(e => throw BadRequestException(e), identity).value
        val property = graph.head.asInstanceOf[JsonLDObject]
        val returnedPropertyIri =
          property.getRequiredString("@id").fold(msg => throw BadRequestException(msg), identity)
        returnedPropertyIri should equal(hasOtherNothingIri)
        val returnedLabel = property
          .getRequiredObject(OntologyConstants.Rdfs.Label)
          .flatMap(_.getRequiredString("@value"))
          .fold(msg => throw BadRequestException(msg), identity)
        returnedLabel should equal(newLabel)
        val lastModDate = responseJsonDoc.body.requireDatatypeValueInObject(
          key = KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )

        assert(lastModDate.isAfter(fooLastModDate))
        anythingLastModDate = lastModDate

        // load back the ontology to verify that the updated property still is editable
        val encodedIri = URLEncoder.encode(s"${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}", "UTF-8")
        Get(
          s"/v2/ontologies/allentities/$encodedIri",
        ) ~> ontologiesPath ~> check {
          val responseStr: String = responseAs[String]
          assert(status == StatusCodes.OK, response.toString)
          val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

          val graph = responseJsonDoc.body
            .getRequiredArray("@graph")
            .fold(e => throw BadRequestException(e), identity)
            .value
            .map(_.asInstanceOf[JsonLDObject])
          val nothingValue = graph
            .filter(
              _.getRequiredString("@id")
                .fold(msg => throw BadRequestException(msg), identity) == hasOtherNothingValueIri,
            )
            .head
          val isEditableMaybe =
            nothingValue
              .getBoolean(KnoraApiV2Complex.IsEditable)
              .fold(msg => throw BadRequestException(msg), identity)

          isEditableMaybe should equal(Some(true))
        }
      }
    }

    "update the property comment and check if the property is still correctly marked as `isEditable: true`" in {
      // update label and comment of a property
      val newComment = "updated comment"

      val params =
        s"""
           |{
           |  "@id" : "$anythingOntoLocalhostIri",
           |  "@type": "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph": [
           |    {
           |      "@id": "$hasOtherNothingIri",
           |      "@type" : "owl:ObjectProperty",
           |      "rdfs:comment": {
           |        "@language": "en",
           |        "@value": "$newComment"
           |      }
           |    }
           |  ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
           |  }
           |}""".stripMargin

      Put("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]

        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc    = JsonLDUtil.parseJsonLD(responseStr)
        val updatedOntologyIri = responseJsonDoc.body.value("@id").asInstanceOf[JsonLDString].value
        assert(updatedOntologyIri == anythingOntoLocalhostIri)
        val graph = responseJsonDoc.body
          .getRequiredArray("@graph")
          .fold(e => throw BadRequestException(e), identity)
          .value
        val property = graph.head.asInstanceOf[JsonLDObject]
        val returnedPropertyIri =
          property.getRequiredString("@id").fold(msg => throw BadRequestException(msg), identity)
        returnedPropertyIri should equal(hasOtherNothingIri)
        val returnedComment = property
          .getRequiredObject(OntologyConstants.Rdfs.Comment)
          .flatMap(_.getRequiredString("@value"))
          .fold(msg => throw BadRequestException(msg), identity)
        returnedComment should equal(newComment)
        val lastModDate = responseJsonDoc.body.requireDatatypeValueInObject(
          key = KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )

        assert(lastModDate.isAfter(fooLastModDate))
        anythingLastModDate = lastModDate

        // load back the ontology to verify that the updated property still is editable
        val encodedIri = URLEncoder.encode(s"${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}", "UTF-8")
        Get(
          s"/v2/ontologies/allentities/$encodedIri",
        ) ~> ontologiesPath ~> check {
          val responseStr: String = responseAs[String]
          assert(status == StatusCodes.OK, response.toString)
          val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

          val graph = responseJsonDoc.body
            .getRequiredArray("@graph")
            .fold(e => throw BadRequestException(e), identity)
            .value
            .map(_.asInstanceOf[JsonLDObject])
          val nothingValue = graph
            .filter(
              _.getRequiredString("@id")
                .fold(msg => throw BadRequestException(msg), identity) == hasOtherNothingValueIri,
            )
            .head
          val isEditableMaybe =
            nothingValue
              .getBoolean(KnoraApiV2Complex.IsEditable)
              .fold(msg => throw BadRequestException(msg), identity)

          isEditableMaybe should equal(Some(true))
        }
      }
    }

    "delete the property comment and check if the property is still correctly marked as `isEditable: true`" in {
      // update label and comment of a property
      val propertyIriEncoded = URLEncoder.encode(hasOtherNothingIri, "UTF-8")

      Delete(
        s"/v2/ontologies/properties/comment/$propertyIriEncoded?lastModificationDate=$anythingLastModDate",
      ) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]

        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc    = JsonLDUtil.parseJsonLD(responseStr)
        val updatedOntologyIri = responseJsonDoc.body.value("@id").asInstanceOf[JsonLDString].value
        assert(updatedOntologyIri == anythingOntoLocalhostIri)
        val graph = responseJsonDoc.body
          .getRequiredArray("@graph")
          .fold(e => throw BadRequestException(e), identity)
          .value
        val property = graph.head.asInstanceOf[JsonLDObject]
        val returnedPropertyIri =
          property.getRequiredString("@id").fold(msg => throw BadRequestException(msg), identity)
        returnedPropertyIri should equal(hasOtherNothingIri)
        assert(property.value.get(OntologyConstants.Rdfs.Comment) == None)
        val lastModDate = responseJsonDoc.body.requireDatatypeValueInObject(
          key = KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )

        assert(lastModDate.isAfter(fooLastModDate))
        anythingLastModDate = lastModDate

        // load back the ontology to verify that the updated property still is editable
        val encodedIri = URLEncoder.encode(s"${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}", "UTF-8")
        Get(
          s"/v2/ontologies/allentities/$encodedIri",
        ) ~> ontologiesPath ~> check {
          val responseStr: String = responseAs[String]
          assert(status == StatusCodes.OK, response.toString)
          val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

          val graph = responseJsonDoc.body
            .getRequiredArray("@graph")
            .fold(e => throw BadRequestException(e), identity)
            .value
            .map(_.asInstanceOf[JsonLDObject])
          val nothingValue = graph
            .filter(
              _.getRequiredString("@id")
                .fold(msg => throw BadRequestException(msg), identity) == hasOtherNothingValueIri,
            )
            .head
          val isEditableMaybe =
            nothingValue.getBoolean(KnoraApiV2Complex.IsEditable).fold(msg => throw BadRequestException(msg), identity)
          isEditableMaybe should equal(Some(true))
        }
      }
    }

    "update the property guiElement and check if the property is still correctly marked as `isEditable: true`" in {
      // update label and comment of a property

      val params =
        s"""
           |{
           |  "@id" : "$anythingOntoLocalhostIri",
           |  "@type": "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph": [
           |    {
           |      "@id": "$hasOtherNothingIri",
           |      "@type" : "owl:ObjectProperty",
           |      "salsah-gui:guiElement": {
           |        "@id": "salsah-gui:Searchbox"
           |      }
           |    }
           |  ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
           |  }
           |}""".stripMargin

      Put(
        "/v2/ontologies/properties/guielement",
        HttpEntity(RdfMediaTypes.`application/ld+json`, params),
      ) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]

        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc    = JsonLDUtil.parseJsonLD(responseStr)
        val updatedOntologyIri = responseJsonDoc.body.value("@id").asInstanceOf[JsonLDString].value
        assert(updatedOntologyIri == anythingOntoLocalhostIri)
        val graph = responseJsonDoc.body
          .getRequiredArray("@graph")
          .fold(e => throw BadRequestException(e), identity)
          .value
        val property = graph.head.asInstanceOf[JsonLDObject]
        val returnedPropertyIri =
          property.getRequiredString("@id").fold(msg => throw BadRequestException(msg), identity)
        returnedPropertyIri should equal(hasOtherNothingIri)
        val lastModDate = responseJsonDoc.body.requireDatatypeValueInObject(
          key = KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )

        assert(lastModDate.isAfter(fooLastModDate))
        anythingLastModDate = lastModDate

        // load back the ontology to verify that the updated property still is editable
        val encodedIri = URLEncoder.encode(s"${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}", "UTF-8")
        Get(
          s"/v2/ontologies/allentities/$encodedIri",
        ) ~> ontologiesPath ~> check {
          val responseStr: String = responseAs[String]
          assert(status == StatusCodes.OK, response.toString)
          val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

          val graph = responseJsonDoc.body
            .getRequiredArray("@graph")
            .fold(e => throw BadRequestException(e), identity)
            .value
            .map(_.asInstanceOf[JsonLDObject])
          val nothingValue = graph
            .filter(
              _.getRequiredString("@id")
                .fold(msg => throw BadRequestException(msg), identity) == hasOtherNothingValueIri,
            )
            .head
          val isEditableMaybe =
            nothingValue.getBoolean(KnoraApiV2Complex.IsEditable).fold(msg => throw BadRequestException(msg), identity)
          isEditableMaybe should equal(Some(true))
        }
      }
    }

    "remove the cardinality for the property anything:hasOtherNothing from the class anything:Nothing" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
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
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      Put("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.directCardinalities.isEmpty should ===(true)

        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "determine that a property can be deleted" in {
      val propertySegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothing", "UTF-8")

      Get(s"/v2/ontologies/candeleteproperty/$propertySegment") ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        assert(responseJsonDoc.body.value(KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
      }
    }

    "delete the property anything:hasOtherNothing" in {
      val propertySegment      = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothing", "UTF-8")
      val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

      Delete(
        s"/v2/ontologies/properties/$propertySegment?lastModificationDate=$lastModificationDate",
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        responseJsonDoc.body.requireStringWithValidation("@id", stringFormatter.toSmartIriWithErr) should ===(
          "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri,
        )

        val newAnythingLastModDate = responseJsonDoc.body.requireDatatypeValueInObject(
          key = KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )

        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create a property anything:hasNothingness with knora-api:subjectType anything:Nothing" in {
      val params =
        s"""{
           |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
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
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

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

    "add a cardinality for the property anything:hasNothingness to the class anything:Nothing" in {
      val params =
        s"""{
           |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
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
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.directCardinalities should ===(
          paramsAsInput.classes.head._2.directCardinalities,
        )

        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the GUI order of the cardinality on anything:hasNothingness in the class anything:Nothing" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality": 1,
           |      "owl:onProperty": {
           |        "@id" : "anything:hasNothingness"
           |      },
           |      "salsah-gui:guiOrder": 2
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put("/v2/ontologies/guiorder", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.directCardinalities should ===(
          paramsAsInput.classes.head._2.directCardinalities,
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create a property anything:hasEmptiness with knora-api:subjectType anything:Nothing" in {
      val params =
        s"""{
           |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
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
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

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

    "determine that a class's cardinalities can be changed" in {
      val classSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#Nothing", "UTF-8")

      Get(s"/v2/ontologies/canreplacecardinalities/$classSegment") ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        assert(responseJsonDoc.body.value(KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
      }
    }

    "change the cardinalities of the class anything:Nothing, replacing anything:hasNothingness with anything:hasEmptiness" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
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
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.directCardinalities should ===(
          paramsAsInput.classes.head._2.directCardinalities,
        )

        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the property anything:hasNothingness" in {
      val propertySegment      = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasNothingness", "UTF-8")
      val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

      Delete(
        s"/v2/ontologies/properties/$propertySegment?lastModificationDate=$lastModificationDate",
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        responseJsonDoc.body.requireStringWithValidation("@id", stringFormatter.toSmartIriWithErr) should ===(
          "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri,
        )

        val newAnythingLastModDate = responseJsonDoc.body.requireDatatypeValueInObject(
          key = KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )

        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "remove all cardinalities from the class anything:Nothing" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
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
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      Put("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.directCardinalities.isEmpty should ===(true)

        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri,
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the property anything:hasEmptiness" in {
      val propertySegment      = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasEmptiness", "UTF-8")
      val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

      Delete(
        s"/v2/ontologies/properties/$propertySegment?lastModificationDate=$lastModificationDate",
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        responseJsonDoc.body.requireStringWithValidation("@id", stringFormatter.toSmartIriWithErr) should ===(
          "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri,
        )

        val newAnythingLastModDate = responseJsonDoc.body.requireDatatypeValueInObject(
          key = KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )

        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "determine that a class can be deleted" in {
      val classSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#Nothing", "UTF-8")

      Get(s"/v2/ontologies/candeleteclass/$classSegment") ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        assert(responseJsonDoc.body.value(KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
      }
    }

    "delete the class anything:Nothing" in {
      val classSegment         = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#Nothing", "UTF-8")
      val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

      Delete(s"/v2/ontologies/classes/$classSegment?lastModificationDate=$lastModificationDate") ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        responseJsonDoc.body.requireStringWithValidation("@id", stringFormatter.toSmartIriWithErr) should ===(
          "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri,
        )

        val newAnythingLastModDate = responseJsonDoc.body.requireDatatypeValueInObject(
          key = KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )

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
           |        "knora-api": "${KnoraApiV2Complex.KnoraApiV2PrefixExpansion}"
           |    }
           |}""".stripMargin

      Post("/v2/ontologies", HttpEntity(RdfMediaTypes.`application/ld+json`, createOntologyJson)) ~> addCredentials(
        BasicHttpCredentials(superUsername, password),
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        val metadata        = responseJsonDoc.body
        val ontologyIri     = metadata.value("@id").asInstanceOf[JsonLDString].value
        assert(ontologyIri == "http://api.knora.org/ontology/shared/useless/v2")
        uselessIri.set(ontologyIri)
        assert(metadata.value(OntologyConstants.Rdfs.Label) == JsonLDString(label))

        val lastModDate = metadata.requireDatatypeValueInObject(
          key = KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = (s, errorFun) => ValuesValidator.xsdDateTimeStampToInstant(s).getOrElse(errorFun),
        )

        uselessLastModDate = lastModDate
      }

      val createPropertyJson =
        s"""
           |{
           |  "@id" : "${uselessIri.get}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$uselessLastModDate"
           |  },
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
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(createPropertyJson)).unescape

      Post(
        "/v2/ontologies/properties",
        HttpEntity(RdfMediaTypes.`application/ld+json`, createPropertyJson),
      ) ~> addCredentials(BasicHttpCredentials(superUsername, password)) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.properties should ===(paramsAsInput.properties)

        // Check that the ontology's last modification date was updated.
        val newLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newLastModDate.isAfter(uselessLastModDate))
        uselessLastModDate = newLastModDate
      }
    }

    "create a class with several cardinalities, then remove one of the cardinalities" in {
      // Create a class with no cardinalities.

      val createClassRequestJson =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:TestClass",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "test class"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "A test class"
           |    },
           |    "rdfs:subClassOf" : [
           |      {
           |        "@id": "knora-api:Resource"
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
           |}""".stripMargin

      Post(
        "/v2/ontologies/classes",
        HttpEntity(RdfMediaTypes.`application/ld+json`, createClassRequestJson),
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

        anythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
      }

      // Create a text property.

      val createTestTextPropRequestJson =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |      "@id" : "anything:testTextProp",
           |      "@type" : "owl:ObjectProperty",
           |      "knora-api:subjectType" : {
           |        "@id" : "anything:TestClass"
           |      },
           |      "knora-api:objectType" : {
           |        "@id" : "knora-api:TextValue"
           |      },
           |      "rdfs:comment" : {
           |        "@language" : "en",
           |        "@value" : "A test text property"
           |      },
           |      "rdfs:label" : {
           |        "@language" : "en",
           |        "@value" : "test text property"
           |      },
           |      "rdfs:subPropertyOf" : {
           |        "@id" : "knora-api:hasValue"
           |      }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      Post(
        "/v2/ontologies/properties",
        HttpEntity(RdfMediaTypes.`application/ld+json`, createTestTextPropRequestJson),
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        anythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get

      }

      // Create an integer property.

      val createTestIntegerPropRequestJson =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |      "@id" : "anything:testIntProp",
           |      "@type" : "owl:ObjectProperty",
           |      "knora-api:subjectType" : {
           |        "@id" : "anything:TestClass"
           |      },
           |      "knora-api:objectType" : {
           |        "@id" : "knora-api:IntValue"
           |      },
           |      "rdfs:comment" : {
           |        "@language" : "en",
           |        "@value" : "A test int property"
           |      },
           |      "rdfs:label" : {
           |        "@language" : "en",
           |        "@value" : "test int property"
           |      },
           |      "rdfs:subPropertyOf" : {
           |        "@id" : "knora-api:hasValue"
           |      }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      Post(
        "/v2/ontologies/properties",
        HttpEntity(RdfMediaTypes.`application/ld+json`, createTestIntegerPropRequestJson),
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        anythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
      }

      // Create a link property.

      val createTestLinkPropRequestJson =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |      "@id" : "anything:testLinkProp",
           |      "@type" : "owl:ObjectProperty",
           |      "knora-api:subjectType" : {
           |        "@id" : "anything:TestClass"
           |      },
           |      "knora-api:objectType" : {
           |        "@id" : "anything:TestClass"
           |      },
           |      "rdfs:comment" : {
           |        "@language" : "en",
           |        "@value" : "A test link property"
           |      },
           |      "rdfs:label" : {
           |        "@language" : "en",
           |        "@value" : "test link property"
           |      },
           |      "rdfs:subPropertyOf" : {
           |        "@id" : "knora-api:hasLinkTo"
           |      }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      Post(
        "/v2/ontologies/properties",
        HttpEntity(RdfMediaTypes.`application/ld+json`, createTestLinkPropRequestJson),
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        anythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
      }

      // Add cardinalities to the class.

      val addCardinalitiesRequestJson =
        s"""
           |{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:TestClass",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : [ {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:testTextProp"
           |      }
           |    },
           |    {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:testIntProp"
           |      }
           |    },
           |    {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:testLinkProp"
           |      }
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      Post(
        "/v2/ontologies/cardinalities",
        HttpEntity(RdfMediaTypes.`application/ld+json`, addCardinalitiesRequestJson),
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        anythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
      }

      // Remove the link value cardinality from to the class.
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
           |    "@id" : "anything:TestClass",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : [ {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:testTextProp"
           |      }
           |    },
           |    {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:testIntProp"
           |      }
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      Put("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password),
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        anythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
      }
    }
  }

  "create a class with two cardinalities, use one in data, and allow only removal of the cardinality for the property not used in data" in {
    // Create a class with no cardinalities.

    val label = LangString.make(LanguageCode.en, "A Blue Free Test class").fold(e => throw e.head, v => v)
    val comment = Some(
      LangString
        .make(LanguageCode.en, "A Blue Free Test class used for testing cardinalities")
        .fold(e => throw e.head, v => v),
    )
    val createClassRequestJson = CreateClassRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "BlueFreeTestClass",
        label = label,
        comment = comment,
      )
      .value

    Post(
      "/v2/ontologies/classes",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createClassRequestJson),
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      assert(status == StatusCodes.OK, response.toString)
      val responseJsonDoc = responseToJsonLDDocument(response)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // Create a text property.
    val label1 = LangString.make(LanguageCode.en, "blue test text property").fold(e => throw e.head, v => v)
    val comment1 = Some(
      LangString
        .make(LanguageCode.en, "A blue test text property")
        .fold(e => throw e.head, v => v),
    )
    val createTestTextPropRequestJson =
      CreatePropertyRequest
        .make(
          ontologyName = "freetest",
          lastModificationDate = freetestLastModDate,
          propertyName = "hasBlueTestTextProp",
          subjectClassName = Some("BlueFreeTestClass"),
          propertyType = PropertyValueType.TextValue,
          label = label1,
          comment = comment1,
        )
        .value

    Post(
      "/v2/ontologies/properties",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createTestTextPropRequestJson),
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get

    }

    // Create an integer property.
    val label2 = LangString.make(LanguageCode.en, "blue test integer property").fold(e => throw e.head, v => v)
    val comment2 = Some(
      LangString
        .make(LanguageCode.en, "A blue test integer property")
        .fold(e => throw e.head, v => v),
    )
    val createTestIntegerPropRequestJson = CreatePropertyRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        propertyName = "hasBlueTestIntProp",
        subjectClassName = Some("BlueFreeTestClass"),
        propertyType = PropertyValueType.IntValue,
        label = label2,
        comment = comment2,
      )
      .value

    Post(
      "/v2/ontologies/properties",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createTestIntegerPropRequestJson),
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // Add cardinalities to the class.

    val addCardinalitiesRequestJson = AddCardinalitiesRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "BlueFreeTestClass",
        restrictions = List(
          Restriction(
            CardinalityRestriction.MaxCardinalityOne,
            onProperty = Property(ontology = "freetest", property = "hasBlueTestTextProp"),
          ),
          Restriction(
            CardinalityRestriction.MaxCardinalityOne,
            onProperty = Property(ontology = "freetest", property = "hasBlueTestIntProp"),
          ),
        ),
      )
      .value

    Post(
      "/v2/ontologies/cardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, addCardinalitiesRequestJson),
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // Create a resource of #BlueTestClass using only #hasBlueTestIntProp

    val resourceLabel: String = {
      val fuzz1 = Random.nextString(1000)
      val fuzz2 = List.fill(1000)(()).map(_ => Random.nextInt(128).toChar).mkString
      val fuzz3 = List.fill(1000)(()).map(_ => Random.nextPrintableChar()).mkString
      fuzz1 ++ fuzz2 ++ fuzz3
    }

    val createResourceWithValues: String =
      s"""{
         |  "@type" : "freetest:BlueFreeTestClass",
         |  "freetest:hasBlueTestIntProp" : {
         |    "@type" : "knora-api:IntValue",
         |    "knora-api:hasPermissions" : "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher",
         |    "knora-api:intValueAsInt" : 5,
         |    "knora-api:valueHasComment" : "this is the number five"
         |  },
         |  "knora-api:attachedToProject" : {
         |    "@id" : "http://rdfh.ch/projects/0001"
         |  },
         |  "rdfs:label" : ${JsString(resourceLabel).toString},
         |  "@context" : {
         |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
         |    "freetest" : "${SharedOntologyTestDataADM.FREETEST_ONTOLOGY_IRI_LocalHost}#"
         |  }
         |}""".stripMargin
    val _ = UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[TestClientService](
        _.checkResponseOK(
          Post(
            appConfig.knoraApi.internalKnoraApiBaseUrl + "/v2/resources",
            HttpEntity(RdfMediaTypes.`application/ld+json`, createResourceWithValues),
          ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)),
        ),
      ),
    )

    // payload to test cardinality can't be deleted
    val cardinalityCantBeDeletedPayload = AddCardinalitiesRequest.make(
      ontologyName = "freetest",
      lastModificationDate = freetestLastModDate,
      className = "FreeTest",
      restrictions = List(
        Restriction(
          CardinalityRestriction.MinCardinalityOne,
          onProperty = Property(ontology = "freetest", property = "hasText"),
        ),
      ),
    )

    // Expect cardinality can't be deleted - endpoint should return CanDo response with value false
    Post(
      "/v2/ontologies/candeletecardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, cardinalityCantBeDeletedPayload.value),
    ) ~>
      addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        assert(
          !responseJsonDoc.body
            .value(KnoraApiV2Complex.CanDo)
            .asInstanceOf[JsonLDBoolean]
            .value,
        )
      }

    // Prepare the JsonLD payload to check if a cardinality can be deleted and then to also actually delete it.
    val params =
      s"""
         |{
         |  "@id" : "${SharedOntologyTestDataADM.FREETEST_ONTOLOGY_IRI_LocalHost}",
         |  "@type" : "owl:Ontology",
         |  "knora-api:lastModificationDate" : {
         |    "@type" : "xsd:dateTimeStamp",
         |    "@value" : "$freetestLastModDate"
         |  },
         |  "@graph" : [ {
         |    "@id" : "freetest:BlueFreeTestClass",
         |    "@type" : "owl:Class",
         |    "rdfs:subClassOf" :  {
         |      "@type": "owl:Restriction",
         |      "owl:maxCardinality" : 1,
         |      "owl:onProperty" : {
         |        "@id" : "freetest:hasBlueTestTextProp"
         |      }
         |    }
         |  } ],
         |  "@context" : {
         |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
         |    "owl" : "http://www.w3.org/2002/07/owl#",
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
         |    "freetest" : "${SharedOntologyTestDataADM.FREETEST_ONTOLOGY_IRI_LocalHost}#"
         |  }
         |}""".stripMargin

    // Successfully check if the cardinality can be deleted
    Post(
      "/v2/ontologies/candeletecardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, params),
    ) ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password),
    ) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, response.toString)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
      assert(responseJsonDoc.body.value(KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
    }

    // Successfully remove the (unused) text value cardinality from the class.
    Patch("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password),
    ) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, response.toString)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }
  }

  "create two classes with the same property, use one in data, and allow removal of the cardinality for the property not used in data" in {
    // Create TestClassOne with no cardinalities.
    val label = LangString.make(LanguageCode.en, "Test class number one").fold(e => throw e.head, v => v)
    val comment = Some(
      LangString
        .make(LanguageCode.en, "A test class used for testing cardinalities")
        .fold(e => throw e.head, v => v),
    )
    val createClassRequestJsonOne = CreateClassRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "TestClassOne",
        label = label,
        comment = comment,
      )
      .value

    Post(
      "/v2/ontologies/classes",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createClassRequestJsonOne),
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      assert(status == StatusCodes.OK, response.toString)
      val responseJsonDoc = responseToJsonLDDocument(response)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // Create TestClassTwo with no cardinalities
    val label1 = LangString.make(LanguageCode.en, "Test class number two").fold(e => throw e.head, v => v)
    val comment1 = Some(
      LangString
        .make(LanguageCode.en, "A test class used for testing cardinalities")
        .fold(e => throw e.head, v => v),
    )
    val createClassRequestJsonTwo = CreateClassRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "TestClassTwo",
        label = label1,
        comment = comment1,
      )
      .value

    Post(
      "/v2/ontologies/classes",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createClassRequestJsonTwo),
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      assert(status == StatusCodes.OK, response.toString)
      val responseJsonDoc = responseToJsonLDDocument(response)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // Create a text property hasTestTextProp.
    val label2 = LangString.make(LanguageCode.en, "Test int property").fold(e => throw e.head, v => v)
    val comment2 = Some(
      LangString
        .make(LanguageCode.en, "A test int property")
        .fold(e => throw e.head, v => v),
    )
    val createPropRequestJson = CreatePropertyRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        propertyName = "hasIntProp",
        subjectClassName = None,
        propertyType = PropertyValueType.IntValue,
        label = label2,
        comment = comment2,
      )
      .value

    Post(
      "/v2/ontologies/properties",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createPropRequestJson),
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get

    }

    // Add cardinality hasIntProp to TestClassOne.
    val addCardinalitiesRequestJsonOne = AddCardinalitiesRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "TestClassOne",
        restrictions = List(
          Restriction(
            CardinalityRestriction.MaxCardinalityOne,
            onProperty = Property(ontology = "freetest", property = "hasIntProp"),
          ),
        ),
      )
      .value

    Post(
      "/v2/ontologies/cardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, addCardinalitiesRequestJsonOne),
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // Add cardinality hasIntProp to TestClassTwo.
    val addCardinalitiesRequestJsonTwo = AddCardinalitiesRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "TestClassTwo",
        restrictions = List(
          Restriction(
            CardinalityRestriction.MaxCardinalityOne,
            onProperty = Property(ontology = "freetest", property = "hasIntProp"),
          ),
        ),
      )
      .value

    Post(
      "/v2/ontologies/cardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, addCardinalitiesRequestJsonTwo),
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // Create a resource of #TestClassOne using #hasIntProp
    val createResourceWithValues: String =
      s"""{
         |  "@type" : "freetest:TestClassOne",
         |  "freetest:hasIntProp" : {
         |    "@type" : "knora-api:IntValue",
         |    "knora-api:hasPermissions" : "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher",
         |    "knora-api:intValueAsInt" : 5,
         |    "knora-api:valueHasComment" : "this is the number five"
         |  },
         |  "knora-api:attachedToProject" : {
         |    "@id" : "http://rdfh.ch/projects/0001"
         |  },
         |  "rdfs:label" : "test class instance",
         |  "@context" : {
         |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
         |    "freetest" : "${SharedOntologyTestDataADM.FREETEST_ONTOLOGY_IRI_LocalHost}#"
         |  }
         |}""".stripMargin
    val _ = UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[TestClientService](
        _.checkResponseOK(
          Post(
            appConfig.knoraApi.internalKnoraApiBaseUrl + "/v2/resources",
            HttpEntity(RdfMediaTypes.`application/ld+json`, createResourceWithValues),
          ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)),
        ),
      ),
    )

    // payload to ask if cardinality can be removed from TestClassTwo
    val cardinalityCanBeDeletedPayload = AddCardinalitiesRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "TestClassTwo",
        restrictions = List(
          Restriction(
            CardinalityRestriction.MaxCardinalityOne,
            onProperty = Property(ontology = "freetest", property = "hasIntProp"),
          ),
        ),
      )
      .value

    // Expect cardinality can be deleted from TestClassTwo - CanDo response should return true
    Post(
      "/v2/ontologies/candeletecardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, cardinalityCanBeDeletedPayload),
    ) ~>
      addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        assert(
          responseJsonDoc.body
            .value(KnoraApiV2Complex.CanDo)
            .asInstanceOf[JsonLDBoolean]
            .value,
        )
      }
  }

  "verify that link-property can not be deleted" in {

    // payload representing a link-property to test that cardinality can't be deleted
    val cardinalityOnLinkPropertyWhichCantBeDeletedPayload = AddCardinalitiesRequest.make(
      ontologyName = "anything",
      lastModificationDate = anythingLastModDate,
      className = "Thing",
      restrictions = List(
        Restriction(
          CardinalityRestriction.MinCardinalityZero,
          onProperty = Property(ontology = "anything", property = "isPartOfOtherThing"),
        ),
      ),
    )

    Post(
      "/v2/ontologies/candeletecardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, cardinalityOnLinkPropertyWhichCantBeDeletedPayload.value),
    ) ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password),
    ) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, response.toString)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
      assert(!responseJsonDoc.body.value(KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
    }
  }

  "verify that a class's cardinalities cannot be changed" in {
    val classSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing", "UTF-8")

    Get(s"/v2/ontologies/canreplacecardinalities/$classSegment") ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password),
    ) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
      assert(!responseJsonDoc.body.value(KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
    }
  }

  "determine that a class cannot be deleted" in {
    val thingIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing", "UTF-8")

    Get(s"/v2/ontologies/candeleteclass/$thingIri") ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password),
    ) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
      assert(!responseJsonDoc.body.value(KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
    }
  }

  "determine that a property cannot be deleted" in {
    val propertyIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger", "UTF-8")

    Get(s"/v2/ontologies/candeleteproperty/$propertyIri") ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password),
    ) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
      assert(!responseJsonDoc.body.value(KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
    }
  }

  "determine that an ontology cannot be deleted" in {
    val ontologyIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2", "UTF-8")

    Get(s"/v2/ontologies/candeleteontology/$ontologyIri") ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password),
    ) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
      assert(!responseJsonDoc.body.value(KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
    }
  }

  "create a class w/o comment" in {
    val label = LangString.make(LanguageCode.en, "Test label").fold(e => throw e.head, v => v)
    val request = CreateClassRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "testClass",
        label = label,
        comment = None,
      )
      .value

    Post(
      "/v2/ontologies/classes",
      HttpEntity(RdfMediaTypes.`application/ld+json`, request),
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      assert(status == StatusCodes.OK, response.toString)

      val responseJsonDoc = responseToJsonLDDocument(response)
      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }
  }

  "create a property w/o comment" in {
    val label = LangString.make(LanguageCode.en, "Test label").fold(e => throw e.head, v => v)
    val request = CreatePropertyRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        propertyName = "testProperty",
        subjectClassName = None,
        propertyType = PropertyValueType.IntValue,
        label = label,
        comment = None,
      )
      .value

    Post(
      "/v2/ontologies/properties",
      HttpEntity(RdfMediaTypes.`application/ld+json`, request),
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {

      val response = responseAs[String]
      assert(status == StatusCodes.OK, response)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(response)
      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }
  }

  "not create a property with invalid gui attribute" in {
    val params =
      s"""{
         |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
         |  "@type" : "owl:Ontology",
         |  "knora-api:lastModificationDate" : {
         |    "@type" : "xsd:dateTimeStamp",
         |    "@value" : "$anythingLastModDate"
         |  },
         |  "@graph" : [ {
         |      "@id" : "anything:hasPropertyWithWrongGuiAttribute",
         |      "@type" : "owl:ObjectProperty",
         |      "knora-api:subjectType" : {
         |        "@id" : "anything:Thing"
         |      },
         |      "knora-api:objectType" : {
         |        "@id" : "knora-api:TextValue"
         |      } ,
         |      "rdfs:label" : [ {
         |        "@language" : "en",
         |        "@value" : "has wrong GUI attribute"
         |      } ],
         |      "rdfs:subPropertyOf" : [ {
         |        "@id" : "knora-api:hasValue"
         |      } ],
         |      "salsah-gui:guiElement" : {
         |        "@id" : "salsah-gui:SimpleText"
         |      },
         |      "salsah-gui:guiAttribute" : [ "size=80", "maxilength=100" ]
         |  } ],
         |  "@context" : {
         |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
         |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
         |    "owl" : "http://www.w3.org/2002/07/owl#",
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
         |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
         |  }
         |}""".stripMargin

    Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password),
    ) ~> ontologiesPath ~> check {

      val responseStr: String = responseAs[String]
      assert(response.status == StatusCodes.BadRequest, responseStr)

    }
  }

  "not create a property with invalid gui attribute value" in {
    val params =
      s"""{
         |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
         |  "@type" : "owl:Ontology",
         |  "knora-api:lastModificationDate" : {
         |    "@type" : "xsd:dateTimeStamp",
         |    "@value" : "$anythingLastModDate"
         |  },
         |  "@graph" : [ {
         |      "@id" : "anything:hasPropertyWithWrongGuiAttribute",
         |      "@type" : "owl:ObjectProperty",
         |      "knora-api:subjectType" : {
         |        "@id" : "anything:Thing"
         |      },
         |      "knora-api:objectType" : {
         |        "@id" : "knora-api:TextValue"
         |      } ,
         |      "rdfs:label" : [ {
         |        "@language" : "en",
         |        "@value" : "has wrong GUI attribute"
         |      } ],
         |      "rdfs:subPropertyOf" : [ {
         |        "@id" : "knora-api:hasValue"
         |      } ],
         |      "salsah-gui:guiElement" : {
         |        "@id" : "salsah-gui:SimpleText"
         |      },
         |      "salsah-gui:guiAttribute" : [ "size=80", "maxlength=100.7" ]
         |  } ],
         |  "@context" : {
         |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
         |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
         |    "owl" : "http://www.w3.org/2002/07/owl#",
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
         |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
         |  }
         |}""".stripMargin

    Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password),
    ) ~> ontologiesPath ~> check {

      val responseStr: String = responseAs[String]
      assert(response.status == StatusCodes.BadRequest, responseStr)

    }
  }
}
