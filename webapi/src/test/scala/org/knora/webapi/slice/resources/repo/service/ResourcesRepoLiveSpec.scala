/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import org.apache.jena.update.UpdateFactory
import zio.*
import zio.test.*

import java.time.Instant
import java.util.UUID

import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.CalendarNameGregorian
import org.knora.webapi.messages.util.DatePrecisionDay
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueV2
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resources.repo.model.FormattedTextValueType
import org.knora.webapi.slice.resources.repo.model.ResourceReadyToCreate
import org.knora.webapi.slice.resources.repo.model.StandoffAttribute
import org.knora.webapi.slice.resources.repo.model.StandoffAttributeValue
import org.knora.webapi.slice.resources.repo.model.StandoffLinkValueInfo
import org.knora.webapi.slice.resources.repo.model.StandoffTagInfo
import org.knora.webapi.slice.resources.repo.model.TypeSpecificValueInfo
import org.knora.webapi.slice.resources.repo.model.ValueInfo
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object TestData {

  val graphIri          = InternalIri("foo:Graph")
  val projectIri        = InternalIri("foo:ProjectIri")
  val userIri           = InternalIri("foo:UserIri")
  val resourceIri       = InternalIri("foo:ResourceInstanceIri")
  val resourceClassIri  = InternalIri("foo:ClassIri")
  val label             = "foo Label"
  val creationDate      = Instant.parse("2024-01-01T10:00:00.673298Z")
  val permissions       = "fooPermissionsString"
  val valueCreator      = InternalIri("foo:ValueCreatorIri")
  val valuePermissions  = "fooValuePermissions"
  val valueCreationDate = Instant.parse("2024-01-01T12:00:00.673298Z")

  val resourceDefinition = ResourceReadyToCreate(
    resourceIri = resourceIri,
    resourceClassIri = resourceClassIri,
    resourceLabel = label,
    creationDate = creationDate,
    permissions = permissions,
    valueInfos = Seq.empty,
    standoffLinks = Seq.empty,
  )

  val linkValueDefinition = ValueInfo(
    resourceIri = resourceIri,
    propertyIri = InternalIri("foo:hasLinkToValue"),
    valueIri = InternalIri("foo:LinkValueIri"),
    valueTypeIri = InternalIri(OntologyConstants.KnoraBase.LinkValue),
    valueUUID = UUID.randomUUID(),
    value = TypeSpecificValueInfo.LinkValueInfo(InternalIri("foo:LinkTargetIri")),
    permissions = valuePermissions,
    creator = valueCreator,
    creationDate = valueCreationDate,
    valueHasOrder = 1,
    valueHasString = "foo:LinkValueIri",
    comment = None,
  )

  val unformattedTextValueDefinition = ValueInfo(
    resourceIri = resourceIri,
    propertyIri = InternalIri("foo:hasUnformattedTextValue"),
    valueIri = InternalIri("foo:UnformattedTextValueIri"),
    valueTypeIri = InternalIri(OntologyConstants.KnoraBase.TextValue),
    valueUUID = UUID.randomUUID(),
    value = TypeSpecificValueInfo.UnformattedTextValueInfo(Some("en")),
    permissions = valuePermissions,
    creator = valueCreator,
    creationDate = valueCreationDate,
    valueHasOrder = 1,
    valueHasString = "this is a text without formatting",
    comment = None,
  )

  val standoffTagUuid = UUID.randomUUID()
  val formattedTextValueDefinition = ValueInfo(
    resourceIri = resourceIri,
    propertyIri = InternalIri("foo:hasFormattedTextValue"),
    valueIri = InternalIri("foo:FormattedTextValueIri"),
    valueTypeIri = InternalIri(OntologyConstants.KnoraBase.TextValue),
    valueUUID = UUID.randomUUID(),
    value = TypeSpecificValueInfo.FormattedTextValueInfo(
      valueHasLanguage = Some("en"),
      mappingIri = InternalIri("foo:MappingIri"),
      maxStandoffStartIndex = 0,
      standoff = List(
        StandoffTagInfo(
          standoffTagClassIri = InternalIri("foo:StandoffTagClassIri"),
          standoffTagInstanceIri = InternalIri("foo:StandoffTagInstanceIri"),
          startParentIri = Some(InternalIri("foo:StartParentIri")),
          endParentIri = Some(InternalIri("foo:EndParentIri")),
          uuid = standoffTagUuid,
          originalXMLID = Some("xml-id"),
          startIndex = 0,
          endIndex = Some(3),
          startPosition = 0,
          endPosition = 3,
          attributes = List(
            StandoffAttribute(
              InternalIri("foo:attributePropertyIri"),
              StandoffAttributeValue.IriAttribute(InternalIri("foo:standoffAttributeIri")),
            ),
            StandoffAttribute(
              InternalIri("foo:attributePropertyIri"),
              StandoffAttributeValue.UriAttribute("http://example.com"),
            ),
            StandoffAttribute(
              InternalIri("foo:attributePropertyIri"),
              StandoffAttributeValue.InternalReferenceAttribute(InternalIri("foo:internalRef")),
            ),
            StandoffAttribute(
              InternalIri("foo:attributePropertyIri"),
              StandoffAttributeValue.StringAttribute("attribute value"),
            ),
            StandoffAttribute(InternalIri("foo:attributePropertyIri"), StandoffAttributeValue.IntegerAttribute(42)),
            StandoffAttribute(
              InternalIri("foo:attributePropertyIri"),
              StandoffAttributeValue.DecimalAttribute(BigDecimal(42.42)),
            ),
            StandoffAttribute(InternalIri("foo:attributePropertyIri"), StandoffAttributeValue.BooleanAttribute(true)),
            StandoffAttribute(
              InternalIri("foo:attributePropertyIri"),
              StandoffAttributeValue.TimeAttribute(Instant.parse("1024-01-01T10:00:00.673298Z")),
            ),
          ),
        ),
      ),
      textValueType = FormattedTextValueType.StandardMapping,
    ),
    permissions = valuePermissions,
    creator = valueCreator,
    creationDate = valueCreationDate,
    valueHasOrder = 1,
    valueHasString = "this is a text with formatting",
    comment = None,
  )

  val intValueDefinition =
    ValueInfo(
      resourceIri = resourceIri,
      propertyIri = InternalIri("foo:hasInt"),
      valueIri = InternalIri("foo:IntValueIri"),
      valueTypeIri = InternalIri(OntologyConstants.KnoraBase.IntValue),
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.IntegerValueInfo(42),
      permissions = valuePermissions,
      creator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "42",
      comment = None,
    )

  val boolValueDefinition =
    ValueInfo(
      resourceIri = resourceIri,
      propertyIri = InternalIri("foo:hasBoolean"),
      valueIri = InternalIri("foo:BooleanValueIri"),
      valueTypeIri = InternalIri(OntologyConstants.KnoraBase.BooleanValue),
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.BooleanValueInfo(true),
      permissions = valuePermissions,
      creator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "true",
      comment = None,
    )

  val decimalValueDefinition =
    ValueInfo(
      resourceIri = resourceIri,
      propertyIri = InternalIri("foo:hasDecimal"),
      valueIri = InternalIri("foo:DecimalValueIri"),
      valueTypeIri = InternalIri(OntologyConstants.KnoraBase.DecimalValue),
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.DecimalValueInfo(BigDecimal(42.42)),
      permissions = valuePermissions,
      creator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "42.42",
      comment = None,
    )

  val uriValueDefinition =
    ValueInfo(
      resourceIri = resourceIri,
      propertyIri = InternalIri("foo:hasUri"),
      valueIri = InternalIri("foo:UriValueIri"),
      valueTypeIri = InternalIri(OntologyConstants.KnoraBase.UriValue),
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.UriValueInfo("http://example.com"),
      permissions = valuePermissions,
      creator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "http://example.com",
      comment = None,
    )

  val dateValueDefinition =
    ValueInfo(
      resourceIri = resourceIri,
      propertyIri = InternalIri("foo:hasDate"),
      valueIri = InternalIri("foo:DateValueIri"),
      valueTypeIri = InternalIri(OntologyConstants.KnoraBase.DateValue),
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.DateValueInfo(0, 0, DatePrecisionDay, DatePrecisionDay, CalendarNameGregorian),
      permissions = valuePermissions,
      creator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "2024-01-01",
      comment = None,
    )

  val colorValueDefinition =
    ValueInfo(
      resourceIri = resourceIri,
      propertyIri = InternalIri("foo:hasColor"),
      valueIri = InternalIri("foo:ColorValueIri"),
      valueTypeIri = InternalIri(OntologyConstants.KnoraBase.ColorValue),
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.ColorValueInfo("#ff0000"),
      permissions = valuePermissions,
      creator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "#ff0000",
      comment = None,
    )

  val geometryValueDefinition =
    ValueInfo(
      resourceIri = resourceIri,
      propertyIri = InternalIri("foo:hasGeom"),
      valueIri = InternalIri("foo:GeomValueIri"),
      valueTypeIri = InternalIri(OntologyConstants.KnoraBase.GeomValue),
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.GeomValueInfo(
        """{"status":"active","lineColor":"#33ff33","lineWidth":2,"points":[{"x":0.20226843100189035,"y":0.3090909090909091},{"x":0.6389413988657845,"y":0.3594405594405594}],"type":"rectangle","original_index":0}""",
      ),
      permissions = valuePermissions,
      creator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString =
        """{"status":"active","lineColor":"#33ff33","lineWidth":2,"points":[{"x":0.20226843100189035,"y":0.3090909090909091},{"x":0.6389413988657845,"y":0.3594405594405594}],"type":"rectangle","original_index":0}""",
      comment = None,
    )

  val stillImageFileValueDefinition =
    ValueInfo(
      resourceIri = resourceIri,
      propertyIri = InternalIri("foo:hasStillImage"),
      valueIri = InternalIri("foo:StillImageFileValueIri"),
      valueTypeIri = InternalIri(OntologyConstants.KnoraBase.StillImageFileValue),
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.StillImageFileValueInfo(
        FileValueV2(
          internalFilename = "24159oO1pNg-ByLN1NLlMSJ.jp2",
          internalMimeType = "image/jp2",
          originalFilename = Some("foo.png"),
          originalMimeType = Some("image/png"),
        ),
        dimX = 100,
        dimY = 60,
      ),
      permissions = valuePermissions,
      creator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "foo.jpg",
      comment = None,
    )

  val stillImageExternalFileValueDefinition =
    ValueInfo(
      resourceIri = resourceIri,
      propertyIri = InternalIri("foo:hasStillImageExternal"),
      valueIri = InternalIri("foo:StillImageExternalFileValueIri"),
      valueTypeIri = InternalIri(OntologyConstants.KnoraBase.StillImageFileValue),
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.StillImageExternalFileValueInfo(
        FileValueV2(
          internalFilename = "24159oO1pNg-ByLN1NLlMSJ.jp2",
          internalMimeType = "image/jp2",
        ),
        externalUrl = "http://example.com/foo.jpg",
      ),
      permissions = valuePermissions,
      creator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "foo.jpg",
      comment = None,
    )

  val documentFileValueDefinition =
    ValueInfo(
      resourceIri = resourceIri,
      propertyIri = InternalIri("foo:hasDocument"),
      valueIri = InternalIri("foo:DocumentFileValueIri"),
      valueTypeIri = InternalIri(OntologyConstants.KnoraBase.DocumentFileValue),
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.DocumentFileValueInfo(
        FileValueV2(
          internalFilename = "24159oO1pNg-ByLN1NLlMSJ.pdf",
          internalMimeType = "application/pdf",
          originalFilename = Some("foo.pdf"),
          originalMimeType = Some("application/pdf"),
        ),
        dimX = Some(100),
        dimY = Some(60),
        pageCount = Some(10),
      ),
      permissions = valuePermissions,
      creator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "foo.pdf",
      comment = None,
    )

  val otherFileValueDefinition =
    ValueInfo(
      resourceIri = resourceIri,
      propertyIri = InternalIri("foo:hasOtherFile"),
      valueIri = InternalIri("foo:OtherFileValueIri"),
      valueTypeIri = InternalIri(OntologyConstants.KnoraBase.ArchiveFileValue),
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.OtherFileValueInfo(
        FileValueV2(
          internalFilename = "24159oO1pNg-ByLN1NLlMSJ.zip",
          internalMimeType = "application/zip",
          originalFilename = Some("foo.zip"),
          originalMimeType = Some("application/zip"),
        ),
      ),
      permissions = valuePermissions,
      creator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "foo.zip",
      comment = None,
    )

  val hierarchicalListValueDefinition =
    ValueInfo(
      resourceIri = resourceIri,
      propertyIri = InternalIri("foo:hasList"),
      valueIri = InternalIri("foo:ListNodeIri"),
      valueTypeIri = InternalIri(OntologyConstants.KnoraBase.ListValue),
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.HierarchicalListValueInfo(InternalIri("foo:ListNodeIri")),
      permissions = valuePermissions,
      creator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "foo list",
      comment = None,
    )

  val intervalValueDefinition =
    ValueInfo(
      resourceIri = resourceIri,
      propertyIri = InternalIri("foo:hasInterval"),
      valueIri = InternalIri("foo:IntervalValueIri"),
      valueTypeIri = InternalIri(OntologyConstants.KnoraBase.IntervalValue),
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.IntervalValueInfo(
        valueHasIntervalStart = BigDecimal(0.0),
        valueHasIntervalEnd = BigDecimal(100.0),
      ),
      permissions = valuePermissions,
      creator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "0.0 - 100.0",
      comment = None,
    )

  val timeValueDefinition =
    ValueInfo(
      resourceIri = resourceIri,
      propertyIri = InternalIri("foo:hasTime"),
      valueIri = InternalIri("foo:TimeValueIri"),
      valueTypeIri = InternalIri(OntologyConstants.KnoraBase.TimeValue),
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.TimeValueInfo(Instant.parse("1024-01-01T10:00:00.673298Z")),
      permissions = valuePermissions,
      creator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "1024-01-01T10:00:00.673298Z",
      comment = None,
    )

  val geonameValueDefinition =
    ValueInfo(
      resourceIri = resourceIri,
      propertyIri = InternalIri("foo:hasGeoname"),
      valueIri = InternalIri("foo:GeonameValueIri"),
      valueTypeIri = InternalIri(OntologyConstants.KnoraBase.GeonameValue),
      valueUUID = UUID.randomUUID(),
      value = TypeSpecificValueInfo.GeonameValueInfo("geoname_code"),
      permissions = valuePermissions,
      creator = valueCreator,
      creationDate = valueCreationDate,
      valueHasOrder = 1,
      valueHasString = "geoname_code",
      comment = None,
    )

  val standoffLinkValue =
    StandoffLinkValueInfo(
      linkPropertyIri = InternalIri("foo:hasStandoffLinkTo"),
      newLinkValueIri = InternalIri("foo:StandoffLinkValueIri"),
      linkTargetIri = InternalIri("foo:StandoffLinkTargetIri"),
      newReferenceCount = 2,
      newLinkValueCreator = valueCreator,
      newLinkValuePermissions = valuePermissions,
      valueUuid = UuidUtil.base64Encode(UUID.randomUUID()),
    )
}

