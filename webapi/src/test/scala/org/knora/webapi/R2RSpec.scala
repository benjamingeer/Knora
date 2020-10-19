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

package org.knora.webapi

import java.io.{File, StringReader}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}
import org.knora.webapi.app.{ApplicationActor, LiveManagers}
import org.knora.webapi.core.Core
import org.knora.webapi.http.handler
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.app.appmessages.{AppStart, AppStop, SetAllowReloadOverHTTPState}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetRepositoryContent}
import org.knora.webapi.messages.util.{JsonLDDocument, JsonLDUtil, KnoraSystemInstances}
import org.knora.webapi.messages.v1.responder.ontologymessages.LoadOntologiesRequest
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl, _}
import org.knora.webapi.util.{FileUtil, StartupUtils}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

/**
  * R(oute)2R(esponder) Spec base class. Please, for any new E2E tests, use E2ESpec.
  */
class R2RSpec extends Core with StartupUtils with Suite with ScalatestRouteTest with AnyWordSpecLike with Matchers with BeforeAndAfterAll with LazyLogging {

    /* needed by the core trait */
    implicit lazy val _system: ActorSystem = ActorSystem(actorSystemNameFrom(getClass), TestContainers.PortConfig.withFallback(ConfigFactory.load()))
    implicit lazy val settings: KnoraSettingsImpl = KnoraSettings(_system)
    lazy val executionContext: ExecutionContext = _system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

    // override so that we can use our own system
    override def createActorSystem(): ActorSystem = _system

    def actorRefFactory: ActorSystem = _system

    StringFormatter.initForTest()

    implicit val knoraExceptionHandler: ExceptionHandler = handler.KnoraExceptionHandler(settings)

    implicit val timeout: Timeout = Timeout(settings.defaultTimeout)

    lazy val appActor: ActorRef = system.actorOf(Props(new ApplicationActor with LiveManagers).withDispatcher(KnoraDispatchers.KnoraActorDispatcher), name = APPLICATION_MANAGER_ACTOR_NAME)

    // The main application actor forwards messages to the responder manager and the store manager.
    val responderManager: ActorRef = appActor
    val storeManager: ActorRef = appActor

    val routeData: KnoraRouteData = KnoraRouteData(
        system = system,
        appActor = appActor
    )

    lazy val rdfDataObjects = List.empty[RdfDataObject]

    val log: LoggingAdapter = akka.event.Logging(system, this.getClass)

    override def beforeAll {
        // set allow reload over http
        appActor ! SetAllowReloadOverHTTPState(true)

        // start the knora service, loading data from the repository
        appActor ! AppStart(ignoreRepository = true, requiresIIIFService = false)

        // waits until knora is up and running
        applicationStateRunning()

        loadTestData(rdfDataObjects)
    }

    override def afterAll {
        /* Stop the server when everything else has finished */
        appActor ! AppStop()
    }

    protected def responseToJsonLDDocument(httpResponse: HttpResponse): JsonLDDocument = {
        val responseBodyFuture: Future[String] = httpResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
        val responseBodyStr = Await.result(responseBodyFuture, 5.seconds)
        JsonLDUtil.parseJsonLD(responseBodyStr)
    }

    protected def parseTurtle(turtleStr: String): Model = {
        Rio.parse(new StringReader(turtleStr), "", RDFFormat.TURTLE, null)
    }

    protected def parseRdfXml(rdfXmlStr: String): Model = {
        Rio.parse(new StringReader(rdfXmlStr), "", RDFFormat.RDFXML, null)
    }

    protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit = {
        implicit val timeout: Timeout = Timeout(settings.defaultTimeout)
        Await.result(appActor ? ResetRepositoryContent(rdfDataObjects), 5 minutes)
        Await.result(appActor ? LoadOntologiesRequest(KnoraSystemInstances.Users.SystemUser), 30 seconds)
    }

    /**
      * Reads or writes a test data file.
      *
      * @param responseAsString the API response received from Knora.
      * @param file             the file in which the expected API response is stored.
      * @param writeFile        if `true`, writes the response to the file and returns it, otherwise returns the current contents of the file.
      * @return the expected response.
      */
    protected def readOrWriteTextFile(responseAsString: String, file: File, writeFile: Boolean = false): String = {
        if (writeFile) {
            // Per default only read access is allowed in the bazel sandbox.
            // This workaround allows to save test output.
            val testOutputDir = sys.env("TEST_UNDECLARED_OUTPUTS_DIR")
            val newOutputFile = new File(testOutputDir, file.getPath)
            newOutputFile.getParentFile.mkdirs()
            FileUtil.writeTextFile(newOutputFile, responseAsString.replaceAll(settings.externalSipiIIIFGetUrl, "IIIF_BASE_URL"))
            responseAsString
        } else {
            FileUtil.readTextFile(file).replaceAll("IIIF_BASE_URL", settings.externalSipiIIIFGetUrl)
        }
    }
}
