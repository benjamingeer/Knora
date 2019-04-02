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

package org.knora.webapi.e2e.v2

import java.io.File
import java.net.URLEncoder
import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{Accept, BasicHttpCredentials}
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, MediaRange, StatusCodes}
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.e2e.v2.ResponseCheckerR2RV2._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.{FileUtil, PermissionUtilADM, StringFormatter}

import scala.concurrent.ExecutionContextExecutor

/**
  * Tests the API v2 resources route.
  */
class ResourcesRouteV2E2ESpec extends E2ESpec(ResourcesRouteV2E2ESpec.config) {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    private val anythingUserEmail = SharedTestDataADM.anythingUser1.email
    private val password = "test"
    private var aThingLastModificationDate = Instant.now

    // If true, writes all API responses to test data files. If false, compares the API responses to the existing test data files.
    private val writeTestDataFiles = false

    override lazy val rdfDataObjects: List[RdfDataObject] = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")

    )

    "The resources v2 endpoint" should {

        "perform a resource request for the book 'Reise ins Heilige Land' using the complex schema in JSON-LD" in {
            val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/0803/2a6221216701", "UTF-8")}")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"), writeTestDataFiles)
            compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "perform a resource request for the book 'Reise ins Heilige Land' using the complex schema in Turtle" in {
            // Test correct handling of q values in the Accept header.
            val acceptHeader: Accept = Accept(
                MediaRange.One(RdfMediaTypes.`application/ld+json`, 0.5F),
                MediaRange.One(RdfMediaTypes.`text/turtle`, 0.8F),
                MediaRange.One(RdfMediaTypes.`application/rdf+xml`, 0.2F)
            )

            val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/0803/2a6221216701", "UTF-8")}").addHeader(acceptHeader)
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerTurtle = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"), writeTestDataFiles)
            assert(parseTurtle(responseAsString) == parseTurtle(expectedAnswerTurtle))
        }

        "perform a resource request for the book 'Reise ins Heilige Land' using the complex schema in RDF/XML" in {
            val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/0803/2a6221216701", "UTF-8")}").addHeader(Accept(RdfMediaTypes.`application/rdf+xml`))
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerRdfXml = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLand.rdf"), writeTestDataFiles)
            assert(parseRdfXml(responseAsString) == parseRdfXml(expectedAnswerRdfXml))
        }

        "perform a resource preview request for the book 'Reise ins Heilige Land' using the complex schema" in {
            val request = Get(s"$baseApiUrl/v2/resourcespreview/${URLEncoder.encode("http://rdfh.ch/0803/2a6221216701", "UTF-8")}")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLandPreview.jsonld"), writeTestDataFiles)
            compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema (specified by an HTTP header) in JSON-LD" in {
            val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/0803/2a6221216701", "UTF-8")}").addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.jsonld"), writeTestDataFiles)
            compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema in Turtle" in {
            val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/0803/2a6221216701", "UTF-8")}").
                addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)).
                addHeader(Accept(RdfMediaTypes.`text/turtle`))
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerTurtle = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.ttl"), writeTestDataFiles)
            assert(parseTurtle(responseAsString) == parseTurtle(expectedAnswerTurtle))
        }

        "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema in RDF/XML" in {
            val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/0803/2a6221216701", "UTF-8")}").
                addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)).
                addHeader(Accept(RdfMediaTypes.`application/rdf+xml`))
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerRdfXml = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.rdf"), writeTestDataFiles)
            assert(parseRdfXml(responseAsString) == parseRdfXml(expectedAnswerRdfXml))
        }

        "perform a resource preview request for the book 'Reise ins Heilige Land' using the simple schema (specified by an HTTP header)" in {
            val request = Get(s"$baseApiUrl/v2/resourcespreview/${URLEncoder.encode("http://rdfh.ch/0803/2a6221216701", "UTF-8")}").addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLandSimplePreview.jsonld"), writeTestDataFiles)
            compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema (specified by a URL parameter)" in {
            val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/0803/2a6221216701", "UTF-8")}?${RouteUtilV2.SCHEMA_PARAM}=${RouteUtilV2.SIMPLE_SCHEMA_NAME}")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.jsonld"), writeTestDataFiles)
            compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "perform a full resource request for a resource with a BCE date property" in {
            val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/thing_with_BCE_date", "UTF-8")}")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/ThingWithBCEDate.jsonld"), writeTestDataFiles)
            compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "perform a full resource request for a resource with a date property that represents a period going from BCE to CE" in {
            val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/thing_with_BCE_date2", "UTF-8")}")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/ThingWithBCEDate2.jsonld"), writeTestDataFiles)
            compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "perform a full resource request for a resource with a list value" ignore { // disabled because the language in which the label is returned is not deterministic
            val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/thing_with_list_value", "UTF-8")}")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/ThingWithListValue.jsonld"), writeTestDataFiles)
            compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "perform a full resource request for a resource with a link (in the complex schema)" in {
            val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ", "UTF-8")}")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/ThingWithLinkComplex.jsonld"), writeTestDataFiles)
            compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "perform a full resource request for a resource with a link (in the simple schema)" in {
            val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ", "UTF-8")}").addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/ThingWithLinkSimple.jsonld"), writeTestDataFiles)
            compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "perform a full resource request for a resource with a Text language (in the complex schema)" in {
            val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/a-thing-with-text-valuesLanguage", "UTF-8")}")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/ThingWithTextLangComplex.jsonld"), writeTestDataFiles)
            compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "perform a full resource request for a resource with a Text language (in the simple schema)" in {
            val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/a-thing-with-text-valuesLanguage", "UTF-8")}").addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/ThingWithTextLangSimple.jsonld"), writeTestDataFiles)
            compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "perform a full resource request for a past version of a resource, using a URL-encoded xsd:dateTimeStamp" in {
            val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/thing-with-history", "UTF-8")}?version=${URLEncoder.encode("2019-02-12T08:05:10.351Z", "UTF-8")}")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/ThingWithVersionHistory.jsonld"), writeTestDataFiles)
            compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "perform a full resource request for a past version of a resource, using a Knora ARK timestamp" in {
            val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/thing-with-history", "UTF-8")}?version=${URLEncoder.encode("20190212T080510351Z", "UTF-8")}")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/ThingWithVersionHistory.jsonld"), writeTestDataFiles)
            compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "return the complete version history of a resource" in {
            val request = Get(s"$baseApiUrl/v2/resources/history/${URLEncoder.encode("http://rdfh.ch/0001/thing-with-history", "UTF-8")}")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/CompleteVersionHistory.jsonld"), writeTestDataFiles)
            compareJSONLDForResourceHistoryResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "return the version history of a resource within a date range" in {
            val resourceIri = URLEncoder.encode("http://rdfh.ch/0001/thing-with-history", "UTF-8")
            val startDate = URLEncoder.encode(Instant.parse("2019-02-08T15:05:11Z").toString, "UTF-8")
            val endDate =  URLEncoder.encode(Instant.parse("2019-02-13T09:05:10Z").toString, "UTF-8")
            val request = Get(s"$baseApiUrl/v2/resources/history/$resourceIri?startDate=$startDate&endDate=$endDate")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/PartialVersionHistory.jsonld"), writeTestDataFiles)
            compareJSONLDForResourceHistoryResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "return each of the versions of a resource listed in its version history" in {
            val resourceIri = URLEncoder.encode("http://rdfh.ch/0001/thing-with-history", "UTF-8")
            val historyRequest = Get(s"$baseApiUrl/v2/resources/history/$resourceIri")
            val historyResponse: HttpResponse = singleAwaitingRequest(historyRequest)
            val historyResponseAsString = responseToString(historyResponse)
            assert(historyResponse.status == StatusCodes.OK, historyResponseAsString)
            val jsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(historyResponseAsString)
            val entries: JsonLDArray = jsonLDDocument.requireArray("@graph")

            for (entry: JsonLDValue <- entries.value) {
                entry match {
                    case jsonLDObject: JsonLDObject =>
                        val versionDate: Instant = jsonLDObject.requireDatatypeValueInObject(
                            key = OntologyConstants.KnoraApiV2WithValueObjects.VersionDate,
                            expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                            validationFun = stringFormatter.xsdDateTimeStampToInstant
                        )

                        val arkTimestamp = stringFormatter.formatArkTimestamp(versionDate)
                        val versionRequest = Get(s"$baseApiUrl/v2/resources/$resourceIri?version=$arkTimestamp")
                        val versionResponse: HttpResponse = singleAwaitingRequest(versionRequest)
                        val versionResponseAsString = responseToString(versionResponse)
                        assert(versionResponse.status == StatusCodes.OK, versionResponseAsString)
                        val expectedAnswerJSONLD = readOrWriteTextFile(versionResponseAsString, new File(s"src/test/resources/test-data/resourcesR2RV2/ThingWithVersionHistory$arkTimestamp.jsonld"), writeTestDataFiles)
                        compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = versionResponseAsString)

                    case other => throw AssertionException(s"Expected JsonLDObject, got $other")
                }
            }
        }

        "return a graph of resources reachable via links from/to a given resource" in {
            val request = Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?direction=both")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)

            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/ThingGraphBoth.jsonld"), writeTestDataFiles)
            val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)

            assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
        }

        "return a graph of resources reachable via links from a given resource" in {
            val request = Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?direction=outbound")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)

            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/ThingGraphOutbound.jsonld"), writeTestDataFiles)
            val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)

            assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
        }

        "return a graph of resources reachable via links to a given resource" in {
            val request = Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?direction=inbound")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)

            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/ThingGraphInbound.jsonld"), writeTestDataFiles)
            val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)

            assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
        }

        "return a graph of resources reachable via links to/from a given resource, excluding a specified property" in {
            val request = Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?direction=both&excludeProperty=${URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#isPartOfOtherThing", "UTF-8")}")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)

            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/ThingGraphBothWithExcludedProp.jsonld"), writeTestDataFiles)
            val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)

            assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
        }

        "return a graph of resources reachable via links from a given resource, specifying search depth" in {
            val request = Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?direction=both&depth=2")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)

            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/ThingGraphBothWithDepth.jsonld"), writeTestDataFiles)
            val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)

            assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
        }

        "not accept a graph request with an invalid direction" in {
            val request = Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?direction=foo")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.BadRequest, responseAsString)
        }

        "not accept a graph request with an invalid depth (< 1)" in {
            val request = Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?depth=0")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.BadRequest, responseAsString)
        }

        "not accept a graph request with an invalid depth (> max)" in {
            val request = Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?depth=${settings.maxGraphBreadth + 1}")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.BadRequest, responseAsString)
        }

        "not accept a graph request with an invalid property to exclude" in {
            val request = Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?excludeProperty=foo")
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.BadRequest, responseAsString)
        }

        "return resources from a project" in {
            val resourceClass = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book", "UTF-8")
            val orderByProperty = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#title", "UTF-8")
            val request = Get(s"$baseApiUrl/v2/resources?resourceClass=$resourceClass&orderByProperty=$orderByProperty&page=0").addHeader(new ProjectHeader(SharedTestDataADM.incunabulaProject.id)) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.incunabulaProjectAdminUser.email, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            val responseAsString = responseToString(response)
            assert(response.status == StatusCodes.OK, responseAsString)
            val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString, new File("src/test/resources/test-data/resourcesR2RV2/BooksFromIncunabula.jsonld"), writeTestDataFiles)
            compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
        }

        "create a resource with values" in {
            val jsonLDEntity =
                """{
                  |  "@type" : "anything:Thing",
                  |  "anything:hasBoolean" : {
                  |    "@type" : "knora-api:BooleanValue",
                  |    "knora-api:booleanValueAsBoolean" : true
                  |  },
                  |  "anything:hasColor" : {
                  |    "@type" : "knora-api:ColorValue",
                  |    "knora-api:colorValueAsColor" : "#ff3333"
                  |  },
                  |  "anything:hasDate" : {
                  |    "@type" : "knora-api:DateValue",
                  |    "knora-api:dateValueHasCalendar" : "GREGORIAN",
                  |    "knora-api:dateValueHasEndEra" : "CE",
                  |    "knora-api:dateValueHasEndYear" : 1489,
                  |    "knora-api:dateValueHasStartEra" : "CE",
                  |    "knora-api:dateValueHasStartYear" : 1489
                  |  },
                  |  "anything:hasDecimal" : {
                  |    "@type" : "knora-api:DecimalValue",
                  |    "knora-api:decimalValueAsDecimal" : {
                  |      "@type" : "xsd:decimal",
                  |      "@value" : "100000000000000.000000000000001"
                  |    }
                  |  },
                  |  "anything:hasGeometry" : {
                  |    "@type" : "knora-api:GeomValue",
                  |    "knora-api:geometryValueAsGeometry" : "{\"status\":\"active\",\"lineColor\":\"#ff3333\",\"lineWidth\":2,\"points\":[{\"x\":0.08098591549295775,\"y\":0.16741071428571427},{\"x\":0.7394366197183099,\"y\":0.7299107142857143}],\"type\":\"rectangle\",\"original_index\":0}"
                  |  },
                  |  "anything:hasGeoname" : {
                  |    "@type" : "knora-api:GeonameValue",
                  |    "knora-api:geonameValueAsGeonameCode" : "2661604"
                  |  },
                  |  "anything:hasInteger" : [ {
                  |    "@type" : "knora-api:IntValue",
                  |    "knora-api:hasPermissions" : "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher",
                  |    "knora-api:intValueAsInt" : 5,
                  |    "knora-api:valueHasComment" : "this is the number five"
                  |  }, {
                  |    "@type" : "knora-api:IntValue",
                  |    "knora-api:intValueAsInt" : 6
                  |  } ],
                  |  "anything:hasInterval" : {
                  |    "@type" : "knora-api:IntervalValue",
                  |    "knora-api:intervalValueHasEnd" : {
                  |      "@type" : "xsd:decimal",
                  |      "@value" : "3.4"
                  |    },
                  |    "knora-api:intervalValueHasStart" : {
                  |      "@type" : "xsd:decimal",
                  |      "@value" : "1.2"
                  |    }
                  |  },
                  |  "anything:hasListItem" : {
                  |    "@type" : "knora-api:ListValue",
                  |    "knora-api:listValueAsListNode" : {
                  |      "@id" : "http://rdfh.ch/lists/0001/treeList03"
                  |    }
                  |  },
                  |  "anything:hasOtherThingValue" : {
                  |    "@type" : "knora-api:LinkValue",
                  |    "knora-api:linkValueHasTargetIri" : {
                  |      "@id" : "http://rdfh.ch/0001/a-thing"
                  |    }
                  |  },
                  |  "anything:hasRichtext" : {
                  |    "@type" : "knora-api:TextValue",
                  |    "knora-api:textValueAsXml" : "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text><p><strong>this is</strong> text</p> with standoff</text>",
                  |    "knora-api:textValueHasMapping" : {
                  |      "@id" : "http://rdfh.ch/standoff/mappings/StandardMapping"
                  |    }
                  |  },
                  |  "anything:hasText" : {
                  |    "@type" : "knora-api:TextValue",
                  |    "knora-api:valueAsString" : "this is text without standoff"
                  |  },
                  |  "anything:hasUri" : {
                  |    "@type" : "knora-api:UriValue",
                  |    "knora-api:uriValueAsUri" : {
                  |      "@type" : "xsd:anyURI",
                  |      "@value" : "https://www.knora.org"
                  |    }
                  |  },
                  |  "knora-api:attachedToProject" : {
                  |    "@id" : "http://rdfh.ch/projects/0001"
                  |  },
                  |  "rdfs:label" : "test thing",
                  |  "@context" : {
                  |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                  |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                  |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                  |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                  |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                  |  }
                  |}""".stripMargin

            val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            assert(resourceIri.toSmartIri.isKnoraDataIri)
        }

        "create a resource with a custom creation date" in {
            val creationDate: Instant = Instant.parse("2019-01-09T15:45:54.502951Z")

            val jsonLDEntity =
                s"""{
                  |  "@type" : "anything:Thing",
                  |  "knora-api:attachedToProject" : {
                  |    "@id" : "http://rdfh.ch/projects/0001"
                  |  },
                  |  "anything:hasBoolean" : {
                  |    "@type" : "knora-api:BooleanValue",
                  |    "knora-api:booleanValueAsBoolean" : true
                  |  },
                  |  "rdfs:label" : "test thing",
                  |  "knora-api:creationDate" : {
                  |    "@type" : "xsd:dateTimeStamp",
                  |    "@value" : "$creationDate"
                  |  },
                  |  "@context" : {
                  |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                  |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                  |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                  |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                  |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                  |  }
                  |}""".stripMargin

            val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            assert(resourceIri.toSmartIri.isKnoraDataIri)

            val savedCreationDate: Instant = responseJsonDoc.body.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2WithValueObjects.CreationDate,
                expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                validationFun = stringFormatter.xsdDateTimeStampToInstant
            )

            assert(savedCreationDate == creationDate)
        }

        "create a resource containing escaped text" in {
            val jsonLDEntity = FileUtil.readTextFile(new File("src/test/resources/test-data/resourcesR2RV2/CreateResourceWithEscape.jsonld"))
            val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK, response.toString)
            val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
            val resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            assert(resourceIri.toSmartIri.isKnoraDataIri)
        }

        "update the metadata of a resource" in {
            val resourceIri = "http://rdfh.ch/0001/a-thing"
            val newLabel = "test thing with modified label"
            val newPermissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:ProjectMember"
            val newModificationDate = Instant.now.plus(java.time.Duration.ofDays(1))

            val jsonLDEntity =
                s"""|{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:Thing",
                   |  "rdfs:label" : "$newLabel",
                   |  "knora-api:hasPermissions" : "$newPermissions",
                   |  "knora-api:newModificationDate" : {
                   |    "@type" : "xsd:dateTimeStamp",
                   |    "@value" : "$newModificationDate"
                   |  },
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val updateRequest = Put(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val updateResponse: HttpResponse = singleAwaitingRequest(updateRequest)
            val updateResponseAsString = responseToString(updateResponse)
            assert(updateResponse.status == StatusCodes.OK, updateResponseAsString)

            val previewRequest = Get(s"$baseApiUrl/v2/resourcespreview/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val previewResponse: HttpResponse = singleAwaitingRequest(previewRequest)
            val previewResponseAsString = responseToString(previewResponse)
            assert(previewResponse.status == StatusCodes.OK, previewResponseAsString)

            val previewJsonLD = JsonLDUtil.parseJsonLD(previewResponseAsString)
            val updatedLabel: String = previewJsonLD.requireString(OntologyConstants.Rdfs.Label)
            assert(updatedLabel == newLabel)
            val updatedPermissions: String = previewJsonLD.requireString(OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions)
            assert(PermissionUtilADM.parsePermissions(updatedPermissions) == PermissionUtilADM.parsePermissions(newPermissions))

            val lastModificationDate: Instant = previewJsonLD.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate,
                expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                validationFun = stringFormatter.xsdDateTimeStampToInstant
            )

            assert(lastModificationDate == newModificationDate)
            aThingLastModificationDate = newModificationDate
        }

        "mark a resource as deleted" in {
            val resourceIri = "http://rdfh.ch/0001/a-thing"

            val jsonLDEntity =
                s"""|{
                    |  "@id" : "http://rdfh.ch/0001/a-thing",
                    |  "@type" : "anything:Thing",
                    |  "knora-api:lastModificationDate" : {
                    |    "@type" : "xsd:dateTimeStamp",
                    |    "@value" : "$aThingLastModificationDate"
                    |  },
                    |  "knora-api:deleteComment" : "This resource is too boring.",
                    |  "@context" : {
                    |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                    |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                    |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                    |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                    |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                    |  }
                    |}""".stripMargin

            val updateRequest = Post(s"$baseApiUrl/v2/resources/delete", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val updateResponse: HttpResponse = singleAwaitingRequest(updateRequest)
            val updateResponseAsString = responseToString(updateResponse)
            assert(updateResponse.status == StatusCodes.OK, updateResponseAsString)

            val previewRequest = Get(s"$baseApiUrl/v2/resourcespreview/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val previewResponse: HttpResponse = singleAwaitingRequest(previewRequest)
            val previewResponseAsString = responseToString(previewResponse)
            assert(previewResponse.status == StatusCodes.NotFound, previewResponseAsString)
        }
    }
}

object ResourcesRouteV2E2ESpec {
    val config: Config = ConfigFactory.parseString(
        """akka.loglevel = "DEBUG"
          |akka.stdout-loglevel = "DEBUG"
          |app.triplestore.profile-queries = false
        """.stripMargin)
}
