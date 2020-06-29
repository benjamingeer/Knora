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

package org.knora.webapi.e2e.v1

import java.io.{File, FileInputStream, FileOutputStream}

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpEntity, _}
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.ITKnoraFakeSpec
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.util.{FileUtil, MutableTestIri}
import spray.json._


object KnoraSipiScriptsV1ITSpec {
    val config: Config = ConfigFactory.parseString(
        """
          |akka.loglevel = "DEBUG"
          |akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing Knora-Sipi scripts. Sipi must be running with the config file
  * `sipi.knora-config.lua`. This spec uses the KnoraFakeService to start a faked `webapi` server that always allows
  * access to files.
  */
class KnoraSipiScriptsV1ITSpec extends ITKnoraFakeSpec(KnoraSipiScriptsV1ITSpec.config) with TriplestoreJsonProtocol {

    implicit override val log = akka.event.Logging(system, this.getClass)

    private val username = "root@example.com"
    private val password = "test"
    private val pathToChlaus = "_test_data/test_route/images/Chlaus.jpg"
    private val pathToMarbles = "_test_data/test_route/images/marbles.tif"
    private val firstPageIri = new MutableTestIri
    private val secondPageIri = new MutableTestIri

    "Calling Knora Sipi Scripts" should {

        "successfully call C++ functions from Lua scripts" in {
            val request = Get(baseInternalSipiUrl + "/test_functions" )
            getResponseString(request)
        }

        "successfully call Lua functions for mediatype handling" in {
            val request = Get(baseInternalSipiUrl + "/test_file_type" )
            getResponseString(request)
        }

        "successfully call Lua function that gets the Knora session id from the cookie header sent to Sipi" in {
            val request = Get(baseInternalSipiUrl + "/test_knora_session_cookie" )
            getResponseString(request)
        }

        "successfully call make_thumbnail.lua sipi script" in {

            // The image to be uploaded.
            val fileToSend = new File(pathToChlaus)
            assert(fileToSend.exists(), s"File $pathToChlaus does not exist")

            // A multipart/form-data request containing the image.
            val sipiFormData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/jpeg`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            // Send a POST request to Sipi, asking it to make a thumbnail of the image.
            val sipiPostRequest = Post(baseInternalSipiUrl + "/make_thumbnail", sipiFormData) ~> addCredentials(BasicHttpCredentials(username, password))
            val sipiPostResponseJson = getResponseJson(sipiPostRequest)

            /* sipiResponseJson will be something like this
            {
                "mimetype_thumb":"image/jpeg",
                "original_mimetype":"image/jpeg",
                "nx_thumb":93,
                "preview_path":"http://localhost:1024/thumbs/CjwDMhlrctI-BG7gms08BJ4.jpg/full/max/0/default.jpg",
                "filename":"CjwDMhlrctI-BG7gms08BJ4",
                "file_type":"IMAGE",
                "original_filename":"Chlaus.jpg",
                "ny_thumb":128
            }
            */

            // get the preview_path
            val previewPath = sipiPostResponseJson.fields("preview_path").asInstanceOf[JsString].value

            // get the filename
            val filename = sipiPostResponseJson.fields("filename").asInstanceOf[JsString].value

            /**
              * Send a GET request to Sipi, asking for the preview image.
              * With testcontainers it is not possible to know the random port
              * in advance, so that we can provide it to Sipi at startup.
              * Instead we need to replace the standard port configured
              * and returned by sipi to the random port known after sipi has
              * already started.
              */
            val sipiGetRequest01 = Get(previewPath.replace("http://0.0.0.0:1024", baseExternalSipiUrl))
            val sipiGetResponse01: HttpResponse = singleAwaitingRequest(sipiGetRequest01)
            log.debug(s"sipiGetResponse01: ${sipiGetResponse01.toString}")
            sipiGetResponse01.status should be(StatusCodes.OK)

            // Send a GET request to Sipi, asking for the info.json of the image
            val sipiGetRequest02 = Get(baseInternalSipiUrl + "/thumbs/" + filename + ".jpg/info.json" )
            val sipiGetResponse02: HttpResponse = singleAwaitingRequest(sipiGetRequest02)
            log.debug(s"sipiGetResponse02: ${sipiGetResponse02.toString}")
            sipiGetResponse02.status should be(StatusCodes.OK)
        }

        "successfully call convert_from_file.lua sipi script" in {

            /* This is the case where the file is already stored on the sipi server as part of make_thumbnail*/

            // The image to be uploaded.
            val fileToSend = new File(pathToChlaus)
            assert(fileToSend.exists(), s"File $pathToChlaus does not exist")

            // A multipart/form-data request containing the image.
            val sipiFormData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/jpeg`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            // Send a POST request to Sipi, asking it to make a thumbnail of the image.
            val sipiMakeThumbnailRequest = Post(baseInternalSipiUrl + "/make_thumbnail", sipiFormData)
            val sipiMakeThumbnailResponse: HttpResponse = singleAwaitingRequest(sipiMakeThumbnailRequest)

            val sipiMakeThumbnailResponseJson = getResponseJson(sipiMakeThumbnailRequest)
            val originalFilename = sipiMakeThumbnailResponseJson.fields("original_filename").asInstanceOf[JsString].value
            val originalMimetype = sipiMakeThumbnailResponseJson.fields("original_mimetype").asInstanceOf[JsString].value
            val filename = sipiMakeThumbnailResponseJson.fields("filename").asInstanceOf[JsString].value

            // A form-data request containing the payload for convert_from_file.
            val sipiFormData02 = FormData(
                Map(
                    "originalFilename" -> originalFilename,
                    "originalMimeType" -> originalMimetype,
                    "prefix" -> "0001",
                    "filename" -> filename
                )
            )
            
            val convertFromFileRequest = Post(baseInternalSipiUrl + "/convert_from_file", sipiFormData02)
            val convertFromFileResponseJson = getResponseJson(convertFromFileRequest)

            val filenameFull = convertFromFileResponseJson.fields("filename_full").asInstanceOf[JsString].value

            // Running with KnoraFakeService which always allows access to files.
            // Send a GET request to Sipi, asking for full image
            // not possible as authentication is required and file needs to be known by knora to be able to authenticate the request
            val sipiGetImageRequest = Get(baseInternalSipiUrl + "/0001/" + filenameFull + "/full/max/0/default.jpg") ~> addCredentials(BasicHttpCredentials(username, password))
            checkResponseOK(sipiGetImageRequest)

            // Send a GET request to Sipi, asking for the info.json of the image
            val sipiGetInfoRequest = Get(baseInternalSipiUrl + "/0001/" + filenameFull + "/info.json" ) ~> addCredentials(BasicHttpCredentials(username, password))
            val sipiGetInfoResponseJson = getResponseJson(sipiGetInfoRequest)
            log.debug("sipiGetInfoResponseJson: {}", sipiGetInfoResponseJson)
        }

        // TODO: fix as part of https://github.com/dasch-swiss/knora-api/pull/1233
        "successfully call convert_from_path.lua sipi script" ignore {

            // The image to be uploaded.
            val fileToSend = new File(pathToChlaus)
            assert(fileToSend.exists(), s"File $pathToChlaus does not exist")

            // To be able to run packaged tests inside Docker, we need to copy
            // the file first to a place which is shared with sipi
            val dest = FileUtil.createTempFile(settings)
            new FileOutputStream(dest)
              .getChannel
              .transferFrom(
                  new FileInputStream(fileToSend).getChannel,
                  0,
                  Long.MaxValue
              )

            // A multipart/form-data request containing the image.
            val sipiFormData = FormData(
                Map(
                    "originalFilename" -> fileToSend.getName,
                    "originalMimeType" -> "image/jpeg",
                    "prefix" -> "0001",
                    "source" -> dest.getAbsolutePath
                )
            )

            // Send a POST request to Sipi, asking it to make a thumbnail of the image.
            val sipiConvertFromPathPostRequest = Post(baseInternalSipiUrl + "/convert_from_path", sipiFormData)
            val sipiConvertFromPathPostResponseJson = getResponseJson(sipiConvertFromPathPostRequest)

            val filenameFull = sipiConvertFromPathPostResponseJson.fields("filename_full").asInstanceOf[JsString].value

            //log.debug("sipiConvertFromPathPostResponseJson: {}", sipiConvertFromPathPostResponseJson)

            // Running with KnoraFakeService which always allows access to files.
            val sipiGetImageRequest = Get(baseInternalSipiUrl + "/0001/" + filenameFull + "/full/max/0/default.jpg") ~> addCredentials(BasicHttpCredentials(username, password))
            checkResponseOK(sipiGetImageRequest)

            // Send a GET request to Sipi, asking for the info.json of the image
            val sipiGetInfoRequest = Get(baseInternalSipiUrl + "/0001/" + filenameFull + "/info.json" ) ~> addCredentials(BasicHttpCredentials(username, password))
            val sipiGetInfoResponseJson = getResponseJson(sipiGetInfoRequest)
            log.debug("sipiGetInfoResponseJson: {}", sipiGetInfoResponseJson)
        }

    }
}


