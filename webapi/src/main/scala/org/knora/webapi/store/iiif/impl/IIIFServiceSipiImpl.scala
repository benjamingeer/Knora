/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.impl

import org.apache.http.Consts
import org.apache.http.HttpEntity
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.NameValuePair
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpDelete
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.config.SocketConfig
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import spray.json._
import zio._
import zio.nio.file.Path

import java.util

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.Sipi
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.routing.JwtService
import org.knora.webapi.slice.admin.domain.service.Asset
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.store.iiif.domain._
import org.knora.webapi.store.iiif.errors.SipiException
import org.knora.webapi.util.SipiUtil
import org.knora.webapi.util.ZScopedJavaIoStreams

/**
 * Makes requests to Sipi.
 *
 * @param sipiConf    The application's configuration for Sipi
 * @param jwt         The JWT Service to handle JWT Tokens
 * @param httpClient  The HTTP Client
 */
final case class IIIFServiceSipiImpl(
  private val sipiConf: Sipi,
  private val jwt: JwtService,
  private val httpClient: CloseableHttpClient
) extends IIIFService {

  /**
   * Asks Sipi for metadata about a file, served from the 'knora.json' route.
   *
   * @param getFileMetadataRequest the request.
   * @return a [[GetFileMetadataResponse]] containing the requested metadata.
   */
  def getFileMetadata(getFileMetadataRequest: GetFileMetadataRequest): Task[GetFileMetadataResponse] = {
    import SipiKnoraJsonResponseProtocol._

    for {
      url             <- ZIO.succeed(sipiConf.internalBaseUrl + getFileMetadataRequest.filePath + "/knora.json")
      request         <- ZIO.succeed(new HttpGet(url))
      sipiResponseStr <- doSipiRequest(request)
      sipiResponse    <- ZIO.attempt(sipiResponseStr.parseJson.convertTo[SipiKnoraJsonResponse])
    } yield GetFileMetadataResponse(
      originalFilename = sipiResponse.originalFilename,
      originalMimeType = sipiResponse.originalMimeType,
      internalMimeType = sipiResponse.internalMimeType,
      width = sipiResponse.width,
      height = sipiResponse.height,
      pageCount = sipiResponse.numpages,
      duration = sipiResponse.duration,
      fps = sipiResponse.fps
    )
  }

  /**
   * Asks Sipi to move a file from temporary storage to permanent storage.
   *
   * @param moveTemporaryFileToPermanentStorageRequestV2 the request.
   * @return a [[SuccessResponseV2]].
   */
  def moveTemporaryFileToPermanentStorage(
    moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest
  ): Task[SuccessResponseV2] = {

    // create the JWT token with the necessary permission
    val jwtToken: Task[String] = jwt.createToken(
      moveTemporaryFileToPermanentStorageRequestV2.requestingUser.id,
      Map(
        "knora-data" -> JsObject(
          Map(
            "permission" -> JsString("StoreFile"),
            "filename"   -> JsString(moveTemporaryFileToPermanentStorageRequestV2.internalFilename),
            "prefix"     -> JsString(moveTemporaryFileToPermanentStorageRequestV2.prefix)
          )
        )
      )
    )

    // builds the url for the operation
    def moveFileUrl(token: String) =
      ZIO.succeed(s"${sipiConf.internalBaseUrl}/${sipiConf.moveFileRoute}?token=$token")

    // build the form to send together with the request
    val formParams = new util.ArrayList[NameValuePair]()
    formParams.add(new BasicNameValuePair("filename", moveTemporaryFileToPermanentStorageRequestV2.internalFilename))
    formParams.add(new BasicNameValuePair("prefix", moveTemporaryFileToPermanentStorageRequestV2.prefix))
    val requestEntity = new UrlEncodedFormEntity(formParams, Consts.UTF_8)

    // build the request
    def request(url: String, requestEntity: UrlEncodedFormEntity) = {
      val req = new HttpPost(url)
      req.setEntity(requestEntity)
      req
    }

    for {
      token   <- jwtToken
      url     <- moveFileUrl(token)
      entity  <- ZIO.succeed(requestEntity)
      request <- ZIO.succeed(request(url, entity))
      _       <- doSipiRequest(request)
    } yield SuccessResponseV2("Moved file to permanent storage.")
  }

  /**
   * Asks Sipi to delete a temporary file.
   *
   * @param deleteTemporaryFileRequestV2 the request.
   * @return a [[SuccessResponseV2]].
   */
  def deleteTemporaryFile(deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest): Task[SuccessResponseV2] = {

    val jwtToken: Task[String] = jwt.createToken(
      deleteTemporaryFileRequestV2.requestingUser.id,
      Map(
        "knora-data" -> JsObject(
          Map(
            "permission" -> JsString("DeleteTempFile"),
            "filename"   -> JsString(deleteTemporaryFileRequestV2.internalFilename)
          )
        )
      )
    )

    def deleteUrl(token: String): Task[String] =
      ZIO.succeed(
        s"${sipiConf.internalBaseUrl}/${sipiConf.deleteTempFileRoute}/${deleteTemporaryFileRequestV2.internalFilename}?token=$token"
      )

    for {
      token   <- jwtToken
      url     <- deleteUrl(token)
      request <- ZIO.succeed(new HttpDelete(url))
      _       <- doSipiRequest(request)
    } yield SuccessResponseV2("Deleted temporary file.")
  }

  /**
   * Asks Sipi for a text file used internally by Knora.
   *
   * @param textFileRequest the request message.
   */
  def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): Task[SipiGetTextFileResponse] = {

    // helper method to handle errors
    def handleErrors(ex: Throwable) = ex match {
      case notFoundException: NotFoundException =>
        ZIO.die(
          NotFoundException(
            s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${notFoundException.message}"
          )
        )

      case badRequestException: BadRequestException =>
        ZIO.die(
          SipiException(
            s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${badRequestException.message}"
          )
        )

      case sipiException: SipiException =>
        ZIO.die(
          SipiException(
            s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${sipiException.message}",
            sipiException.cause
          )
        )

      case other =>
        ZIO.logError(
          s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${other.getMessage}"
        ) *>
          ZIO.die(
            SipiException(
              s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${other.getMessage}"
            )
          )
    }

    for {
      request     <- ZIO.succeed(new HttpGet(textFileRequest.fileUrl))
      responseStr <- doSipiRequest(request).catchAll(ex => handleErrors(ex))
    } yield SipiGetTextFileResponse(responseStr)
  }

  /**
   * Tries to access the IIIF Service to check if Sipi is running.
   */
  def getStatus(): Task[IIIFServiceStatusResponse] =
    for {
      request  <- ZIO.succeed(new HttpGet(sipiConf.internalBaseUrl + "/server/test.html"))
      response <- doSipiRequest(request).fold(_ => IIIFServiceStatusNOK, _ => IIIFServiceStatusOK)
    } yield response

  /**
   * Makes an HTTP request to Sipi and returns the response.
   *
   * @param request the HTTP request.
   * @return Sipi's response.
   */
  private def doSipiRequest(request: HttpRequest): Task[String] = {
    val targetHost: HttpHost                         = new HttpHost(sipiConf.internalHost, sipiConf.internalPort, sipiConf.internalProtocol)
    val httpContext: HttpClientContext               = HttpClientContext.create()
    var maybeResponse: Option[CloseableHttpResponse] = None

    val sipiRequest: Task[String] = ZIO.attemptBlocking {
      maybeResponse = Some(httpClient.execute(targetHost, request, httpContext))

      val responseEntityStr: String = Option(maybeResponse.get.getEntity) match {
        case Some(responseEntity) => EntityUtils.toString(responseEntity)
        case None                 => ""
      }

      val statusCode: Int     = maybeResponse.get.getStatusLine.getStatusCode
      val statusCategory: Int = statusCode / 100

      // Was the request successful?
      if (statusCategory == 2) {
        // Yes.
        responseEntityStr
      } else {
        // No. Throw an appropriate exception.
        val sipiErrorMsg = SipiUtil.getSipiErrorMessage(responseEntityStr)

        if (statusCode == 404) {
          throw NotFoundException(sipiErrorMsg)
        } else if (statusCategory == 4) {
          throw BadRequestException(s"Sipi responded with HTTP status code $statusCode: $sipiErrorMsg")
        } else {
          throw SipiException(s"Sipi responded with HTTP status code $statusCode: $sipiErrorMsg")
        }
      }
    }

    maybeResponse match {
      case Some(response) => response.close()
      case None           => ()
    }

    sipiRequest.catchAll {
      case badRequestException: BadRequestException => ZIO.fail(badRequestException)
      case notFoundException: NotFoundException     => ZIO.fail(notFoundException)
      case sipiException: SipiException             => ZIO.fail(sipiException)
      case e: Exception                             => ZIO.logError(e.getMessage) *> ZIO.fail(SipiException("Failed to connect to Sipi", e))
    }
  }

  override def downloadAsset(asset: Asset, targetDir: Path, user: UserADM): Task[Option[Path]] = {
    def makeRequest(): UIO[HttpGet] = for {
      jwtToken <- jwt.createToken(user.id)
      url       = s"${sipiConf.internalBaseUrl}/${asset.belongsToProject.shortcode}/${asset.internalFilename}/file"
      request   = new HttpGet(url)
      _         = request.addHeader("Authorization", s"Bearer $jwtToken")
      _        <- ZIO.logDebug(s"Created GET $url request for ${Asset.logString(asset)}")
    } yield request

    def code(response: HttpResponse): Int = response.getStatusLine.getStatusCode

    for {
      request <- makeRequest()
      downloaded <- ZIO.scoped {
                      sendRequest(request)
                        .filterOrElseWith(code(_) == 200)(it => ZIO.fail(new Exception(s"${code(it)} code from sipi")))
                        .flatMap(r => saveToFile(asset, r.getEntity, targetDir))
                        .tapError(e => ZIO.logWarning(s"Failed downloading ${Asset.logString(asset)}: ${e.getMessage}"))
                        .fold(_ => None, Some(_))
                    }
    } yield downloaded
  }

  private def sendRequest(request: HttpUriRequest): ZIO[Scope, Throwable, HttpResponse] = {
    def acquire                                  = ZIO.attemptBlocking(httpClient.execute(request))
    def release(response: CloseableHttpResponse) = ZIO.attempt(response.close()).logError.ignore
    ZIO.acquireRelease(acquire)(release)
  }

  private def saveToFile(asset: Asset, entity: HttpEntity, targetDir: Path): ZIO[Scope, Throwable, Path] = {
    val targetFile = targetDir / asset.internalFilename
    for {
      out <- ZScopedJavaIoStreams.fileOutputStream(targetFile)
      _   <- ZIO.attemptBlocking(entity.getContent.transferTo(out))
      _   <- ZIO.logInfo(s"Downloaded ${Asset.logString(asset)} to ${targetFile.toFile.toPath.toAbsolutePath}")
    } yield targetFile
  }
}

