/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.impl

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Status
import akka.event.LoggingAdapter
import org.apache.commons.lang3.StringUtils
import org.apache.http.Consts
import org.apache.http.HttpEntity
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import org.apache.http.NameValuePair
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.AuthCache
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ContentType
import org.apache.http.entity.FileEntity
import org.apache.http.entity.StringEntity
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.knora.webapi._
import org.knora.webapi.exceptions._
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.instrumentation.InstrumentationSupport
import org.knora.webapi.messages.store.triplestoremessages.SparqlResultProtocol._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.FakeTriplestore
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.settings.KnoraSettings
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.FileUtil
import spray.json._

import java.io.BufferedInputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.store.triplestore.defaults.DefaultRdfData

import zio._
import zio.json.ast.Json
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.config.SocketConfig

/**
 * Submits SPARQL queries and updates to a triplestore over HTTP. Supports different triplestores, which can be configured in
 * `application.conf`.
 */
case class TriplestoreServiceHttpConnectorImpl(
  config: AppConfig,
  httpClient: CloseableHttpClient
) extends TriplestoreService {

  // MIME type constants.
  private val mimeTypeApplicationJson              = "application/json"
  private val mimeTypeApplicationSparqlResultsJson = "application/sparql-results+json"
  private val mimeTypeTextTurtle                   = "text/turtle"
  private val mimeTypeApplicationSparqlUpdate      = "application/sparql-update"
  private val mimeTypeApplicationNQuads            = "application/n-quads"

  private val targetHost: HttpHost = new HttpHost(config.triplestore.host, config.triplestore.fuseki.port, "http")

  private val credsProvider: BasicCredentialsProvider = new BasicCredentialsProvider
  credsProvider.setCredentials(
    new AuthScope(targetHost.getHostName, targetHost.getPort),
    new UsernamePasswordCredentials(config.triplestore.fuseki.username, config.triplestore.fuseki.password)
  )

  // Reading data should be quick, except when it is not ;-)
  private val queryTimeoutMillis = config.triplestore.queryTimeoutAsDuration.toMillis.toInt

  private val queryRequestConfig = RequestConfig
    .custom()
    .setConnectTimeout(queryTimeoutMillis)
    .setConnectionRequestTimeout(queryTimeoutMillis)
    .setSocketTimeout(queryTimeoutMillis)
    .build

  private val queryHttpClient: CloseableHttpClient = HttpClients.custom
    .setDefaultCredentialsProvider(credsProvider)
    .setDefaultRequestConfig(queryRequestConfig)
    .build

  // Some updates could take a while.
  private val updateTimeoutMillis = config.triplestore.updateTimeoutAsDuration.toMillis.toInt

  private val updateTimeoutConfig = RequestConfig
    .custom()
    .setConnectTimeout(updateTimeoutMillis)
    .setConnectionRequestTimeout(updateTimeoutMillis)
    .setSocketTimeout(updateTimeoutMillis)
    .build

  private val updateHttpClient: CloseableHttpClient = HttpClients.custom
    .setDefaultCredentialsProvider(credsProvider)
    .setDefaultRequestConfig(updateTimeoutConfig)
    .build

  // For updates that could take a very long time.
  private val longUpdateTimeoutMillis = updateTimeoutMillis * 10

  private val longRequestConfig = RequestConfig
    .custom()
    .setConnectTimeout(longUpdateTimeoutMillis)
    .setConnectionRequestTimeout(longUpdateTimeoutMillis)
    .setSocketTimeout(longUpdateTimeoutMillis)
    .build

  private val longRequestClient: CloseableHttpClient = HttpClients.custom
    .setDefaultCredentialsProvider(credsProvider)
    .setDefaultRequestConfig(longRequestConfig)
    .build

  private val dbName                 = config.triplestore.fuseki.repositoryName
  private val queryPath              = s"/${dbName}/query"
  private val sparqlUpdatePath       = s"/${dbName}/update"
  private val graphPath              = s"/${dbName}/get"
  private val dataInsertPath         = s"/${dbName}/data"
  private val repositoryDownloadPath = s"/${dbName}"
  private val checkRepositoryPath    = "/$/server"
  private val repositoryUploadPath   = repositoryDownloadPath
  private val logDelimiter           = "\n" + StringUtils.repeat('=', 80) + "\n"

  /**
   * Simulates a read timeout.
   */
  private def doSimulateTimeout(): UIO[SparqlSelectResult] = {
    val sparql = """SELECT ?foo WHERE {
                   |    BIND("foo" AS ?foo)
                   |}""".stripMargin

    sparqlHttpSelect(sparql = sparql, simulateTimeout = true)
  }

  /**
   * Given a SPARQL SELECT query string, runs the query, returning the result as a [[SparqlSelectResult]].
   *
   * @param sparql the SPARQL SELECT query string.
   * @param simulateTimeout if `true`, simulate a read timeout.
   * @return a [[SparqlSelectResult]].
   */
  private def sparqlHttpSelect(sparql: String, simulateTimeout: Boolean = false): UIO[SparqlSelectResult] = {

    def parseJsonResponse(sparql: String, resultStr: String): IO[TriplestoreException, SparqlSelectResult] =
      ZIO
        .attemptBlocking(resultStr.parseJson.convertTo[SparqlSelectResult])
        .foldZIO(
          failure =>
            if (resultStr.contains("##  Query cancelled due to timeout during execution")) {
              ZIO.logError("Triplestore timed out while sending a response, after sending statuscode 200.") *>
                ZIO.fail(
                  TriplestoreTimeoutException(
                    "Triplestore timed out while sending a response, after sending statuscode 200."
                  )
                )
            } else {
              ZIO.logError(
                s"Couldn't parse response from triplestore:$logDelimiter$resultStr${logDelimiter}in response to SPARQL query:$logDelimiter$sparql"
              ) *>
                ZIO.fail(TriplestoreResponseException("Couldn't parse Turtle from triplestore"))
            },
          ZIO.succeed(_)
        )

    for {
      resultStr <- getSparqlHttpResponse(sparql, isUpdate = false, simulateTimeout = simulateTimeout).orDie

      // Parse the response as a JSON object and generate a response message.
      responseMessage <- parseJsonResponse(sparql, resultStr).orDie
    } yield responseMessage
  }

  /**
   * Given a SPARQL CONSTRUCT query string, runs the query, returning the result as a [[SparqlConstructResponse]].
   *
   * @param sparqlConstructRequest the request message.
   * @return a [[SparqlConstructResponse]]
   */
  private def sparqlHttpConstruct(sparqlConstructRequest: SparqlConstructRequest): UIO[SparqlConstructResponse] = {
    // println(logDelimiter + sparql)

    val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil()

    def parseTurtleResponse(
      sparql: String,
      turtleStr: String,
      rdfFormatUtil: RdfFormatUtil
    ): IO[TriplestoreException, SparqlConstructResponse] =
      ZIO.attemptBlocking {
        val rdfModel: RdfModel                                 = rdfFormatUtil.parseToRdfModel(rdfStr = turtleStr, rdfFormat = Turtle)
        val statementMap: mutable.Map[IRI, Seq[(IRI, String)]] = mutable.Map.empty

        for (st: Statement <- rdfModel) {
          val subjectIri   = st.subj.stringValue
          val predicateIri = st.pred.stringValue
          val objectIri    = st.obj.stringValue
          val currentStatementsForSubject: Seq[(IRI, String)] =
            statementMap.getOrElse(subjectIri, Vector.empty[(IRI, String)])
          statementMap += (subjectIri -> (currentStatementsForSubject :+ (predicateIri, objectIri)))
        }

        SparqlConstructResponse(statementMap.toMap)
      }.foldZIO(
        failure =>
          if (turtleStr.contains("##  Query cancelled due to timeout during execution")) {
            ZIO.logError("Triplestore timed out while sending a response, after sending statuscode 200.") *>
              ZIO.fail(
                TriplestoreTimeoutException(
                  "Triplestore timed out while sending a response, after sending statuscode 200."
                )
              )
          } else {
            ZIO.logError(
              s"Couldn't parse response from triplestore:$logDelimiter$turtleStr${logDelimiter}in response to SPARQL query:$logDelimiter$sparql"
            ) *>
              ZIO.fail(TriplestoreResponseException("Couldn't parse Turtle from triplestore"))
          },
        ZIO.succeed(_)
      )

    for {
      turtleStr <-
        getSparqlHttpResponse(
          sparqlConstructRequest.sparql,
          isUpdate = false,
          acceptMimeType = mimeTypeTextTurtle
        ).orDie

      response <- parseTurtleResponse(
                    sparql = sparqlConstructRequest.sparql,
                    turtleStr = turtleStr,
                    rdfFormatUtil = rdfFormatUtil
                  ).orDie
    } yield response
  }

  /**
   * Given a SPARQL CONSTRUCT query string, runs the query, saving the result in a file.
   *
   * @param sparql       the SPARQL CONSTRUCT query string.
   * @param graphIri     the named graph IRI to be used in the output file.
   * @param outputFile   the output file.
   * @param outputFormat the output file format.
   * @return a [[FileWrittenResponse]].
   */
  private def sparqlHttpConstructFile(
    sparql: String,
    graphIri: IRI,
    outputFile: Path,
    outputFormat: QuadFormat
  ): UIO[FileWrittenResponse] = {
    val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil()

    for {
      turtleStr <- getSparqlHttpResponse(sparql, isUpdate = false, acceptMimeType = mimeTypeTextTurtle).orDie
      _ <- rdfFormatUtil
             .turtleToQuadsFile(
               rdfSource = RdfStringSource(turtleStr),
               graphIri = graphIri,
               outputFile = outputFile,
               outputFormat = outputFormat
             )
             .orDie
    } yield FileWrittenResponse()
  }

  /**
   * Given a SPARQL CONSTRUCT query string, runs the query, returns the result as a [[SparqlExtendedConstructResponse]].
   *
   * @param sparqlExtendedConstructRequest the request message.
   * @return a [[SparqlExtendedConstructResponse]]
   */
  private def sparqlHttpExtendedConstruct(
    sparqlExtendedConstructRequest: SparqlExtendedConstructRequest
  ): UIO[SparqlExtendedConstructResponse] =
    for {
      turtleStr <- getSparqlHttpResponse(
                     sparqlExtendedConstructRequest.sparql,
                     isUpdate = false,
                     acceptMimeType = mimeTypeTextTurtle
                   ).orDie

      response <- SparqlExtendedConstructResponse
                    .parseTurtleResponse(turtleStr)
                    .foldZIO(
                      failure =>
                        ZIO.die(
                          TriplestoreResponseException(
                            s"Couldn't parse Turtle from triplestore: ${sparqlExtendedConstructRequest}"
                          )
                        ),
                      ZIO.succeed(_)
                    )
    } yield response

  /**
   * Performs a SPARQL update operation.
   *
   * @param sparqlUpdate the SPARQL update.
   * @return a [[SparqlUpdateResponse]].
   */
  private def sparqlHttpUpdate(sparqlUpdate: String): UIO[SparqlUpdateResponse] =
    // println(logDelimiter + sparqlUpdate)
    for {
      // Send the request to the triplestore.
      _ <- getSparqlHttpResponse(sparqlUpdate, isUpdate = true).orDie
    } yield SparqlUpdateResponse()

  /**
   * Performs a SPARQL ASK query.
   *
   * @param sparql the SPARQL ASK query.
   * @return a [[SparqlAskResponse]].
   */
  def sparqlHttpAsk(sparql: String): UIO[SparqlAskResponse] =
    for {
      resultString <- getSparqlHttpResponse(sparql, isUpdate = false).orDie
      _            <- ZIO.logDebug(s"sparqlHttpAsk - resultString: ${resultString}")
      result <- ZIO
                  .attemptBlocking(
                    resultString.parseJson.asJsObject.getFields("boolean").head.convertTo[Boolean]
                  )
                  .orDie
    } yield SparqlAskResponse(result)

  /**
   * Resets the content of the triplestore with the data supplied with the request.
   * First performs `dropAllTriplestoreContent` and afterwards `insertDataIntoTriplestore`.
   *
   * @param rdfDataObjects a sequence of paths and graph names referencing data that needs to be inserted.
   * @param prependDefaults denotes if the rdfDataObjects list should be prepended with a default set. Default is `true`.
   */
  def resetTripleStoreContent(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean = true
  ): UIO[ResetRepositoryContentACK] =
    for {
      _ <- ZIO.logDebug("resetTripleStoreContent")

      // drop old content
      _ <- dropAllTriplestoreContent()

      // insert new content
      _ <- insertDataIntoTriplestore(rdfDataObjects, prependDefaults)
    } yield ResetRepositoryContentACK()

  /**
   * Drops (deletes) all data from the triplestore.
   */
  def dropAllTriplestoreContent(): UIO[DropAllRepositoryContentACK] = {

    val DropAllSparqlString =
      """
      DROP ALL
      """

    for {
      _      <- ZIO.logDebug("==>> Drop All Data Start")
      result <- getSparqlHttpResponse(DropAllSparqlString, isUpdate = true).orDie
      _      <- ZIO.logDebug(s"==>> Drop All Data End, Result: $result")
    } yield DropAllRepositoryContentACK()
  }

  /**
   * Inserts the data referenced inside the `rdfDataObjects` by appending it to a default set of `rdfDataObjects`
   * based on the list defined in `application.conf` under the `app.triplestore.default-rdf-data` key.
   *
   * @param rdfDataObjects  a sequence of paths and graph names referencing data that needs to be inserted.
   * @param prependDefaults denotes if the rdfDataObjects list should be prepended with a default set. Default is `true`.
   * @return [[InsertTriplestoreContentACK]]
   */
  def insertDataIntoTriplestore(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean = true
  ): UIO[InsertTriplestoreContentACK] = {

    val calculateCompleteRdfDataObjectList: UIO[NonEmptyChunk[RdfDataObject]] =
      if (prependDefaults) { // prepend
        if (rdfDataObjects.nonEmpty) {
          ZIO.succeed(DefaultRdfData.data)
        } else {
          //prepend default data objects like those of knora-base, knora-admin, etc.
          ZIO.succeed(DefaultRdfData.data ++ NonEmptyChunk.fromIterable(rdfDataObjects.head, rdfDataObjects.tail))
        }
      } else { // don't prepend
        if (rdfDataObjects.nonEmpty) {
          ZIO.succeed(NonEmptyChunk.fromIterable(rdfDataObjects.head, rdfDataObjects.tail))
        } else {
          ZIO.die(BadRequestException("Cannot insert list with empty data into triplestore."))
        }
      }

    for {
      _    <- ZIO.logDebug("==>> Loading Data Start")
      list <- calculateCompleteRdfDataObjectList
      request <-
        ZIO.foreach(list)(elem =>
          for {
            graphName <-
              if (elem.name.toLowerCase == "default") {
                ZIO.die(TriplestoreUnsupportedFeatureException("Requests to the default graph are not supported"))
              } else {
                ZIO.succeed(elem.name)
              }
            uriBuilder <- ZIO.attempt {
                            val uriBuilder: URIBuilder = new URIBuilder(dataInsertPath)
                            uriBuilder.addParameter("graph", graphName) //Note: addParameter encodes the graphName URL
                            uriBuilder
                          }.orDie
            httpPost <- ZIO.attempt {
                          val httpPost = new HttpPost(uriBuilder.build())
                          // Add the input file to the body of the request.
                          // here we need to tweak the base directory path from "webapi"
                          // to the parent folder where the files can be found
                          val inputFile = Paths.get("..", elem.path)
                          if (!Files.exists(inputFile)) {
                            throw BadRequestException(s"File ${inputFile} does not exist")
                          }

                          val fileEntity =
                            new FileEntity(inputFile.toFile, ContentType.create(mimeTypeTextTurtle, "UTF-8"))
                          httpPost.setEntity(fileEntity)
                          httpPost
                        }.orDie
            responseHandler <- ZIO.attempt(returnInsertGraphDataResponse(graphName)(_)).orDie
          } yield (httpPost, responseHandler)
        )
      httpContext <- makeHttpContext.orDie
      _ <- ZIO.foreachDiscard(request)(elem =>
             doHttpRequest(
               client = longRequestClient,
               request = elem._1,
               context = httpContext,
               processResponse = elem._2
             )
           )
      _ <- ZIO.logDebug("==>> Loading Data End")
    } yield InsertTriplestoreContentACK()
  }

  /**
   * Checks the Fuseki triplestore if it is available and configured correctly. If it is not
   * configured, tries to automatically configure (initialize) the required dataset.
   */
  private def checkFusekiTriplestore(): UIO[CheckTriplestoreResponse] = {

    val triplestoreAvailableResponse =
      ZIO.succeed(
        CheckTriplestoreResponse(
          triplestoreStatus = TriplestoreStatus.ServiceAvailable,
          msg = "Triplestore is available."
        )
      )

    val triplestoreNotInitializedResponse =
      ZIO.succeed(
        CheckTriplestoreResponse(
          triplestoreStatus = TriplestoreStatus.NotInitialized,
          msg = s"None of the active datasets meet our requirement of name: ${config.triplestore.fuseki.repositoryName}"
        )
      )

    def triplestoreUnavailableResponse(cause: String) =
      CheckTriplestoreResponse(
        triplestoreStatus = TriplestoreStatus.ServiceUnavailable,
        msg = s"Triplestore not available: $cause"
      )

    ZIO
      .ifZIO(checkTriplestoreInitialized())(
        triplestoreAvailableResponse,
        if (config.triplestore.autoInit) {
          ZIO
            .ifZIO(attemptToInitialize())(
              triplestoreAvailableResponse,
              triplestoreNotInitializedResponse
            )
        } else {
          triplestoreNotInitializedResponse
        }
      )
      .catchAll(ex => ZIO.succeed(triplestoreUnavailableResponse(ex.getMessage())))
  }

  /**
   * Attempt to initialize the triplestore.
   */
  private def attemptToInitialize(): Task[Boolean] =
    for {
      _           <- initJenaFusekiTriplestore()
      initialized <- checkTriplestoreInitialized()
    } yield initialized

  /**
   * Call an endpoint that returns all datasets and check if our required dataset is present.
   */
  private def checkTriplestoreInitialized(): Task[Boolean] = {

    val httpGet = ZIO.attempt {
      val httpGet = new HttpGet(checkRepositoryPath)
      httpGet.addHeader("Accept", mimeTypeApplicationJson)
      httpGet
    }

    def checkForExpectedDataset(response: String) = ZIO.attempt {
      val nameShouldBe = config.triplestore.fuseki.repositoryName

      import org.knora.webapi.messages.store.triplestoremessages.FusekiJsonProtocol._
      val fusekiServer: FusekiServer = JsonParser(response).convertTo[FusekiServer]
      val neededDataset: Option[FusekiDataset] =
        fusekiServer.datasets.find(dataset => dataset.dsName == s"/$nameShouldBe" && dataset.dsState)
      neededDataset.nonEmpty
    }

    for {
      req <- httpGet
      ctx <- makeHttpContext
      res <- doHttpRequest(
               client = updateHttpClient,
               request = req,
               context = ctx,
               processResponse = returnResponseAsString
             )
      result <- checkForExpectedDataset(res)
    } yield result
  }

  /**
   * Initialize the Jena Fuseki triplestore. Currently only works for
   * 'knora-test' and 'knora-test-unit' repository names. To be used, the
   * API needs to be started with 'KNORA_WEBAPI_TRIPLESTORE_AUTOINIT' set
   * to 'true' (settings.triplestoreAutoInit). This is set to `true` for tests
   * (`test/resources/test.conf`). Usage is only recommended for automated
   * testing and not for production use.
   */
  private def initJenaFusekiTriplestore(): UIO[Unit] = {

    val httpPost = ZIO.attemptBlocking {
      // TODO: Needs https://github.com/scalameta/metals/issues/3623 to be resolved
      // val configFileName = s"webapi/scripts/fuseki-repository-config.ttl.template"
      val configFileName = s"fuseki-repository-config.ttl.template"

      // take config from the classpath and write to triplestore
      val triplestoreConfig: String =
        FileUtil.readTextResource(configFileName).replace("@REPOSITORY@", config.triplestore.fuseki.repositoryName)

      val httpPost: HttpPost = new HttpPost("/$/datasets")
      val stringEntity       = new StringEntity(triplestoreConfig, ContentType.create(mimeTypeTextTurtle))
      httpPost.setEntity(stringEntity)
      httpPost
    }

    for {
      request     <- httpPost.orDie
      httpContext <- makeHttpContext.orDie
      _ <- doHttpRequest(
             client = updateHttpClient,
             request = request,
             context = httpContext,
             processResponse = returnUploadResponse
           )
    } yield ()
  }

  /**
   * Makes a triplestore URI for downloading a named graph.
   *
   * @param graphIri the IRI of the named graph.
   * @return a triplestore-specific URI for downloading the named graph.
   */
  private def makeNamedGraphDownloadUri(graphIri: IRI): URI = {
    val uriBuilder: URIBuilder = new URIBuilder(graphPath)
    uriBuilder.setParameter("graph", s"$graphIri")
    uriBuilder.build()
  }

  /**
   * Requests the contents of a named graph, saving the response in a file.
   *
   * @param graphIri             the IRI of the named graph.
   * @param outputFile           the file to be written.
   * @param outputFormat         the output file format.
   * @param featureFactoryConfig the feature factory configuration.
   * @return a string containing the contents of the graph in N-Quads format.
   */
  private def sparqlHttpGraphFile(
    graphIri: IRI,
    outputFile: Path,
    outputFormat: QuadFormat
  ): Task[FileWrittenResponse] = {

    val httpGet = ZIO.attempt {
      val httpGet = new HttpGet(makeNamedGraphDownloadUri(graphIri))
      httpGet.addHeader("Accept", mimeTypeTextTurtle)
      httpGet
    }

    val makeResponse: CloseableHttpResponse => FileWrittenResponse = writeResponseFile(
      outputFile = outputFile,
      maybeGraphIriAndFormat = Some(GraphIriAndFormat(graphIri = graphIri, quadFormat = outputFormat))
    )

    for {
      ctx <- makeHttpContext
      req <- httpGet
      res <- doHttpRequest(
               client = queryHttpClient,
               request = req,
               context = ctx,
               processResponse = makeResponse
             )
    } yield res

  }

  /**
   * Requests the contents of a named graph, returning the response as Turtle.
   *
   * @param graphIri the IRI of the named graph.
   * @return a string containing the contents of the graph in Turtle format.
   */
  private def sparqlHttpGraphData(graphIri: IRI): Task[NamedGraphDataResponse] = {

    val httpGet = ZIO.attempt {
      val httpGet = new HttpGet(makeNamedGraphDownloadUri(graphIri))
      httpGet.addHeader("Accept", mimeTypeTextTurtle)
      httpGet
    }

    val makeResponse: CloseableHttpResponse => NamedGraphDataResponse = returnGraphDataAsTurtle(graphIri)

    for {
      ctx <- makeHttpContext
      req <- httpGet
      res <- doHttpRequest(
               client = queryHttpClient,
               request = req,
               context = ctx,
               processResponse = makeResponse
             )
    } yield res
  }

  /**
   * Submits a SPARQL request to the triplestore and returns the response as a string.
   *
   * @param sparql         the SPARQL request to be submitted.
   * @param isUpdate       `true` if this is an update request.
   * @param acceptMimeType the MIME type to be provided in the HTTP Accept header.
   * @param simulateTimeout if `true`, simulate a read timeout.
   * @return the triplestore's response.
   */
  private def getSparqlHttpResponse(
    sparql: String,
    isUpdate: Boolean,
    acceptMimeType: String = mimeTypeApplicationSparqlResultsJson,
    simulateTimeout: Boolean = false
  ): Task[String] = {

    val httpClient = ZIO.attempt {
      if (isUpdate) {
        updateHttpClient
      } else {
        queryHttpClient
      }
    }

    val httpPost = ZIO.attempt {
      if (isUpdate) {
        // Send updates as application/sparql-update (as per SPARQL 1.1 Protocol §3.2.2, "UPDATE using POST directly").
        val requestEntity  = new StringEntity(sparql, ContentType.create(mimeTypeApplicationSparqlUpdate, "UTF-8"))
        val updateHttpPost = new HttpPost(sparqlUpdatePath)
        updateHttpPost.setEntity(requestEntity)
        updateHttpPost
      } else {
        // Send queries as application/x-www-form-urlencoded (as per SPARQL 1.1 Protocol §2.1.2,
        // "query via POST with URL-encoded parameters"), so we can include the "infer" parameter when using GraphDB.
        val formParams = new util.ArrayList[NameValuePair]()
        formParams.add(new BasicNameValuePair("query", sparql))
        val requestEntity: UrlEncodedFormEntity = new UrlEncodedFormEntity(formParams, Consts.UTF_8)
        val queryHttpPost: HttpPost             = new HttpPost(queryPath)
        queryHttpPost.setEntity(requestEntity)
        queryHttpPost.addHeader("Accept", acceptMimeType)
        queryHttpPost
      }
    }

    for {
      ctx <- makeHttpContext
      clt <- httpClient
      req <- httpPost
      res <- doHttpRequest(
               client = clt,
               request = req,
               context = ctx,
               processResponse = returnResponseAsString,
               simulateTimeout = simulateTimeout
             )
    } yield res
  }

  /**
   * Dumps the whole repository in N-Quads format, saving the response in a file.
   *
   * @param outputFile           the output file.
   * @param featureFactoryConfig the feature factory configuration.
   * @return a string containing the contents of the graph in N-Quads format.
   */
  private def downloadRepository(
    outputFile: Path
  ): UIO[FileWrittenResponse] = {

    val httpGet = ZIO.attempt {
      val uriBuilder: URIBuilder = new URIBuilder(repositoryDownloadPath)
      val httpGet                = new HttpGet(uriBuilder.build())
      httpGet.addHeader("Accept", mimeTypeApplicationNQuads)
      httpGet
    }

    val makeResponse: CloseableHttpResponse => FileWrittenResponse = writeResponseFile(
      outputFile = outputFile
    )

    for {
      ctx <- makeHttpContext.orDie
      req <- httpGet.orDie
      res <- doHttpRequest(
               client = longRequestClient,
               request = req,
               context = ctx,
               processResponse = makeResponse
             )
    } yield res
  }

  /**
   * Uploads repository content from an N-Quads file.
   *
   * @param inputFile an N-Quads file containing the content to be uploaded to the repository.
   */
  private def uploadRepository(inputFile: Path): UIO[RepositoryUploadedResponse] = {

    val httpPost = ZIO.attempt {
      val httpPost: HttpPost = new HttpPost(repositoryUploadPath)
      val fileEntity         = new FileEntity(inputFile.toFile, ContentType.create(mimeTypeApplicationNQuads, "UTF-8"))
      httpPost.setEntity(fileEntity)
      httpPost
    }

    for {
      ctx <- makeHttpContext.orDie
      req <- httpPost.orDie
      res <- doHttpRequest(
               client = longRequestClient,
               request = req,
               context = ctx,
               processResponse = returnUploadResponse
             )
    } yield res
  }

  /**
   * Puts a data graph into the repository.
   *
   * @param graphContent a data graph in Turtle format to be inserted into the repository.
   * @param graphName    the name of the graph.
   */
  private def insertDataGraphRequest(graphContent: String, graphName: String): UIO[InsertGraphDataContentResponse] = {

    val httpPut = ZIO.attempt {
      val uriBuilder: URIBuilder = new URIBuilder(dataInsertPath)
      uriBuilder.addParameter("graph", graphName)
      val httpPut: HttpPut = new HttpPut(uriBuilder.build())
      val requestEntity    = new StringEntity(graphContent, ContentType.create(mimeTypeTextTurtle, "UTF-8"))
      httpPut.setEntity(requestEntity)
      httpPut
    }

    val makeResponse: CloseableHttpResponse => InsertGraphDataContentResponse = returnInsertGraphDataResponse(graphName)

    for {
      ctx <- makeHttpContext.orDie
      req <- httpPut.orDie
      res <- doHttpRequest(
               client = longRequestClient,
               request = req,
               context = ctx,
               processResponse = makeResponse
             )
    } yield res
  }

  /**
   * Formulate HTTP context.
   *
   * @return httpContext with credentials and authorization
   */
  private def makeHttpContext: Task[HttpClientContext] = ZIO.attempt {
    val authCache: AuthCache   = new BasicAuthCache
    val basicAuth: BasicScheme = new BasicScheme
    authCache.put(targetHost, basicAuth)

    val httpContext: HttpClientContext = HttpClientContext.create
    httpContext.setCredentialsProvider(credsProvider)
    httpContext.setAuthCache(authCache)
    httpContext
  }

  /**
   * Makes an HTTP connection to the triplestore, and delegates processing of the response
   * to a function.
   *
   * @param client          the HTTP client to be used for the request.
   * @param request         the request to be sent.
   * @param context         the request context to be used.
   * @param processResponse a function that processes the HTTP response.
   * @param simulateTimeout if `true`, simulate a read timeout.
   * @tparam T the return type of `processResponse`.
   * @return the return value of `processResponse`.
   */
  private def doHttpRequest[T](
    client: CloseableHttpClient,
    request: HttpRequest,
    context: HttpClientContext,
    processResponse: CloseableHttpResponse => T,
    simulateTimeout: Boolean = false
  ): UIO[T] = {

    // TODO: Can we make Fuseki abandon the query if it takes too long?

    def checkSimulateTimeout(simulateTimeout: Boolean): UIO[Unit] =
      if (simulateTimeout)
        ZIO.die(new java.net.SocketTimeoutException("Simulated read timeout"))
      else
        ZIO.unit

    def checkResponse(response: CloseableHttpResponse, statusCode: Int): UIO[Unit] =
      if (statusCode == 404) {
        ZIO.die(NotFoundException("The requested data was not found"))
      } else {
        val statusCategory: Int = statusCode / 100
        if (statusCategory != 2) {
          Option(response.getEntity)
            .map(responseEntity => EntityUtils.toString(responseEntity, StandardCharsets.UTF_8)) match {
            case Some(responseEntityStr) =>
              val msg = s"Triplestore responded with HTTP code $statusCode: $responseEntityStr"
              if (statusCode == 503 && responseEntityStr.contains("Query timed out"))
                ZIO.die(TriplestoreTimeoutException(msg))
              else
                ZIO.die(TriplestoreResponseException(msg))
            case None =>
              ZIO.die(TriplestoreResponseException(s"Triplestore responded with HTTP code $statusCode"))
          }
        } else {
          ZIO.unit
        }
      }

    def attemptProcessResponse(response: CloseableHttpResponse, f: CloseableHttpResponse => T): UIO[T] =
      ZIO
        .attempt(f(response))
        .catchSome {
          case socketTimeoutException: java.net.SocketTimeoutException => {
            val message =
              "The triplestore took too long to process a request. This can happen because the triplestore needed too much time to search through the data that is currently in the triplestore. Query optimisation may help."
            ZIO.logError(message + socketTimeoutException.getMessage()) *>
              ZIO.die(TriplestoreTimeoutException(message))
          }
          case e: Exception => {
            val message = s"Failed to connect to triplestore."
            ZIO.logError(message + e.getMessage()) *>
              ZIO.die(TriplestoreConnectionException(message))
          }
        }
        .orDie

    def logTimeTook(start: Long, statusCode: Int) =
      ZIO
        .succeed(java.lang.System.currentTimeMillis() - start)
        .flatMap(took => ZIO.logInfo(s"[$statusCode] Triplestore query took: ${took}ms"))

    for {
      _          <- checkSimulateTimeout(simulateTimeout)
      start      <- ZIO.attempt(java.lang.System.currentTimeMillis()).orDie
      response   <- ZIO.attempt(client.execute(targetHost, request, context)).orDie
      statusCode <- ZIO.attempt(response.getStatusLine.getStatusCode).orDie
      _          <- checkResponse(response, statusCode)
      result     <- attemptProcessResponse(response, processResponse)
      _          <- ZIO.attempt(response.close()).orDie // TODO: rewrite with ensuring
      _          <- logTimeTook(start, statusCode)
    } yield result
  }

  /**
   * Attempts to transforms a [[CloseableHttpResponse]] to a [[String]].
   */
  def returnResponseAsString(response: CloseableHttpResponse): String =
    Option(response.getEntity) match {
      case None => ""

      case Some(responseEntity) =>
        EntityUtils.toString(responseEntity, StandardCharsets.UTF_8)
    }

  /**
   * Attempts to transforms a [[CloseableHttpResponse]] to a [[NamedGraphDataResponse]].
   */
  def returnGraphDataAsTurtle(graphIri: IRI)(response: CloseableHttpResponse): NamedGraphDataResponse =
    Option(response.getEntity) match {
      case None =>
        throw TriplestoreResponseException(s"Triplestore returned no content for graph $graphIri")

      case Some(responseEntity: HttpEntity) =>
        NamedGraphDataResponse(
          turtle = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8)
        )
    }

  /**
   * Attempts to transforms a [[CloseableHttpResponse]] to a [[RepositoryUploadedResponse]].
   */
  def returnUploadResponse: CloseableHttpResponse => RepositoryUploadedResponse = { _ =>
    RepositoryUploadedResponse()
  }

  /**
   * Attempts to transforms a [[CloseableHttpResponse]] to a [[InsertGraphDataContentResponse]].
   */
  def returnInsertGraphDataResponse(
    graphName: String
  )(response: CloseableHttpResponse): InsertGraphDataContentResponse =
    Option(response.getEntity) match {
      case None =>
        throw TriplestoreResponseException(s"$graphName could not be inserted into Triplestore.")

      case Some(_) =>
        InsertGraphDataContentResponse()
    }

  /**
   * Represents a named graph IRI and the file format that the graph should be written in.
   *
   * @param graphIri   the named graph IRI.
   * @param quadFormat the file format.
   */
  case class GraphIriAndFormat(graphIri: IRI, quadFormat: QuadFormat)

  /**
   * Writes an HTTP response to a file.
   *
   * @param outputFile             the output file.
   * @param featureFactoryConfig   the feature factory configuration.
   * @param maybeGraphIriAndFormat a graph IRI and quad format for the output file. If defined, the response
   *                               is parsed as Turtle and converted to the output format, with the graph IRI
   *                               added to each statement. Otherwise, the response is written as-is to the
   *                               output file.
   * @param response               the response to be read.
   * @return a [[FileWrittenResponse]].
   */
  def writeResponseFile(
    outputFile: Path,
    maybeGraphIriAndFormat: Option[GraphIriAndFormat] = None
  )(response: CloseableHttpResponse): FileWrittenResponse =
    Option(response.getEntity) match {
      case Some(responseEntity: HttpEntity) =>
        // Are we converting the response to a quad format?
        maybeGraphIriAndFormat match {
          case Some(GraphIriAndFormat(graphIri, quadFormat)) =>
            // Yes. Stream the HTTP entity to a temporary Turtle file.
            val turtleFile = Paths.get(outputFile.toString + ".ttl")
            Files.copy(responseEntity.getContent, turtleFile, StandardCopyOption.REPLACE_EXISTING)

            // Convert the Turtle to the output format.

            val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil()

            val processFileTry: Try[Unit] = Try {
              rdfFormatUtil.turtleToQuadsFile(
                rdfSource = RdfInputStreamSource(new BufferedInputStream(Files.newInputStream(turtleFile))),
                graphIri = graphIri,
                outputFile = outputFile,
                outputFormat = quadFormat
              )
            }

            Files.delete(turtleFile)

            processFileTry match {
              case Success(_)  => ()
              case Failure(ex) => throw ex
            }

          case None =>
            // No. Stream the HTTP entity directly to the output file.
            Files.copy(responseEntity.getContent, outputFile)
        }

        FileWrittenResponse()

      case None =>
        maybeGraphIriAndFormat match {
          case Some(GraphIriAndFormat(graphIri, _)) =>
            // log.error(s"Triplestore returned no content for graph $graphIri")
            throw TriplestoreResponseException(s"Triplestore returned no content for graph $graphIri")

          case None =>
            // log.error(s"Triplestore returned no content for repository dump")
            throw TriplestoreResponseException(s"Triplestore returned no content for for repository dump")
        }
    }
}

