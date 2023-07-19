/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import akka.http.javadsl.model.StatusCodes
import spray.json._

import java.net.URLEncoder
import java.nio.file.Paths
import scala.concurrent.ExecutionContextExecutor

import org.knora.webapi._
import org.knora.webapi.e2e.v2.ResponseCheckerV2._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.routing.v2.ResourcesRouteV2

/**
 * End-to-end specification for the handling of JSONLD documents.
 */
class JSONLDHandlingV2R2RSpec extends R2RSpec {

  private val resourcesPath = ResourcesRouteV2(appConfig).makeRoute

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula")
  )

  "The JSON-LD processor" should {
    "expand prefixes (on the client side)" in {
      // JSON-LD with prefixes and context object
      val jsonldWithPrefixes =
        readOrWriteTextFile(
          "",
          Paths.get("..", "test_data/resourcesR2RV2/NarrenschiffFirstPage.jsonld"),
          writeFile = false
        )

      // expand JSON-LD with JSON-LD processor
      val jsonldParsedExpanded = JsonLDUtil.parseJsonLD(jsonldWithPrefixes)

      // expected result after expansion
      val expectedJsonldExpandedParsed = JsonLDUtil.parseJsonLD(
        readOrWriteTextFile(
          "",
          Paths.get("..", "test_data/resourcesR2RV2/NarrenschiffFirstPageExpanded.jsonld"),
          writeFile = false
        )
      )

      compareParsedJSONLDForResourcesResponse(
        expectedResponse = expectedJsonldExpandedParsed,
        receivedResponse = jsonldParsedExpanded
      )

    }

    "produce the expected JSON-LD context object (on the server side)" in {
      Get("/v2/resources/" + URLEncoder.encode("http://rdfh.ch/0803/7bbb8e59b703", "UTF-8")) ~> resourcesPath ~> check {

        assert(status == StatusCodes.OK, responseToJsonLDDocument(response))

        val receivedJson: JsObject = JsonParser(responseAs[String]).asJsObject

        val expectedJson: JsObject =
          JsonParser(
            readOrWriteTextFile(
              "",
              Paths.get("..", "test_data/resourcesR2RV2/NarrenschiffFirstPage.jsonld"),
              writeFile = false
            )
          ).asJsObject

        assert(receivedJson.fields("@context") == expectedJson.fields("@context"), "@context incorrect")
      }
    }
  }
}