object IIIFServiceSipiImpl {

  /**
   * Acquires a configured httpClient, backed by a connection pool,
   * to be used in communicating with SIPI.
   */
  private def acquire(config: Sipi): UIO[CloseableHttpClient] = ZIO.attemptBlocking {

    // timeout from config
    val sipiTimeoutMillis: Int = config.timeoutInSeconds.toMillis.toInt

    // Create a connection manager with custom configuration.
    val connManager: PoolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager()

    // Create socket configuration
    val socketConfig: SocketConfig = SocketConfig
      .custom()
      .setTcpNoDelay(true)
      .build()

    // Configure the connection manager to use socket configuration by default.
    connManager.setDefaultSocketConfig(socketConfig)

    // Validate connections after 1 sec of inactivity
    connManager.setValidateAfterInactivity(1000)

    // Configure total max or per route limits for persistent connections
    // that can be kept in the pool or leased by the connection manager.
    connManager.setMaxTotal(100)
    connManager.setDefaultMaxPerRoute(10)

    // Sipi custom default request config
    val defaultRequestConfig = RequestConfig
      .custom()
      .setConnectTimeout(sipiTimeoutMillis)
      .setConnectionRequestTimeout(sipiTimeoutMillis)
      .setSocketTimeout(sipiTimeoutMillis)
      .build()

    // Return an HttpClient with the given custom dependencies and configuration.
    HttpClients
      .custom()
      .setConnectionManager(connManager)
      .setDefaultRequestConfig(defaultRequestConfig)
      .build()
  }.logError.orDie.zipLeft(ZIO.logInfo(">>> Acquire Sipi IIIF Service <<<"))

  /**
   * Releases the httpClient, freeing all resources.
   */
  private def release(httpClient: CloseableHttpClient): UIO[Unit] =
    ZIO.attemptBlocking(httpClient.close()).logError.ignore <* ZIO.logInfo(">>> Release Sipi IIIF Service <<<")

  val layer: URLayer[AppConfig with JwtService, IIIFServiceSipiImpl] =
    ZLayer.scoped {
      for {
        config     <- ZIO.serviceWith[AppConfig](_.sipi)
        jwtService <- ZIO.service[JwtService]
        httpClient <- ZIO.acquireRelease(acquire(config))(release)
      } yield IIIFServiceSipiImpl(config, jwtService, httpClient)
    }
}
