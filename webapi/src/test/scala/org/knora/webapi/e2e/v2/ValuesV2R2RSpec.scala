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

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.knora.webapi._
import org.knora.webapi.app.{APPLICATION_MANAGER_ACTOR_NAME, ApplicationActor}
import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.responders.v2.search.SparqlQueryConstants
import org.knora.webapi.routing.v2.{SearchRouteV2, ValuesRouteV2}
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.util.{JsonLDArray, JsonLDConstants, JsonLDDocument, JsonLDObject}
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.stringformatter.SmartIri
import org.knora.webapi.util.{MutableTestIri, SmartIri}
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}

import scala.concurrent.ExecutionContextExecutor

/**
  * Tests creating a still image file value using a mock Sipi.
  */
class ValuesV2R2RSpec extends R2RSpec {
    override def testConfigSource: String =
        """
          |# akka.loglevel = "DEBUG"
          |# akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val valuesPath = new ValuesRouteV2(routeData).knoraApiPath
    private val searchPath = new SearchRouteV2(routeData).knoraApiPath

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    /* we need to run our app with the mocked sipi actor */
    override lazy val appActor: ActorRef = system.actorOf(Props(new ApplicationActor with ManagersWithMockedSipi).withDispatcher(KnoraDispatchers.KnoraActorDispatcher), name = APPLICATION_MANAGER_ACTOR_NAME)

    private val aThingPictureIri = "http://rdfh.ch/0001/a-thing-picture"

    private val anythingUserEmail = SharedTestDataADM.anythingUser1.email
    private val password = "test"

    private val stillImageFileValueIri = new MutableTestIri

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    private def getResourceWithValues(resourceIri: IRI,
                                      propertyIrisForGravsearch: Seq[SmartIri],
                                      userEmail: String): JsonLDDocument = {
        // Make a Gravsearch query from a template.
        val gravsearchQuery: String = twirl.queries.gravsearch.txt.getResourceWithSpecifiedProperties(
            resourceIri = resourceIri,
            propertyIris = propertyIrisForGravsearch
        ).toString()

        // Run the query.

        Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(userEmail, password)) ~> searchPath ~> check {
            assert(status == StatusCodes.OK, response.toString)
            responseToJsonLDDocument(response)
        }
    }

    private def getValuesFromResource(resource: JsonLDDocument,
                                      propertyIriInResult: SmartIri): JsonLDArray = {
        resource.requireArray(propertyIriInResult.toString)
    }

    private def getValueFromResource(resource: JsonLDDocument,
                                     propertyIriInResult: SmartIri,
                                     expectedValueIri: IRI): JsonLDObject = {
        val resourceIri: IRI = resource.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
        val propertyValues: JsonLDArray = getValuesFromResource(resource = resource, propertyIriInResult = propertyIriInResult)

        val matchingValues: Seq[JsonLDObject] = propertyValues.value.collect {
            case jsonLDObject: JsonLDObject if jsonLDObject.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri) == expectedValueIri => jsonLDObject
        }

        if (matchingValues.isEmpty) {
            throw AssertionException(s"Property <$propertyIriInResult> of resource <$resourceIri> does not have value <$expectedValueIri>")
        }

        if (matchingValues.size > 1) {
            throw AssertionException(s"Property <$propertyIriInResult> of resource <$resourceIri> has more than one value with the IRI <$expectedValueIri>")
        }

        matchingValues.head
    }

    private def getValue(resourceIri: IRI,
                         propertyIriForGravsearch: SmartIri,
                         propertyIriInResult: SmartIri,
                         expectedValueIri: IRI,
                         userEmail: String): JsonLDObject = {
        val resource: JsonLDDocument = getResourceWithValues(
            resourceIri = resourceIri,
            propertyIrisForGravsearch = Seq(propertyIriForGravsearch),
            userEmail = userEmail
        )

        getValueFromResource(
            resource = resource,
            propertyIriInResult = propertyIriInResult,
            expectedValueIri = expectedValueIri
        )
    }

    "The values v2 endpoint" should {
        "update a still image file value using a mock Sipi" in {
            val resourceIri: IRI = aThingPictureIri
            val internalFilename = "IQUO3t1AABm-FSLC0vNvVpr.jp2"

            val jsonLdEntity = SharedTestDataADM.updateStillImageFileValueRequest(
                resourceIri = resourceIri,
                valueIri = "http://rdfh.ch/0001/a-thing-picture/values/goZ7JFRNSeqF-dNxsqAS7Q",
                internalFilename = internalFilename
            )

            Put("/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> valuesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                val valueIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
                stillImageFileValueIri.set(valueIri)
                val valueType: SmartIri = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)
                valueType should ===(OntologyConstants.KnoraApiV2Complex.StillImageFileValue.toSmartIri)

                val savedValue: JsonLDObject = getValue(
                    resourceIri = resourceIri,
                    propertyIriForGravsearch = OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri,
                    propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri,
                    expectedValueIri = stillImageFileValueIri.get,
                    userEmail = anythingUserEmail
                )

                savedValue.requireString(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename) should ===(internalFilename)
                savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasDimX) should ===(512)
                savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasDimY) should ===(256)
            }
        }
    }
}
