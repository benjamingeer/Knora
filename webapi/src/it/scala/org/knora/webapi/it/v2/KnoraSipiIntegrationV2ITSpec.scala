/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal

import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Paths
import scala.concurrent.Await
import scala.concurrent.duration._
import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.v2.routing.authenticationmessages._
import org.knora.webapi.models.filemodels._
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.testservices.FileToUpload
import org.knora.webapi.util.MutableTestIri

/**
 * Tests interaction between Knora and Sipi using Knora API v2.
 */
class KnoraSipiIntegrationV2ITSpec
    extends ITKnoraLiveSpec
    with AuthenticationV2JsonProtocol
    with TriplestoreJsonProtocol {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val anythingUserEmail   = SharedTestDataADM.anythingAdminUser.email
  private val incunabulaUserEmail = SharedTestDataADM.incunabulaMemberUser.email
  private val password            = SharedTestDataADM.testPass

  private val stillImageResourceIri  = new MutableTestIri
  private val stillImageFileValueIri = new MutableTestIri
  private val pdfResourceIri         = new MutableTestIri
  private val pdfValueIri            = new MutableTestIri
  private val xmlResourceIri         = new MutableTestIri
  private val xmlValueIri            = new MutableTestIri
  private val csvResourceIri         = new MutableTestIri
  private val csvValueIri            = new MutableTestIri
  private val zipResourceIri         = new MutableTestIri
  private val zipValueIri            = new MutableTestIri
  private val wavResourceIri         = new MutableTestIri
  private val wavValueIri            = new MutableTestIri

  private val videoResourceIri = new MutableTestIri
  private val videoValueIri    = new MutableTestIri

  private val marblesOriginalFilename = "marbles.tif"
  private val pathToMarbles           = Paths.get("..", s"test_data/test_route/images/$marblesOriginalFilename")
  private val marblesWidth            = 1419
  private val marblesHeight           = 1001

  private val pathToMarblesWithWrongExtension =
    Paths.get("..", "test_data/test_route/images/marbles_with_wrong_extension.jpg")

  private val jp2OriginalFilename = "67352ccc-d1b0-11e1-89ae-279075081939.jp2"
  private val pathToJp2           = Paths.get("..", s"test_data/test_route/images/$jp2OriginalFilename")

  private val trp88OriginalFilename = "Trp88.tiff"
  private val pathToTrp88           = Paths.get("..", s"test_data/test_route/images/$trp88OriginalFilename")
  private val trp88Width            = 499
  private val trp88Height           = 630

  private val minimalPdfOriginalFilename = "minimal.pdf"
  private val pathToMinimalPdf           = Paths.get("..", s"test_data/test_route/files/$minimalPdfOriginalFilename")

  private val testPdfOriginalFilename = "test.pdf"
  private val pathToTestPdf           = Paths.get("..", s"test_data/test_route/files/$testPdfOriginalFilename")

  private val csv1OriginalFilename = "eggs.csv"
  private val pathToCsv1           = Paths.get("..", s"test_data/test_route/files/$csv1OriginalFilename")

  private val csv2OriginalFilename = "spam.csv"
  private val pathToCsv2           = Paths.get("..", s"test_data/test_route/files/$csv2OriginalFilename")

  private val xml1OriginalFilename = "test1.xml"
  private val pathToXml1           = Paths.get("..", s"test_data/test_route/files/$xml1OriginalFilename")

  private val xml2OriginalFilename = "test2.xml"
  private val pathToXml2           = Paths.get("..", s"test_data/test_route/files/$xml2OriginalFilename")

  private val minimalZipOriginalFilename = "minimal.zip"
  private val pathToMinimalZip           = Paths.get("..", s"test_data/test_route/files/$minimalZipOriginalFilename")

  private val testZipOriginalFilename = "test.zip"
  private val pathToTestZip           = Paths.get("..", s"test_data/test_route/files/$testZipOriginalFilename")

  private val test7zOriginalFilename = "test.7z"
  private val pathToTest7z           = Paths.get("..", s"test_data/test_route/files/$test7zOriginalFilename")

  private val minimalWavOriginalFilename = "minimal.wav"
  private val pathToMinimalWav           = Paths.get("..", s"test_data/test_route/files/$minimalWavOriginalFilename")

  private val testWavOriginalFilename = "test.wav"
  private val pathToTestWav           = Paths.get("..", s"test_data/test_route/files/$testWavOriginalFilename")

  private val testVideoOriginalFilename = "testVideo.mp4"
  private val pathToTestVideo           = Paths.get("..", s"test_data/test_route/files/$testVideoOriginalFilename")

  private val testVideo2OriginalFilename = "testVideo2.mp4"
  private val pathToTestVideo2           = Paths.get("..", s"test_data/test_route/files/$testVideo2OriginalFilename")

  private val thingDocumentIRI = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingDocument"

  private val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)

  /**
   * Represents the information that Knora returns about an image file value that was created.
   *
   * @param internalFilename the image's internal filename.
   * @param iiifUrl          the image's IIIF URL.
   * @param width            the image's width in pixels.
   * @param height           the image's height in pixels.
   */
  case class SavedImage(internalFilename: String, iiifUrl: String, width: Int, height: Int)

  /**
   * Represents the information that Knora returns about a document file value that was created.
   *
   * @param internalFilename the files's internal filename.
   * @param url              the file's URL.
   * @param pageCount        the document's page count.
   * @param width            the document's width in pixels.
   * @param height           the document's height in pixels.
   */
  case class SavedDocument(
    internalFilename: String,
    url: String
  )

  /**
   * Represents the information that Knora returns about a text file value that was created.
   *
   * @param internalFilename the file's internal filename.
   * @param url              the file's URL.
   */
  case class SavedTextFile(internalFilename: String, url: String)

  /**
   * Represents the information that Knora returns about an audio file value that was created.
   *
   * @param internalFilename the file's internal filename.
   * @param url              the file's URL.
   */
  case class SavedAudioFile(internalFilename: String, url: String)

  /**
   * Represents the information that Knora returns about a video file value that was created.
   *
   * @param internalFilename the file's internal filename.
   * @param url              the file's URL.
   */
  case class SavedVideoFile(
    internalFilename: String,
    url: String
  )

  /**
   * Given a JSON-LD document representing a resource, returns a JSON-LD array containing the values of the specified
   * property.
   *
   * @param resource            the JSON-LD document.
   * @param propertyIriInResult the property IRI.
   * @return a JSON-LD array containing the values of the specified property.
   */
  private def getValuesFromResource(resource: JsonLDDocument, propertyIriInResult: SmartIri): JsonLDArray =
    resource.body.requireArray(propertyIriInResult.toString)

  /**
   * Given a JSON-LD document representing a resource, returns a JSON-LD object representing the expected single
   * value of the specified property.
   *
   * @param resource            the JSON-LD document.
   * @param propertyIriInResult the property IRI.
   * @param expectedValueIri    the IRI of the expected value.
   * @return a JSON-LD object representing the expected single value of the specified property.
   */
  private def getValueFromResource(
    resource: JsonLDDocument,
    propertyIriInResult: SmartIri,
    expectedValueIri: IRI
  ): JsonLDObject = {
    val resourceIri: IRI =
      resource.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
    val propertyValues: JsonLDArray =
      getValuesFromResource(resource = resource, propertyIriInResult = propertyIriInResult)

    val matchingValues: Seq[JsonLDObject] = propertyValues.value.collect {
      case jsonLDObject: JsonLDObject
          if jsonLDObject.requireStringWithValidation(
            JsonLDKeywords.ID,
            validationFun
          ) == expectedValueIri =>
        jsonLDObject
    }

    if (matchingValues.isEmpty) {
      throw AssertionException(
        s"Property <$propertyIriInResult> of resource <$resourceIri> does not have value <$expectedValueIri>"
      )
    }

    if (matchingValues.size > 1) {
      throw AssertionException(
        s"Property <$propertyIriInResult> of resource <$resourceIri> has more than one value with the IRI <$expectedValueIri>"
      )
    }

    matchingValues.head
  }

  /**
   * Given a JSON-LD object representing a Knora image file value, returns a [[SavedImage]] containing the same information.
   *
   * @param savedValue a JSON-LD object representing a Knora image file value.
   * @return a [[SavedImage]] containing the same information.
   */
  private def savedValueToSavedImage(savedValue: JsonLDObject): SavedImage = {
    val internalFilename = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename)

    val validationFun: (String, => Nothing) => String = (s, errorFun) =>
      Iri.toSparqlEncodedString(s).getOrElse(errorFun)
    val iiifUrl = savedValue.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
      expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
      validationFun
    )

    val width  = savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasDimX)
    val height = savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasDimY)

    SavedImage(
      internalFilename = internalFilename,
      iiifUrl = iiifUrl,
      width = width,
      height = height
    )
  }

  /**
   * Given a JSON-LD object representing a Knora document file value, returns a [[SavedDocument]] containing the same information.
   *
   * @param savedValue a JSON-LD object representing a Knora document file value.
   * @return a [[SavedDocument]] containing the same information.
   */
  private def savedValueToSavedDocument(savedValue: JsonLDObject): SavedDocument = {
    val internalFilename = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename)

    val validationFun: (String, => Nothing) => String = (s, errorFun) =>
      Iri.toSparqlEncodedString(s).getOrElse(errorFun)
    val url: String = savedValue.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
      expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
      validationFun
    )

    SavedDocument(
      internalFilename = internalFilename,
      url = url
    )
  }

  /**
   * Given a JSON-LD object representing a Knora text file value, returns a [[SavedTextFile]] containing the same information.
   *
   * @param savedValue a JSON-LD object representing a Knora document file value.
   * @return a [[SavedTextFile]] containing the same information.
   */
  private def savedValueToSavedTextFile(savedValue: JsonLDObject): SavedTextFile = {
    val internalFilename = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename)

    val validationFun: (String, => Nothing) => String = (s, errorFun) =>
      Iri.toSparqlEncodedString(s).getOrElse(errorFun)
    val url: String = savedValue.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
      expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
      validationFun
    )

    SavedTextFile(
      internalFilename = internalFilename,
      url = url
    )
  }

  /**
   * Given a JSON-LD object representing a Knora audio file value, returns a [[SavedAudioFile]] containing the same information.
   *
   * @param savedValue a JSON-LD object representing a Knora audio file value.
   * @return a [[SavedAudioFile]] containing the same information.
   */
  private def savedValueToSavedAudioFile(savedValue: JsonLDObject): SavedAudioFile = {
    val internalFilename = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename)

    val validationFun: (String, => Nothing) => String = (s, errorFun) =>
      Iri.toSparqlEncodedString(s).getOrElse(errorFun)
    val url: String = savedValue.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
      expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
      validationFun
    )

    SavedAudioFile(
      internalFilename = internalFilename,
      url = url
    )
  }

  /**
   * Given a JSON-LD object representing a Knora video file value, returns a [[SavedVideoFile]] containing the same information.
   *
   * @param savedValue a JSON-LD object representing a Knora video file value.
   * @return a [[SavedVideoFile]] containing the same information.
   */
  private def savedValueToSavedVideoFile(savedValue: JsonLDObject): SavedVideoFile = {
    val internalFilename = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename)
    val validationFun: (String, => Nothing) => String = (s, errorFun) =>
      Iri.toSparqlEncodedString(s).getOrElse(errorFun)
    val url: String = savedValue.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
      expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
      validationFun
    )

    SavedVideoFile(
      internalFilename = internalFilename,
      url = url
    )
  }

  "The Knora/Sipi integration" should {
    var loginToken: String = ""

    "log in as a Knora user" in {
      /* Correct username and correct password */

      val params =
        s"""
           |{
           |    "email": "$anythingUserEmail",
           |    "password": "$password"
           |}
                """.stripMargin

      val request                = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK)

      val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)
      loginToken = lr.token

      loginToken.nonEmpty should be(true)
    }

    "create a resource with a still image file" in {
      // Upload the image to Sipi.
      val sipiUploadResponse: SipiUploadResponse =
        uploadToSipi(
          loginToken = loginToken,
          filesToUpload =
            Seq(FileToUpload(path = pathToMarbles, mimeType = org.apache.http.entity.ContentType.IMAGE_TIFF))
        )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(marblesOriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(
          fileType = FileType.StillImageFile(),
          internalFilename = uploadedFile.internalFilename
        )
        .toJsonLd(
          className = Some("ThingPicture"),
          ontologyName = "anything"
        )

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      stillImageResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest          = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(stillImageResourceIri.get, "UTF-8")}")
      val resource: JsonLDDocument = getResponseJsonLD(knoraGetRequest)
      assert(
        resource.body.requireTypeAsKnoraApiV2ComplexTypeIri.toString == "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture"
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri
      )

      val savedValue: JsonLDValue = if (savedValues.value.size == 1) {
        savedValues.value.head
      } else {
        throw AssertionException(s"Expected one file value, got ${savedValues.value.size}")
      }

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      stillImageFileValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

      val savedImage = savedValueToSavedImage(savedValueObj)
      assert(savedImage.internalFilename == uploadedFile.internalFilename)
      assert(savedImage.width == marblesWidth)
      assert(savedImage.height == marblesHeight)
    }

    "create a resource with a still image file without processing" in {
      // Upload the image to Sipi.
      val sipiUploadResponse: SipiUploadWithoutProcessingResponse =
        uploadWithoutProcessingToSipi(
          loginToken = loginToken,
          filesToUpload = Seq(FileToUpload(path = pathToJp2, mimeType = org.apache.http.entity.ContentType.IMAGE_JPEG))
        )

      val uploadedFile: SipiUploadWithoutProcessingResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.filename should ===(jp2OriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(
          fileType = FileType.StillImageFile(),
          internalFilename = uploadedFile.filename
        )
        .toJsonLd(
          className = Some("ThingPicture"),
          ontologyName = "anything"
        )

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      stillImageResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest          = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(stillImageResourceIri.get, "UTF-8")}")
      val resource: JsonLDDocument = getResponseJsonLD(knoraGetRequest)
      assert(
        resource.body.requireTypeAsKnoraApiV2ComplexTypeIri.toString == "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture"
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri
      )

      val savedValue = savedValues.value.head

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      stillImageFileValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

      val savedImage = savedValueToSavedImage(savedValueObj)
      assert(savedImage.internalFilename == uploadedFile.filename)
    }

    "reject an image file with the wrong file extension" in {
      val exception = intercept[BadRequestException] {
        uploadToSipi(
          loginToken = loginToken,
          filesToUpload = Seq(
            FileToUpload(
              path = pathToMarblesWithWrongExtension,
              mimeType = org.apache.http.entity.ContentType.IMAGE_TIFF
            )
          )
        )
      }

      assert(exception.getMessage.contains("MIME type and/or file extension are inconsistent"))
    }

    "change a still image file value" in {
      // Upload the image to Sipi.
      val sipiUploadResponse: SipiUploadResponse =
        uploadToSipi(
          loginToken = loginToken,
          filesToUpload =
            Seq(FileToUpload(path = pathToTrp88, mimeType = org.apache.http.entity.ContentType.IMAGE_TIFF))
        )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(trp88OriginalFilename)

      // JSON describing the new image to the API.
      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.StillImageFile(),
          internalFilename = uploadedFile.internalFilename,
          resourceIri = stillImageResourceIri.get,
          valueIri = stillImageFileValueIri.get,
          className = Some("ThingPicture"),
          ontologyName = "anything"
        )
        .toJsonLd

      // Send the JSON in a PUT request to the API.
      val knoraPostRequest =
        Put(baseApiUrl + "/v2/values", HttpEntity(ContentTypes.`application/json`, jsonLdEntity)) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password)
        )
      val responseJsonDoc = getResponseJsonLD(knoraPostRequest)
      stillImageFileValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(stillImageResourceIri.get, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri,
        expectedValueIri = stillImageFileValueIri.get
      )

      val savedImage = savedValueToSavedImage(savedValue)
      assert(savedImage.internalFilename == uploadedFile.internalFilename)
      assert(savedImage.width == trp88Width)
      assert(savedImage.height == trp88Height)

      // Request the permanently stored image from Sipi.
      val sipiGetImageRequest = Get(savedImage.iiifUrl.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetImageRequest)
    }

    "create a resource with a PDF file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(path = pathToMinimalPdf, mimeType = org.apache.http.entity.ContentType.create("application/pdf"))
        )
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(minimalPdfOriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity =
        UploadFileRequest
          .make(
            fileType = FileType.DocumentFile(),
            internalFilename = uploadedFile.internalFilename
          )
          .toJsonLd(
            className = Some("ThingDocument"),
            ontologyName = "anything"
          )

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      pdfResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest          = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(pdfResourceIri.get, "UTF-8")}")
      val resource: JsonLDDocument = getResponseJsonLD(knoraGetRequest)
      assert(resource.body.requireTypeAsKnoraApiV2ComplexTypeIri.toString == thingDocumentIRI)

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasDocumentFileValue.toSmartIri
      )

      val savedValue: JsonLDValue = if (savedValues.value.size == 1) {
        savedValues.value.head
      } else {
        throw AssertionException(s"Expected one file value, got ${savedValues.value.size}")
      }

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      pdfValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

      val savedDocument: SavedDocument = savedValueToSavedDocument(savedValueObj)
      assert(savedDocument.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedDocument.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "change a PDF file value" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(path = pathToTestPdf, mimeType = org.apache.http.entity.ContentType.create("application/pdf"))
        )
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testPdfOriginalFilename)

      // Update the value.
      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.DocumentFile(),
          internalFilename = uploadedFile.internalFilename,
          resourceIri = pdfResourceIri.get,
          valueIri = pdfValueIri.get,
          className = Some("ThingDocument"),
          ontologyName = "anything"
        )
        .toJsonLd

      val request =
        Put(s"$baseApiUrl/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password)
        )
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      pdfValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(pdfResourceIri.get, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasDocumentFileValue.toSmartIri,
        expectedValueIri = pdfValueIri.get
      )

      val savedDocument: SavedDocument = savedValueToSavedDocument(savedValue)
      assert(savedDocument.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedDocument.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "not create a document resource if the file is actually a zip file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(path = pathToMinimalZip, mimeType = org.apache.http.entity.ContentType.create("application/zip"))
        )
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(minimalZipOriginalFilename)
      uploadedFile.fileType should equal("archive")

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(
          fileType = FileType.DocumentFile(),
          internalFilename = uploadedFile.internalFilename
        )
        .toJsonLd(
          className = Some("ThingDocument"),
          ontologyName = "anything"
        )

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val response = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest)
    }

    "create a resource with a CSV file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToCsv1, mimeType = org.apache.http.entity.ContentType.create("text/csv")))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(csv1OriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(
          fileType = FileType.TextFile,
          internalFilename = uploadedFile.internalFilename
        )
        .toJsonLd()

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      csvResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasTextFileValue.toSmartIri
      )

      val savedValue: JsonLDValue = if (savedValues.value.size == 1) {
        savedValues.value.head
      } else {
        throw AssertionException(s"Expected one file value, got ${savedValues.value.size}")
      }

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      csvValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

      val savedTextFile: SavedTextFile = savedValueToSavedTextFile(savedValueObj)
      assert(savedTextFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedTextFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "change a CSV file value" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToCsv2, mimeType = org.apache.http.entity.ContentType.create("text/csv")))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(csv2OriginalFilename)

      // Update the value.
      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.TextFile,
          internalFilename = uploadedFile.internalFilename,
          resourceIri = csvResourceIri.get,
          valueIri = csvValueIri.get
        )
        .toJsonLd

      val request =
        Put(s"$baseApiUrl/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password)
        )
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      csvValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(csvResourceIri.get, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasTextFileValue.toSmartIri,
        expectedValueIri = csvValueIri.get
      )

      val savedTextFile: SavedTextFile = savedValueToSavedTextFile(savedValue)
      assert(savedTextFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedTextFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "not create a resource with a still image file that's actually a text file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToCsv1, mimeType = org.apache.http.entity.ContentType.create("text/csv")))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(csv1OriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.StillImageFile(), internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val response = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest)
    }

    "create a resource with an XML file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(FileToUpload(path = pathToXml1, mimeType = org.apache.http.entity.ContentType.TEXT_XML))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(xml1OriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.TextFile, internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      xmlResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasTextFileValue.toSmartIri
      )

      val savedValue: JsonLDValue = if (savedValues.value.size == 1) {
        savedValues.value.head
      } else {
        throw AssertionException(s"Expected one file value, got ${savedValues.value.size}")
      }

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      xmlValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

      val savedTextFile: SavedTextFile = savedValueToSavedTextFile(savedValueObj)
      assert(savedTextFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedTextFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "change an XML file value" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(FileToUpload(path = pathToXml2, mimeType = org.apache.http.entity.ContentType.TEXT_XML))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(xml2OriginalFilename)

      // Update the value.

      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.TextFile,
          internalFilename = uploadedFile.internalFilename,
          resourceIri = xmlResourceIri.get,
          valueIri = xmlValueIri.get
        )
        .toJsonLd

      val request =
        Put(s"$baseApiUrl/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password)
        )
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      xmlValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(xmlResourceIri.get, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasTextFileValue.toSmartIri,
        expectedValueIri = xmlValueIri.get
      )

      val savedTextFile: SavedTextFile = savedValueToSavedTextFile(savedValue)
      assert(savedTextFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedTextFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "not create a resource of type TextRepresentation with a Zip file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(path = pathToMinimalZip, mimeType = org.apache.http.entity.ContentType.create("application/zip"))
        )
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(minimalZipOriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.TextFile, internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val response = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest)
    }

    "create a resource of type ArchiveRepresentation with a Zip file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(path = pathToMinimalZip, mimeType = org.apache.http.entity.ContentType.create("application/zip"))
        )
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(minimalZipOriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.ArchiveFile, internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      zipResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest          = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(zipResourceIri.get, "UTF-8")}")
      val resource: JsonLDDocument = getResponseJsonLD(knoraGetRequest)

      resource.body.requireTypeAsKnoraApiV2ComplexTypeIri.toString should equal(
        OntologyConstants.KnoraApiV2Complex.ArchiveRepresentation
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasArchiveFileValue.toSmartIri
      )

      val savedValue: JsonLDValue = if (savedValues.value.size == 1) {
        savedValues.value.head
      } else {
        throw AssertionException(s"Expected one file value, got ${savedValues.value.size}")
      }

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      zipValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

      val savedDocument: SavedDocument = savedValueToSavedDocument(savedValueObj)
      assert(savedDocument.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedDocument.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "change a Zip file value" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(path = pathToTestZip, mimeType = org.apache.http.entity.ContentType.create("application/zip"))
        )
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testZipOriginalFilename)

      // Update the value.

      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.ArchiveFile,
          resourceIri = zipResourceIri.get,
          valueIri = zipValueIri.get,
          internalFilename = uploadedFile.internalFilename
        )
        .toJsonLd

      val request =
        Put(s"$baseApiUrl/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password)
        )
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      zipValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(zipResourceIri.get, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasArchiveFileValue.toSmartIri,
        expectedValueIri = zipValueIri.get
      )

      val savedDocument: SavedDocument = savedValueToSavedDocument(savedValue)
      assert(savedDocument.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedDocument.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "create a resource of type ArchiveRepresentation with a 7z file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(
            path = pathToTest7z,
            mimeType = org.apache.http.entity.ContentType.create("application/x-7z-compressed")
          )
        )
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(test7zOriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.ArchiveFile, internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      zipResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest          = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(zipResourceIri.get, "UTF-8")}")
      val resource: JsonLDDocument = getResponseJsonLD(knoraGetRequest)

      resource.body.requireTypeAsKnoraApiV2ComplexTypeIri.toString should equal(
        OntologyConstants.KnoraApiV2Complex.ArchiveRepresentation
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasArchiveFileValue.toSmartIri
      )

      val savedValue: JsonLDValue = if (savedValues.value.size == 1) {
        savedValues.value.head
      } else {
        throw AssertionException(s"Expected one file value, got ${savedValues.value.size}")
      }

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      zipValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

      val savedDocument: SavedDocument = savedValueToSavedDocument(savedValueObj)
      assert(savedDocument.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedDocument.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "create a resource with a WAV file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToMinimalWav, mimeType = org.apache.http.entity.ContentType.create("audio/wav")))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(minimalWavOriginalFilename)

      // Create the resource in the API.
      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.AudioFile, internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      wavResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest          = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(wavResourceIri.get, "UTF-8")}")
      val resource: JsonLDDocument = getResponseJsonLD(knoraGetRequest)
      assert(
        resource.body.requireTypeAsKnoraApiV2ComplexTypeIri.toString == "http://api.knora.org/ontology/knora-api/v2#AudioRepresentation"
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasAudioFileValue.toSmartIri
      )

      val savedValue: JsonLDValue = if (savedValues.value.size == 1) {
        savedValues.value.head
      } else {
        throw AssertionException(s"Expected one file value, got ${savedValues.value.size}")
      }

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      wavValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

      val savedAudioFile: SavedAudioFile = savedValueToSavedAudioFile(savedValueObj)
      assert(savedAudioFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedAudioFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "change a WAV file value" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToTestWav, mimeType = org.apache.http.entity.ContentType.create("audio/wav")))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testWavOriginalFilename)

      // Update the value.

      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.AudioFile,
          resourceIri = wavResourceIri.get,
          internalFilename = uploadedFile.internalFilename,
          valueIri = wavValueIri.get
        )
        .toJsonLd

      val request =
        Put(s"$baseApiUrl/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password)
        )
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      wavValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(wavResourceIri.get, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasAudioFileValue.toSmartIri,
        expectedValueIri = wavValueIri.get
      )

      val savedAudioFile: SavedAudioFile = savedValueToSavedAudioFile(savedValue)
      assert(savedAudioFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedAudioFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "create a resource with a video file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToTestVideo, mimeType = org.apache.http.entity.ContentType.create("video/mp4")))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testVideoOriginalFilename)

      // Create the resource in the API.
      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.MovingImageFile(), internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      videoResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest          = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(videoResourceIri.get, "UTF-8")}")
      val resource: JsonLDDocument = getResponseJsonLD(knoraGetRequest)
      assert(
        resource.body.requireTypeAsKnoraApiV2ComplexTypeIri.toString == "http://api.knora.org/ontology/knora-api/v2#MovingImageRepresentation"
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasMovingImageFileValue.toSmartIri
      )

      val savedValue: JsonLDValue = if (savedValues.value.size == 1) {
        savedValues.value.head
      } else {
        throw AssertionException(s"Expected one file value, got ${savedValues.value.size}")
      }

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      videoValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

      val savedVideoFile: SavedVideoFile = savedValueToSavedVideoFile(savedValueObj)
      assert(savedVideoFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedVideoFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "change a video file value" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToTestVideo2, mimeType = org.apache.http.entity.ContentType.create("video/mp4")))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testVideo2OriginalFilename)

      // Update the value.

      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.MovingImageFile(),
          resourceIri = videoResourceIri.get,
          internalFilename = uploadedFile.internalFilename,
          valueIri = videoValueIri.get
        )
        .toJsonLd

      val request =
        Put(s"$baseApiUrl/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password)
        )
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      videoValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(videoResourceIri.get, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasMovingImageFileValue.toSmartIri,
        expectedValueIri = videoValueIri.get
      )

      val savedVideoFile: SavedVideoFile = savedValueToSavedVideoFile(savedValue)
      assert(savedVideoFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedVideoFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }
  }
}
