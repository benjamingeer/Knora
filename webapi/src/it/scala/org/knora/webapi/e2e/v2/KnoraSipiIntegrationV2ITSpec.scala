package org.knora.webapi.e2e.v2

import java.io.File
import java.net.URLEncoder

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util._
import org.knora.webapi.messages.v2.routing.authenticationmessages._
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.MutableTestIri
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._

object KnoraSipiIntegrationV2ITSpec {
    val config: Config = ConfigFactory.parseString(
        """
          |akka.loglevel = "DEBUG"
          |akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * Tests interaction between Knora and Sipi using Knora API v2.
 */
class KnoraSipiIntegrationV2ITSpec extends ITKnoraLiveSpec(KnoraSipiIntegrationV2ITSpec.config) with AuthenticationV2JsonProtocol with TriplestoreJsonProtocol {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val anythingUserEmail = SharedTestDataADM.anythingUser1.email
    private val incunabulaUserEmail = SharedTestDataADM.incunabulaMemberUser.email
    private val password = SharedTestDataADM.testPass

    private val stillImageResourceIri = new MutableTestIri
    private val stillImageFileValueIri = new MutableTestIri

    private val pdfResourceIri = new MutableTestIri
    private val pdfValueIri = new MutableTestIri

    private val marblesOriginalFilename = "marbles.tif"
    private val pathToMarbles = s"test_data/test_route/images/$marblesOriginalFilename"
    private val marblesWidth = 1419
    private val marblesHeight = 1001

    private val pathToMarblesWithWrongExtension = "test_data/test_route/images/marbles_with_wrong_extension.jpg"

    private val trp88OriginalFilename = "Trp88.tiff"
    private val pathToTrp88 = s"test_data/test_route/images/$trp88OriginalFilename"
    private val trp88Width = 499
    private val trp88Height = 630

    private val minimalPdfOriginalFilename = "minimal.pdf"
    private val pathToMinimalPdf = s"test_data/test_route/files/$minimalPdfOriginalFilename"
    private val minimalPdfWidth = 1250
    private val minimalPdfHeight = 600

    private val testPdfOriginalFilename = "test.pdf"
    private val pathToTestPdf = s"test_data/test_route/files/$testPdfOriginalFilename"
    private val testPdfWidth = 2480
    private val testPdfHeight = 3508

    private val csv1OriginalFilename = "eggs.csv"
    private val pathToCsv1 = s"test_data/test_route/files/$csv1OriginalFilename"

    private val csv2OriginalFilename = "spam.csv"
    private val pathToCsv2 = s"test_data/test_route/files/$csv2OriginalFilename"

    private val csvResourceIri = new MutableTestIri
    private val csvValueIri = new MutableTestIri

    private val xml1OriginalFilename = "test1.xml"
    private val pathToXml1 = s"test_data/test_route/files/$xml1OriginalFilename"

    private val xml2OriginalFilename = "test2.xml"
    private val pathToXml2 = s"test_data/test_route/files/$xml2OriginalFilename"

    private val xmlResourceIri = new MutableTestIri
    private val xmlValueIri = new MutableTestIri

    /**
     * Represents a file to be uploaded to Sipi.
     *
     * @param path     the path of the file.
     * @param mimeType the MIME type of the file.
     *
     */
    case class FileToUpload(path: String, mimeType: ContentType)

    /**
     * Represents an image file to be uploaded to Sipi.
     *
     * @param fileToUpload the file to be uploaded.
     * @param width        the image's width in pixels.
     * @param height       the image's height in pixels.
     */
    case class InputFile(fileToUpload: FileToUpload, width: Int, height: Int)

    /**
     * Represents the information that Sipi returns about each file that has been uploaded.
     *
     * @param originalFilename the original filename that was submitted to Sipi.
     * @param internalFilename Sipi's internal filename for the stored temporary file.
     * @param temporaryUrl     the URL at which the temporary file can be accessed.
     * @param fileType         `image`, `text`, or `document`.
     */
    case class SipiUploadResponseEntry(originalFilename: String, internalFilename: String, temporaryUrl: String, fileType: String)

    /**
     * Represents Sipi's response to a file upload request.
     *
     * @param uploadedFiles the information about each file that was uploaded.
     */
    case class SipiUploadResponse(uploadedFiles: Seq[SipiUploadResponseEntry])

    object SipiUploadResponseJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
        implicit val sipiUploadResponseEntryFormat: RootJsonFormat[SipiUploadResponseEntry] = jsonFormat4(SipiUploadResponseEntry)
        implicit val sipiUploadResponseFormat: RootJsonFormat[SipiUploadResponse] = jsonFormat1(SipiUploadResponse)
    }

    import SipiUploadResponseJsonProtocol._

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
    case class SavedDocument(internalFilename: String, url: String, pageCount: Int, width: Option[Int], height: Option[Int])

    /**
     * Represents the information that Knora returns about a text file value that was created.
     *
     * @param internalFilename the files's internal filename.
     * @param url              the file's URL.
     */
    case class SavedTextFile(internalFilename: String, url: String)

    /**
     * Uploads a file to Sipi and returns the information in Sipi's response.
     *
     * @param loginToken    the login token to be included in the request to Sipi.
     * @param filesToUpload the files to be uploaded.
     * @return a [[SipiUploadResponse]] representing Sipi's response.
     */
    private def uploadToSipi(loginToken: String, filesToUpload: Seq[FileToUpload]): SipiUploadResponse = {
        // Make a multipart/form-data request containing the files.

        val formDataParts: Seq[Multipart.FormData.BodyPart] = filesToUpload.map {
            fileToUpload =>
                val fileToSend = new File(fileToUpload.path)
                assert(fileToSend.exists(), s"File ${fileToUpload.path} does not exist")

                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(fileToUpload.mimeType, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
        }

        val sipiFormData = Multipart.FormData(formDataParts: _*)

        // Send Sipi the file in a POST request.
        val sipiRequest = Post(s"$baseInternalSipiUrl/upload?token=$loginToken", sipiFormData)

        val sipiUploadResponseJson: JsObject = getResponseJson(sipiRequest)
        // println(sipiUploadResponseJson.prettyPrint)
        val sipiUploadResponse: SipiUploadResponse = sipiUploadResponseJson.convertTo[SipiUploadResponse]
        // println(s"sipiUploadResponse: $sipiUploadResponse")

        // Request the temporary file from Sipi.
        for (responseEntry <- sipiUploadResponse.uploadedFiles) {
            val sipiGetTmpFileRequest: HttpRequest = if (responseEntry.fileType == "image") {
                Get(responseEntry.temporaryUrl.replace("http://0.0.0.0:1024", baseExternalSipiUrl) + "/full/max/0/default.jpg")
            } else {
                Get(responseEntry.temporaryUrl.replace("http://0.0.0.0:1024", baseExternalSipiUrl))
            }

            checkResponseOK(sipiGetTmpFileRequest)
        }

        sipiUploadResponse
    }

    /**
     * Given a JSON-LD document representing a resource, returns a JSON-LD array containing the values of the specified
     * property.
     *
     * @param resource            the JSON-LD document.
     * @param propertyIriInResult the property IRI.
     * @return a JSON-LD array containing the values of the specified property.
     */
    private def getValuesFromResource(resource: JsonLDDocument,
                                      propertyIriInResult: SmartIri): JsonLDArray = {
        resource.requireArray(propertyIriInResult.toString)
    }

    /**
     * Given a JSON-LD document representing a resource, returns a JSON-LD object representing the expected single
     * value of the specified property.
     *
     * @param resource            the JSON-LD document.
     * @param propertyIriInResult the property IRI.
     * @param expectedValueIri    the IRI of the expected value.
     * @return a JSON-LD object representing the expected single value of the specified property.
     */
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

    /**
     * Given a JSON-LD object representing a Knora image file value, returns a [[SavedImage]] containing the same information.
     *
     * @param savedValue a JSON-LD object representing a Knora image file value.
     * @return a [[SavedImage]] containing the same information.
     */
    private def savedValueToSavedImage(savedValue: JsonLDObject): SavedImage = {
        val internalFilename = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename)

        val iiifUrl = savedValue.requireDatatypeValueInObject(
            key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
            expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
            validationFun = stringFormatter.toSparqlEncodedString
        )

        val width = savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasDimX)
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

        val url: String = savedValue.requireDatatypeValueInObject(
            key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
            expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
            validationFun = stringFormatter.toSparqlEncodedString
        )

        val pageCount: Int = savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.DocumentFileValueHasPageCount)
        val dimX: Option[Int] = savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DocumentFileValueHasDimX)
        val dimY: Option[Int] = savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DocumentFileValueHasDimY)

        SavedDocument(
            internalFilename = internalFilename,
            url = url,
            pageCount = pageCount,
            width = dimX,
            height = dimY
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

        val url: String = savedValue.requireDatatypeValueInObject(
            key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
            expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
            validationFun = stringFormatter.toSparqlEncodedString
        )

        SavedTextFile(
            internalFilename = internalFilename,
            url = url
        )
    }

    "The Knora/Sipi integration" should {
        var loginToken: String = ""

        "not accept a token in Sipi that hasn't been signed by Knora" in {
            val invalidToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJLbm9yYSIsInN1YiI6Imh0dHA6Ly9yZGZoLmNoL3VzZXJzLzlYQkNyRFYzU1JhN2tTMVd3eW5CNFEiLCJhdWQiOlsiS25vcmEiLCJTaXBpIl0sImV4cCI6NDY5NDM1MTEyMiwiaWF0IjoxNTQxNzU5MTIyLCJqdGkiOiJ4bnlYeklFb1QxNmM2dkpDbHhSQllnIn0.P2Aq37G6XMLLBVMdnpDVVhWjenbVw0HTb1BpEuTWGRo"

            // The image to be uploaded.
            val fileToSend = new File(pathToMarbles)
            assert(fileToSend.exists(), s"File $pathToMarbles does not exist")

            // A multipart/form-data request containing the image.
            val sipiFormData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/tiff`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            // Send a POST request to Sipi, asking it to convert the image to JPEG 2000 and store it in a temporary file.
            val sipiRequest = Post(s"$baseInternalSipiUrl/upload?token=$invalidToken", sipiFormData)
            val sipiResponse = singleAwaitingRequest(sipiRequest)
            assert(sipiResponse.status == StatusCodes.Unauthorized)
        }

        "log in as a Knora user" in {
            /* Correct username and correct password */

            val params =
                s"""
                   |{
                   |    "email": "$anythingUserEmail",
                   |    "password": "$password"
                   |}
                """.stripMargin

            val request = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK)

            val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)
            loginToken = lr.token

            loginToken.nonEmpty should be(true)

            logger.debug("token: {}", loginToken)
        }

        "create a resource with a still image file" in {
            // Upload the image to Sipi.
            val sipiUploadResponse: SipiUploadResponse =
                uploadToSipi(
                    loginToken = loginToken,
                    filesToUpload = Seq(FileToUpload(path = pathToMarbles, mimeType = MediaTypes.`image/tiff`))
                )

            val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
            uploadedFile.originalFilename should ===(marblesOriginalFilename)

            // Ask Knora to create the resource.

            val jsonLdEntity =
                s"""{
                   |  "@type" : "anything:ThingPicture",
                   |  "knora-api:hasStillImageFileValue" : {
                   |    "@type" : "knora-api:StillImageFileValue",
                   |    "knora-api:fileValueHasFilename" : "${uploadedFile.internalFilename}"
                   |  },
                   |  "knora-api:attachedToProject" : {
                   |    "@id" : "http://rdfh.ch/projects/0001"
                   |  },
                   |  "rdfs:label" : "test thing",
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
            stillImageResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

            // Get the resource from Knora.
            val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(stillImageResourceIri.get, "UTF-8")}")
            val resource: JsonLDDocument = getResponseJsonLD(knoraGetRequest)
            assert(resource.requireTypeAsKnoraTypeIri.toString == "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture")

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
                case other => throw AssertionException(s"Invalid value object: $other")
            }

            stillImageFileValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

            val savedImage = savedValueToSavedImage(savedValueObj)
            assert(savedImage.internalFilename == uploadedFile.internalFilename)
            assert(savedImage.width == marblesWidth)
            assert(savedImage.height == marblesHeight)
        }

        "reject an image file with the wrong file extension" in {
            val exception = intercept[AssertionException] {
                uploadToSipi(
                    loginToken = loginToken,
                    filesToUpload = Seq(FileToUpload(path = pathToMarblesWithWrongExtension, mimeType = MediaTypes.`image/tiff`))
                )
            }

            assert(exception.getMessage.contains("MIME type and/or file extension are inconsistent"))
        }

        "change a still image file value" in {
            // Upload the image to Sipi.
            val sipiUploadResponse: SipiUploadResponse =
                uploadToSipi(
                    loginToken = loginToken,
                    filesToUpload = Seq(FileToUpload(path = pathToTrp88, mimeType = MediaTypes.`image/tiff`))
                )

            val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
            uploadedFile.originalFilename should ===(trp88OriginalFilename)

            // JSON describing the new image to Knora.
            val jsonLdEntity =
                s"""{
                   |  "@id" : "${stillImageResourceIri.get}",
                   |  "@type" : "anything:ThingPicture",
                   |  "knora-api:hasStillImageFileValue" : {
                   |    "@id" : "${stillImageFileValueIri.get}",
                   |    "@type" : "knora-api:StillImageFileValue",
                   |    "knora-api:fileValueHasFilename" : "${uploadedFile.internalFilename}"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            // Send the JSON in a PUT request to Knora.
            val knoraPostRequest = Put(baseApiUrl + "/v2/values", HttpEntity(ContentTypes.`application/json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val responseJsonDoc = getResponseJsonLD(knoraPostRequest)
            stillImageFileValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

            // Get the resource from Knora.
            val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(stillImageResourceIri.get, "UTF-8")}")
            val resource = getResponseJsonLD(knoraGetRequest)

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
            val sipiGetImageRequest = Get(savedImage.iiifUrl)
            checkResponseOK(sipiGetImageRequest)
        }

        "delete the temporary file if Knora rejects the request to create a file value" in {
            // Upload the image to Sipi.
            val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
                loginToken = loginToken,
                filesToUpload = Seq(FileToUpload(path = pathToMarbles, mimeType = MediaTypes.`image/tiff`))
            )

            val internalFilename = sipiUploadResponse.uploadedFiles.head.internalFilename
            val temporaryBaseIIIFUrl = sipiUploadResponse.uploadedFiles.head.temporaryUrl.replace("http://0.0.0.0:1024", baseExternalSipiUrl)
            val temporaryBaseIIIFDirectDownloadUrl = temporaryBaseIIIFUrl + "/file"

            // JSON describing the new image to Knora.
            val jsonLdEntity =
                s"""{
                   |  "@id" : "${stillImageResourceIri.get}",
                   |  "@type" : "anything:ThingDocument",
                   |  "knora-api:hasStillImageFileValue" : {
                   |    "@type" : "knora-api:StillImageFileValue",
                   |    "knora-api:fileValueHasFilename" : "$internalFilename"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            // Send the JSON in a POST request to Knora.
            val knoraPostRequest = Post(baseApiUrl + "/v2/values", HttpEntity(ContentTypes.`application/json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password))
            val knoraPostResponse = singleAwaitingRequest(knoraPostRequest)
            assert(knoraPostResponse.status == StatusCodes.Forbidden)

            // Request the temporary image from Sipi.
            val sipiGetTmpFileRequest = Get(temporaryBaseIIIFDirectDownloadUrl)
            val sipiResponse = singleAwaitingRequest(sipiGetTmpFileRequest)
            assert(sipiResponse.status == StatusCodes.NotFound)
        }

        "create a resource with a PDF file" in {
            // Upload the file to Sipi.
            val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
                loginToken = loginToken,
                filesToUpload = Seq(FileToUpload(path = pathToMinimalPdf, mimeType = MediaTypes.`application/pdf`))
            )

            val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
            uploadedFile.originalFilename should ===(minimalPdfOriginalFilename)

            // Ask Knora to create the resource.

            val jsonLdEntity =
                s"""{
                   |  "@type" : "anything:ThingDocument",
                   |  "knora-api:hasDocumentFileValue" : {
                   |    "@type" : "knora-api:DocumentFileValue",
                   |    "knora-api:fileValueHasFilename" : "${uploadedFile.internalFilename}"
                   |  },
                   |  "knora-api:attachedToProject" : {
                   |    "@id" : "http://rdfh.ch/projects/0001"
                   |  },
                   |  "rdfs:label" : "test thing",
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
            pdfResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

            // Get the resource from Knora.
            val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(pdfResourceIri.get, "UTF-8")}")
            val resource: JsonLDDocument = getResponseJsonLD(knoraGetRequest)
            assert(resource.requireTypeAsKnoraTypeIri.toString == "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingDocument")

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
                case other => throw AssertionException(s"Invalid value object: $other")
            }

            pdfValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

            val savedDocument: SavedDocument = savedValueToSavedDocument(savedValueObj)
            assert(savedDocument.internalFilename == uploadedFile.internalFilename)
            assert(savedDocument.pageCount == 1)
            assert(savedDocument.width.contains(minimalPdfWidth))
            assert(savedDocument.height.contains(minimalPdfHeight))
        }

        "change a PDF file value" in {
            // Upload the file to Sipi.
            val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
                loginToken = loginToken,
                filesToUpload = Seq(FileToUpload(path = pathToTestPdf, mimeType = MediaTypes.`application/pdf`))
            )

            val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
            uploadedFile.originalFilename should ===(testPdfOriginalFilename)

            // Ask Knora to update the value.

            val jsonLdEntity =
                s"""{
                   |  "@id" : "${pdfResourceIri.get}",
                   |  "@type" : "anything:ThingDocument",
                   |  "knora-api:hasDocumentFileValue" : {
                   |    "@id" : "${pdfValueIri.get}",
                   |    "@type" : "knora-api:DocumentFileValue",
                   |    "knora-api:fileValueHasFilename" : "${uploadedFile.internalFilename}"
                   |  },
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Put(s"$baseApiUrl/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
            pdfValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

            // Get the resource from Knora.
            val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(pdfResourceIri.get, "UTF-8")}")
            val resource = getResponseJsonLD(knoraGetRequest)

            // Get the new file value from the resource.
            val savedValue: JsonLDObject = getValueFromResource(
                resource = resource,
                propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasDocumentFileValue.toSmartIri,
                expectedValueIri = pdfValueIri.get
            )

            val savedDocument: SavedDocument = savedValueToSavedDocument(savedValue)
            assert(savedDocument.internalFilename == uploadedFile.internalFilename)
            assert(savedDocument.pageCount == 1)
            assert(savedDocument.width.contains(testPdfWidth))
            assert(savedDocument.height.contains(testPdfHeight))
        }

        "create a resource with a CSV file" in {
            // Upload the file to Sipi.
            val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
                loginToken = loginToken,
                filesToUpload = Seq(FileToUpload(path = pathToCsv1, mimeType = MediaTypes.`text/csv`.toContentType(HttpCharsets.`UTF-8`)))
            )

            val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
            uploadedFile.originalFilename should ===(csv1OriginalFilename)

            // Ask Knora to create the resource.

            val jsonLdEntity =
                s"""{
                   |  "@type" : "knora-api:TextRepresentation",
                   |  "knora-api:hasTextFileValue" : {
                   |    "@type" : "knora-api:TextFileValue",
                   |    "knora-api:fileValueHasFilename" : "${uploadedFile.internalFilename}"
                   |  },
                   |  "knora-api:attachedToProject" : {
                   |    "@id" : "http://rdfh.ch/projects/0001"
                   |  },
                   |  "rdfs:label" : "text file",
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
                   |  }
                   |}""".stripMargin

            val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
            val resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            csvResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

            // Get the resource from Knora.
            val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}")
            val resource = getResponseJsonLD(knoraGetRequest)

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
                case other => throw AssertionException(s"Invalid value object: $other")
            }

            csvValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

            val savedTextFile: SavedTextFile = savedValueToSavedTextFile(savedValueObj)
            assert(savedTextFile.internalFilename == uploadedFile.internalFilename)
        }

        "change a CSV file value" in {
            // Upload the file to Sipi.
            val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
                loginToken = loginToken,
                filesToUpload = Seq(FileToUpload(path = pathToCsv2, mimeType = MediaTypes.`text/csv`.toContentType(HttpCharsets.`UTF-8`)))
            )

            val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
            uploadedFile.originalFilename should ===(csv2OriginalFilename)

            // Ask Knora to update the value.

            val jsonLdEntity =
                s"""{
                   |  "@id" : "${csvResourceIri.get}",
                   |  "@type" : "knora-api:TextRepresentation",
                   |  "knora-api:hasTextFileValue" : {
                   |    "@id" : "${csvValueIri.get}",
                   |    "@type" : "knora-api:TextFileValue",
                   |    "knora-api:fileValueHasFilename" : "${uploadedFile.internalFilename}"
                   |  },
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
                   |  }
                   |}""".stripMargin

            val request = Put(s"$baseApiUrl/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
            csvValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

            // Get the resource from Knora.
            val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(csvResourceIri.get, "UTF-8")}")
            val resource = getResponseJsonLD(knoraGetRequest)

            // Get the new file value from the resource.
            val savedValue: JsonLDObject = getValueFromResource(
                resource = resource,
                propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasTextFileValue.toSmartIri,
                expectedValueIri = csvValueIri.get
            )

            val savedTextFile: SavedTextFile = savedValueToSavedTextFile(savedValue)
            assert(savedTextFile.internalFilename == uploadedFile.internalFilename)
        }

        "not create a resource with a still image file that's actually a text file" in {
            // Upload the file to Sipi.
            val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
                loginToken = loginToken,
                filesToUpload = Seq(FileToUpload(path = pathToCsv1, mimeType = MediaTypes.`text/csv`.toContentType(HttpCharsets.`UTF-8`)))
            )

            val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
            uploadedFile.originalFilename should ===(csv1OriginalFilename)

            // Ask Knora to create the resource.

            val jsonLdEntity =
                s"""{
                   |  "@type" : "knora-api:StillImageRepresentation",
                   |  "knora-api:hasStillImageValue" : {
                   |    "@type" : "knora-api:StillImageFileValue",
                   |    "knora-api:fileValueHasFilename" : "${uploadedFile.internalFilename}"
                   |  },
                   |  "knora-api:attachedToProject" : {
                   |    "@id" : "http://rdfh.ch/projects/0001"
                   |  },
                   |  "rdfs:label" : "still image file",
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
                   |  }
                   |}""".stripMargin

            val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val response = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.BadRequest)
        }

        "create a resource with an XML file" in {
            // Upload the file to Sipi.
            val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
                loginToken = loginToken,
                filesToUpload = Seq(FileToUpload(path = pathToXml1, mimeType = MediaTypes.`text/xml`.toContentType(HttpCharsets.`UTF-8`)))
            )

            val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
            uploadedFile.originalFilename should ===(xml1OriginalFilename)

            // Ask Knora to create the resource.

            val jsonLdEntity =
                s"""{
                   |  "@type" : "knora-api:TextRepresentation",
                   |  "knora-api:hasTextFileValue" : {
                   |    "@type" : "knora-api:TextFileValue",
                   |    "knora-api:fileValueHasFilename" : "${uploadedFile.internalFilename}"
                   |  },
                   |  "knora-api:attachedToProject" : {
                   |    "@id" : "http://rdfh.ch/projects/0001"
                   |  },
                   |  "rdfs:label" : "text file",
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
                   |  }
                   |}""".stripMargin

            val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
            val resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            xmlResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

            // Get the resource from Knora.
            val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}")
            val resource = getResponseJsonLD(knoraGetRequest)

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
                case other => throw AssertionException(s"Invalid value object: $other")
            }

            xmlValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

            val savedTextFile: SavedTextFile = savedValueToSavedTextFile(savedValueObj)
            assert(savedTextFile.internalFilename == uploadedFile.internalFilename)
        }

        "change an XML file value" in {
            // Upload the file to Sipi.
            val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
                loginToken = loginToken,
                filesToUpload = Seq(FileToUpload(path = pathToXml2, mimeType = MediaTypes.`text/xml`.toContentType(HttpCharsets.`UTF-8`)))
            )

            val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
            uploadedFile.originalFilename should ===(xml2OriginalFilename)

            // Ask Knora to update the value.

            val jsonLdEntity =
                s"""{
                   |  "@id" : "${xmlResourceIri.get}",
                   |  "@type" : "knora-api:TextRepresentation",
                   |  "knora-api:hasTextFileValue" : {
                   |    "@id" : "${xmlValueIri.get}",
                   |    "@type" : "knora-api:TextFileValue",
                   |    "knora-api:fileValueHasFilename" : "${uploadedFile.internalFilename}"
                   |  },
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
                   |  }
                   |}""".stripMargin

            val request = Put(s"$baseApiUrl/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
            xmlValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

            // Get the resource from Knora.
            val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(xmlResourceIri.get, "UTF-8")}")
            val resource = getResponseJsonLD(knoraGetRequest)

            // Get the new file value from the resource.
            val savedValue: JsonLDObject = getValueFromResource(
                resource = resource,
                propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasTextFileValue.toSmartIri,
                expectedValueIri = xmlValueIri.get
            )

            val savedTextFile: SavedTextFile = savedValueToSavedTextFile(savedValue)
            assert(savedTextFile.internalFilename == uploadedFile.internalFilename)
        }
    }
}
