/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.e2e.v1

import akka.actor.ActorDSL._
import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import org.knora.webapi.e2e.E2ESpec
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.routing.v1.StoreRouteV1
import org.knora.webapi.store._
import org.knora.webapi.{BadRequestException, LiveActorMaker, StartupFlags}
import spray.http.MediaTypes._
import spray.http._
import spray.httpx.RequestBuilding

import scala.concurrent.Await
import scala.concurrent.duration._



/**
  * End-to-end test specification for testing [[StoreRouteV1]]. This specification uses the
  * Spray Testkit as documented here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class StoreRouteV1E2ESpec extends E2ESpec with RequestBuilding {

    override def testConfigSource =
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    /* Start a live ResponderManager */
    val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)

    /* Start a mocked StoreManager */
    val storeManagerProbe = actor(STORE_MANAGER_ACTOR_NAME)(new Act {
        become {
            case ResetTriplestoreContent(rdo) => {
                if (rdo === rdfDataObjects) {
                    Thread.sleep(10000)
                    sender ! ResetTriplestoreContentACK
                } else {
                    throw BadRequestException(s"Payload not what is expected: ${rdo.toString}")
                }
            }
            case _ => throw BadRequestException("Shouldn't be here")
        }
    })

    /* get the path of the route we want to test */
    val storePath = StoreRouteV1.rapierPath(system, settings, log)

    /* set the timeout for the route test */
    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(60).second)

    val rdfDataObjectsJsonList =
        """
            [
                {"path": "../knora-ontologies/knora-base.ttl", "name": "http://www.knora.org/ontology/knora-base"},
                {"path": "../knora-ontologies/knora-dc.ttl", "name": "http://www.knora.org/ontology/dc"},
                {"path": "../knora-ontologies/salsah-gui.ttl", "name": "http://www.knora.org/ontology/salsah-gui"},
                {"path": "_test_data/ontologies/incunabula-onto.ttl", "name": "http://www.knora.org/ontology/incunabula"},
                {"path": "_test_data/all_data/incunabula-data.ttl", "name": "http://www.knora.org/data/incunabula"}
            ]
        """

    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula")
    )

    "The ResetTriplestoreContent Route ('v1/store/ResetTriplestoreContent')" should {
        "succeed with resetting if startup flag is set" in {
            /**
              * This test corresponds to the following curl call:
              * curl -H "Content-Type: application/json" -X POST -d '[{"path":"../knora-ontologies/knora-base.ttl","name":"http://www.knora.org/ontology/knora-base"}]' http://localhost:3333/v1/store/ResetTriplestoreContent
              */
            println("=>>")
            StartupFlags.allowResetTriplestoreContentOperationOverHTTP send true
            println("=>>" + Await.result(StartupFlags.allowResetTriplestoreContentOperationOverHTTP.future(), 5.seconds))
            log.debug(s"StartupFlags.allowResetTriplestoreContentOperationOverHTTP = ${StartupFlags.allowResetTriplestoreContentOperationOverHTTP.get}")
            println("=>>" + Await.result(StartupFlags.allowResetTriplestoreContentOperationOverHTTP.future(), 5.seconds))
            Post("/v1/store/ResetTriplestoreContent", HttpEntity(`application/json`, rdfDataObjectsJsonList)) ~> storePath ~> check {
                log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
            }
        }
        "fail with resetting if startup flag is not set" in {
            StartupFlags.allowResetTriplestoreContentOperationOverHTTP send false
            log.debug(s"StartupFlags.allowResetTriplestoreContentOperationOverHTTP = ${StartupFlags.allowResetTriplestoreContentOperationOverHTTP.get}")
            Post("/v1/store/ResetTriplestoreContent", HttpEntity(`application/json`, rdfDataObjectsJsonList)) ~> storePath ~> check {
                log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Forbidden)

            }
        }
    }
}