object TriplestoreServiceHttpConnectorImpl {

  /**
   * Acquires a configured httpClient, backed by a connection pool,
   * to be used in communicating with Fuseki.
   */
  private def acquire(config: AppConfig) = ZIO.attemptBlocking {

    // timeout from config
    val sipiTimeoutMillis: Int = config.sipi.timeoutInSeconds.toMillis.toInt

    // Create a connection manager with custom configuration.
    val connManager: PoolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager()

    // Create socket configuration
    val socketConfig: SocketConfig = SocketConfig
      .custom()
      .setTcpNoDelay(true)
      .build();

    // Configure the connection manager to use socket configuration by default.
    connManager.setDefaultSocketConfig(socketConfig)

    // Validate connections after 1 sec of inactivity
    connManager.setValidateAfterInactivity(1000);

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

    // Create an HttpClient with the given custom dependencies and configuration.
    val httpClient: CloseableHttpClient = HttpClients
      .custom()
      .setConnectionManager(connManager)
      .setDefaultRequestConfig(defaultRequestConfig)
      .build()

    httpClient
  }.tap(_ => ZIO.debug(">>> Acquire Triplestore Service Http Connector <<<")).orDie

  /**
   * Releases the httpClient, freeing all resources.
   */
  private def release(httpClient: CloseableHttpClient): URIO[Any, Unit] =
    ZIO.attemptBlocking {
      httpClient.close()
    }.tap(_ => ZIO.debug(">>> Release Triplestore Service Http Connector <<<")).orDie

  val layer: ZLayer[AppConfig, Nothing, TriplestoreService] = {
    ZLayer {
      for {
        config <- ZIO.service[AppConfig]
        // _          <- ZIO.debug(config)
        // HINT: Scope does not work when used together with unsafeRun to
        // bridge over to Akka. Need to change this as soon Akka is removed
        // httpClient <- ZIO.acquireRelease(acquire(config))(release(_))
        httpClient <- acquire(config)
      } yield TriplestoreServiceHttpConnectorImpl(config, httpClient)
    }.tap(_ => ZIO.debug(">>> Triplestore Service Http Connector Initialized <<<"))
  }

}