object ResourcesRepoLiveSpec extends ZIOSpecDefault {
  import TestData.*

  private def assertUpdateQueriesEqual(expected: Update, actual: Update) = {
    val parsedExpected = UpdateFactory.create(expected.sparql)
    val parsedActual   = UpdateFactory.create(actual.sparql)
    if (parsedExpected.equalTo(parsedActual)) {
      assertTrue(true)
    } else {
      val expectedStr = parsedExpected.toString()
      val actualStr   = parsedActual.toString()
      assertTrue(actualStr == expectedStr)
    }
  }

  def spec: Spec[Environment & (TestEnvironment & Scope), Any] = tests.provide(StringFormatter.test)

  private val createResourceWithoutValuesTest = test("Create a new resource query without values") {
    val expected =
      Update(s"""|
                 |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                 |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |
                 |INSERT DATA {
                 |    GRAPH <${graphIri.value}> {
                 |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
                 |            rdfs:label "$label" ;
                 |            knora-base:isDeleted false ;
                 |            knora-base:attachedToUser <${userIri.value}> ;
                 |            knora-base:attachedToProject <${projectIri.value}> ;
                 |            knora-base:hasPermissions "$permissions" ;
                 |            knora-base:creationDate "$creationDate"^^xsd:dateTime .
                 |    }
                 |}
                 |""".stripMargin)
    val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resourceDefinition, projectIri, userIri)
    assertUpdateQueriesEqual(expected, result)
  }

  private val createResourceWithValueSuite = suite("Create new resource with any type of value")(
    test("Create a new resource with a link value") {
      val resource = resourceDefinition.copy(valueInfos = List(linkValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasLinkToValue> <foo:LinkValueIri> .
            |        <foo:LinkValueIri> rdf:type <http://www.knora.org/ontology/knora-base#LinkValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "foo:LinkValueIri" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(linkValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime .
            |        <${resourceIri.value}> <foo:hasLinkTo> <foo:LinkTargetIri> .
            |        <foo:LinkValueIri> rdf:subject <${resourceIri.value}> ;
            |            rdf:predicate <foo:hasLinkTo> ;
            |            rdf:object <foo:LinkTargetIri> ;
            |            knora-base:valueHasRefCount 1 .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with an unformatted text value") {
      val resource = resourceDefinition.copy(valueInfos = List(unformattedTextValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasUnformattedTextValue> <foo:UnformattedTextValueIri> .
            |        <foo:UnformattedTextValueIri> rdf:type <http://www.knora.org/ontology/knora-base#TextValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "this is a text without formatting" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(unformattedTextValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:hasTextValueType knora-base:UnformattedText ;
            |            knora-base:valueHasLanguage "en" .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a formatted text value") {
      val resource = resourceDefinition.copy(valueInfos = List(formattedTextValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasFormattedTextValue> <foo:FormattedTextValueIri> .
            |        <foo:FormattedTextValueIri> rdf:type <http://www.knora.org/ontology/knora-base#TextValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "this is a text with formatting" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(formattedTextValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasMapping <foo:MappingIri> ;
            |            knora-base:hasTextValueType knora-base:FormattedText ;
            |            knora-base:valueHasMaxStandoffStartIndex 0 ;
            |            knora-base:valueHasLanguage "en" ;
            |            knora-base:valueHasStandoff <foo:StandoffTagInstanceIri> .
            |        <foo:StandoffTagInstanceIri> rdf:type <foo:StandoffTagClassIri> ;
            |            knora-base:standoffTagHasEndIndex 3 ;
            |            knora-base:standoffTagHasStartParent <foo:StartParentIri> ;
            |            knora-base:standoffTagHasEndParent <foo:EndParentIri> ;
            |            knora-base:standoffTagHasOriginalXMLID "xml-id" ;
            |            <foo:attributePropertyIri> <foo:standoffAttributeIri> ;
            |            <foo:attributePropertyIri> "http://example.com"^^xsd:anyURI ;
            |            <foo:attributePropertyIri> <foo:internalRef> ;
            |            <foo:attributePropertyIri> "attribute value" ;
            |            <foo:attributePropertyIri> 42 ;
            |            <foo:attributePropertyIri> 42.42 ;
            |            <foo:attributePropertyIri> true ;
            |            <foo:attributePropertyIri> "1024-01-01T10:00:00.673298Z"^^xsd:dateTime ;
            |            knora-base:standoffTagHasStartIndex 0 ;
            |            knora-base:standoffTagHasUUID "${UuidUtil.base64Encode(standoffTagUuid)}" ;
            |            knora-base:standoffTagHasStart 0 ;
            |            knora-base:standoffTagHasEnd 3 .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with an integer value") {
      val resource = resourceDefinition.copy(valueInfos = List(intValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasInt> <foo:IntValueIri> .
            |        <foo:IntValueIri> rdf:type <http://www.knora.org/ontology/knora-base#IntValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "42" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(intValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasInteger 42 .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a boolean value") {
      val resource = resourceDefinition.copy(valueInfos = List(boolValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasBoolean> <foo:BooleanValueIri> .
            |        <foo:BooleanValueIri> rdf:type <http://www.knora.org/ontology/knora-base#BooleanValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "true" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(boolValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasBoolean true .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a decimal value") {
      val resource = resourceDefinition.copy(valueInfos = List(decimalValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasDecimal> <foo:DecimalValueIri> .
            |        <foo:DecimalValueIri> rdf:type <http://www.knora.org/ontology/knora-base#DecimalValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "42.42" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(decimalValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasDecimal 42.42 .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a URI value") {
      val resource = resourceDefinition.copy(valueInfos = List(uriValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasUri> <foo:UriValueIri> .
            |        <foo:UriValueIri> rdf:type <http://www.knora.org/ontology/knora-base#UriValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "http://example.com" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(uriValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasUri "http://example.com"^^xsd:anyURI .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a date value") {
      val resource = resourceDefinition.copy(valueInfos = List(dateValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasDate> <foo:DateValueIri> .
            |        <foo:DateValueIri> rdf:type <http://www.knora.org/ontology/knora-base#DateValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "2024-01-01" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(dateValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasStartJDN 0 ;
            |            knora-base:valueHasEndJDN 0 ;
            |            knora-base:valueHasStartPrecision "DAY" ;
            |            knora-base:valueHasEndPrecision "DAY" ;
            |            knora-base:valueHasCalendar "GREGORIAN" .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a color value") {
      val resource = resourceDefinition.copy(valueInfos = List(colorValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasColor> <foo:ColorValueIri> .
            |        <foo:ColorValueIri> rdf:type <http://www.knora.org/ontology/knora-base#ColorValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "#ff0000" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(colorValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasColor "#ff0000" .
            |    }
            |}
            |""".stripMargin,
      )

      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a geometry value") {
      val resource = resourceDefinition.copy(valueInfos = List(geometryValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasGeom> <foo:GeomValueIri> .
            |        <foo:GeomValueIri> rdf:type <http://www.knora.org/ontology/knora-base#GeomValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "{\\"status\\":\\"active\\",\\"lineColor\\":\\"#33ff33\\",\\"lineWidth\\":2,\\"points\\":[{\\"x\\":0.20226843100189035,\\"y\\":0.3090909090909091},{\\"x\\":0.6389413988657845,\\"y\\":0.3594405594405594}],\\"type\\":\\"rectangle\\",\\"original_index\\":0}" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(geometryValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasGeometry "{\\"status\\":\\"active\\",\\"lineColor\\":\\"#33ff33\\",\\"lineWidth\\":2,\\"points\\":[{\\"x\\":0.20226843100189035,\\"y\\":0.3090909090909091},{\\"x\\":0.6389413988657845,\\"y\\":0.3594405594405594}],\\"type\\":\\"rectangle\\",\\"original_index\\":0}" .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a still image file value") {
      val resource = resourceDefinition.copy(valueInfos = List(stillImageFileValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasStillImage> <foo:StillImageFileValueIri> .
            |        <foo:StillImageFileValueIri> rdf:type <http://www.knora.org/ontology/knora-base#StillImageFileValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "foo.jpg" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(stillImageFileValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:internalFilename "24159oO1pNg-ByLN1NLlMSJ.jp2" ;
            |            knora-base:internalMimeType "image/jp2" ;
            |            knora-base:originalFilename "foo.png" ;
            |            knora-base:originalMimeType "image/png" ;
            |            knora-base:dimX 100 ;
            |            knora-base:dimY 60  .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a still image external file value") {
      val resource    = resourceDefinition.copy(valueInfos = List(stillImageExternalFileValueDefinition))
      val uuidEncoded = UuidUtil.base64Encode(stillImageExternalFileValueDefinition.valueUUID)
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasStillImageExternal> <foo:StillImageExternalFileValueIri> .
            |        <foo:StillImageExternalFileValueIri> rdf:type <http://www.knora.org/ontology/knora-base#StillImageFileValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "foo.jpg" ;
            |            knora-base:valueHasUUID "$uuidEncoded" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:internalFilename "24159oO1pNg-ByLN1NLlMSJ.jp2" ;
            |            knora-base:internalMimeType "image/jp2" ;
            |            knora-base:externalUrl "http://example.com/foo.jpg" .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a document file value") {
      val resource = resourceDefinition.copy(valueInfos = List(documentFileValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasDocument> <foo:DocumentFileValueIri> .
            |        <foo:DocumentFileValueIri> rdf:type <http://www.knora.org/ontology/knora-base#DocumentFileValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "foo.pdf" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(documentFileValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:internalFilename "24159oO1pNg-ByLN1NLlMSJ.pdf" ;
            |            knora-base:internalMimeType "application/pdf" ;
            |            knora-base:originalFilename "foo.pdf" ;
            |            knora-base:originalMimeType "application/pdf" ;
            |            knora-base:dimX 100 ;
            |            knora-base:dimY 60 ;
            |            knora-base:pageCount 10 .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with another file value") {
      val resource = resourceDefinition.copy(valueInfos = List(otherFileValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasOtherFile> <foo:OtherFileValueIri> .
            |        <foo:OtherFileValueIri> rdf:type <http://www.knora.org/ontology/knora-base#ArchiveFileValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "foo.zip" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(otherFileValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:internalFilename "24159oO1pNg-ByLN1NLlMSJ.zip" ;
            |            knora-base:internalMimeType "application/zip" ;
            |            knora-base:originalFilename "foo.zip" ;
            |            knora-base:originalMimeType "application/zip" .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a list value") {
      val resource = resourceDefinition.copy(valueInfos = List(hierarchicalListValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasList> <foo:ListNodeIri> .
            |        <foo:ListNodeIri> rdf:type <http://www.knora.org/ontology/knora-base#ListValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "foo list" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(hierarchicalListValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasListNode <foo:ListNodeIri> .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with an interval value") {
      val resource = resourceDefinition.copy(valueInfos = List(intervalValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasInterval> <foo:IntervalValueIri> .
            |        <foo:IntervalValueIri> rdf:type <http://www.knora.org/ontology/knora-base#IntervalValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "0.0 - 100.0" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(intervalValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasIntervalStart 0.0 ;
            |            knora-base:valueHasIntervalEnd 100.0 ;
            |    }
            |}
            |""".stripMargin,
      )

      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a time value") {
      val resource = resourceDefinition.copy(valueInfos = List(timeValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasTime> <foo:TimeValueIri> .
            |        <foo:TimeValueIri> rdf:type <http://www.knora.org/ontology/knora-base#TimeValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "1024-01-01T10:00:00.673298Z" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(timeValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasTimeStamp "1024-01-01T10:00:00.673298Z"^^xsd:dateTime .
            |    }
            |}
            |""".stripMargin,
      )

      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
    test("Create a new resource with a geoname value") {
      val resource = resourceDefinition.copy(valueInfos = List(geonameValueDefinition))
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasGeoname> <foo:GeonameValueIri> .
            |        <foo:GeonameValueIri> rdf:type <http://www.knora.org/ontology/knora-base#GeonameValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "geoname_code" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(geonameValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasGeonameCode "geoname_code" .
            |    }
            |}
            |""".stripMargin,
      )

      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    },
  )

  private val createResourceWithMultipleValuesTest = test("Create a resource with multiple values") {
    val resource = resourceDefinition.copy(valueInfos = List(intValueDefinition, boolValueDefinition))
    val expected = Update(
      s"""|
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT DATA {
          |    GRAPH <${graphIri.value}> {
          |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
          |            rdfs:label "$label" ;
          |            knora-base:isDeleted false ;
          |            knora-base:attachedToUser <${userIri.value}> ;
          |            knora-base:attachedToProject <${projectIri.value}> ;
          |            knora-base:hasPermissions "$permissions" ;
          |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
          |            <foo:hasInt> <foo:IntValueIri> .
          |        <foo:IntValueIri> rdf:type <http://www.knora.org/ontology/knora-base#IntValue> ;
          |            knora-base:isDeleted false  ;
          |            knora-base:valueHasString "42" ;
          |            knora-base:valueHasUUID "${UuidUtil.base64Encode(intValueDefinition.valueUUID)}" ;
          |            knora-base:attachedToUser <${valueCreator.value}> ;
          |            knora-base:hasPermissions "$valuePermissions" ;
          |            knora-base:valueHasOrder 1 ;
          |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
          |            knora-base:valueHasInteger 42 .
          |        <${resourceIri.value}> <foo:hasBoolean> <foo:BooleanValueIri> .
          |        <foo:BooleanValueIri> rdf:type <http://www.knora.org/ontology/knora-base#BooleanValue> ;
          |            knora-base:isDeleted false  ;
          |            knora-base:valueHasString "true" ;
          |            knora-base:valueHasUUID "${UuidUtil.base64Encode(boolValueDefinition.valueUUID)}" ;
          |            knora-base:attachedToUser <${valueCreator.value}> ;
          |            knora-base:hasPermissions "$valuePermissions" ;
          |            knora-base:valueHasOrder 1 ;
          |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
          |            knora-base:valueHasBoolean true .
          |    }
          |}
          |""".stripMargin,
    )

    val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
    assertUpdateQueriesEqual(expected, result)
  }

  private val createResourceWithStandoffLinkTest = test("Create a resource with a standoff link value") {
    val resource = resourceDefinition.copy(standoffLinks = List(standoffLinkValue))
    val expected = Update(
      s"""|
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT DATA {
          |    GRAPH <${graphIri.value}> {
          |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
          |            rdfs:label "$label" ;
          |            knora-base:isDeleted false ;
          |            knora-base:attachedToUser <${userIri.value}> ;
          |            knora-base:attachedToProject <${projectIri.value}> ;
          |            knora-base:hasPermissions "$permissions" ;
          |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
          |            <foo:hasStandoffLinkTo> <foo:StandoffLinkTargetIri> ;
          |            <foo:hasStandoffLinkToValue> <foo:StandoffLinkValueIri> .
          |        <foo:StandoffLinkValueIri> rdf:type <http://www.knora.org/ontology/knora-base#LinkValue> ;
          |            rdf:subject <${resourceIri.value}> ;
          |            rdf:predicate <foo:hasStandoffLinkTo> ;
          |            rdf:object <foo:StandoffLinkTargetIri> ;
          |            knora-base:valueHasString "foo:StandoffLinkTargetIri" ;
          |            knora-base:valueHasRefCount 2;
          |            knora-base:isDeleted false  ;
          |            knora-base:valueCreationDate "$creationDate"^^xsd:dateTime ;
          |            knora-base:attachedToUser <${valueCreator.value}> ;
          |            knora-base:hasPermissions "$valuePermissions" ;
          |            knora-base:valueHasUUID "${standoffLinkValue.valueUuid}" .
          |    }
          |}
          |""".stripMargin,
    )
    val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
    assertUpdateQueriesEqual(expected, result)
  }

  private val createResourceWithValueAndStandoffLinkTest =
    test("Create a resource with a value and a standoff link value") {
      val resource = resourceDefinition.copy(
        standoffLinks = List(standoffLinkValue),
        valueInfos = List(intValueDefinition),
      )
      val expected = Update(
        s"""|
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |
            |INSERT DATA {
            |    GRAPH <${graphIri.value}> {
            |        <${resourceIri.value}> rdf:type <${resourceClassIri.value}> ;
            |            rdfs:label "$label" ;
            |            knora-base:isDeleted false ;
            |            knora-base:attachedToUser <${userIri.value}> ;
            |            knora-base:attachedToProject <${projectIri.value}> ;
            |            knora-base:hasPermissions "$permissions" ;
            |            knora-base:creationDate "$creationDate"^^xsd:dateTime ;
            |            <foo:hasInt> <foo:IntValueIri> .
            |        <foo:IntValueIri> rdf:type <http://www.knora.org/ontology/knora-base#IntValue> ;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueHasString "42" ;
            |            knora-base:valueHasUUID "${UuidUtil.base64Encode(intValueDefinition.valueUUID)}" ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasOrder 1 ;
            |            knora-base:valueCreationDate "$valueCreationDate"^^xsd:dateTime ;
            |            knora-base:valueHasInteger 42 .
            |        <${resourceIri.value}> <foo:hasStandoffLinkTo> <foo:StandoffLinkTargetIri> ;
            |            <foo:hasStandoffLinkToValue> <foo:StandoffLinkValueIri> .
            |        <foo:StandoffLinkValueIri> rdf:type <http://www.knora.org/ontology/knora-base#LinkValue> ;
            |            rdf:subject <${resourceIri.value}> ;
            |            rdf:predicate <foo:hasStandoffLinkTo> ;
            |            rdf:object <foo:StandoffLinkTargetIri> ;
            |            knora-base:valueHasString "foo:StandoffLinkTargetIri" ;
            |            knora-base:valueHasRefCount 2;
            |            knora-base:isDeleted false  ;
            |            knora-base:valueCreationDate "$creationDate"^^xsd:dateTime ;
            |            knora-base:attachedToUser <${valueCreator.value}> ;
            |            knora-base:hasPermissions "$valuePermissions" ;
            |            knora-base:valueHasUUID "${standoffLinkValue.valueUuid}" .
            |    }
            |}
            |""".stripMargin,
      )
      val result = ResourcesRepoLive.createNewResourceQuery(graphIri, resource, projectIri, userIri)
      assertUpdateQueriesEqual(expected, result)
    }

  val tests: Spec[StringFormatter, Nothing] =
    suite("ResourcesRepoLiveSpec")(
      createResourceWithoutValuesTest,
      createResourceWithValueSuite,
      createResourceWithMultipleValuesTest,
      createResourceWithStandoffLinkTest,
      createResourceWithValueAndStandoffLinkTest,
    )
}
