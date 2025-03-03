/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.apache.pekko.testkit.ImplicitSender
import org.scalatest.compatible.Assertion
import zio.ZIO

import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID
import scala.concurrent.duration.*

import dsp.errors.*
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.util.CalendarNameGregorian
import org.knora.webapi.messages.util.DatePrecisionYear
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.messages.v2.responder.standoffmessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.models.filemodels.FileModelUtil
import org.knora.webapi.models.filemodels.FileType
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.util.MutableTestIri
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA

/**
 * Tests [[ValuesResponderV2]].
 */
class ValuesResponderV2Spec extends CoreSpec with ImplicitSender {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val zeitglöckleinIri = "http://rdfh.ch/0803/c5058f3a"
  private val generationeIri   = "http://rdfh.ch/0803/c3f913666f"
  private val aThingIri        = "http://rdfh.ch/0001/a-thing"
  private val freetestWithAPropertyFromAnythingOntologyIri =
    "http://rdfh.ch/0001/freetest-with-a-property-from-anything-ontology"
  private val aThingPictureIri     = "http://rdfh.ch/0001/a-thing-picture"
  private val sierraIri            = "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ"
  private val thingPictureClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture"

  private val incunabulaUser        = SharedTestDataADM.incunabulaMemberUser
  private val incunabulaCreatorUser = SharedTestDataADM.incunabulaCreatorUser
  private val anythingUser1         = SharedTestDataADM.anythingUser1
  private val anythingUser2         = SharedTestDataADM.anythingUser2

  private val mimeTypeTIFF = "image/tiff"
  private val mimeTypeJP2  = "image/jp2"

