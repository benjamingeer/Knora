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

package org.knora.webapi.e2e.admin

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Cookie
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.messages.admin.responder.sipimessages.SipiFileInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.sipimessages.SipiResponderResponseADMJsonProtocol._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.{SessionJsonProtocol, SessionResponse}
import org.knora.webapi.routing.Authenticator.KNORA_AUTHENTICATION_COOKIE_NAME
import org.knora.webapi.{E2ESpec, SharedTestDataV1}

import scala.concurrent.Await
import scala.concurrent.duration._


object SipiADME2ESpec {
    val config: Config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for Sipi access.
  *
  * This spec tests the 'admin/files'.
  */
class SipiADME2ESpec extends E2ESpec(SipiADME2ESpec.config) with SessionJsonProtocol with TriplestoreJsonProtocol {

    private implicit def default(implicit system: ActorSystem) = RouteTestTimeout(30.seconds)

    private val anythingAdminEmail = SharedTestDataV1.anythingAdminUser.userData.email.get
    private val anythingAdminEmailEnc = java.net.URLEncoder.encode(anythingAdminEmail, "utf-8")
    private val normalUserEmail = SharedTestDataV1.normalUser.userData.email.get
    private val normalUserEmailEnc = java.net.URLEncoder.encode(normalUserEmail, "utf-8")
    private val testPass = java.net.URLEncoder.encode("test", "utf-8")

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    def sessionLogin(email: String, password: String): String = {

        val request = Get(baseApiUrl + s"/v1/session?login&email=$email&password=$password")
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status == StatusCodes.OK)

        val sr: SessionResponse = Await.result(Unmarshal(response.entity).to[SessionResponse], 1.seconds)
        sr.sid
    }

    def sessionLogout(sessionId: String): Unit = {
        Get(baseApiUrl + "/v1/session?logout") ~> Cookie(KNORA_AUTHENTICATION_COOKIE_NAME, sessionId)
    }


    "The Files Route ('admin/files') using token credentials" should {

        "return CR (8) permission code" in {
            /* anything image */
            val request = Get(baseApiUrl + s"/admin/files/0001/B1D0OkEgfFp-Cew2Seur7Wi.jp2?email=$anythingAdminEmailEnc&password=$testPass")
            val response: HttpResponse = singleAwaitingRequest(request)

            // println(response.toString)

            assert(response.status == StatusCodes.OK)

            val fr: SipiFileInfoGetResponseADM = Await.result(Unmarshal(response.entity).to[SipiFileInfoGetResponseADM], 1.seconds)

            (fr.permissionCode === 8) should be (true)
        }

        "return RV (1) permission code" in {
            /* anything image */
            val request = Get(baseApiUrl + s"/admin/files/0001/B1D0OkEgfFp-Cew2Seur7Wi.jp2?email=$normalUserEmailEnc&password=$testPass")
            val response: HttpResponse = singleAwaitingRequest(request)

            // println(response.toString)

            assert(response.status == StatusCodes.OK)

            val fr: SipiFileInfoGetResponseADM = Await.result(Unmarshal(response.entity).to[SipiFileInfoGetResponseADM], 1.seconds)

            (fr.permissionCode === 1) should be (true)
        }

        "return 404 Not Found if a file value is in a deleted resource" in {
            val request = Get(baseApiUrl + s"/admin/files/0001/9hxmmrWh0a7-CnRCq0650ro.jpx?email=$normalUserEmailEnc&password=$testPass")
            val response: HttpResponse = singleAwaitingRequest(request)

            // println(response.toString)

            assert(response.status == StatusCodes.NotFound)
        }

        "return permissions for a previous version of a file value" in {
            val request = Get(baseApiUrl + s"/admin/files/0001/QxFMm5wlRlatStw9ft3iZA.jp2?email=$normalUserEmailEnc&password=$testPass")
            val response: HttpResponse = singleAwaitingRequest(request)

            // println(response.toString)

            assert(response.status == StatusCodes.OK)

            val fr: SipiFileInfoGetResponseADM = Await.result(Unmarshal(response.entity).to[SipiFileInfoGetResponseADM], 1.seconds)

            (fr.permissionCode === 2) should be (true)

        }
    }

    "The Files Route ('admin/files') using session credentials" should {

        "return CR (8) permission code" in {
            /* login */
            val sessionId = sessionLogin(anythingAdminEmailEnc, testPass)

            /* anything image */
            val request = Get(baseApiUrl + s"/admin/files/0001/B1D0OkEgfFp-Cew2Seur7Wi.jp2") ~> Cookie(KNORA_AUTHENTICATION_COOKIE_NAME, sessionId)
            val response: HttpResponse = singleAwaitingRequest(request)

            // println(response.toString)

            assert(response.status == StatusCodes.OK)

            val fr: SipiFileInfoGetResponseADM = Await.result(Unmarshal(response.entity).to[SipiFileInfoGetResponseADM], 1.seconds)

            (fr.permissionCode === 8) should be (true)

            /* logout */
            sessionLogout(sessionId)
        }

        "return RV (1) permission code" in {
            /* login */
            val sessionId = sessionLogin(normalUserEmailEnc, testPass)

            /* anything image */
            val request = Get(baseApiUrl + s"/admin/files/0001/B1D0OkEgfFp-Cew2Seur7Wi.jp2")~> Cookie(KNORA_AUTHENTICATION_COOKIE_NAME, sessionId)
            val response: HttpResponse = singleAwaitingRequest(request)

            // println(response.toString)

            assert(response.status == StatusCodes.OK)

            val fr: SipiFileInfoGetResponseADM = Await.result(Unmarshal(response.entity).to[SipiFileInfoGetResponseADM], 1.seconds)

            (fr.permissionCode === 1) should be (true)
        }
    }
}
