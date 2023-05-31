/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v1

import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpCharsets
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling.Unmarshal
import org.scalatest.DoNotDiscover
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import org.xmlunit.diff.Diff
import spray.json._

import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.xml._
import scala.xml.transform.RewriteRule
import scala.xml.transform.RuleTransformer

import dsp.errors.AssertionException
import dsp.errors.InvalidApiJsonException
import org.knora.webapi._
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.v2.routing.authenticationmessages.AuthenticationV2JsonProtocol
import org.knora.webapi.messages.v2.routing.authenticationmessages.LoginResponse
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.testservices.FileToUpload
import org.knora.webapi.util.FileUtil
import org.knora.webapi.util.MutableTestIri

/**
 * End-to-End (E2E) test specification for testing Knora-Sipi integration.
 */
@DoNotDiscover
class KnoraSipiIntegrationV1ITSpec
    extends ITKnoraLiveSpec
    with AuthenticationV2JsonProtocol
    with TriplestoreJsonProtocol {

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  private val userEmail               = SharedTestDataADM.rootUser.email
  private val password                = SharedTestDataADM.testPass
  private val pathToChlaus            = Paths.get("..", "test_data/test_route/images/Chlaus.jpg")
  private val pathToMarbles           = Paths.get("..", "test_data/test_route/images/marbles.tif")
  private val pathToXSLTransformation = Paths.get("..", "test_data/test_route/texts/letterToHtml.xsl")
  private val pathToMappingWithXSLT =
    Paths.get("..", "test_data/test_route/texts/mappingForLetterWithXSLTransformation.xml")
  private val secondPageIri = new MutableTestIri

  private val pathToBEOLBodyXSLTransformation   = Paths.get("..", "test_data/test_route/texts/beol/standoffToTEI.xsl")
  private val pathToBEOLStandoffTEIMapping      = Paths.get("..", "test_data/test_route/texts/beol/BEOLTEIMapping.xml")
  private val pathToBEOLHeaderXSLTransformation = Paths.get("..", "test_data/test_route/texts/beol/header.xsl")
  private val pathToBEOLGravsearchTemplate      = Paths.get("..", "test_data/test_route/texts/beol/gravsearch.txt")
  private val pathToBEOLLetterMapping           = Paths.get("..", "test_data/test_route/texts/beol/testLetter/beolMapping.xml")
  private val pathToBEOLBulkXML                 = Paths.get("..", "test_data/test_route/texts/beol/testLetter/bulk.xml")
  private val letterIri                         = new MutableTestIri
  private val gravsearchTemplateIri             = new MutableTestIri

  private val pdfResourceIri   = new MutableTestIri
  private val zipResourceIri   = new MutableTestIri
  private val wavResourceIri   = new MutableTestIri
  private val videoResourceIri = new MutableTestIri

  private val minimalPdfOriginalFilename = "minimal.pdf"
  private val pathToMinimalPdf           = Paths.get("..", s"test_data/test_route/files/$minimalPdfOriginalFilename")

  private val testPdfOriginalFilename = "test.pdf"
  private val pathToTestPdf           = Paths.get("..", s"test_data/test_route/files/$testPdfOriginalFilename")

  private val minimalZipOriginalFilename = "minimal.zip"
  private val pathToMinimalZip           = Paths.get("..", s"test_data/test_route/files/$minimalZipOriginalFilename")

  private val testZipOriginalFilename = "test.zip"
  private val pathToTestZip           = Paths.get("..", s"test_data/test_route/files/$testZipOriginalFilename")

  private val minimalWavOriginalFilename = "minimal.wav"
  private val pathToMinimalWav           = Paths.get("..", s"test_data/test_route/files/$minimalWavOriginalFilename")

  private val testWavOriginalFilename = "test.wav"
  private val pathToTestWav           = Paths.get("..", s"test_data/test_route/files/$testWavOriginalFilename")

  private val testVideoOriginalFilename = "testVideo.mp4"
  private val pathToTestVideo           = Paths.get("..", s"test_data/test_route/files/$testVideoOriginalFilename")

  private val testVideo2OriginalFilename = "testVideo2.mp4"
  private val pathToTestVideo2           = Paths.get("..", s"test_data/test_route/files/$testVideoOriginalFilename")

  /**
   * Adds the IRI of a XSL transformation to the given mapping.
   *
   * @param mapping the mapping to be updated.
   * @param xsltIri the Iri of the XSLT to be added.
   * @return the updated mapping.
   */
  private def addXSLTIriToMapping(mapping: String, xsltIri: String): String = {

    val mappingXML: Elem = XML.loadString(mapping)

    // add the XSL transformation's Iri to the mapping XML (replacing the string 'toBeDefined')
    val rule: RewriteRule = new RewriteRule() {
      override def transform(node: Node): Node =
        node match {
          case e: Elem if e.label == "defaultXSLTransformation" =>
            e.copy(child = e.child collect { case Text(_) =>
              Text(xsltIri)
            })
          case other => other
        }

    }

    val transformer = new RuleTransformer(rule)

    // apply transformer
    val transformed: Node = transformer(mappingXML)

    val xsltEle: NodeSeq = transformed \ "defaultXSLTransformation"

    if (xsltEle.size != 1 || xsltEle.head.text != xsltIri)
      throw AssertionException("XSLT Iri was not updated as expected")

    transformed.toString
  }

  /**
   * Given the id originally provided by the client, gets the generated IRI from a bulk import response.
   *
   * @param bulkResponse the response from the bulk import route.
   * @param clientID     the client id to look for.
   * @return the Knora IRI of the resource.
   */
  private def getResourceIriFromBulkResponse(bulkResponse: JsObject, clientID: String): String = {
    val resIriOption: Option[JsValue] = bulkResponse.fields.get("createdResources") match {
      case Some(createdResources: JsArray) =>
        createdResources.elements.find {
          case res: JsObject =>
            res.fields.get("clientResourceID") match {
              case Some(JsString(id)) if id == clientID => true
              case _                                    => false
            }
          case _ => false
        }

      case _ => throw InvalidApiJsonException("bulk import response should have member 'createdResources'")
    }

    if (resIriOption.nonEmpty) {
      resIriOption.get match {
        case res: JsObject =>
          res.fields.get("resourceIri") match {
            case Some(JsString(resIri)) =>
              resIri

            case _ => throw InvalidApiJsonException("expected client IRI for letter could not be found")
          }

        case _ => throw InvalidApiJsonException("expected client IRI for letter could not be found")
      }
    } else {
      throw InvalidApiJsonException("expected client IRI for letter could not be found")
    }
  }

  private def getLoginToken() = {
    val params =
      s"""
         |{
         |    "email": "$userEmail",
         |    "password": "$password"
         |}
              """.stripMargin
    val loginResponse: HttpResponse = singleAwaitingRequest(
      Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
    )
    assert(loginResponse.status == StatusCodes.OK)
    Await.result(Unmarshal(loginResponse.entity).to[LoginResponse], 1.seconds).token
  }

  "Knora and Sipi" should {
    lazy val loginToken: String = getLoginToken()

    "log in as a Knora user" in {
      loginToken.nonEmpty should be(true)
    }

    "create an 'incunabula:page' with parameters" in {
      // Upload the image to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(FileToUpload(path = pathToChlaus, mimeType = org.apache.http.entity.ContentType.IMAGE_TIFF))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head

      val knoraParams =
        s"""
           |{
           |    "restype_id": "http://www.knora.org/ontology/0803/incunabula#page",
           |    "properties": {
           |        "http://www.knora.org/ontology/0803/incunabula#pagenum": [
           |            {"richtext_value": {"utf8str": "test page"}}
           |        ],
           |        "http://www.knora.org/ontology/0803/incunabula#origname": [
           |            {"richtext_value": {"utf8str": "Chlaus"}}
           |        ],
           |        "http://www.knora.org/ontology/0803/incunabula#partOf": [
           |            {"link_value": "http://rdfh.ch/0803/5e77e98d2603"}
           |        ],
           |        "http://www.knora.org/ontology/0803/incunabula#seqnum": [{"int_value": 99999999}]
           |    },
           |    "file": "${uploadedFile.internalFilename}",
           |    "label": "test page",
           |    "project_id": "http://rdfh.ch/projects/0803"
           |}
                """.stripMargin

      // Send the JSON in a POST request to the Knora API server.
      val knoraPostRequest =
        Post(baseApiUrl + "/v1/resources", HttpEntity(ContentTypes.`application/json`, knoraParams)) ~> addCredentials(
          BasicHttpCredentials(userEmail, password)
        )
      val knoraPostResponseJson = getResponseJson(knoraPostRequest)

      // Get the IRI of the newly created resource.
      val resourceIri: String = knoraPostResponseJson.fields("res_id").asInstanceOf[JsString].value
      secondPageIri.set(resourceIri)

      // Request the resource from the Knora API server.
      val knoraRequestNewResource = Get(
        baseApiUrl + "/v1/resources/" + URLEncoder.encode(resourceIri, "UTF-8")
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      checkResponseOK(knoraRequestNewResource)
    }

    "change an 'incunabula:page' with parameters" in {
      // Upload the image to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToMarbles, mimeType = org.apache.http.entity.ContentType.IMAGE_TIFF))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head

      // JSON describing the new image to Knora.
      val knoraParams = JsObject(
        Map(
          "file" -> JsString(s"${uploadedFile.internalFilename}")
        )
      )

      // Send the JSON in a PUT request to the Knora API server.
      val knoraPutRequest = Put(
        baseApiUrl + "/v1/filevalue/" + URLEncoder.encode(secondPageIri.get, "UTF-8"),
        HttpEntity(ContentTypes.`application/json`, knoraParams.compactPrint)
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      checkResponseOK(knoraPutRequest)
    }

    "create an 'anything:thing'" in {
      val standoffXml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<text>
          |    <u><strong>Wild thing</strong></u>, <u>you make my</u> <a class="salsah-link" href="http://rdfh.ch/0803/9935159f67">heart</a> sing
          |</text>
                """.stripMargin

      val knoraParams = JsObject(
        Map(
          "restype_id" -> JsString("http://www.knora.org/ontology/0001/anything#Thing"),
          "label"      -> JsString("Wild thing"),
          "project_id" -> JsString("http://rdfh.ch/projects/0001"),
          "properties" -> JsObject(
            Map(
              "http://www.knora.org/ontology/0001/anything#hasText" -> JsArray(
                JsObject(
                  Map(
                    "richtext_value" -> JsObject(
                      "xml"        -> JsString(standoffXml),
                      "mapping_id" -> JsString("http://rdfh.ch/standoff/mappings/StandardMapping")
                    )
                  )
                )
              ),
              "http://www.knora.org/ontology/0001/anything#hasInteger" -> JsArray(
                JsObject(
                  Map(
                    "int_value" -> JsNumber(12345)
                  )
                )
              )
            )
          )
        )
      )

      // Send the JSON in a POST request to the Knora API server.
      val knoraPostRequest = Post(
        baseApiUrl + "/v1/resources",
        HttpEntity(ContentTypes.`application/json`, knoraParams.compactPrint)
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      checkResponseOK(knoraPostRequest)
    }

    "create an 'p0803-incunabula:book' and an 'p0803-incunabula:page' with file parameters via XML import" in {
      // To be able to run packaged tests inside Docker, we need to copy
      // the file first to a place which is shared with sipi
      val dest: Path = FileUtil.createTempFile(Some("jpg"), appConfig)
      Files.copy(pathToChlaus, dest, StandardCopyOption.REPLACE_EXISTING)

      // Upload the image to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(FileToUpload(path = dest, mimeType = org.apache.http.entity.ContentType.IMAGE_TIFF))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head

      val knoraParams =
        s"""<?xml version="1.0" encoding="UTF-8"?>
           |<knoraXmlImport:resources xmlns="http://api.knora.org/ontology/0803/incunabula/xml-import/v1#"
           |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           |    xsi:schemaLocation="http://api.knora.org/ontology/0803/incunabula/xml-import/v1# p0803-incunabula.xsd"
           |    xmlns:p0803-incunabula="http://api.knora.org/ontology/0803/incunabula/xml-import/v1#"
           |    xmlns:knoraXmlImport="http://api.knora.org/ontology/knoraXmlImport/v1#">
           |    <p0803-incunabula:book id="test_book">
           |        <knoraXmlImport:label>a book with one page</knoraXmlImport:label>
           |        <p0803-incunabula:title knoraType="richtext_value">the title of a book with one page</p0803-incunabula:title>
           |    </p0803-incunabula:book>
           |    <p0803-incunabula:page id="test_page">
           |        <knoraXmlImport:label>a page with an image</knoraXmlImport:label>
           |        <knoraXmlImport:file filename="${uploadedFile.internalFilename}"/>
           |        <p0803-incunabula:origname knoraType="richtext_value">Chlaus</p0803-incunabula:origname>
           |        <p0803-incunabula:pagenum knoraType="richtext_value">1a</p0803-incunabula:pagenum>
           |        <p0803-incunabula:partOf>
           |            <p0803-incunabula:book knoraType="link_value" linkType="ref" target="test_book"/>
           |        </p0803-incunabula:partOf>
           |        <p0803-incunabula:seqnum knoraType="int_value">1</p0803-incunabula:seqnum>
           |    </p0803-incunabula:page>
           |</knoraXmlImport:resources>""".stripMargin

      val projectIri = URLEncoder.encode("http://rdfh.ch/projects/0803", "UTF-8")

      // Send the JSON in a POST request to the Knora API server.
      val knoraPostRequest: HttpRequest = Post(
        baseApiUrl + s"/v1/resources/xmlimport/$projectIri",
        HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), knoraParams)
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      val knoraPostResponseJson: JsObject = getResponseJson(knoraPostRequest)

      val createdResources = knoraPostResponseJson.fields("createdResources").asInstanceOf[JsArray].elements
      assert(createdResources.size == 2)

      val bookResourceIri = createdResources.head.asJsObject.fields("resourceIri").asInstanceOf[JsString].value
      val pageResourceIri = createdResources(1).asJsObject.fields("resourceIri").asInstanceOf[JsString].value

      // Request the book resource from the Knora API server.
      val knoraRequestNewBookResource = Get(
        baseApiUrl + "/v1/resources/" + URLEncoder.encode(bookResourceIri, "UTF-8")
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      checkResponseOK(knoraRequestNewBookResource)

      // Request the page resource from the Knora API server.
      val knoraRequestNewPageResource = Get(
        baseApiUrl + "/v1/resources/" + URLEncoder.encode(pageResourceIri, "UTF-8")
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      val pageJson: JsObject = getResponseJson(knoraRequestNewPageResource)
      val locdata            = pageJson.fields("resinfo").asJsObject.fields("locdata").asJsObject
      val origname           = locdata.fields("origname").asInstanceOf[JsString].value
      val imageUrl =
        locdata.fields("path").asInstanceOf[JsString].value.replace("http://0.0.0.0:1024", baseInternalSipiUrl)
      assert(origname == dest.getFileName.toString)

      // Request the file from Sipi.
      val sipiGetRequest = Get(imageUrl) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      checkResponseOK(sipiGetRequest)
    }

    "create a TextRepresentation of type XSLTransformation and refer to it in a mapping" in {
      // Upload the XSLT file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(
            path = pathToXSLTransformation,
            mimeType = org.apache.http.entity.ContentType.APPLICATION_XML
          )
        )
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head

      // Create a resource for the XSL transformation.
      val knoraParams = JsObject(
        Map(
          "restype_id" -> JsString("http://www.knora.org/ontology/knora-base#XSLTransformation"),
          "label"      -> JsString("XSLT"),
          "project_id" -> JsString("http://rdfh.ch/projects/0001"),
          "properties" -> JsObject(),
          "file"       -> JsString(uploadedFile.internalFilename)
        )
      )

      // Send the JSON in a POST request to the Knora API server.
      val knoraPostRequest: HttpRequest = Post(
        baseApiUrl + "/v1/resources",
        HttpEntity(ContentTypes.`application/json`, knoraParams.compactPrint)
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      val responseJson: JsObject = getResponseJson(knoraPostRequest)

      // get the Iri of the XSL transformation
      val resId: String = responseJson.fields.get("res_id") match {
        case Some(JsString(resid: String)) => resid
        case _                             => throw InvalidApiJsonException("member 'res_id' was expected")
      }

      // add a mapping referring to the XSLT as the default XSL transformation

      val mapping = FileUtil.readTextFile(pathToMappingWithXSLT)

      val updatedMapping = addXSLTIriToMapping(mapping, resId)

      val paramsCreateLetterMappingFromXML =
        s"""
           |{
           |  "project_id": "http://rdfh.ch/projects/0001",
           |  "label": "mapping for letters with XSLT",
           |  "mappingName": "LetterMappingXSLT"
           |}
             """.stripMargin

      // create a mapping referring to the XSL transformation
      val formDataMapping = Multipart.FormData(
        Multipart.FormData.BodyPart(
          "json",
          HttpEntity(ContentTypes.`application/json`, paramsCreateLetterMappingFromXML)
        ),
        Multipart.FormData.BodyPart(
          "xml",
          HttpEntity(ContentTypes.`text/xml(UTF-8)`, updatedMapping),
          Map("filename" -> "mapping.xml")
        )
      )

      // send mapping xml to route
      val knoraPostRequest2 =
        Post(baseApiUrl + "/v1/mapping", formDataMapping) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      checkResponseOK(knoraPostRequest2)

    }

    "create a sample BEOL letter" in {
      val mapping = FileUtil.readTextFile(pathToBEOLLetterMapping)

      val paramsForMapping =
        s"""
           |{
           |  "project_id": "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF",
           |  "label": "mapping for BEOL letter",
           |  "mappingName": "BEOLMapping"
           |}
             """.stripMargin

      // create a mapping referring to the XSL transformation
      val formDataMapping = Multipart.FormData(
        Multipart.FormData.BodyPart(
          "json",
          HttpEntity(ContentTypes.`application/json`, paramsForMapping)
        ),
        Multipart.FormData.BodyPart(
          "xml",
          HttpEntity(ContentTypes.`text/xml(UTF-8)`, mapping),
          Map("filename" -> "mapping.xml")
        )
      )

      // send mapping xml to route
      val knoraPostRequest =
        Post(baseApiUrl + "/v1/mapping", formDataMapping) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      getResponseJson(knoraPostRequest)

      // create a letter via bulk import

      val bulkXML = FileUtil.readTextFile(pathToBEOLBulkXML)

      val bulkRequest = Post(
        baseApiUrl + "/v1/resources/xmlimport/" + URLEncoder
          .encode("http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF", "UTF-8"),
        HttpEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), bulkXML)
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      val bulkResponse: JsObject = getResponseJson(bulkRequest)

      letterIri.set(getResourceIriFromBulkResponse(bulkResponse, "testletter"))

    }

    "create a mapping for standoff conversion to TEI referring to an XSLT and also create a Gravsearch template and an XSLT for transforming TEI header data" in {
      // Upload the body XSLT file to Sipi.
      val bodyXsltUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(
            path = pathToBEOLBodyXSLTransformation,
            mimeType = org.apache.http.entity.ContentType.APPLICATION_XML
          )
        )
      )

      val uploadedBodyXsltFile: SipiUploadResponseEntry = bodyXsltUploadResponse.uploadedFiles.head

      // Create a resource for the XSL transformation.
      val bodyXsltParams = JsObject(
        Map(
          "restype_id" -> JsString("http://www.knora.org/ontology/knora-base#XSLTransformation"),
          "label"      -> JsString("XSLT"),
          "project_id" -> JsString("http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF"),
          "properties" -> JsObject(),
          "file"       -> JsString(uploadedBodyXsltFile.internalFilename)
        )
      )

      // Send the JSON in a POST request to the Knora API server.
      val bodyXSLTRequest: HttpRequest = Post(
        baseApiUrl + "/v1/resources",
        HttpEntity(ContentTypes.`application/json`, bodyXsltParams.compactPrint)
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      val bodyXSLTJson: JsObject = getResponseJson(bodyXSLTRequest)

      // get the Iri of the body XSL transformation
      val resId: String = bodyXSLTJson.fields.get("res_id") match {
        case Some(JsString(resid: String)) => resid
        case _                             => throw InvalidApiJsonException("member 'res_id' was expected")
      }

      // add a mapping referring to the XSLT as the default XSL transformation

      val mapping = FileUtil.readTextFile(pathToBEOLStandoffTEIMapping)

      val updatedMapping = addXSLTIriToMapping(mapping, resId)

      val paramsCreateLetterMappingFromXML =
        s"""
           |{
           |  "project_id": "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF",
           |  "label": "mapping for BEOL to TEI",
           |  "mappingName": "BEOLToTEI"
           |}
             """.stripMargin

      // create a mapping referring to the XSL transformation
      val formDataMapping = Multipart.FormData(
        Multipart.FormData.BodyPart(
          "json",
          HttpEntity(ContentTypes.`application/json`, paramsCreateLetterMappingFromXML)
        ),
        Multipart.FormData.BodyPart(
          "xml",
          HttpEntity(ContentTypes.`text/xml(UTF-8)`, updatedMapping),
          Map("filename" -> "mapping.xml")
        )
      )

      // send mapping xml to route
      val mappingRequest =
        Post(baseApiUrl + "/v1/mapping", formDataMapping) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      getResponseJson(mappingRequest)

      // Upload a Gravsearch template to Sipi.
      val gravsearchTemplateUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(
            path = pathToBEOLGravsearchTemplate,
            mimeType = org.apache.http.entity.ContentType.TEXT_PLAIN
          )
        )
      )

      val uploadedGravsearchTemplateFile: SipiUploadResponseEntry = gravsearchTemplateUploadResponse.uploadedFiles.head

      val gravsearchTemplateParams = JsObject(
        Map(
          "restype_id" -> JsString("http://www.knora.org/ontology/knora-base#TextRepresentation"),
          "label"      -> JsString("BEOL Gravsearch template"),
          "project_id" -> JsString("http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF"),
          "properties" -> JsObject(),
          "file"       -> JsString(uploadedGravsearchTemplateFile.internalFilename)
        )
      )

      // Send the JSON in a POST request to the Knora API server.
      val gravsearchTemplateRequest: HttpRequest = Post(
        baseApiUrl + "/v1/resources",
        HttpEntity(ContentTypes.`application/json`, gravsearchTemplateParams.compactPrint)
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      val gravsearchTemplateJSON: JsObject = getResponseJson(gravsearchTemplateRequest)

      gravsearchTemplateIri.set(gravsearchTemplateJSON.fields.get("res_id") match {
        case Some(JsString(gravsearchIri)) => gravsearchIri
        case _                             => throw InvalidApiJsonException("expected IRI for Gravsearch template")
      })

      // Upload the header XSLT file to Sipi.
      val headerXsltUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(
            path = pathToBEOLHeaderXSLTransformation,
            mimeType = org.apache.http.entity.ContentType.APPLICATION_XML
          )
        )
      )

      val uploadedHeaderXsltFile: SipiUploadResponseEntry = headerXsltUploadResponse.uploadedFiles.head

      val headerXsltParams = JsObject(
        Map(
          "restype_id" -> JsString("http://www.knora.org/ontology/knora-base#XSLTransformation"),
          "label"      -> JsString("BEOL header XSLT"),
          "project_id" -> JsString("http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF"),
          "properties" -> JsObject(),
          "file"       -> JsString(uploadedHeaderXsltFile.internalFilename)
        )
      )

      // Send the JSON in a POST request to the Knora API server.
      val headerXSLTRequest: HttpRequest = Post(
        baseApiUrl + "/v1/resources",
        HttpEntity(ContentTypes.`application/json`, headerXsltParams.compactPrint)
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      val headerXSLTJson = getResponseJson(headerXSLTRequest)

      val headerXSLTIri: IRI = headerXSLTJson.fields.get("res_id") match {
        case Some(JsString(gravsearchIri)) => gravsearchIri
        case _                             => throw InvalidApiJsonException("expected IRI for header XSLT template")
      }

      // get tei TEI/XML representation of a beol:letter

      val letterTEIRequest: HttpRequest = Get(
        baseApiUrl + "/v2/tei/" + URLEncoder.encode(letterIri.get, "UTF-8") +
          "?textProperty=" + URLEncoder.encode("http://0.0.0.0:3333/ontology/0801/beol/v2#hasText", "UTF-8") +
          "&mappingIri=" + URLEncoder.encode(
            "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF/mappings/BEOLToTEI",
            "UTF-8"
          ) +
          "&gravsearchTemplateIri=" + URLEncoder.encode(gravsearchTemplateIri.get, "UTF-8") +
          "&teiHeaderXSLTIri=" + URLEncoder.encode(headerXSLTIri, "UTF-8")
      )

      val letterTEIResponse: HttpResponse = singleAwaitingRequest(letterTEIRequest)
      val letterResponseBodyFuture: Future[String] =
        letterTEIResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
      val letterResponseBodyXML: String = Await.result(letterResponseBodyFuture, 5.seconds)

      val xmlExpected =
        s"""<?xml version="1.0" encoding="UTF-8"?>
           |<TEI version="3.3.0" xmlns="http://www.tei-c.org/ns/1.0">
           |<teiHeader>
           |   <fileDesc>
           |      <titleStmt>
           |         <title>Testletter</title>
           |      </titleStmt>
           |      <publicationStmt>
           |         <p>This is the TEI/XML representation of the resource identified by the Iri
           |                        ${letterIri.get}.
           |                    </p>
           |      </publicationStmt>
           |      <sourceDesc>
           |         <p>Representation of the resource's text as TEI/XML</p>
           |      </sourceDesc>
           |   </fileDesc>
           |   <profileDesc>
           |      <correspDesc ref="${letterIri.get}">
           |         <correspAction type="sent">
           |            <persName ref="http://d-nb.info/gnd/118607308">Scheuchzer,
           |                Johann Jacob</persName>
           |            <date when="1703-06-10"/>
           |         </correspAction>
           |         <correspAction type="received">
           |            <persName ref="http://d-nb.info/gnd/119112450">Hermann,
           |                Jacob</persName>
           |         </correspAction>
           |      </correspDesc>
           |   </profileDesc>
           |</teiHeader>
           |
           |<text><body>
           |                <p>[...] Viro Clarissimo.</p>
           |                <p>Dn. Jacobo Hermanno S. S. M. C. </p>
           |                <p>et Ph. M.</p>
           |                <p>S. P. D. </p>
           |                <p>J. J. Sch.</p>
           |                <p>En quae desideras, vir Erud.<hi rend="sup">e</hi> κεχαρισμένω θυμῷ Actorum Lipsiensium fragmenta<note>Gemeint sind die im Brief Hermanns von 1703.06.05 erbetenen Exemplare AE Aprilis 1703 und AE Suppl., tom. III, 1702.</note> animi mei erga te prope[n]sissimi tenuia indicia. Dudum est, ex quo Tibi innotescere, et tuam ambire amicitiam decrevi, dudum, ex quo Ingenij Tui acumen suspexi, immo non potui quin admirarer pro eo, quod summam Demonstrationem Tuam de Iride communicare dignatus fueris summas ago grates; quamvis in hoc studij genere, non alias [siquid] μετρικώτατος, propter aliorum negotiorum continuam seriem non altos possim scandere gradus. Perge Vir Clariss. Erudito orbi propalare Ingenij Tui fructum; sed et me amare. </p>
           |                <p>d. [10] Jun. 1703.<note>Der Tag ist im Manuskript unleserlich. Da der Entwurf in Scheuchzers "Copiae epistolarum" zwischen zwei Einträgen vom 10. Juni 1703 steht, ist der Brief wohl auf den gleichen Tag zu datieren.</note>
           |                </p>
           |            </body></text>
           |</TEI>
                """.stripMargin

      val xmlDiff: Diff =
        DiffBuilder.compare(Input.fromString(letterResponseBodyXML)).withTest(Input.fromString(xmlExpected)).build()
      xmlDiff.hasDifferences should be(false)
    }

    "provide a helpful error message if an XSLT file is not found" in {
      val missingHeaderXSLTIri = "http://rdfh.ch/0801/608NfPLCRpeYnkXKABC5mg"

      val letterTEIRequest: HttpRequest = Get(
        baseApiUrl + "/v2/tei/" + URLEncoder.encode(letterIri.get, "UTF-8") +
          "?textProperty=" + URLEncoder.encode("http://0.0.0.0:3333/ontology/0801/beol/v2#hasText", "UTF-8") +
          "&mappingIri=" + URLEncoder.encode(
            "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF/mappings/BEOLToTEI",
            "UTF-8"
          ) +
          "&gravsearchTemplateIri=" + URLEncoder.encode(gravsearchTemplateIri.get, "UTF-8") +
          "&teiHeaderXSLTIri=" + URLEncoder.encode(missingHeaderXSLTIri, "UTF-8")
      )

      val response: HttpResponse = singleAwaitingRequest(letterTEIRequest)
      assert(response.status.intValue == 404)
      val responseBodyStr: String =
        Await.result(response.entity.toStrict(2.seconds).map(_.data.decodeString("UTF-8")), 2.seconds)
      assert(responseBodyStr.contains("Unable to get file"))
      assert(responseBodyStr.contains("as requested by org.knora.webapi.responders.v2.StandoffResponderV2"))
      assert(responseBodyStr.contains("Not Found"))
    }

    "create a resource with a PDF file attached" in {
      // Upload the PDF file to Sipi.
      val pdfUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(path = pathToMinimalPdf, mimeType = org.apache.http.entity.ContentType.create("application/pdf"))
        )
      )

      val uploadedPdfFile: SipiUploadResponseEntry = pdfUploadResponse.uploadedFiles.head
      uploadedPdfFile.originalFilename should ===(minimalPdfOriginalFilename)

      // Create a resource for the PDF file.
      val createDocumentResourceParams = JsObject(
        Map(
          "restype_id" -> JsString("http://www.knora.org/ontology/0001/anything#ThingDocument"),
          "label"      -> JsString("PDF file"),
          "project_id" -> JsString("http://rdfh.ch/projects/0001"),
          "properties" -> JsObject(),
          "file"       -> JsString(uploadedPdfFile.internalFilename)
        )
      )

      // Send the JSON in a POST request to the Knora API server.
      val createDocumentResourceRequest: HttpRequest = Post(
        baseApiUrl + "/v1/resources",
        HttpEntity(ContentTypes.`application/json`, createDocumentResourceParams.compactPrint)
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      val createDocumentResourceResponseJson: JsObject = getResponseJson(createDocumentResourceRequest)

      // get the IRI of the document file resource
      val resourceIri: String = createDocumentResourceResponseJson.fields.get("res_id") match {
        case Some(JsString(res_id: String)) => res_id
        case _                              => throw InvalidApiJsonException("member 'res_id' was expected")
      }

      pdfResourceIri.set(resourceIri)

      // Request the document resource from the Knora API server.
      val documentResourceRequest = Get(
        baseApiUrl + "/v1/resources/" + URLEncoder.encode(resourceIri, "UTF-8")
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      val documentResourceResponse: JsObject = getResponseJson(documentResourceRequest)
      val locdata                            = documentResourceResponse.fields("resinfo").asJsObject.fields("locdata").asJsObject
      val pdfUrl =
        locdata.fields("path").asInstanceOf[JsString].value.replace("http://0.0.0.0:1024", baseInternalSipiUrl)

      // Request the file from Sipi.
      val sipiGetRequest = Get(pdfUrl) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      checkResponseOK(sipiGetRequest)
    }

    "change the PDF file attached to a resource" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(path = pathToTestPdf, mimeType = org.apache.http.entity.ContentType.create("application/pdf"))
        )
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testPdfOriginalFilename)

      // JSON describing the new file to Knora.
      val knoraParams = JsObject(
        Map(
          "file" -> JsString(s"${uploadedFile.internalFilename}")
        )
      )

      // Send the JSON in a PUT request to the Knora API server.
      val knoraPutRequest = Put(
        baseApiUrl + "/v1/filevalue/" + URLEncoder.encode(pdfResourceIri.get, "UTF-8"),
        HttpEntity(ContentTypes.`application/json`, knoraParams.compactPrint)
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      checkResponseOK(knoraPutRequest)

      // Request the document resource from the Knora API server.
      val documentResourceRequest = Get(
        baseApiUrl + "/v1/resources/" + URLEncoder.encode(pdfResourceIri.get, "UTF-8")
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      getResponseJson(documentResourceRequest)
    }

    "create a resource with a Zip file attached" in {
      // Upload the Zip file to Sipi.
      val zipUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(path = pathToMinimalZip, mimeType = org.apache.http.entity.ContentType.create("application/zip"))
        )
      )

      val uploadedZipFile: SipiUploadResponseEntry = zipUploadResponse.uploadedFiles.head
      uploadedZipFile.originalFilename should ===(minimalZipOriginalFilename)

      // Create a resource for the Zip file.
      val archiveResourceParams = JsObject(
        Map(
          "restype_id" -> JsString("http://www.knora.org/ontology/knora-base#ArchiveRepresentation"),
          "label"      -> JsString("Zip file"),
          "project_id" -> JsString("http://rdfh.ch/projects/0001"),
          "properties" -> JsObject(),
          "file"       -> JsString(uploadedZipFile.internalFilename)
        )
      )

      // Send the JSON in a POST request to the Knora API server.
      val createDocumentResourceRequest: HttpRequest = Post(
        baseApiUrl + "/v1/resources",
        HttpEntity(ContentTypes.`application/json`, archiveResourceParams.compactPrint)
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      val createDocumentResourceResponseJson: JsObject = getResponseJson(createDocumentResourceRequest)

      // get the IRI of the document file resource
      val resourceIri: String = createDocumentResourceResponseJson.fields.get("res_id") match {
        case Some(JsString(res_id: String)) => res_id
        case _                              => throw InvalidApiJsonException("member 'res_id' was expected")
      }

      zipResourceIri.set(resourceIri)

      // Request the document resource from the Knora API server.
      val documentResourceRequest = Get(
        baseApiUrl + "/v1/resources/" + URLEncoder.encode(resourceIri, "UTF-8")
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      val documentResourceResponse: JsObject = getResponseJson(documentResourceRequest)
      val locdata                            = documentResourceResponse.fields("resinfo").asJsObject.fields("locdata").asJsObject
      val zipUrl =
        locdata.fields("path").asInstanceOf[JsString].value.replace("http://0.0.0.0:1024", baseInternalSipiUrl)

      // Request the file from Sipi.
      val sipiGetRequest = Get(zipUrl) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      checkResponseOK(sipiGetRequest)
    }

    "change the Zip file attached to a resource" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(path = pathToTestZip, mimeType = org.apache.http.entity.ContentType.create("application/zip"))
        )
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testZipOriginalFilename)

      // JSON describing the new file to Knora.
      val knoraParams = JsObject(
        Map(
          "file" -> JsString(s"${uploadedFile.internalFilename}")
        )
      )

      // Send the JSON in a PUT request to the Knora API server.
      val knoraPutRequest = Put(
        baseApiUrl + "/v1/filevalue/" + URLEncoder.encode(zipResourceIri.get, "UTF-8"),
        HttpEntity(ContentTypes.`application/json`, knoraParams.compactPrint)
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      checkResponseOK(knoraPutRequest)

      // Request the document resource from the Knora API server.
      val documentResourceRequest = Get(
        baseApiUrl + "/v1/resources/" + URLEncoder.encode(zipResourceIri.get, "UTF-8")
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      val documentResourceResponse: JsObject = getResponseJson(documentResourceRequest)
      val locdata                            = documentResourceResponse.fields("resinfo").asJsObject.fields("locdata").asJsObject
      val zipUrl =
        locdata.fields("path").asInstanceOf[JsString].value.replace("http://0.0.0.0:1024", baseInternalSipiUrl)

      // Request the file from Sipi.
      val sipiGetRequest = Get(zipUrl) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      checkResponseOK(sipiGetRequest)
    }

    "create a resource with a WAV file attached" in {
      // Upload the WAV file to Sipi.
      val zipUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToMinimalWav, mimeType = org.apache.http.entity.ContentType.create("audio/wav")))
      )

      val uploadedWavFile: SipiUploadResponseEntry = zipUploadResponse.uploadedFiles.head
      uploadedWavFile.originalFilename should ===(minimalWavOriginalFilename)

      // Create a resource for the WAV file.
      val createAudioResourceParams = JsObject(
        Map(
          "restype_id" -> JsString("http://www.knora.org/ontology/knora-base#AudioRepresentation"),
          "label"      -> JsString("Wav file"),
          "project_id" -> JsString("http://rdfh.ch/projects/0001"),
          "properties" -> JsObject(),
          "file"       -> JsString(uploadedWavFile.internalFilename)
        )
      )

      // Send the JSON in a POST request to the Knora API server.
      val createAudioResourceRequest: HttpRequest = Post(
        baseApiUrl + "/v1/resources",
        HttpEntity(ContentTypes.`application/json`, createAudioResourceParams.compactPrint)
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      val createAudioResourceResponseJson: JsObject = getResponseJson(createAudioResourceRequest)

      // get the IRI of the audio file resource
      val resourceIri: String = createAudioResourceResponseJson.fields.get("res_id") match {
        case Some(JsString(res_id: String)) => res_id
        case _                              => throw InvalidApiJsonException("member 'res_id' was expected")
      }

      wavResourceIri.set(resourceIri)

      // Request the audio file resource from the Knora API server.
      val audioResourceRequest = Get(
        baseApiUrl + "/v1/resources/" + URLEncoder.encode(resourceIri, "UTF-8")
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      val audioResourceResponse: JsObject = getResponseJson(audioResourceRequest)
      val locdata                         = audioResourceResponse.fields("resinfo").asJsObject.fields("locdata").asJsObject
      val zipUrl =
        locdata.fields("path").asInstanceOf[JsString].value.replace("http://0.0.0.0:1024", baseInternalSipiUrl)

      // Request the file from Sipi.
      val sipiGetRequest = Get(zipUrl) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      checkResponseOK(sipiGetRequest)
    }

    "change the WAV file attached to a resource" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToTestWav, mimeType = org.apache.http.entity.ContentType.create("audio/wav")))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testWavOriginalFilename)

      // JSON describing the new file to Knora.
      val knoraParams = JsObject(
        Map(
          "file" -> JsString(s"${uploadedFile.internalFilename}")
        )
      )

      // Send the JSON in a PUT request to the Knora API server.
      val knoraPutRequest = Put(
        baseApiUrl + "/v1/filevalue/" + URLEncoder.encode(wavResourceIri.get, "UTF-8"),
        HttpEntity(ContentTypes.`application/json`, knoraParams.compactPrint)
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      checkResponseOK(knoraPutRequest)

      // Request the document resource from the Knora API server.
      val audioResourceRequest = Get(
        baseApiUrl + "/v1/resources/" + URLEncoder.encode(wavResourceIri.get, "UTF-8")
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      val audioResourceResponse: JsObject = getResponseJson(audioResourceRequest)
      val locdata                         = audioResourceResponse.fields("resinfo").asJsObject.fields("locdata").asJsObject
      val wavUrl =
        locdata.fields("path").asInstanceOf[JsString].value.replace("http://0.0.0.0:1024", baseInternalSipiUrl)

      // Request the file from Sipi.
      val sipiGetRequest = Get(wavUrl) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      checkResponseOK(sipiGetRequest)
    }

    // TODO: activate the following two tests after video support is implemented in sipi
    "create a resource with a video file attached" ignore {
      // Upload the video file to Sipi.
      val zipUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToTestVideo, mimeType = org.apache.http.entity.ContentType.create("video/mp4")))
      )

      val uploadedVideoFile: SipiUploadResponseEntry = zipUploadResponse.uploadedFiles.head
      uploadedVideoFile.originalFilename should ===(testVideoOriginalFilename)

      // Create a resource for the video file.
      val createVideoResourceParams = JsObject(
        Map(
          "restype_id" -> JsString("http://www.knora.org/ontology/knora-base#MovingImageRepresentation"),
          "label"      -> JsString("Wav file"),
          "project_id" -> JsString("http://rdfh.ch/projects/0001"),
          "properties" -> JsObject(),
          "file"       -> JsString(uploadedVideoFile.internalFilename)
        )
      )

      // Send the JSON in a POST request to the Knora API server.
      val createVideoResourceRequest: HttpRequest = Post(
        baseApiUrl + "/v1/resources",
        HttpEntity(ContentTypes.`application/json`, createVideoResourceParams.compactPrint)
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      val createVideoResourceResponseJson: JsObject = getResponseJson(createVideoResourceRequest)

      // get the IRI of the audio file resource
      val resourceIri: String = createVideoResourceResponseJson.fields.get("res_id") match {
        case Some(JsString(res_id: String)) => res_id
        case _                              => throw InvalidApiJsonException("member 'res_id' was expected")
      }

      videoResourceIri.set(resourceIri)

      // Request the video file resource from the Knora API server.
      val videoResourceRequest = Get(
        baseApiUrl + "/v1/resources/" + URLEncoder.encode(resourceIri, "UTF-8")
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      val videoResourceResponse: JsObject = getResponseJson(videoResourceRequest)
      val locdata                         = videoResourceResponse.fields("resinfo").asJsObject.fields("locdata").asJsObject
      val videoUrl =
        locdata.fields("path").asInstanceOf[JsString].value.replace("http://0.0.0.0:1024", baseInternalSipiUrl)

      // Request the file from Sipi.
      val sipiGetRequest = Get(videoUrl) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      checkResponseOK(sipiGetRequest)
    }

    "change the video file attached to a resource" ignore {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToTestVideo2, mimeType = org.apache.http.entity.ContentType.create("video/mp4")))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testVideo2OriginalFilename)

      // JSON describing the new file to Knora.
      val knoraParams = JsObject(
        Map(
          "file" -> JsString(s"${uploadedFile.internalFilename}")
        )
      )

      // Send the JSON in a PUT request to the Knora API server.
      val knoraPutRequest = Put(
        baseApiUrl + "/v1/filevalue/" + URLEncoder.encode(wavResourceIri.get, "UTF-8"),
        HttpEntity(ContentTypes.`application/json`, knoraParams.compactPrint)
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      checkResponseOK(knoraPutRequest)

      // Request the document resource from the Knora API server.
      val videoResourceRequest = Get(
        baseApiUrl + "/v1/resources/" + URLEncoder.encode(videoResourceIri.get, "UTF-8")
      ) ~> addCredentials(BasicHttpCredentials(userEmail, password))

      val videoResourceResponse: JsObject = getResponseJson(videoResourceRequest)
      val locdata                         = videoResourceResponse.fields("resinfo").asJsObject.fields("locdata").asJsObject
      val videoUrl =
        locdata.fields("path").asInstanceOf[JsString].value.replace("http://0.0.0.0:1024", baseInternalSipiUrl)

      // Request the file from Sipi.
      val sipiGetRequest = Get(videoUrl) ~> addCredentials(BasicHttpCredentials(userEmail, password))
      checkResponseOK(sipiGetRequest)
    }
  }
}
