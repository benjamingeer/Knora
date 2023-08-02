/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.impl

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
import org.apache.http.config.SocketConfig
import org.apache.http.entity.ContentType
import org.apache.http.entity.FileEntity
import org.apache.http.entity.StringEntity
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import spray.json._
import zio._

import java.io.BufferedInputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util
import scala.collection.mutable

import dsp.errors._
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.SparqlResultProtocol._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.defaults.DefaultRdfData
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus
import org.knora.webapi.store.triplestore.errors._
import org.knora.webapi.util.FileUtil

/**
 * Implementation of the the [[TriplestoreService]] for accessing Fuseki over HTTP.
 */
case class TriplestoreServiceLive(
  config: AppConfig,
  httpClient: CloseableHttpClient,
  implicit val stringFormatter: StringFormatter
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

  // timeouts sent to Fuseki
  private val queryTimeoutString      = config.triplestore.queryTimeout.toSeconds.toInt.toString
  private val gravsearchTimeoutString = config.triplestore.gravsearchTimeout.toSeconds.toInt.toString

  // the client config used for queries to the triplestore. The timeout has to be larger than
  // config.triplestore.queryTimeoutAsDuration and config.triplestore.gravsearchTimeoutAsDuration.
  private val requestTimeoutMillis = 7200000 // 2 hours

  private val queryRequestConfig = RequestConfig
    .custom()
    .setConnectTimeout(requestTimeoutMillis)
    .setConnectionRequestTimeout(requestTimeoutMillis)
    .setSocketTimeout(requestTimeoutMillis)
    .build

  private val queryHttpClient: CloseableHttpClient = HttpClients.custom
    .setDefaultCredentialsProvider(credsProvider)
    .setDefaultRequestConfig(queryRequestConfig)
    .build

  private val dbName                 = config.triplestore.fuseki.repositoryName
  private val queryPath              = s"/$dbName/query"
  private val sparqlUpdatePath       = s"/$dbName/update"
  private val graphPath              = s"/$dbName/get"
  private val dataInsertPath         = s"/$dbName/data"
  private val repositoryDownloadPath = s"/$dbName"
  private val checkRepositoryPath    = "/$/server"
  private val repositoryUploadPath   = repositoryDownloadPath

  private def processError(sparql: String, response: String): IO[TriplestoreException, Nothing] = {
    val delimiter: String = "\n" + StringUtils.repeat('=', 80) + "\n"
    val message: String   = "Triplestore timed out while sending a response, after sending statuscode 200."

    if (response.contains("##  Query cancelled due to timeout during execution"))
      ZIO.logError(message) *> ZIO.fail(TriplestoreTimeoutException(message))
    else
      ZIO.logError(
        s"Couldn't parse response from triplestore:$delimiter$response${delimiter}in response to SPARQL query:$delimiter$sparql"
      ) *> ZIO.fail(TriplestoreResponseException("Couldn't parse Turtle from triplestore"))
  }

  /**
   * Simulates a read timeout.
   */
  def doSimulateTimeout(): Task[SparqlSelectResult] = {
    val sparql = """SELECT ?foo WHERE {
                   |    BIND("foo" AS ?foo)
                   |}""".stripMargin

    for {
      result <- sparqlHttpSelect(sparql = sparql, simulateTimeout = true)
    } yield result
  }

  /**
   * Given a SPARQL SELECT query string, runs the query, returning the result as a [[SparqlSelectResult]].
   *
   * @param sparql          the SPARQL SELECT query string.
   * @param simulateTimeout if `true`, simulate a read timeout.
   * @param isGravsearch    `true` if it is a gravsearch query (relevant for timeout)
   * @return a [[SparqlSelectResult]].
   */
  def sparqlHttpSelect(
    sparql: String,
    simulateTimeout: Boolean = false,
    isGravsearch: Boolean = false
  ): Task[SparqlSelectResult] = {
    def parseJsonResponse(sparql: String, resultStr: String): IO[TriplestoreException, SparqlSelectResult] =
      ZIO
        .attemptBlocking(resultStr.parseJson.convertTo[SparqlSelectResult])
        .orElse(processError(sparql, resultStr))

    for {
      resultStr <-
        getSparqlHttpResponse(sparql, isUpdate = false, simulateTimeout = simulateTimeout, isGravsearch = isGravsearch)
      // Parse the response as a JSON object and generate a response message.
      responseMessage <- parseJsonResponse(sparql, resultStr)
    } yield responseMessage
  }

  /**
   * Given a SPARQL CONSTRUCT query string, runs the query, returning the result as a [[SparqlConstructResponse]].
   *
   * @param sparqlConstructRequest the request message.
   * @return a [[SparqlConstructResponse]]
   */
  def sparqlHttpConstruct(sparqlConstructRequest: SparqlConstructRequest): Task[SparqlConstructResponse] = {

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
      }.orElse(processError(sparql, turtleStr))

    for {
      turtleStr <-
        getSparqlHttpResponse(
          sparqlConstructRequest.sparql,
          isUpdate = false,
          acceptMimeType = mimeTypeTextTurtle
        )

      response <- parseTurtleResponse(
                    sparql = sparqlConstructRequest.sparql,
                    turtleStr = turtleStr,
                    rdfFormatUtil = rdfFormatUtil
                  )
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
  def sparqlHttpConstructFile(
    sparql: String,
    graphIri: IRI,
    outputFile: Path,
    outputFormat: QuadFormat
  ): Task[FileWrittenResponse] = {
    val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil()

    for {
      turtleStr <- getSparqlHttpResponse(sparql, isUpdate = false, acceptMimeType = mimeTypeTextTurtle)
      _ <- ZIO
             .attempt(
               rdfFormatUtil
                 .turtleToQuadsFile(
                   rdfSource = RdfStringSource(turtleStr),
                   graphIri = graphIri,
                   outputFile = outputFile,
                   outputFormat = outputFormat
                 )
             )

    } yield FileWrittenResponse()
  }

  /**
   * Given a SPARQL CONSTRUCT query string, runs the query, returns the result as a [[SparqlExtendedConstructResponse]].
   *
   * @param sparqlExtendedConstructRequest the request message.
   * @return a [[SparqlExtendedConstructResponse]]
   */
  def sparqlHttpExtendedConstruct(
    sparqlExtendedConstructRequest: SparqlExtendedConstructRequest
  ): Task[SparqlExtendedConstructResponse] =
    for {
      turtleStr <- getSparqlHttpResponse(
                     sparqlExtendedConstructRequest.sparql,
                     isUpdate = false,
                     isGravsearch = sparqlExtendedConstructRequest.isGravsearch,
                     acceptMimeType = mimeTypeTextTurtle
                   )

      response <- SparqlExtendedConstructResponse
                    .parseTurtleResponse(turtleStr)
                    .foldZIO(
                      _ =>
                        ZIO.fail(
                          TriplestoreResponseException(
                            s"Couldn't parse Turtle from triplestore: $sparqlExtendedConstructRequest"
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
  def sparqlHttpUpdate(sparqlUpdate: String): Task[SparqlUpdateResponse] =
    for {
      // Send the request to the triplestore.
      _ <- getSparqlHttpResponse(sparqlUpdate, isUpdate = true)
    } yield SparqlUpdateResponse()

  /**
   * Performs a SPARQL ASK query.
   *
   * @param sparql the SPARQL ASK query.
   * @return a [[SparqlAskResponse]].
   */
  def sparqlHttpAsk(sparql: String): Task[SparqlAskResponse] =
    for {
      resultString <- getSparqlHttpResponse(sparql, isUpdate = false)
      _            <- ZIO.logDebug(s"sparqlHttpAsk - resultString: $resultString")
      result <- ZIO
                  .attemptBlocking(
                    resultString.parseJson.asJsObject.getFields("boolean").head.convertTo[Boolean]
                  )

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
    prependDefaults: Boolean
  ): Task[ResetRepositoryContentACK] =
    for {
      _ <- ZIO.logDebug("resetTripleStoreContent")

      // drop old content
      _ <- dropAllTriplestoreContent()

      // insert new content
      _ <- insertDataIntoTriplestore(rdfDataObjects, prependDefaults)
    } yield ResetRepositoryContentACK()

  /**
   * Drops (deletes) all data from the triplestore using "DROP ALL" SPARQL query.
   */
  def dropAllTriplestoreContent(): Task[DropAllRepositoryContentACK] = {
    val sparqlQuery = "DROP ALL"

    for {
      _      <- ZIO.logDebug("==>> Drop All Data Start")
      result <- getSparqlHttpResponse(sparqlQuery, isUpdate = true)
      _      <- ZIO.logDebug(s"==>> Drop All Data End, Result: $result")
    } yield DropAllRepositoryContentACK()
  }

  /**
   * Drops all triplestore data graph by graph using "DROP GRAPH" SPARQL query.
   * This method is useful in cases with large amount of data (over 10 million statements),
   * where the method [[dropAllTriplestoreContent()]] could create timeout issues.
   */
  def dropDataGraphByGraph(): Task[DropDataGraphByGraphACK] =
    for {
      _      <- ZIO.logInfo("==>> Drop All Data Start")
      graphs <- getAllGraphs
      _      <- ZIO.logInfo(s"Number of graphs found: ${graphs.length}")
      _      <- ZIO.foreachDiscard(graphs)(dropGraph)
      _      <- ZIO.logInfo("==>> Drop All Data End")
    } yield DropDataGraphByGraphACK()

  private def dropGraph(graphName: String): Task[Unit] =
    ZIO.logInfo(s"==>> Dropping graph: $graphName") *>
      getSparqlHttpResponse(s"DROP GRAPH <$graphName>", isUpdate = true) *>
      ZIO.logDebug("Graph dropped")

  /**
   * Gets all graphs stored in the triplestore.
   *
   * @return All graphs stored in the triplestore as a [[Seq[String]]
   */
  private def getAllGraphs: Task[Seq[String]] =
    for {
      res     <- sparqlHttpSelect("select ?g {graph ?g {?s ?p ?o}} group by ?g")
      bindings = res.results.bindings
      graphs   = bindings.map(_.rowMap("g"))
    } yield graphs

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
    prependDefaults: Boolean
  ): Task[InsertTriplestoreContentACK] = {

    val calculateCompleteRdfDataObjectList: Task[NonEmptyChunk[RdfDataObject]] =
      if (prependDefaults) { // prepend
        if (rdfDataObjects.isEmpty) {
          ZIO.succeed(DefaultRdfData.data)
        } else {
          // prepend default data objects like those of knora-base, knora-admin, etc.
          ZIO.succeed(DefaultRdfData.data ++ NonEmptyChunk.fromIterable(rdfDataObjects.head, rdfDataObjects.tail))
        }
      } else { // don't prepend
        if (rdfDataObjects.isEmpty) {
          ZIO.fail(BadRequestException("Cannot insert list with empty data into triplestore."))
        } else {
          ZIO.succeed(NonEmptyChunk.fromIterable(rdfDataObjects.head, rdfDataObjects.tail))
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
                ZIO.fail(TriplestoreUnsupportedFeatureException("Requests to the default graph are not supported"))
              } else {
                ZIO.succeed(elem.name)
              }

            uriBuilder <-
              ZIO.attempt {
                val uriBuilder: URIBuilder = new URIBuilder(dataInsertPath)
                uriBuilder.addParameter("graph", graphName) // Note: addParameter encodes the graphName URL
                uriBuilder
              }

            httpPost <-
              ZIO.attempt {
                val httpPost = new HttpPost(uriBuilder.build())
                // Add the input file to the body of the request.
                // here we need to tweak the base directory path from "webapi"
                // to the parent folder where the files can be found
                val inputFile = Paths.get("..", elem.path)
                if (!Files.exists(inputFile)) {
                  throw BadRequestException(s"File $inputFile does not exist")
                }
                val fileEntity =
                  new FileEntity(inputFile.toFile, ContentType.create(mimeTypeTextTurtle, "UTF-8"))
                httpPost.setEntity(fileEntity)
                httpPost
              }
            responseHandler <- ZIO.attempt(returnInsertGraphDataResponse(graphName)(_))
          } yield (httpPost, responseHandler)
        )
      httpContext <- makeHttpContext
      _ <- ZIO.foreachDiscard(request)(elem =>
             doHttpRequest(
               client = queryHttpClient,
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
  def checkTriplestore(): Task[CheckTriplestoreResponse] = {

    val triplestoreAvailableResponse = ZIO.succeed(CheckTriplestoreResponse.Available)

    val triplestoreNotInitializedResponse =
      ZIO.succeed(
        CheckTriplestoreResponse(
          triplestoreStatus = TriplestoreStatus.NotInitialized(
            s"None of the active datasets meet our requirement of name: ${config.triplestore.fuseki.repositoryName}"
          )
        )
      )

    def triplestoreUnavailableResponse(cause: String) =
      CheckTriplestoreResponse(
        triplestoreStatus = TriplestoreStatus.Unavailable(s"Triplestore not available: $cause")
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
      .catchAll(ex => ZIO.succeed(triplestoreUnavailableResponse(ex.getMessage)))
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
               client = queryHttpClient,
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
   * to 'true' (appConfig.triplestore.autoInit). This is set to `true` for tests
   * (`test/resources/test.conf`). Usage is only recommended for automated
   * testing and not for production use.
   */
  private def initJenaFusekiTriplestore(): Task[Unit] = {

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
      request     <- httpPost
      httpContext <- makeHttpContext
      _ <- doHttpRequest(
             client = queryHttpClient,
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
   * @return a string containing the contents of the graph in N-Quads format.
   */
  def sparqlHttpGraphFile(
    graphIri: IRI,
    outputFile: Path,
    outputFormat: QuadFormat
  ): Task[FileWrittenResponse] = {

    val httpGet = ZIO.attempt {
      val httpGet = new HttpGet(makeNamedGraphDownloadUri(graphIri))
      httpGet.addHeader("Accept", mimeTypeTextTurtle)
      httpGet
    }

    for {
      ctx <- makeHttpContext
      req <- httpGet
      res <- doHttpRequest(
               client = queryHttpClient,
               request = req,
               context = ctx,
               processResponse = writeResponseFileAsTurtleContent(
                 outputFile = outputFile,
                 graphIri = graphIri,
                 quadFormat = outputFormat
               )
             )
    } yield res

  }

  /**
   * Requests the contents of a named graph, returning the response as Turtle.
   *
   * @param graphIri the IRI of the named graph.
   * @return a string containing the contents of the graph in Turtle format.
   */
  def sparqlHttpGraphData(graphIri: IRI): Task[NamedGraphDataResponse] = {

    val httpGet = ZIO.attempt {
      val httpGet = new HttpGet(makeNamedGraphDownloadUri(graphIri))
      httpGet.addHeader("Accept", mimeTypeTextTurtle)
      httpGet
    }

    for {
      ctx <- makeHttpContext
      req <- httpGet
      res <- doHttpRequest(
               client = queryHttpClient,
               request = req,
               context = ctx,
               processResponse = returnGraphDataAsTurtle(graphIri)
             )
    } yield res
  }

  /**
   * Submits a SPARQL request to the triplestore and returns the response as a string.
   *   According to the request type a different timeout are used.
   *
   * @param sparql         the SPARQL request to be submitted.
   * @param isUpdate       `true` if this is an update request.
   * @param isGravsearch   `true` if this is a Gravsearch query (needs a greater timeout).
   * @param acceptMimeType the MIME type to be provided in the HTTP Accept header.
   * @param simulateTimeout if `true`, simulate a read timeout.
   * @return the triplestore's response.
   */
  private def getSparqlHttpResponse(
    sparql: String,
    isUpdate: Boolean,
    isGravsearch: Boolean = false,
    acceptMimeType: String = mimeTypeApplicationSparqlResultsJson,
    simulateTimeout: Boolean = false
  ): Task[String] = {
    val httpClient = ZIO.attempt(queryHttpClient)
    val httpPost = ZIO.attempt {
      if (isUpdate) {
        // Send updates as application/sparql-update (as per SPARQL 1.1 Protocol §3.2.2, "UPDATE using POST directly").
        val requestEntity  = new StringEntity(sparql, ContentType.create(mimeTypeApplicationSparqlUpdate, "UTF-8"))
        val updateHttpPost = new HttpPost(sparqlUpdatePath)
        updateHttpPost.setEntity(requestEntity)
        updateHttpPost
      } else {
        // Send queries as application/x-www-form-urlencoded (as per SPARQL 1.1 Protocol §2.1.2, "query via POST with URL-encoded parameters").
        val formParams = new util.ArrayList[NameValuePair]()
        formParams.add(new BasicNameValuePair("query", sparql))
        // in case of a gravsearch query, a specific (longer) timeout is set
        formParams.add(
          new BasicNameValuePair("timeout", if (isGravsearch) gravsearchTimeoutString else queryTimeoutString)
        )
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
   * @return a string containing the contents of the graph in N-Quads format.
   */
  def downloadRepository(
    outputFile: Path
  ): Task[FileWrittenResponse] = {

    val httpGet = ZIO.attempt {
      val uriBuilder: URIBuilder = new URIBuilder(repositoryDownloadPath)
      val httpGet                = new HttpGet(uriBuilder.build())
      httpGet.addHeader("Accept", mimeTypeApplicationNQuads)
      httpGet
    }

    for {
      ctx <- makeHttpContext
      req <- httpGet
      res <- doHttpRequest(
               client = queryHttpClient,
               request = req,
               context = ctx,
               processResponse = writeResponseFileAsPlainContent(outputFile)
             )
    } yield res
  }

  /**
   * Uploads repository content from an N-Quads file.
   *
   * @param inputFile an N-Quads file containing the content to be uploaded to the repository.
   */
  def uploadRepository(inputFile: Path): Task[RepositoryUploadedResponse] = {

    val httpPost = ZIO.attempt {
      val httpPost: HttpPost = new HttpPost(repositoryUploadPath)
      val fileEntity         = new FileEntity(inputFile.toFile, ContentType.create(mimeTypeApplicationNQuads, "UTF-8"))
      httpPost.setEntity(fileEntity)
      httpPost
    }

    for {
      ctx <- makeHttpContext
      req <- httpPost
      res <- doHttpRequest(
               client = queryHttpClient,
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
  def insertDataGraphRequest(graphContent: String, graphName: String): Task[InsertGraphDataContentResponse] = {

    val httpPut = ZIO.attempt {
      val uriBuilder: URIBuilder = new URIBuilder(dataInsertPath)
      uriBuilder.addParameter("graph", graphName)
      val httpPut: HttpPut = new HttpPut(uriBuilder.build())
      val requestEntity    = new StringEntity(graphContent, ContentType.create(mimeTypeTextTurtle, "UTF-8"))
      httpPut.setEntity(requestEntity)
      httpPut
    }

    for {
      ctx <- makeHttpContext
      req <- httpPut
      res <- doHttpRequest(
               client = queryHttpClient,
               request = req,
               context = ctx,
               processResponse = returnInsertGraphDataResponse(graphName)
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
   * @param processError a function that processes the HTTP response.
   * @param simulateTimeout if `true`, simulate a read timeout.
   * @tparam T the return type of `processError`.
   * @return the return value of `processError`.
   */
  private def doHttpRequest[T](
    client: CloseableHttpClient,
    request: HttpRequest,
    context: HttpClientContext,
    processResponse: CloseableHttpResponse => Task[T],
    simulateTimeout: Boolean = false
  ): Task[T] = {

    def checkSimulateTimeout(): Task[Unit] =
      if (simulateTimeout) {
        ZIO.fail(
          TriplestoreTimeoutException(
            "The triplestore took too long to process a request. This can happen because the triplestore needed too much time to search through the data that is currently in the triplestore. Query optimisation may help."
          )
        )
      } else
        ZIO.unit

    def executeQuery(): Task[CloseableHttpResponse] = {
      ZIO
        .attempt(client.execute(targetHost, request, context))
        .catchSome {
          case socketTimeoutException: java.net.SocketTimeoutException =>
            val message =
              "The triplestore took too long to process a request. This can happen because the triplestore needed too much time to search through the data that is currently in the triplestore. Query optimisation may help."
            val error = TriplestoreTimeoutException(message, socketTimeoutException)
            ZIO.logError(error.toString) *>
              ZIO.fail(error)
          case e: Exception =>
            val message = s"Failed to connect to triplestore."
            val error   = TriplestoreConnectionException(message, Some(e))
            ZIO.logError(error.toString) *>
              ZIO.fail(error)
        }
    } <* ZIO.logDebug(s"Executing Query: $request")

    def checkResponse(response: CloseableHttpResponse, statusCode: Int): Task[Unit] =
      if (statusCode / 100 == 2)
        ZIO.unit
      else {
        val entity =
          Option(response.getEntity)
            .map(responseEntity => EntityUtils.toString(responseEntity, StandardCharsets.UTF_8))

        val statusResponseMsg =
          s"Triplestore responded with HTTP code $statusCode"

        (statusCode, entity) match {
          case (404, _) => ZIO.fail(NotFoundException.notFound)
          case (400, Some(response)) if response.contains("Text search parse error") =>
            ZIO.fail(BadRequestException(s"$response"))
          case (500, _) => ZIO.fail(TriplestoreResponseException(statusResponseMsg))
          case (503, Some(response)) if response.contains("Query timed out") =>
            ZIO.fail(TriplestoreTimeoutException(s"$statusResponseMsg: $response"))
          case (503, _) => ZIO.fail(TriplestoreResponseException(statusResponseMsg))
          case _        => ZIO.fail(TriplestoreResponseException(statusResponseMsg))
        }
      }

    def getResponse = ZIO.acquireRelease(executeQuery())(response => ZIO.succeed(response.close()))

    ZIO.scoped(for {
      _          <- checkSimulateTimeout()
      _          <- ZIO.logDebug("Executing query...")
      response   <- getResponse
      statusCode <- ZIO.attempt(response.getStatusLine.getStatusCode)
      _          <- ZIO.logDebug(s"Executing query done with status code: $statusCode")
      _          <- checkResponse(response, statusCode)
      _          <- ZIO.logDebug("Checking response done.")
      result     <- processResponse(response)
      _          <- ZIO.logDebug("Processing response done.")
    } yield result)
  }

  /**
   * Attempts to transforms a [[CloseableHttpResponse]] to a [[String]].
   */
  private def returnResponseAsString(response: CloseableHttpResponse): Task[String] =
    Option(response.getEntity) match {
      case None => ZIO.succeed("")
      case Some(responseEntity) =>
        ZIO
          .attempt(EntityUtils.toString(responseEntity, StandardCharsets.UTF_8))
          .tapDefect(e => ZIO.logError(s"Failed to return response as string: $e"))

    }

  /**
   * Attempts to transforms a [[CloseableHttpResponse]] to a [[NamedGraphDataResponse]].
   */
  private def returnGraphDataAsTurtle(graphIri: IRI)(response: CloseableHttpResponse): Task[NamedGraphDataResponse] =
    Option(response.getEntity) match {
      case None => ZIO.fail(TriplestoreResponseException(s"Triplestore returned no content for graph $graphIri"))
      case Some(responseEntity: HttpEntity) =>
        ZIO
          .attempt(EntityUtils.toString(responseEntity, StandardCharsets.UTF_8))
          .flatMap(entity =>
            ZIO.succeed(
              NamedGraphDataResponse(
                turtle = entity
              )
            )
          )

    }

  /**
   * Attempts to transforms a [[CloseableHttpResponse]] to a [[RepositoryUploadedResponse]].
   */
  private def returnUploadResponse: CloseableHttpResponse => Task[RepositoryUploadedResponse] =
    _ => ZIO.succeed(RepositoryUploadedResponse())

  /**
   * Attempts to transforms a [[CloseableHttpResponse]] to a [[InsertGraphDataContentResponse]].
   */
  private def returnInsertGraphDataResponse(
    graphName: String
  )(response: CloseableHttpResponse): Task[InsertGraphDataContentResponse] =
    Option(response.getEntity) match {
      case None    => ZIO.fail(TriplestoreResponseException(s"$graphName could not be inserted into Triplestore."))
      case Some(_) => ZIO.succeed(InsertGraphDataContentResponse())
    }

  /**
   * Writes an HTTP response the response is written as-is to the output file.
   *
   * @param outputFile             the output file.
   * @param response               the response to be read.
   * @return a [[FileWrittenResponse]].
   */
  private def writeResponseFileAsPlainContent(
    outputFile: Path
  )(response: CloseableHttpResponse): Task[FileWrittenResponse] =
    Option(response.getEntity) match {
      case Some(responseEntity: HttpEntity) =>
        {
          ZIO.attempt {
            // Stream the HTTP entity directly to the output file.
            Files.copy(responseEntity.getContent, outputFile)
          }
        } *> ZIO.succeed(FileWrittenResponse())

      case None =>
        val error = TriplestoreResponseException(s"Triplestore returned no content for for repository dump")
        ZIO.logError(error.toString) *>
          ZIO.fail(error)
    }

  /**
   * Writes an HTTP response to a file, where the response is parsed as Turtle
   * and converted to the output format, with the graph IRI added to each statement.
   *
   * @param outputFile        the output file.
   * @param graphIri           the IRI of the graph used in the output.
   * @param quadFormat         the output format.
   * @param response           the response to be read.
   * @return a [[FileWrittenResponse]].
   */
  private def writeResponseFileAsTurtleContent(
    outputFile: Path,
    graphIri: IRI,
    quadFormat: QuadFormat
  )(response: CloseableHttpResponse): Task[FileWrittenResponse] =
    Option(response.getEntity) match {
      case Some(responseEntity: HttpEntity) =>
        ZIO.attempt {
          // Yes. Stream the HTTP entity to a temporary Turtle file.
          val tempTurtleFile = Paths.get(outputFile.toString + ".ttl")
          Files.copy(responseEntity.getContent, tempTurtleFile, StandardCopyOption.REPLACE_EXISTING)

          // Convert the Turtle to the output format.

          val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil()

          rdfFormatUtil.turtleToQuadsFile(
            rdfSource = RdfInputStreamSource(new BufferedInputStream(Files.newInputStream(tempTurtleFile))),
            graphIri = graphIri,
            outputFile = outputFile,
            outputFormat = quadFormat
          )

          Files.delete(tempTurtleFile)

          FileWrittenResponse()
        }

      case None =>
        val message = s"Triplestore returned no content for graph $graphIri"
        val error   = TriplestoreResponseException(message)
        ZIO.logError(error.toString) *>
          ZIO.fail(error)
    }

}

object TriplestoreServiceLive {

  /**
   * Acquires a configured httpClient, backed by a connection pool,
   * to be used in communicating with Fuseki.
   */
  private def acquire(config: AppConfig) = {
    ZIO.attemptBlocking {

      // timeout from config
      val sipiTimeoutMillis: Int = config.sipi.timeout.toMillis.toInt

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
      connManager.setDefaultMaxPerRoute(15)

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
    }.logError.orDie
  } <* ZIO.logInfo(">>> Acquire Triplestore Service Http Connector <<<")

  /**
   * Releases the httpClient, freeing all resources.
   */
  private def release(httpClient: CloseableHttpClient): URIO[Any, Unit] = {
    ZIO.attemptBlocking {
      httpClient.close()
    }.logError.ignore
  } <* ZIO.logInfo(">>> Release Triplestore Service Http Connector <<<")

  val layer: ZLayer[AppConfig with StringFormatter, Nothing, TriplestoreService] =
    ZLayer.scoped {
      for {
        sf         <- ZIO.service[StringFormatter]
        config     <- ZIO.service[AppConfig]
        httpClient <- ZIO.acquireRelease(acquire(config))(release)
      } yield TriplestoreServiceLive(config, httpClient, sf)
    }
}