  override lazy val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/project_ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest",
    ),
    RdfDataObject(path = "test_data/project_data/freetest-data.ttl", name = "http://www.knora.org/data/0001/freetest"),
    RdfDataObject(
      path = "test_data/generated_test_data/responders.v2.ValuesResponderV2Spec/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
    RdfDataObject(path = "test_data/project_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    RdfDataObject(
      path = "test_data/project_ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything",
    ),
  )

  // The default timeout for receiving reply messages from actors.
  override implicit val timeout: FiniteDuration = 30.seconds

  private val firstIntValueVersionIri                   = new MutableTestIri
  private val intValueIri                               = new MutableTestIri
  private val intValueIriForFreetest                    = new MutableTestIri
  private val intValueIriWithCustomPermissions          = new MutableTestIri
  private val intValueForRsyncIri                       = new MutableTestIri
  private val zeitglöckleinCommentWithoutStandoffIri    = new MutableTestIri
  private val zeitglöckleinCommentWithStandoffIri       = new MutableTestIri
  private val zeitglöckleinCommentWithCommentIri        = new MutableTestIri
  private val zeitglöckleinSecondCommentWithStandoffIri = new MutableTestIri
  private val lobComment1Iri                            = new MutableTestIri
  private val lobComment2Iri                            = new MutableTestIri
  private val decimalValueIri                           = new MutableTestIri
  private val timeValueIri                              = new MutableTestIri
  private val dateValueIri                              = new MutableTestIri
  private val booleanValueIri                           = new MutableTestIri
  private val geometryValueIri                          = new MutableTestIri
  private val intervalValueIri                          = new MutableTestIri
  private val listValueIri                              = new MutableTestIri
  private val colorValueIri                             = new MutableTestIri
  private val uriValueIri                               = new MutableTestIri
  private val geonameValueIri                           = new MutableTestIri
  private val linkValueIri                              = new MutableTestIri
  private val standoffLinkValueIri                      = new MutableTestIri
  private val stillImageFileValueIri                    = new MutableTestIri

  private var integerValueUUID = randomUUID
  private var linkValueUUID    = randomUUID

  private val sampleStandoff: Vector[StandoffTagV2] = Vector(
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag.toSmartIri,
      startPosition = 0,
      endPosition = 7,
      uuid = randomUUID(),
      originalXMLID = None,
      startIndex = 0,
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffParagraphTag.toSmartIri,
      startPosition = 0,
      endPosition = 10,
      uuid = randomUUID(),
      originalXMLID = None,
      startIndex = 1,
    ),
  )

  private val sampleStandoffModified: Vector[StandoffTagV2] = Vector(
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag.toSmartIri,
      startPosition = 1,
      endPosition = 7,
      uuid = randomUUID(),
      originalXMLID = None,
      startIndex = 0,
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffParagraphTag.toSmartIri,
      startPosition = 0,
      endPosition = 10,
      uuid = randomUUID(),
      originalXMLID = None,
      startIndex = 1,
    ),
  )

  private val sampleStandoffWithLink: Vector[StandoffTagV2] = Vector(
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag.toSmartIri,
      dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
      startPosition = 0,
      endPosition = 7,
      uuid = randomUUID(),
      originalXMLID = None,
      startIndex = 0,
      attributes = Vector(
        StandoffTagIriAttributeV2(
          standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink.toSmartIri,
          value = aThingIri,
        ),
      ),
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffParagraphTag.toSmartIri,
      startPosition = 0,
      endPosition = 10,
      uuid = randomUUID(),
      originalXMLID = None,
      startIndex = 1,
    ),
  )

  private var standardMapping: Option[MappingXMLtoStandoff] = None

  private def getResourceWithValues(
    resourceIri: IRI,
    propertyIrisForGravsearch: Seq[SmartIri],
    requestingUser: User,
  ): ReadResourceV2 = {
    // Make a Gravsearch query from a template.
    val gravsearchQuery: String = org.knora.webapi.messages.twirl.queries.gravsearch.txt
      .getResourceWithSpecifiedProperties(
        resourceIri = resourceIri,
        propertyIris = propertyIrisForGravsearch,
      )
      .toString()

    // Run the query.
    val result = UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[SearchResponderV2](
        _.gravsearchV2(
          GravsearchParser.parseQuery(gravsearchQuery),
          SchemaRendering.apiV2SchemaWithOption(MarkupRendering.Xml),
          requestingUser,
        ),
      ),
    )

    result.toResource(resourceIri).toOntologySchema(ApiV2Complex)
  }

  private def getValuesFromResource(resource: ReadResourceV2, propertyIriInResult: SmartIri): Seq[ReadValueV2] =
    resource.values.getOrElse(
      propertyIriInResult,
      throw AssertionException(s"Resource <${resource.resourceIri}> does not have property <$propertyIriInResult>"),
    )

  private def getValueFromResource(
    resource: ReadResourceV2,
    propertyIriInResult: SmartIri,
    expectedValueIri: IRI,
  ): ReadValueV2 = {
    val propertyValues: Seq[ReadValueV2] =
      getValuesFromResource(resource = resource, propertyIriInResult = propertyIriInResult)
    propertyValues
      .find(_.valueIri == expectedValueIri)
      .getOrElse(
        throw AssertionException(
          s"Property <$propertyIriInResult> of resource <${resource.resourceIri}> does not have value <$expectedValueIri>",
        ),
      )
  }

  private def checkValueIsDeleted(
    resourceIri: IRI,
    maybePreviousLastModDate: Option[Instant],
    valueIri: IRI,
    customDeleteDate: Option[Instant] = None,
    deleteComment: Option[String] = None,
    requestingUser: User,
    isLinkValue: Boolean = false,
  ): Unit = {
    appActor ! ResourcesGetRequestV2(
      resourceIris = Seq(resourceIri),
      targetSchema = ApiV2Complex,
      requestingUser = requestingUser,
    )

    val resource = expectMsgPF(timeout) { case getResponse: ReadResourcesSequenceV2 =>
      getResponse.toResource(resourceIri)
    }
    //  ensure the resource was not deleted
    resource.deletionInfo should be(None)

    val deletedValues = resource.values.getOrElse(
      OntologyConstants.KnoraBase.DeletedValue.toSmartIri,
      throw AssertionException(
        s"Resource <$resourceIri> does not have any deleted values, even though value <$valueIri> should be deleted.",
      ),
    )

    if (!isLinkValue) {
      // not a LinkValue, so the value should be a DeletedValue of the resource
      val deletedValue = deletedValues.collectFirst { case v if v.valueIri == valueIri => v }
        .getOrElse(throw AssertionException(s"Value <$valueIri> was not among the deleted resources"))

      checkLastModDate(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybePreviousLastModDate,
        maybeUpdatedLastModDate = resource.lastModificationDate,
      )

      val deletionInfo = deletedValue.deletionInfo.getOrElse(
        throw AssertionException(s"Value <$valueIri> does not have deletion information"),
      )

      customDeleteDate match {
        case Some(deleteDate) => deletionInfo.deleteDate should equal(deleteDate)
        case None             => ()
      }

      deleteComment match {
        case Some(comment) => deletionInfo.maybeDeleteComment.get should equal(comment)
        case None          => ()
      }
      ()
    } else {
      // The value is a LinkValue, so there should be a DeletedValue having a PreviousValue with the IRI of the value.
      if (
        !deletedValues.exists(v =>
          v.previousValueIri match {
            case Some(previousValueIRI) => previousValueIRI == valueIri
            case None                   => false
          },
        )
      ) throw AssertionException(s"ListValue <$valueIri> was not deleted correctly.")
    }
  }

  private def checkLastModDate(
    resourceIri: IRI,
    maybePreviousLastModDate: Option[Instant],
    maybeUpdatedLastModDate: Option[Instant],
  ): Unit =
    maybeUpdatedLastModDate match {
      case Some(updatedLastModDate) =>
        maybePreviousLastModDate match {
          case Some(previousLastModDate) => assert(updatedLastModDate.isAfter(previousLastModDate)); ()
          case None                      => assert(true); ()
        }

      case None => throw AssertionException(s"Resource $resourceIri has no knora-base:lastModificationDate")
    }

  private def getValue(
    resourceIri: IRI,
    maybePreviousLastModDate: Option[Instant],
    propertyIriForGravsearch: SmartIri,
    propertyIriInResult: SmartIri,
    expectedValueIri: IRI,
    requestingUser: User,
    checkLastModDateChanged: Boolean = true,
  ): ReadValueV2 = {
    val resource = getResourceWithValues(
      resourceIri = resourceIri,
      propertyIrisForGravsearch = Seq(propertyIriForGravsearch),
      requestingUser = requestingUser,
    )

    if (checkLastModDateChanged) {
      checkLastModDate(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybePreviousLastModDate,
        maybeUpdatedLastModDate = resource.lastModificationDate,
      )
    }
    getValueFromResource(
      resource = resource,
      propertyIriInResult = propertyIriInResult,
      expectedValueIri = expectedValueIri,
    )
  }

  private def getResourceLastModificationDate(resourceIri: IRI, requestingUser: User): Option[Instant] = {
    appActor ! ResourcesPreviewGetRequestV2(
      resourceIris = Seq(resourceIri),
      targetSchema = ApiV2Complex,
      requestingUser = requestingUser,
    )

    expectMsgPF(timeout) { case previewResponse: ReadResourcesSequenceV2 =>
      val resourcePreview: ReadResourceV2 = previewResponse.toResource(resourceIri)
      resourcePreview.lastModificationDate
    }
  }

  private def getValueUUID(valueIri: IRI): Option[UUID] = {
    val sparqlQuery =
      s"""
         |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
         |
         |SELECT ?valueUUID WHERE {
         |    <$valueIri> knora-base:valueHasUUID ?valueUUID .
         |}
         |""".stripMargin

    val actual = UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[TriplestoreService](_.query(Select(sparqlQuery))))

    val rows = actual.results.bindings
    if (rows.isEmpty) {
      None
    } else if (rows.size > 1) {
      throw AssertionException(s"Expected one knora-base:valueHasUUID, got ${rows.size}")
    } else {
      Some(UuidUtil.base64Decode(rows.head.rowMap("valueUUID")).get)
    }
  }

  private def getValuePermissions(valueIri: IRI): Option[UUID] = {
    val sparqlQuery =
      s"""
         |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
         |
         |SELECT ?valuePermissions WHERE {
         |    <$valueIri> knora-base:hasPermissions ?valuePermissions .
         |}""".stripMargin

    val actual = UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[TriplestoreService](_.query(Select(sparqlQuery))))

    val rows = actual.results.bindings
    if (rows.isEmpty) {
      None
    } else if (rows.size > 1) {
      throw AssertionException(s"Expected one knora-base:hasPermissions, got ${rows.size}")
    } else {
      Some(UuidUtil.base64Decode(rows.head.rowMap("valuePermissions")).get)
    }
  }

  "Load test data" in {
    appActor ! GetMappingRequestV2("http://rdfh.ch/standoff/mappings/StandardMapping")

    expectMsgPF(timeout) { case mappingResponse: GetMappingResponseV2 =>
      standardMapping = Some(mappingResponse.mapping)
    }
  }

  "The values responder" should {

    "create an integer value" in {
      // Add the value.

      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue                                  = 4
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      intValueIri.set(createValueResponse.valueIri)
      firstIntValueVersionIri.set(createValueResponse.valueIri)
      integerValueUUID = createValueResponse.valueUUID

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 => savedValue.valueHasInteger should ===(intValue)
        case _                                 => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate integer value" in {
      val resourceIri      = aThingIri
      val resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri
      val propertyIri      = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intVal           = IntegerValueContentV2(ApiV2Complex, 4)
      val duplicateValue =
        CreateValueV2(resourceIri, resourceClassIri, propertyIri, intVal)

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](_.createValueV2(duplicateValue, anythingUser1, randomUUID)),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "update an integer value" in {
      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      // Get the value before update.
      val previousValueFromTriplestore: ReadValueV2 = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1,
        checkLastModDateChanged = false,
      )

      // Update the value.

      val intValue = 5

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIri.get,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )

      intValueIri.set(updateValueResponse.valueIri)
      assert(updateValueResponse.valueUUID == integerValueUUID)

      // Read the value back to check that it was added correctly.

      val updatedValueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1,
      )

      updatedValueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          updatedValueFromTriplestore.permissions should ===(previousValueFromTriplestore.permissions)
          updatedValueFromTriplestore.valueHasUUID should ===(previousValueFromTriplestore.valueHasUUID)

        case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
      }

      // Check that the permissions and UUID were deleted from the previous version of the value.
      assert(getValueUUID(previousValueFromTriplestore.valueIri).isEmpty)
      assert(getValuePermissions(previousValueFromTriplestore.valueIri).isEmpty)
    }

    "create an integer value that belongs to a property of another ontology" in {
      val resourceIri: IRI = freetestWithAPropertyFromAnythingOntologyIri
      val propertyIri: SmartIri =
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasIntegerUsedByOtherOntologies".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      // Create the value.
      val intValue = 40

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri =
                "http://0.0.0.0:3333/ontology/0001/freetest/v2#FreetestWithAPropertyFromAnythingOntology".toSmartIri,
              propertyIri = propertyIri,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      intValueIriForFreetest.set(createValueResponse.valueIri)
      integerValueUUID = createValueResponse.valueUUID

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIriForFreetest.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 => savedValue.valueHasInteger should ===(intValue)
        case _                                 => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
      }
    }

    "update an integer value that belongs to a property of another ontology" in {
      val resourceIri: IRI = freetestWithAPropertyFromAnythingOntologyIri
      val propertyIri: SmartIri =
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasIntegerUsedByOtherOntologies".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser2)

      // Get the value before update.
      val previousValueFromTriplestore: ReadValueV2 = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIriForFreetest.get,
        requestingUser = anythingUser2,
        checkLastModDateChanged = false,
      )

      // Update the value.
      val intValue: Int = 50

      val updateValueContent: UpdateValueContentV2 = UpdateValueContentV2(
        resourceIri = resourceIri,
        resourceClassIri =
          "http://0.0.0.0:3333/ontology/0001/freetest/v2#FreetestWithAPropertyFromAnythingOntology".toSmartIri,
        propertyIri = propertyIri,
        valueIri = intValueIriForFreetest.get,
        valueContent = IntegerValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasInteger = intValue,
        ),
      )

      val updateValueResponse =
        UnsafeZioRun.runOrThrow(
          ZIO.serviceWithZIO[ValuesResponderV2](_.updateValueV2(updateValueContent, anythingUser2, randomUUID)),
        )

      intValueIriForFreetest.set(updateValueResponse.valueIri)
      assert(updateValueResponse.valueUUID == previousValueFromTriplestore.valueHasUUID)

      // Read the value back to check that it was added correctly.

      val updatedValueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIriForFreetest.get,
        requestingUser = anythingUser2,
      )

      updatedValueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          updatedValueFromTriplestore.permissions should ===(previousValueFromTriplestore.permissions)
          updatedValueFromTriplestore.valueHasUUID should ===(previousValueFromTriplestore.valueHasUUID)

        case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
      }

      // Check that the permissions and UUID were deleted from the previous version of the value.
      assert(getValueUUID(previousValueFromTriplestore.valueIri).isEmpty)
      assert(getValuePermissions(previousValueFromTriplestore.valueIri).isEmpty)
    }

    "delete an integer value that belongs to a property of another ontology" in {
      val resourceIri: IRI = freetestWithAPropertyFromAnythingOntologyIri
      val propertyIri: SmartIri =
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasIntegerUsedByOtherOntologies".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser2)

      UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.deleteValueV2(
            DeleteValueV2(
              resourceIri = resourceIri,
              resourceClassIri =
                "http://0.0.0.0:3333/ontology/0001/freetest/v2#FreetestWithAPropertyFromAnythingOntology".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIriForFreetest.get,
              valueTypeIri = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri,
              deleteComment = Some("this value was incorrect"),
            ),
            anythingUser2,
            randomUUID,
          ),
        ),
      )

      checkValueIsDeleted(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        valueIri = intValueIriForFreetest.get,
        requestingUser = anythingUser2,
      )
    }

    "not update an integer value without a comment without changing it" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue              = 5

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIri.get,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "update an integer value, adding a comment" in {
      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)
      val comment                                   = "Added a comment"

      // Get the value before update.
      val previousValueFromTriplestore: ReadValueV2 = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1,
        checkLastModDateChanged = false,
      )

      // Update the value.

      val intValue = 5

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIri.get,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
                comment = Some(comment),
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )

      intValueIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val updatedValueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1,
      )

      updatedValueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          updatedValueFromTriplestore.permissions should ===(previousValueFromTriplestore.permissions)
          updatedValueFromTriplestore.valueHasUUID should ===(previousValueFromTriplestore.valueHasUUID)
          assert(updatedValueFromTriplestore.valueContent.comment.contains(comment))

        case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
      }

      // Check that the permissions and UUID were deleted from the previous version of the value.
      assert(getValueUUID(previousValueFromTriplestore.valueIri).isEmpty)
      assert(getValuePermissions(previousValueFromTriplestore.valueIri).isEmpty)
    }

    "not update an integer value with a comment without changing it" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue              = 5
      val comment               = "Added a comment"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIri.get,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
                comment = Some(comment),
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "update an integer value with a comment, changing only the comment" in {
      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)
      val comment                                   = "An updated comment"

      // Get the value before update.
      val previousValueFromTriplestore: ReadValueV2 = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1,
        checkLastModDateChanged = false,
      )

      // Update the value.

      val intValue = 5

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIri.get,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
                comment = Some(comment),
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )

      intValueIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val updatedValueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1,
      )

      updatedValueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          updatedValueFromTriplestore.permissions should ===(previousValueFromTriplestore.permissions)
          updatedValueFromTriplestore.valueHasUUID should ===(previousValueFromTriplestore.valueHasUUID)
          assert(updatedValueFromTriplestore.valueContent.comment.contains(comment))

        case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
      }

      // Check that the permissions and UUID were deleted from the previous version of the value.
      assert(getValueUUID(previousValueFromTriplestore.valueIri).isEmpty)
      assert(getValuePermissions(previousValueFromTriplestore.valueIri).isEmpty)
    }

    "create an integer value with a comment" in {
      // Add the value.

      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue                                  = 8
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)
      val comment                                   = "Initial comment"

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
                comment = Some(comment),
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      intValueIri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          assert(savedValue.comment.contains(comment))

        case _ => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
      }
    }

    "create an integer value with custom permissions" in {
      // Add the value.

      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue                                  = 1
      val permissions                               = "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
              permissions = Some(permissions),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      intValueIriWithCustomPermissions.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIriWithCustomPermissions.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          PermissionUtilADM.parsePermissions(valueFromTriplestore.permissions) should ===(
            PermissionUtilADM.parsePermissions(permissions),
          )

        case _ => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
      }
    }

    "not create an integer value with syntactically invalid custom permissions" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue              = 1024
      val permissions           = "M knora-admin:Creator,V knora-admin:KnownUser"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
              permissions = Some(permissions),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      assertFailsWithA[BadRequestException](actual)
    }

    "not create an integer value with custom permissions referring to a nonexistent group" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue              = 1024
      val permissions           = "M knora-admin:Creator|V http://rdfh.ch/groups/0001/nonexistent-group"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
              permissions = Some(permissions),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      assertFailsWithA[NotFoundException](actual)
    }

    "create an integer value with custom UUID and creation date" in {
      // Add the value.

      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue                                  = 987
      val valueUUID                                 = randomUUID
      val valueCreationDate                         = Instant.now
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
              valueUUID = Some(valueUUID),
              valueCreationDate = Some(valueCreationDate),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      intValueForRsyncIri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueForRsyncIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          valueFromTriplestore.valueHasUUID should ===(valueUUID)
          valueFromTriplestore.valueCreationDate should ===(valueCreationDate)

        case _ => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
      }
    }

    "not update an integer value with a custom creation date that is earlier than the date of the current version" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue              = 989
      val valueCreationDate     = Instant.parse("2019-11-29T10:00:00Z")

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueForRsyncIri.get,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
              valueCreationDate = Some(valueCreationDate),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[BadRequestException](actual)
    }

    "update an integer value with a custom creation date" in {
      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      // Update the value.

      val intValue          = 988
      val valueCreationDate = Instant.now

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueForRsyncIri.get,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
              valueCreationDate = Some(valueCreationDate),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )

      intValueForRsyncIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val updatedValueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueForRsyncIri.get,
        requestingUser = anythingUser1,
      )

      updatedValueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          updatedValueFromTriplestore.valueCreationDate should ===(valueCreationDate)

        case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
      }
    }

    "update an integer value with a custom new version IRI" in {
      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      // Update the value.

      val intValue                = 1000
      val newValueVersionIri: IRI = stringFormatter.makeRandomValueIri(resourceIri)

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueForRsyncIri.get,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
              newValueVersionIri = Some(newValueVersionIri.toSmartIri),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      intValueForRsyncIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val updatedValueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueForRsyncIri.get,
        requestingUser = anythingUser1,
      )

      updatedValueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          updatedValueFromTriplestore.valueIri should ===(newValueVersionIri)

        case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
      }
    }

    "not create a value if the user does not have modify permission on the resource" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue              = 5

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
            ),
            requestingUser = incunabulaUser,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[ForbiddenException](actual)
    }

    "create a text value without standoff" in {
      val valueHasString                            = "Comment 1a"
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = zeitglöckleinIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some(valueHasString),
                textValueType = TextValueType.UnformattedText,
              ),
            ),
            requestingUser = incunabulaUser,
            apiRequestID = randomUUID,
          ),
        ),
      )

      zeitglöckleinCommentWithoutStandoffIri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = zeitglöckleinIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = zeitglöckleinCommentWithoutStandoffIri.get,
        requestingUser = incunabulaUser,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TextValueContentV2 => assert(savedValue.valueHasString.contains(valueHasString))
        case _                              => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate text value without standoff" in {
      val valueHasString        = "Comment 1a"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = zeitglöckleinIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some(valueHasString),
                textValueType = TextValueType.UnformattedText,
              ),
            ),
            requestingUser = incunabulaUser,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "create a text value with a comment" in {
      val valueHasString                            = "this is a text value that has a comment"
      val valueHasComment                           = "this is a comment"
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = zeitglöckleinIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some(valueHasString),
                comment = Some(valueHasComment),
                textValueType = TextValueType.UnformattedText,
              ),
            ),
            requestingUser = incunabulaUser,
            apiRequestID = randomUUID,
          ),
        ),
      )

      zeitglöckleinCommentWithCommentIri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = zeitglöckleinIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = zeitglöckleinCommentWithCommentIri.get,
        requestingUser = incunabulaUser,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TextValueContentV2 =>
          assert(savedValue.valueHasString.contains(valueHasString))
          savedValue.comment should ===(Some(valueHasComment))

        case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
      }
    }

    "create a text value with standoff" in {
      val valueHasString = "Comment 1aa"

      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = zeitglöckleinIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some(valueHasString),
                standoff = sampleStandoff,
                mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
                mapping = standardMapping,
                textValueType = TextValueType.FormattedText,
              ),
            ),
            requestingUser = incunabulaUser,
            apiRequestID = randomUUID,
          ),
        ),
      )

      zeitglöckleinCommentWithStandoffIri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = zeitglöckleinIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = zeitglöckleinCommentWithStandoffIri.get,
        requestingUser = incunabulaUser,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TextValueContentV2 =>
          assert(savedValue.valueHasString.contains(valueHasString))
          savedValue.standoff should ===(sampleStandoff)
          assert(savedValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"))
          assert(savedValue.mapping == standardMapping)

        case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate text value with standoff (even if the standoff is different)" in {
      val valueHasString = "Comment 1aa"

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = zeitglöckleinIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some(valueHasString),
                standoff = sampleStandoffModified,
                mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
                mapping = standardMapping,
                textValueType = TextValueType.FormattedText,
              ),
            ),
            requestingUser = incunabulaUser,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "create a decimal value" in {
      // Add the value.

      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
      val valueHasDecimal                           = BigDecimal("4.3")
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = DecimalValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasDecimal = valueHasDecimal,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      decimalValueIri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = decimalValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: DecimalValueContentV2 => savedValue.valueHasDecimal should ===(valueHasDecimal)
        case _                                 => throw AssertionException(s"Expected decimal value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate decimal value" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
      val valueHasDecimal       = BigDecimal("4.3")

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = DecimalValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasDecimal = valueHasDecimal,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "create a time value" in {
      // Add the value.

      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasTimeStamp".toSmartIri
      val valueHasTimeStamp                         = Instant.parse("2019-08-28T15:59:12.725007Z")
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = TimeValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasTimeStamp = valueHasTimeStamp,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      timeValueIri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = timeValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TimeValueContentV2 => savedValue.valueHasTimeStamp should ===(valueHasTimeStamp)
        case _                              => throw AssertionException(s"Expected time value, got $valueFromTriplestore")
      }
    }

    "create a date value" in {
      // Add the value.

      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val submittedValueContent = DateValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasCalendar = CalendarNameGregorian,
        valueHasStartJDN = 2264907,
        valueHasStartPrecision = DatePrecisionYear,
        valueHasEndJDN = 2265271,
        valueHasEndPrecision = DatePrecisionYear,
      )

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = submittedValueContent,
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      dateValueIri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = dateValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: DateValueContentV2 =>
          savedValue.valueHasCalendar should ===(submittedValueContent.valueHasCalendar)
          savedValue.valueHasStartJDN should ===(submittedValueContent.valueHasStartJDN)
          savedValue.valueHasStartPrecision should ===(submittedValueContent.valueHasStartPrecision)
          savedValue.valueHasEndJDN should ===(submittedValueContent.valueHasEndJDN)
          savedValue.valueHasEndPrecision should ===(submittedValueContent.valueHasEndPrecision)

        case _ => throw AssertionException(s"Expected date value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate date value" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri

      val submittedValueContent = DateValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasCalendar = CalendarNameGregorian,
        valueHasStartJDN = 2264907,
        valueHasStartPrecision = DatePrecisionYear,
        valueHasEndJDN = 2265271,
        valueHasEndPrecision = DatePrecisionYear,
      )

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = submittedValueContent,
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "create a boolean value" in {
      // Add the value.

      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
      val valueHasBoolean                           = true
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = BooleanValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasBoolean = valueHasBoolean,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      booleanValueIri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = booleanValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: BooleanValueContentV2 => savedValue.valueHasBoolean should ===(valueHasBoolean)
        case _                                 => throw AssertionException(s"Expected boolean value, got $valueFromTriplestore")
      }
    }

    "create a geometry value" in {
      // Add the value.

      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
      val valueHasGeometry =
        """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = GeomValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasGeometry = valueHasGeometry,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      geometryValueIri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = geometryValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: GeomValueContentV2 => savedValue.valueHasGeometry should ===(valueHasGeometry)
        case _                              => throw AssertionException(s"Expected geometry value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate geometry value" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
      val valueHasGeometry =
        """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = GeomValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasGeometry = valueHasGeometry,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "create an interval value" in {
      // Add the value.

      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
      val valueHasIntervalStart                     = BigDecimal("1.2")
      val valueHasIntervalEnd                       = BigDecimal("3")
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = IntervalValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasIntervalStart = valueHasIntervalStart,
                valueHasIntervalEnd = valueHasIntervalEnd,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      intervalValueIri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intervalValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: IntervalValueContentV2 =>
          savedValue.valueHasIntervalStart should ===(valueHasIntervalStart)
          savedValue.valueHasIntervalEnd should ===(valueHasIntervalEnd)

        case _ => throw AssertionException(s"Expected interval value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate interval value" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
      val valueHasIntervalStart = BigDecimal("1.2")
      val valueHasIntervalEnd   = BigDecimal("3")

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = IntervalValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasIntervalStart = valueHasIntervalStart,
                valueHasIntervalEnd = valueHasIntervalEnd,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "create a list value" in {
      // Add the value.

      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode                          = "http://rdfh.ch/lists/0001/treeList03"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = HierarchicalListValueContentV2(ApiV2Complex, valueHasListNode, None, None),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      listValueIri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = listValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: HierarchicalListValueContentV2 =>
          savedValue.valueHasListNode should ===(valueHasListNode)

        case _ => throw AssertionException(s"Expected list value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate list value" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode      = "http://rdfh.ch/lists/0001/treeList03"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = HierarchicalListValueContentV2(ApiV2Complex, valueHasListNode, None, None),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "not create a list value referring to a nonexistent list node" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode      = "http://rdfh.ch/lists/0001/nonexistent"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = HierarchicalListValueContentV2(ApiV2Complex, valueHasListNode, None, None),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[NotFoundException](actual)
    }

    "not create a list value that is a root list node" in {
      val resourceIri           = "http://rdfh.ch/0001/a-blue-thing"
      val resourceClassIri      = "http://www.knora.org/ontology/0001/anything#BlueThing".toSmartIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode      = "http://rdfh.ch/lists/0001/otherTreeList"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = resourceClassIri,
              propertyIri = propertyIri,
              valueContent = HierarchicalListValueContentV2(ApiV2Complex, valueHasListNode, None, None),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[BadRequestException](actual)
    }

    "create a color value" in {
      // Add the value.

      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
      val valueHasColor                             = "#ff3333"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = ColorValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasColor = valueHasColor,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      colorValueIri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = colorValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: ColorValueContentV2 =>
          savedValue.valueHasColor should ===(valueHasColor)

        case _ => throw AssertionException(s"Expected color value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate color value" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
      val valueHasColor         = "#ff3333"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = ColorValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasColor = valueHasColor,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "create a URI value" in {
      // Add the value.

      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
      val valueHasUri                               = "https://www.knora.org"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = UriValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasUri = valueHasUri,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      uriValueIri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = uriValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: UriValueContentV2 =>
          savedValue.valueHasUri should ===(valueHasUri)

        case _ => throw AssertionException(s"Expected URI value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate URI value" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
      val valueHasUri           = "https://www.knora.org"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = UriValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasUri = valueHasUri,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "create a geoname value" in {
      // Add the value.

      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
      val valueHasGeonameCode                       = "2661604"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = GeonameValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasGeonameCode = valueHasGeonameCode,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )

      geonameValueIri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = geonameValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: GeonameValueContentV2 =>
          savedValue.valueHasGeonameCode should ===(valueHasGeonameCode)

        case _ => throw AssertionException(s"Expected GeoNames value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate geoname value" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
      val valueHasGeonameCode   = "2661604"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = GeonameValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasGeonameCode = valueHasGeonameCode,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "create a link between two resources" in {
      val resourceIri: IRI                          = "http://rdfh.ch/0803/cb1a74e3e2f6"
      val linkPropertyIri: SmartIri                 = OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri
      val linkValuePropertyIri: SmartIri            = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              propertyIri = linkValuePropertyIri,
              resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
              valueContent = LinkValueContentV2(
                ontologySchema = ApiV2Complex,
                referredResourceIri = zeitglöckleinIri,
              ),
            ),
            requestingUser = incunabulaUser,
            apiRequestID = randomUUID,
          ),
        ),
      )
      linkValueIri.set(createValueResponse.valueIri)
      linkValueUUID = createValueResponse.valueUUID

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = linkPropertyIri,
        propertyIriInResult = linkValuePropertyIri,
        expectedValueIri = linkValueIri.get,
        requestingUser = incunabulaUser,
      )

      valueFromTriplestore match {
        case readLinkValueV2: ReadLinkValueV2 =>
          readLinkValueV2.valueContent.referredResourceIri should ===(zeitglöckleinIri)
          readLinkValueV2.valueHasRefCount should ===(1)

        case _ => throw AssertionException(s"Expected link value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate link" in {
      val resourceIri: IRI               = "http://rdfh.ch/0803/cb1a74e3e2f6"
      val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
              propertyIri = linkValuePropertyIri,
              valueContent = LinkValueContentV2(
                ontologySchema = ApiV2Complex,
                referredResourceIri = zeitglöckleinIri,
              ),
            ),
            requestingUser = incunabulaUser,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "not accept a link property in a request to create a link between two resources" in {
      val resourceIri: IRI          = "http://rdfh.ch/0803/cb1a74e3e2f6"
      val linkPropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
              propertyIri = linkPropertyIri,
              valueContent = LinkValueContentV2(
                ontologySchema = ApiV2Complex,
                referredResourceIri = zeitglöckleinIri,
              ),
            ),
            requestingUser = incunabulaUser,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[BadRequestException](actual)
    }

    "not create a standoff link directly" in {
      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = zeitglöckleinIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
              valueContent = LinkValueContentV2(
                ontologySchema = ApiV2Complex,
                referredResourceIri = generationeIri,
              ),
            ),
            requestingUser = SharedTestDataADM.superUser,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[BadRequestException](actual)
    }

    "not add a new value to a nonexistent resource" in {
      val resourceIri: IRI      = "http://rdfh.ch/0001/nonexistent"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue              = 6

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[NotFoundException](actual)
    }

    "not add a new value to a deleted resource" in {
      val resourceIri: IRI      = "http://rdfh.ch/0803/9935159f67"
      val valueHasString        = "Comment 2"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some(valueHasString),
                textValueType = TextValueType.UnformattedText,
              ),
            ),
            requestingUser = incunabulaUser,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[NotFoundException](actual)
    }

    "not add a new value if the resource's rdf:type is not correctly given" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue              = 2048

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[BadRequestException](actual)
    }

    "not add a new value of the wrong type" in {
      val resourceIri: IRI      = "http://rdfh.ch/0803/21abac2162"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#pubdate".toSmartIri

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some("this is not a date"),
                textValueType = TextValueType.UnformattedText,
              ),
            ),
            requestingUser = incunabulaUser,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[OntologyConstraintException](actual)
    }

    "not add a new value that would violate a cardinality restriction" in {
      val resourceIri: IRI = "http://rdfh.ch/0803/4f11adaf"

      // The cardinality of incunabula:partOf in incunabula:page is 1, and page http://rdfh.ch/0803/4f11adaf is already part of a book.

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page".toSmartIri,
              propertyIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue".toSmartIri,
              valueContent = LinkValueContentV2(
                ontologySchema = ApiV2Complex,
                referredResourceIri = "http://rdfh.ch/0803/e41ab5695c",
              ),
            ),
            requestingUser = incunabulaUser,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[OntologyConstraintException](actual)

      // The cardinality of incunabula:seqnum in incunabula:page is 0-1, and page http://rdfh.ch/0803/4f11adaf already has a seqnum.

      val actual2 = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = "http://rdfh.ch/0803/4f11adaf",
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page".toSmartIri,
              propertyIri = "http://www.knora.org/ontology/0803/incunabula#seqnum".toSmartIri,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = 1,
              ),
            ),
            requestingUser = incunabulaUser,
            apiRequestID = randomUUID,
          ),
        ),
      )
      assertFailsWithA[OntologyConstraintException](actual2)
    }

    "add a new text value containing a Standoff resource reference, and create a hasStandoffLinkTo direct link and a corresponding LinkValue" in {
      val resourceIri: IRI                          = "http://rdfh.ch/0803/21abac2162"
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val valueHasString                            = "This comment refers to another resource"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

      val standoff = Seq(
        StandoffTagV2(
          dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
          standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag.toSmartIri,
          startPosition = 31,
          endPosition = 39,
          attributes = Vector(
            StandoffTagIriAttributeV2(
              standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink.toSmartIri,
              value = zeitglöckleinIri,
            ),
          ),
          uuid = randomUUID(),
          originalXMLID = None,
          startIndex = 0,
        ),
      )

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some(valueHasString),
                standoff = standoff,
                mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
                mapping = standardMapping,
                textValueType = TextValueType.FormattedText,
              ),
            ),
            requestingUser = incunabulaUser,
            apiRequestID = randomUUID,
          ),
        ),
      )

      lobComment1Iri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val updatedResource = getResourceWithValues(
        resourceIri = resourceIri,
        propertyIrisForGravsearch = Seq(propertyIri, OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri),
        requestingUser = incunabulaUser,
      )

      checkLastModDate(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        maybeUpdatedLastModDate = updatedResource.lastModificationDate,
      )

      val textValueFromTriplestore: ReadValueV2 = getValueFromResource(
        resource = updatedResource,
        propertyIriInResult = propertyIri,
        expectedValueIri = lobComment1Iri.get,
      )

      textValueFromTriplestore.valueContent match {
        case savedTextValue: TextValueContentV2 =>
          assert(savedTextValue.valueHasString.contains(valueHasString))
          savedTextValue.standoff should ===(standoff)
          assert(savedTextValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"))
          savedTextValue.mapping should ===(standardMapping)

        case _ => throw AssertionException(s"Expected text value, got $textValueFromTriplestore")
      }

      // Since this is the first Standoff resource reference between the source and target resources, we should
      // now have version 1 of a LinkValue (it should have no previous version), with a reference count of 1.

      val linkValuesFromTripletore: Seq[ReadValueV2] = getValuesFromResource(
        resource = updatedResource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
      )

      assert(linkValuesFromTripletore.size == 1)

      val linkValueFromTriplestore: ReadLinkValueV2 = linkValuesFromTripletore.head match {
        case readLinkValueV2: ReadLinkValueV2 => readLinkValueV2
        case other                            => throw AssertionException(s"Expected link value, got $other")
      }

      linkValueFromTriplestore.previousValueIri.isEmpty should ===(true)
      linkValueFromTriplestore.valueHasRefCount should ===(1)
      linkValueFromTriplestore.valueContent.referredResourceIri should ===(zeitglöckleinIri)
      standoffLinkValueIri.set(linkValueFromTriplestore.valueIri)
    }

    "add another new text value containing a Standoff resource reference, and make a new version of the LinkValue" in {
      val resourceIri: IRI                          = "http://rdfh.ch/0803/21abac2162"
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val valueHasString                            = "This remark refers to another resource"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

      val standoff = Seq(
        StandoffTagV2(
          dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
          standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag.toSmartIri,
          startPosition = 30,
          endPosition = 38,
          attributes = Vector(
            StandoffTagIriAttributeV2(
              standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink.toSmartIri,
              value = zeitglöckleinIri,
            ),
          ),
          uuid = randomUUID(),
          originalXMLID = None,
          startIndex = 0,
        ),
      )

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some(valueHasString),
                standoff = standoff,
                mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
                mapping = standardMapping,
                textValueType = TextValueType.FormattedText,
              ),
            ),
            requestingUser = incunabulaUser,
            apiRequestID = randomUUID,
          ),
        ),
      )

      lobComment2Iri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val updatedResource = getResourceWithValues(
        resourceIri = resourceIri,
        propertyIrisForGravsearch = Seq(propertyIri, OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri),
        requestingUser = incunabulaUser,
      )

      checkLastModDate(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        maybeUpdatedLastModDate = updatedResource.lastModificationDate,
      )

      val textValueFromTriplestore: ReadValueV2 = getValueFromResource(
        resource = updatedResource,
        propertyIriInResult = propertyIri,
        expectedValueIri = lobComment2Iri.get,
      )

      textValueFromTriplestore.valueContent match {
        case savedTextValue: TextValueContentV2 =>
          assert(savedTextValue.valueHasString.contains(valueHasString))
          savedTextValue.standoff should ===(standoff)
          assert(savedTextValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"))
          savedTextValue.mapping should ===(standardMapping)

        case _ => throw AssertionException(s"Expected text value, got $textValueFromTriplestore")
      }

      // Now that we've added a different TextValue that refers to the same resource, we should have version 2
      // of the LinkValue, with a reference count of 2. It should have a previousValue pointing to the previous
      // version.

      val linkValuesFromTripletore: Seq[ReadValueV2] = getValuesFromResource(
        resource = updatedResource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
      )

      assert(linkValuesFromTripletore.size == 1)

      val linkValueFromTriplestore: ReadLinkValueV2 = linkValuesFromTripletore.head match {
        case readLinkValueV2: ReadLinkValueV2 => readLinkValueV2
        case other                            => throw AssertionException(s"Expected link value, got $other")
      }

      linkValueFromTriplestore.previousValueIri.contains(standoffLinkValueIri.get) should ===(true)
      linkValueFromTriplestore.valueHasRefCount should ===(2)
      linkValueFromTriplestore.valueContent.referredResourceIri should ===(zeitglöckleinIri)
      standoffLinkValueIri.set(linkValueFromTriplestore.valueIri)
    }

    "not update a value if an outdated value IRI is given" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue              = 3

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = firstIntValueVersionIri.get,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[NotFoundException](actual)
    }

    "not update a value if the user does not have modify permission on the value" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue              = 9

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIri.get,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
            ),
            incunabulaUser,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[ForbiddenException](actual)
    }

    "update a value with custom permissions" in {
      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val permissions                               = "CR knora-admin:Creator|V knora-admin:ProjectMember"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)
      val intValue                                  = 6

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIri.get,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
              permissions = Some(permissions),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )

      intValueIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val updatedValueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1,
      )

      updatedValueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          updatedValueFromTriplestore.permissions should ===(permissions)

        case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
      }
    }

    "not update a value with custom permissions if the requesting user does not have Permission.ObjectAccess.ChangeRights on the value" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val permissions           = "CR knora-admin:Creator"
      val intValue              = 10

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIri.get,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
              permissions = Some(permissions),
            ),
            anythingUser2,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[ForbiddenException](actual)
    }

    "not update a value with syntactically invalid custom permissions" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val permissions           = "M knora-admin:Creator,V knora-admin:KnownUser"
      val intValue              = 7

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIri.get,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
              permissions = Some(permissions),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[BadRequestException](actual)
    }

    "not update a value with custom permissions referring to a nonexistent group" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val permissions           = "M knora-admin:Creator|V http://rdfh.ch/groups/0001/nonexistent-group"
      val intValue              = 8

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIri.get,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
              permissions = Some(permissions),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[NotFoundException](actual)
    }

    "update a value, changing only its permissions" in {
      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val permissions                               = "CR knora-admin:Creator|V knora-admin:KnownUser"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val oldValueFromTriplestore: ReadValueV2 = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = None,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1,
        checkLastModDateChanged = false,
      )

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValuePermissionsV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIri.get,
              valueType = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri,
              permissions = permissions,
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )

      intValueIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val updatedValueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1,
      )

      updatedValueFromTriplestore.valueContent should ===(oldValueFromTriplestore.valueContent)
      updatedValueFromTriplestore.permissions should ===(permissions)
    }

    "not update a value, changing only its permissions, if the requesting user does not have Permission.ObjectAccess.ChangeRights on the value" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val permissions           = "CR knora-admin:Creator"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValuePermissionsV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIri.get,
              valueType = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri,
              permissions = permissions,
            ),
            anythingUser2,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[ForbiddenException](actual)
    }

    "not update a value, changing only its permissions, with syntactically invalid custom permissions" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val permissions           = "M knora-admin:Creator,V knora-admin:KnownUser"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValuePermissionsV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIri.get,
              valueType = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri,
              permissions = permissions,
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[BadRequestException](actual)
    }

    "not update a value, changing only its permissions, with permissions referring to a nonexistent group" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val permissions           = "M knora-admin:Creator|V http://rdfh.ch/groups/0001/nonexistent-group"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValuePermissionsV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIri.get,
              valueType = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri,
              permissions = permissions,
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[NotFoundException](actual)
    }

    "not update an integer value, giving it the same value as another integer value of the same property" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue              = 1

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIri.get,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "update a text value (without submitting standoff)" in {
      val valueHasString                            = "This updated comment has no standoff"
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = zeitglöckleinIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueIri = zeitglöckleinCommentWithoutStandoffIri.get,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some(valueHasString),
                textValueType = TextValueType.UnformattedText,
              ),
            ),
            incunabulaUser,
            randomUUID,
          ),
        ),
      )
      zeitglöckleinCommentWithoutStandoffIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = zeitglöckleinIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = zeitglöckleinCommentWithoutStandoffIri.get,
        requestingUser = incunabulaUser,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TextValueContentV2 => assert(savedValue.valueHasString.contains(valueHasString))
        case _                              => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
      }
    }

    "update a text value (submitting standoff)" in {
      val valueHasString = "Comment 1ab"

      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = zeitglöckleinIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueIri = zeitglöckleinCommentWithStandoffIri.get,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some(valueHasString),
                standoff = sampleStandoffWithLink,
                mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
                mapping = standardMapping,
                textValueType = TextValueType.FormattedText,
              ),
            ),
            incunabulaUser,
            randomUUID,
          ),
        ),
      )
      zeitglöckleinCommentWithStandoffIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = zeitglöckleinIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = zeitglöckleinCommentWithStandoffIri.get,
        requestingUser = incunabulaUser,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TextValueContentV2 =>
          assert(savedValue.valueHasString.contains(valueHasString))
          savedValue.standoff should ===(sampleStandoffWithLink)
          assert(savedValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"))
          savedValue.mapping should ===(standardMapping)

        case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
      }

      // There should be a link value for a standoff link.

      val resource = getResourceWithValues(
        resourceIri = zeitglöckleinIri,
        propertyIrisForGravsearch = Seq(OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri),
        requestingUser = incunabulaUser,
      )

      val standoffLinkValues: Seq[ReadValueV2] = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
      )

      assert(standoffLinkValues.size == 1)
      val standoffLinkValueFromTriplestore = standoffLinkValues.head

      standoffLinkValueFromTriplestore.valueContent match {
        case linkValueContentV2: LinkValueContentV2 =>
          standoffLinkValueIri.set(standoffLinkValueFromTriplestore.valueIri)
          assert(linkValueContentV2.referredResourceIri == aThingIri)

        case _ => throw AssertionException(s"Expected a link value, got $standoffLinkValueFromTriplestore")
      }
    }

    "not update a text value, duplicating an existing text value (without submitting standoff)" in {
      val valueHasString        = "this is a text value that has a comment"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = zeitglöckleinIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueIri = zeitglöckleinCommentWithoutStandoffIri.get,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some(valueHasString),
                textValueType = TextValueType.UnformattedText,
              ),
            ),
            incunabulaUser,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "create a second text value with standoff" in {
      val valueHasString = "Comment 1ac"

      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

      val createValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.createValueV2(
            CreateValueV2(
              resourceIri = zeitglöckleinIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some(valueHasString),
                standoff = sampleStandoff,
                mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
                mapping = standardMapping,
                textValueType = TextValueType.FormattedText,
              ),
            ),
            requestingUser = incunabulaUser,
            apiRequestID = randomUUID,
          ),
        ),
      )

      zeitglöckleinSecondCommentWithStandoffIri.set(createValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = zeitglöckleinIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = zeitglöckleinSecondCommentWithStandoffIri.get,
        requestingUser = incunabulaUser,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TextValueContentV2 =>
          assert(savedValue.valueHasString.contains(valueHasString))
          savedValue.standoff should ===(sampleStandoff)
          assert(savedValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"))
          savedValue.mapping should ===(standardMapping)

        case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
      }
    }

    "not update a text value, duplicating an existing text value (submitting standoff)" in {
      val valueHasString = "Comment 1ac"

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = zeitglöckleinIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueIri = zeitglöckleinCommentWithStandoffIri.get,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some(valueHasString),
                standoff = sampleStandoff,
                mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
                mapping = standardMapping,
                textValueType = TextValueType.FormattedText,
              ),
            ),
            incunabulaUser,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "update a text value, changing only the standoff" in {
      val valueHasString = "Comment 1ac"

      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = zeitglöckleinIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueIri = zeitglöckleinSecondCommentWithStandoffIri.get,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some(valueHasString),
                standoff = sampleStandoffModified,
                mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
                mapping = standardMapping,
                textValueType = TextValueType.FormattedText,
              ),
            ),
            incunabulaUser,
            randomUUID,
          ),
        ),
      )

      zeitglöckleinSecondCommentWithStandoffIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = zeitglöckleinIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = zeitglöckleinSecondCommentWithStandoffIri.get,
        requestingUser = incunabulaUser,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TextValueContentV2 =>
          assert(savedValue.valueHasString.contains(valueHasString))
          savedValue.standoff should ===(sampleStandoffModified)
          assert(savedValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"))
          savedValue.mapping should ===(standardMapping)

        case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
      }
    }

    "not update a text value so it differs only from an existing value in that it has different standoff" in {
      val valueHasString = "Comment 1ac"

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = zeitglöckleinIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueIri = zeitglöckleinCommentWithStandoffIri.get,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some(valueHasString),
                standoff = sampleStandoffModified,
                mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
                mapping = standardMapping,
                textValueType = TextValueType.FormattedText,
              ),
            ),
            incunabulaUser,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "not update a text value without changing it (without submitting standoff)" in {
      val valueHasString        = "This updated comment has no standoff"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = zeitglöckleinIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
              propertyIri = propertyIri,
              valueIri = zeitglöckleinCommentWithoutStandoffIri.get,
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some(valueHasString),
                textValueType = TextValueType.UnformattedText,
              ),
            ),
            incunabulaUser,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "update a decimal value" in {
      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
      val valueHasDecimal                           = BigDecimal("3.1415926")
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = decimalValueIri.get,
              valueContent = DecimalValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasDecimal = valueHasDecimal,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      decimalValueIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = decimalValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: DecimalValueContentV2 => savedValue.valueHasDecimal should ===(valueHasDecimal)
        case _                                 => throw AssertionException(s"Expected decimal value, got $valueFromTriplestore")
      }
    }

    "not update a decimal value without changing it" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
      val valueHasDecimal       = BigDecimal("3.1415926")

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = decimalValueIri.get,
              valueContent = DecimalValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasDecimal = valueHasDecimal,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "update a time value" in {
      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasTimeStamp".toSmartIri
      val valueHasTimeStamp                         = Instant.parse("2019-08-28T16:01:46.952237Z")
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = timeValueIri.get,
              valueContent = TimeValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasTimeStamp = valueHasTimeStamp,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      timeValueIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = timeValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TimeValueContentV2 => savedValue.valueHasTimeStamp should ===(valueHasTimeStamp)
        case _                              => throw AssertionException(s"Expected time value, got $valueFromTriplestore")
      }
    }

    "not update a time value without changing it" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasTimeStamp".toSmartIri
      val valueHasTimeStamp     = Instant.parse("2019-08-28T16:01:46.952237Z")

      val value = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = timeValueIri.get,
              valueContent = TimeValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasTimeStamp = valueHasTimeStamp,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](value)
    }

    "update a date value" in {
      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val submittedValueContent = DateValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasCalendar = CalendarNameGregorian,
        valueHasStartJDN = 2264908,
        valueHasStartPrecision = DatePrecisionYear,
        valueHasEndJDN = 2265272,
        valueHasEndPrecision = DatePrecisionYear,
      )

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = dateValueIri.get,
              valueContent = submittedValueContent,
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )

      dateValueIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = dateValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: DateValueContentV2 =>
          savedValue.valueHasCalendar should ===(submittedValueContent.valueHasCalendar)
          savedValue.valueHasStartJDN should ===(submittedValueContent.valueHasStartJDN)
          savedValue.valueHasStartPrecision should ===(submittedValueContent.valueHasStartPrecision)
          savedValue.valueHasEndJDN should ===(submittedValueContent.valueHasEndJDN)
          savedValue.valueHasEndPrecision should ===(submittedValueContent.valueHasEndPrecision)

        case _ => throw AssertionException(s"Expected date value, got $valueFromTriplestore")
      }
    }

    "not update a date value without changing it" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri

      val submittedValueContent = DateValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasCalendar = CalendarNameGregorian,
        valueHasStartJDN = 2264908,
        valueHasStartPrecision = DatePrecisionYear,
        valueHasEndJDN = 2265272,
        valueHasEndPrecision = DatePrecisionYear,
      )

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = dateValueIri.get,
              valueContent = submittedValueContent,
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "update a boolean value" in {
      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
      val valueHasBoolean                           = false
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = booleanValueIri.get,
              valueContent = BooleanValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasBoolean = valueHasBoolean,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      booleanValueIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = booleanValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: BooleanValueContentV2 => savedValue.valueHasBoolean should ===(valueHasBoolean)
        case _                                 => throw AssertionException(s"Expected boolean value, got $valueFromTriplestore")
      }
    }

    "not update a boolean value without changing it" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
      val valueHasBoolean       = false

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = booleanValueIri.get,
              valueContent = BooleanValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasBoolean = valueHasBoolean,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "update a geometry value" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
      val valueHasGeometry =
        """{"status":"active","lineColor":"#ff3334","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = geometryValueIri.get,
              valueContent = GeomValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasGeometry = valueHasGeometry,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      geometryValueIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = geometryValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: GeomValueContentV2 => savedValue.valueHasGeometry should ===(valueHasGeometry)
        case _                              => throw AssertionException(s"Expected geometry value, got $valueFromTriplestore")
      }
    }

    "not update a geometry value without changing it" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
      val valueHasGeometry =
        """{"status":"active","lineColor":"#ff3334","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = geometryValueIri.get,
              valueContent = GeomValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasGeometry = valueHasGeometry,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "update an interval value" in {
      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
      val valueHasIntervalStart                     = BigDecimal("1.23")
      val valueHasIntervalEnd                       = BigDecimal("3.45")
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intervalValueIri.get,
              valueContent = IntervalValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasIntervalStart = valueHasIntervalStart,
                valueHasIntervalEnd = valueHasIntervalEnd,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      intervalValueIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intervalValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: IntervalValueContentV2 =>
          savedValue.valueHasIntervalStart should ===(valueHasIntervalStart)
          savedValue.valueHasIntervalEnd should ===(valueHasIntervalEnd)

        case _ => throw AssertionException(s"Expected interval value, got $valueFromTriplestore")
      }
    }

    "not update an interval value without changing it" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
      val valueHasIntervalStart = BigDecimal("1.23")
      val valueHasIntervalEnd   = BigDecimal("3.45")

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intervalValueIri.get,
              valueContent = IntervalValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasIntervalStart = valueHasIntervalStart,
                valueHasIntervalEnd = valueHasIntervalEnd,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "update a list value" in {
      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode                          = "http://rdfh.ch/lists/0001/treeList02"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = listValueIri.get,
              valueContent = HierarchicalListValueContentV2(ApiV2Complex, valueHasListNode, None, None),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      listValueIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = listValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: HierarchicalListValueContentV2 =>
          savedValue.valueHasListNode should ===(valueHasListNode)

        case _ => throw AssertionException(s"Expected list value, got $valueFromTriplestore")
      }
    }

    "not update a list value without changing it" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode      = "http://rdfh.ch/lists/0001/treeList02"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = listValueIri.get,
              valueContent = HierarchicalListValueContentV2(ApiV2Complex, valueHasListNode, None, None),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "not update a list value with the IRI of a nonexistent list node" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode      = "http://rdfh.ch/lists/0001/nonexistent"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = listValueIri.get,
              valueContent = HierarchicalListValueContentV2(ApiV2Complex, valueHasListNode, None, None),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[NotFoundException](actual)
    }

    "update a color value" in {
      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
      val valueHasColor                             = "#ff3334"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = colorValueIri.get,
              valueContent = ColorValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasColor = valueHasColor,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      colorValueIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = colorValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: ColorValueContentV2 =>
          savedValue.valueHasColor should ===(valueHasColor)

        case _ => throw AssertionException(s"Expected color value, got $valueFromTriplestore")
      }
    }

    "not update a color value without changing it" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
      val valueHasColor         = "#ff3334"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = colorValueIri.get,
              valueContent = ColorValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasColor = valueHasColor,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "update a URI value" in {
      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
      val valueHasUri                               = "https://en.wikipedia.org"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = uriValueIri.get,
              valueContent = UriValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasUri = valueHasUri,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      uriValueIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = uriValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: UriValueContentV2 =>
          savedValue.valueHasUri should ===(valueHasUri)

        case _ => throw AssertionException(s"Expected URI value, got $valueFromTriplestore")
      }
    }

    "not update a URI value without changing it" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
      val valueHasUri           = "https://en.wikipedia.org"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = uriValueIri.get,
              valueContent = UriValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasUri = valueHasUri,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)
    }

    "update a geoname value" in {
      val resourceIri: IRI                          = aThingIri
      val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
      val valueHasGeonameCode                       = "2988507"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val updateValueResponse = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = geonameValueIri.get,
              valueContent = GeonameValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasGeonameCode = valueHasGeonameCode,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      geonameValueIri.set(updateValueResponse.valueIri)

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = geonameValueIri.get,
        requestingUser = anythingUser1,
      )

      valueFromTriplestore.valueContent match {
        case savedValue: GeonameValueContentV2 =>
          savedValue.valueHasGeonameCode should ===(valueHasGeonameCode)

        case _ => throw AssertionException(s"Expected GeoNames value, got $valueFromTriplestore")
      }
    }

    "not update a geoname value without changing it" in {
      val resourceIri: IRI      = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
      val valueHasGeonameCode   = "2988507"

      val actual = UnsafeZioRun.run(
        ZIO.serviceWithZIO[ValuesResponderV2](
          _.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = geonameValueIri.get,
              valueContent = GeonameValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasGeonameCode = valueHasGeonameCode,
              ),
            ),
            anythingUser1,
            randomUUID,
          ),
        ),
      )
      assertFailsWithA[DuplicateValueException](actual)

    }
  }

  "update a link between two resources" in {
    val resourceIri: IRI                          = "http://rdfh.ch/0803/cb1a74e3e2f6"
    val linkPropertyIri: SmartIri                 = OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri
    val linkValuePropertyIri: SmartIri            = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri
    val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

    val updateValueResponse = UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.updateValueV2(
          UpdateValueContentV2(
            resourceIri = resourceIri,
            resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
            propertyIri = linkValuePropertyIri,
            valueIri = linkValueIri.get,
            valueContent = LinkValueContentV2(
              ontologySchema = ApiV2Complex,
              referredResourceIri = generationeIri,
            ),
          ),
          incunabulaUser,
          randomUUID,
        ),
      ),
    )
    linkValueIri.set(updateValueResponse.valueIri)

    // When you change a link value's target, it gets a new UUID.
    assert(updateValueResponse.valueUUID != linkValueUUID)
    linkValueUUID = updateValueResponse.valueUUID

    val valueFromTriplestore = getValue(
      resourceIri = resourceIri,
      maybePreviousLastModDate = maybeResourceLastModDate,
      propertyIriForGravsearch = linkPropertyIri,
      propertyIriInResult = linkValuePropertyIri,
      expectedValueIri = linkValueIri.get,
      requestingUser = incunabulaUser,
    )

    valueFromTriplestore match {
      case readLinkValueV2: ReadLinkValueV2 =>
        readLinkValueV2.valueContent.referredResourceIri should ===(generationeIri)
        readLinkValueV2.valueHasRefCount should ===(1)

      case _ => throw AssertionException(s"Expected link value, got $valueFromTriplestore")
    }
  }

  "not update a link without a comment without changing it" in {
    val resourceIri: IRI               = "http://rdfh.ch/0803/cb1a74e3e2f6"
    val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri

    val actual = UnsafeZioRun.run(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.updateValueV2(
          UpdateValueContentV2(
            resourceIri = resourceIri,
            resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
            propertyIri = linkValuePropertyIri,
            valueIri = linkValueIri.get,
            valueContent = LinkValueContentV2(
              ontologySchema = ApiV2Complex,
              referredResourceIri = generationeIri,
            ),
          ),
          incunabulaUser,
          randomUUID,
        ),
      ),
    )
    assertFailsWithA[DuplicateValueException](actual)
  }

  "update a link, adding a comment" in {
    val resourceIri: IRI                          = "http://rdfh.ch/0803/cb1a74e3e2f6"
    val linkPropertyIri: SmartIri                 = OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri
    val linkValuePropertyIri: SmartIri            = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri
    val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)
    val comment: String                           = "Adding a comment"

    val updateValueResponse = UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.updateValueV2(
          UpdateValueContentV2(
            resourceIri = resourceIri,
            resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
            propertyIri = linkValuePropertyIri,
            valueIri = linkValueIri.get,
            valueContent = LinkValueContentV2(
              ontologySchema = ApiV2Complex,
              referredResourceIri = generationeIri,
              comment = Some(comment),
            ),
          ),
          incunabulaUser,
          randomUUID,
        ),
      ),
    )

    linkValueIri.set(updateValueResponse.valueIri)

    // Since we only changed metadata, the link should have the same UUID.
    assert(updateValueResponse.valueUUID == linkValueUUID)

    val valueFromTriplestore = getValue(
      resourceIri = resourceIri,
      maybePreviousLastModDate = maybeResourceLastModDate,
      propertyIriForGravsearch = linkPropertyIri,
      propertyIriInResult = linkValuePropertyIri,
      expectedValueIri = linkValueIri.get,
      requestingUser = incunabulaUser,
    )

    valueFromTriplestore match {
      case readLinkValueV2: ReadLinkValueV2 =>
        readLinkValueV2.valueContent.referredResourceIri should ===(generationeIri)
        readLinkValueV2.valueHasRefCount should ===(1)
        assert(readLinkValueV2.valueContent.comment.contains(comment))

      case _ => throw AssertionException(s"Expected link value, got $valueFromTriplestore")
    }
  }

  "not update a link with a comment without changing it" in {
    val resourceIri: IRI               = "http://rdfh.ch/0803/cb1a74e3e2f6"
    val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri
    val comment: String                = "Adding a comment"

    val actual = UnsafeZioRun.run(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.updateValueV2(
          UpdateValueContentV2(
            resourceIri = resourceIri,
            resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
            propertyIri = linkValuePropertyIri,
            valueIri = linkValueIri.get,
            valueContent = LinkValueContentV2(
              ontologySchema = ApiV2Complex,
              referredResourceIri = generationeIri,
              comment = Some(comment),
            ),
          ),
          incunabulaUser,
          randomUUID,
        ),
      ),
    )
    assertFailsWithA[DuplicateValueException](actual)
  }

  "update a link with a comment, changing only the comment" in {
    val resourceIri: IRI                          = "http://rdfh.ch/0803/cb1a74e3e2f6"
    val linkPropertyIri: SmartIri                 = OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri
    val linkValuePropertyIri: SmartIri            = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri
    val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)
    val comment                                   = "An updated comment"

    val updateValueResponse = UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.updateValueV2(
          UpdateValueContentV2(
            resourceIri = resourceIri,
            resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
            propertyIri = linkValuePropertyIri,
            valueIri = linkValueIri.get,
            valueContent = LinkValueContentV2(
              ontologySchema = ApiV2Complex,
              referredResourceIri = generationeIri,
              comment = Some(comment),
            ),
          ),
          incunabulaUser,
          randomUUID,
        ),
      ),
    )

    linkValueIri.set(updateValueResponse.valueIri)

    // Since we only changed metadata, the link should have the same UUID.
    assert(updateValueResponse.valueUUID == linkValueUUID)

    val valueFromTriplestore = getValue(
      resourceIri = resourceIri,
      maybePreviousLastModDate = maybeResourceLastModDate,
      propertyIriForGravsearch = linkPropertyIri,
      propertyIriInResult = linkValuePropertyIri,
      expectedValueIri = linkValueIri.get,
      requestingUser = incunabulaUser,
    )

    valueFromTriplestore match {
      case readLinkValueV2: ReadLinkValueV2 =>
        readLinkValueV2.valueContent.referredResourceIri should ===(generationeIri)
        readLinkValueV2.valueHasRefCount should ===(1)
        assert(readLinkValueV2.valueContent.comment.contains(comment))

      case _ => throw AssertionException(s"Expected link value, got $valueFromTriplestore")
    }
  }

  "create a link with a comment" in {
    val resourceIri: IRI                          = "http://rdfh.ch/0803/cb1a74e3e2f6"
    val linkPropertyIri: SmartIri                 = OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri
    val linkValuePropertyIri: SmartIri            = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri
    val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)
    val comment                                   = "Initial comment"

    val createValueResponse = UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.createValueV2(
          CreateValueV2(
            resourceIri = resourceIri,
            propertyIri = linkValuePropertyIri,
            resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
            valueContent = LinkValueContentV2(
              ontologySchema = ApiV2Complex,
              referredResourceIri = zeitglöckleinIri,
              comment = Some(comment),
            ),
          ),
          requestingUser = incunabulaUser,
          apiRequestID = randomUUID,
        ),
      ),
    )
    linkValueIri.set(createValueResponse.valueIri)

    val valueFromTriplestore = getValue(
      resourceIri = resourceIri,
      maybePreviousLastModDate = maybeResourceLastModDate,
      propertyIriForGravsearch = linkPropertyIri,
      propertyIriInResult = linkValuePropertyIri,
      expectedValueIri = linkValueIri.get,
      requestingUser = incunabulaUser,
    )

    valueFromTriplestore match {
      case readLinkValueV2: ReadLinkValueV2 =>
        readLinkValueV2.valueContent.referredResourceIri should ===(zeitglöckleinIri)
        readLinkValueV2.valueHasRefCount should ===(1)
        assert(readLinkValueV2.valueContent.comment.contains(comment))

      case _ => throw AssertionException(s"Expected link value, got $valueFromTriplestore")
    }
  }

  "not update a standoff link directly" in {
    val actual = UnsafeZioRun.run(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.updateValueV2(
          UpdateValueContentV2(
            resourceIri = zeitglöckleinIri,
            resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
            propertyIri = OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
            valueIri = zeitglöckleinCommentWithStandoffIri.get,
            valueContent = LinkValueContentV2(
              ontologySchema = ApiV2Complex,
              referredResourceIri = generationeIri,
            ),
          ),
          SharedTestDataADM.superUser,
          randomUUID,
        ),
      ),
    )
    assertFailsWithA[BadRequestException](actual)
  }

  "not update a still image file value without changing it" in {
    val resourceIri: IRI = aThingPictureIri
    stillImageFileValueIri.set("http://rdfh.ch/0001/a-thing-picture/values/goZ7JFRNSeqF-dNxsqAS7Q")
    val fileType         = FileType.StillImageFile(dimX = 512, dimY = 256)
    val internalFilename = "B1D0OkEgfFp-Cew2Seur7Wi.jp2"
    val valueIri         = stillImageFileValueIri.get
    val actual = UnsafeZioRun.run(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.updateValueV2(
          UpdateValueContentV2(
            resourceIri,
            thingPictureClassIri.toSmartIri,
            OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri,
            valueIri,
            FileModelUtil.getFileValueContent(
              fileType,
              internalFilename,
              Some(mimeTypeJP2),
              Some("test.tiff"),
              Some(mimeTypeTIFF),
            ),
          ),
          anythingUser1,
          randomUUID,
        ),
      ),
    )
    assertFailsWithA[DuplicateValueException](actual)
  }

  "update a still image file value" in {
    val resourceIri: IRI                          = aThingPictureIri
    val propertyIri: SmartIri                     = OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri
    val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

    // Get the value before update.
    val previousValueFromTriplestore: ReadValueV2 = getValue(
      resourceIri = resourceIri,
      maybePreviousLastModDate = maybeResourceLastModDate,
      propertyIriForGravsearch = propertyIri,
      propertyIriInResult = propertyIri,
      expectedValueIri = stillImageFileValueIri.get,
      requestingUser = anythingUser1,
      checkLastModDateChanged = false,
    )

    // Update the value.
    val dimX             = 512
    val dimY             = 256
    val internalFilename = "updated-filename.jp2"
    val internalMimeType = mimeTypeJP2
    val originalFilename = Some("test.tiff")
    val originalMimeType = Some(mimeTypeTIFF)

    val updateValueResponse = UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.updateValueV2(
          UpdateValueContentV2(
            resourceIri,
            thingPictureClassIri.toSmartIri,
            propertyIri,
            stillImageFileValueIri.get,
            FileModelUtil.getFileValueContent(
              FileType.StillImageFile(dimX = dimX, dimY = dimY),
              internalFilename,
              Some(internalMimeType),
              originalFilename,
              originalMimeType,
            ),
          ),
          anythingUser1,
          randomUUID,
        ),
      ),
    )

    stillImageFileValueIri.set(updateValueResponse.valueIri)

    // Read the value back to check that it was added correctly.

    val updatedValueFromTriplestore = getValue(
      resourceIri = resourceIri,
      maybePreviousLastModDate = maybeResourceLastModDate,
      propertyIriForGravsearch = propertyIri,
      propertyIriInResult = propertyIri,
      expectedValueIri = stillImageFileValueIri.get,
      requestingUser = anythingUser1,
    )

    updatedValueFromTriplestore.valueIri should equal(stillImageFileValueIri.get)
    updatedValueFromTriplestore.valueContent match {
      case savedValue: StillImageFileValueContentV2 =>
        savedValue.comment should be(None)
        savedValue.dimX should equal(dimX)
        savedValue.dimY should equal(dimY)
        savedValue.fileValue.internalFilename should equal(internalFilename)
        savedValue.fileValue.internalMimeType should equal(internalMimeType)
        savedValue.fileValue.originalMimeType should equal(originalMimeType)
        savedValue.fileValue.originalFilename should equal(originalFilename)

      case _ => throw AssertionException(s"Expected still image file value, got $updatedValueFromTriplestore")
    }
    updatedValueFromTriplestore.permissions should equal(previousValueFromTriplestore.permissions)

  }

  "not delete a value if the requesting user does not have DeletePermission on the value" in {
    val resourceIri: IRI      = aThingIri
    val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri

    val actual = UnsafeZioRun.run(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.deleteValueV2(
          DeleteValueV2(
            resourceIri = resourceIri,
            resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
            propertyIri = propertyIri,
            valueIri = intValueIri.get,
            valueTypeIri = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri,
            deleteComment = Some("this value was incorrect"),
          ),
          anythingUser2,
          randomUUID,
        ),
      ),
    )
    assertFailsWithA[ForbiddenException](actual)
  }

  "delete an integer value" in {
    val resourceIri: IRI                          = aThingIri
    val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
    val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

    val valueIri = intValueIri.get

    UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.deleteValueV2(
          DeleteValueV2(
            resourceIri = resourceIri,
            resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
            propertyIri = propertyIri,
            valueIri = valueIri,
            valueTypeIri = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri,
            deleteComment = Some("this value was incorrect"),
          ),
          anythingUser1,
          randomUUID,
        ),
      ),
    )

    checkValueIsDeleted(
      resourceIri = resourceIri,
      maybePreviousLastModDate = maybeResourceLastModDate,
      valueIri = intValueIri.get,
      requestingUser = anythingUser1,
    )
  }

  "delete an integer value, specifying a custom delete date" in {
    val resourceIri: IRI                          = aThingIri
    val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
    val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)
    val deleteDate: Instant                       = Instant.now
    val deleteComment                             = Some("this value was incorrect")

    UnsafeZioRun.run(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.deleteValueV2(
          DeleteValueV2(
            resourceIri = resourceIri,
            resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
            propertyIri = propertyIri,
            valueIri = intValueForRsyncIri.get,
            valueTypeIri = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri,
            deleteComment = deleteComment,
            deleteDate = Some(deleteDate),
          ),
          anythingUser1,
          randomUUID,
        ),
      ),
    )

    checkValueIsDeleted(
      resourceIri = resourceIri,
      maybePreviousLastModDate = maybeResourceLastModDate,
      valueIri = intValueForRsyncIri.get,
      customDeleteDate = Some(deleteDate),
      deleteComment = deleteComment,
      requestingUser = anythingUser1,
    )
  }

  "not delete a standoff link directly" in {
    val actual = UnsafeZioRun.run(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.deleteValueV2(
          DeleteValueV2(
            resourceIri = zeitglöckleinIri,
            resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
            propertyIri = OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
            valueIri = standoffLinkValueIri.get,
            valueTypeIri = OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri,
          ),
          SharedTestDataADM.superUser,
          randomUUID,
        ),
      ),
    )
    assertFailsWithA[BadRequestException](actual)
  }

  "delete a text value with a standoff link" in {
    val propertyIri: SmartIri                     = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
    val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

    UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.deleteValueV2(
          DeleteValueV2(
            resourceIri = zeitglöckleinIri,
            resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
            propertyIri = propertyIri,
            valueIri = zeitglöckleinCommentWithStandoffIri.get,
            valueTypeIri = OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri,
            deleteComment = Some("this value was incorrect"),
          ),
          incunabulaUser,
          randomUUID,
        ),
      ),
    )

    checkValueIsDeleted(
      resourceIri = zeitglöckleinIri,
      maybePreviousLastModDate = maybeResourceLastModDate,
      valueIri = zeitglöckleinCommentWithStandoffIri.get,
      requestingUser = incunabulaUser,
    )

    // There should be no standoff link values left in the resource.

    val resource = getResourceWithValues(
      resourceIri = zeitglöckleinIri,
      propertyIrisForGravsearch = Seq(OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri),
      requestingUser = incunabulaUser,
    )

    assert(!resource.values.contains(OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri))
  }

  "delete a link between two resources" in {
    val resourceIri: IRI                          = "http://rdfh.ch/0803/cb1a74e3e2f6"
    val linkValuePropertyIri: SmartIri            = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri
    val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)
    val linkValueIRI                              = linkValueIri.get

    UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.deleteValueV2(
          DeleteValueV2(
            resourceIri = resourceIri,
            resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
            propertyIri = linkValuePropertyIri,
            valueIri = linkValueIRI,
            valueTypeIri = OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri,
          ),
          incunabulaUser,
          randomUUID,
        ),
      ),
    )

    checkValueIsDeleted(
      resourceIri = resourceIri,
      maybePreviousLastModDate = maybeResourceLastModDate,
      valueIri = linkValueIRI,
      requestingUser = anythingUser1,
      isLinkValue = true,
    )
  }

  "not delete a value if the property's cardinality doesn't allow it" in {
    val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri

    val actual = UnsafeZioRun.run(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.deleteValueV2(
          DeleteValueV2(
            resourceIri = zeitglöckleinIri,
            resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
            propertyIri = propertyIri,
            valueIri = "http://rdfh.ch/0803/c5058f3a/values/c3295339",
            valueTypeIri = OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri,
          ),
          incunabulaCreatorUser,
          randomUUID,
        ),
      ),
    )
    assertFailsWithA[OntologyConstraintException](actual)
  }

  "not accept custom value permissions that would give the requesting user a higher permission on a value than the default" in {
    val resourceIri: IRI = stringFormatter.makeRandomResourceIri(SharedTestDataADM.imagesProject.shortcode)

    val inputResource = CreateResourceV2(
      resourceIri = Some(resourceIri.toSmartIri),
      resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
      label = "test bildformat",
      values = Map.empty,
      projectADM = SharedTestDataADM.imagesProject,
      permissions = Some("M knora-admin:ProjectMember"),
    )

    val _ = UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[ResourcesResponderV2](
        _.createResource(
          CreateResourceRequestV2(
            createResource = inputResource,
            requestingUser = SharedTestDataADM.imagesUser01,
            apiRequestID = randomUUID,
          ),
        ),
      ),
    )

    val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#stueckzahl".toSmartIri

    val actual = UnsafeZioRun.run(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.createValueV2(
          CreateValueV2(
            resourceIri = resourceIri,
            resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
            propertyIri = propertyIri,
            valueContent = IntegerValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasInteger = 5,
              comment = Some("this is the number five"),
            ),
            permissions = Some("CR knora-admin:Creator"),
          ),
          requestingUser = SharedTestDataADM.imagesReviewerUser,
          apiRequestID = randomUUID,
        ),
      ),
    )
    assertFailsWithA[ForbiddenException](actual)
  }

  "accept custom value permissions that would give the requesting user a higher permission on a value than the default if the user is a system admin" in {
    val resourceIri: IRI = stringFormatter.makeRandomResourceIri(SharedTestDataADM.imagesProject.shortcode)

    val inputResource = CreateResourceV2(
      resourceIri = Some(resourceIri.toSmartIri),
      resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
      label = "test bildformat",
      values = Map.empty,
      projectADM = SharedTestDataADM.imagesProject,
      permissions = Some("M knora-admin:ProjectMember"),
    )
    val _ = UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[ResourcesResponderV2](
        _.createResource(
          CreateResourceRequestV2(
            createResource = inputResource,
            requestingUser = SharedTestDataADM.imagesUser01,
            apiRequestID = randomUUID,
          ),
        ),
      ),
    )

    val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#stueckzahl".toSmartIri
    UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.createValueV2(
          CreateValueV2(
            resourceIri = resourceIri,
            resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
            propertyIri = propertyIri,
            valueContent = IntegerValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasInteger = 5,
              comment = Some("this is the number five"),
            ),
            permissions = Some("CR knora-admin:Creator"),
          ),
          requestingUser = SharedTestDataADM.rootUser,
          apiRequestID = randomUUID,
        ),
      ),
    )
  }

  "accept custom value permissions that would give the requesting user a higher permission on a value than the default if the user is a project admin" in {
    val resourceIri: IRI = stringFormatter.makeRandomResourceIri(SharedTestDataADM.imagesProject.shortcode)

    val inputResource = CreateResourceV2(
      resourceIri = Some(resourceIri.toSmartIri),
      resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
      label = "test bildformat",
      values = Map.empty,
      projectADM = SharedTestDataADM.imagesProject,
      permissions = Some("M knora-admin:ProjectMember"),
    )
    val _ = UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[ResourcesResponderV2](
        _.createResource(
          CreateResourceRequestV2(
            createResource = inputResource,
            requestingUser = SharedTestDataADM.imagesUser01,
            apiRequestID = randomUUID,
          ),
        ),
      ),
    )

    val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#stueckzahl".toSmartIri
    UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.createValueV2(
          CreateValueV2(
            resourceIri = resourceIri,
            resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
            propertyIri = propertyIri,
            valueContent = IntegerValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasInteger = 5,
              comment = Some("this is the number five"),
            ),
            permissions = Some("CR knora-admin:Creator"),
          ),
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = randomUUID,
        ),
      ),
    )
  }

  "create and update text values with standoff links, managing value UUIDs correctly" in {
    val resourceClassIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri
    val propertyIri: SmartIri      = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext".toSmartIri

    // Create a text value with a standoff link.

    val createValueResponse = UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.createValueV2(
          CreateValueV2(
            resourceIri = sierraIri,
            resourceClassIri = resourceClassIri,
            propertyIri = propertyIri,
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("Comment 1 for UUID checking"),
              standoff = sampleStandoffWithLink,
              mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
              mapping = standardMapping,
              textValueType = TextValueType.FormattedText,
            ),
          ),
          requestingUser = anythingUser1,
          apiRequestID = randomUUID,
        ),
      ),
    )

    val resourceVersion1: ReadResourceV2 = getResourceWithValues(
      resourceIri = sierraIri,
      propertyIrisForGravsearch = Seq(
        propertyIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
      ),
      requestingUser = anythingUser1,
    )

    // Get the UUIDs of the text value and of the standoff link value.
    val textValue1: ReadValueV2 = resourceVersion1.values(propertyIri).head
    assert(textValue1.valueIri == createValueResponse.valueIri)
    assert(getValueUUID(textValue1.valueIri).contains(textValue1.valueHasUUID))
    val standoffLinkValueVersion1: ReadLinkValueV2 = resourceVersion1
      .values(OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri)
      .head
      .asInstanceOf[ReadLinkValueV2]
    assert(getValueUUID(standoffLinkValueVersion1.valueIri).contains(standoffLinkValueVersion1.valueHasUUID))
    assert(standoffLinkValueVersion1.valueHasRefCount == 1)

    // Create a second text value with the same standoff link.

    val createValueResponse2 = UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.createValueV2(
          CreateValueV2(
            resourceIri = sierraIri,
            resourceClassIri = resourceClassIri,
            propertyIri = propertyIri,
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("Comment 2 for UUID checking"),
              standoff = sampleStandoffWithLink,
              mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
              mapping = standardMapping,
              textValueType = TextValueType.FormattedText,
            ),
          ),
          requestingUser = anythingUser1,
          apiRequestID = randomUUID,
        ),
      ),
    )

    val resourceVersion2: ReadResourceV2 = getResourceWithValues(
      resourceIri = sierraIri,
      propertyIrisForGravsearch = Seq(
        propertyIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
      ),
      requestingUser = anythingUser1,
    )

    // Get the second text value's UUID.
    val textValue2Version1: ReadValueV2 = resourceVersion2
      .values(propertyIri)
      .find(_.valueIri == createValueResponse2.valueIri)
      .getOrElse(throw AssertionException("Value not found"))
    assert(getValueUUID(textValue2Version1.valueIri).contains(textValue2Version1.valueHasUUID))

    // We should have a new version of the standoff link value, containing the UUID that was in the previous version.
    val standoffLinkValueVersion2: ReadLinkValueV2 = resourceVersion2
      .values(OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri)
      .head
      .asInstanceOf[ReadLinkValueV2]
    assert(standoffLinkValueVersion2.previousValueIri.contains(standoffLinkValueVersion1.valueIri))
    assert(standoffLinkValueVersion2.valueHasUUID == standoffLinkValueVersion1.valueHasUUID)
    assert(getValueUUID(standoffLinkValueVersion2.valueIri).contains(standoffLinkValueVersion2.valueHasUUID))
    assert(standoffLinkValueVersion2.valueHasRefCount == 2)

    // The previous version of the standoff link value should have no UUID.
    assert(getValueUUID(standoffLinkValueVersion1.valueIri).isEmpty)

    // Update the second text value.

    val updateValueResponse = UnsafeZioRun.runOrThrow(
      ZIO.serviceWithZIO[ValuesResponderV2](
        _.updateValueV2(
          UpdateValueContentV2(
            resourceIri = sierraIri,
            resourceClassIri = resourceClassIri,
            propertyIri = propertyIri,
            valueIri = createValueResponse2.valueIri,
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("Comment 3 for UUID checking"),
              standoff = sampleStandoffWithLink,
              mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
              mapping = standardMapping,
              textValueType = TextValueType.FormattedText,
            ),
          ),
          anythingUser1,
          randomUUID,
        ),
      ),
    )

    val resourceVersion3: ReadResourceV2 = getResourceWithValues(
      resourceIri = sierraIri,
      propertyIrisForGravsearch = Seq(
        propertyIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
      ),
      requestingUser = anythingUser1,
    )

    // We should now have a new version of the second text value, containing the UUID that was in the previous version.
    val textValue2Version2: ReadValueV2 = resourceVersion3
      .values(propertyIri)
      .find(_.valueIri == updateValueResponse.valueIri)
      .getOrElse(throw AssertionException("Value not found"))
    assert(getValueUUID(textValue2Version2.valueIri).contains(textValue2Version2.valueHasUUID))
    assert(textValue2Version2.previousValueIri.contains(textValue2Version1.valueIri))

    // The previous version of the second text value should have no UUID.
    assert(getValueUUID(textValue2Version1.valueIri).isEmpty)

    // We should not have a new version of the standoff link value.
    assert(
      resourceVersion3
        .values(OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri)
        .head
        .valueIri == standoffLinkValueVersion2.valueIri,
    )
  }
}
