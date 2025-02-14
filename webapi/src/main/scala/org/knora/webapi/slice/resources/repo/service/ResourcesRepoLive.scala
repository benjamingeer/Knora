/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import org.eclipse.rdf4j.model.Namespace
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.InsertDataQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.literalOf
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.literalOfType
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.predicateObjectList
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfObject
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfPredicate
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfPredicateObjectList
import zio.*

import java.time.Instant

import dsp.valueobjects.UuidUtil
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resources.repo.model.FileValueTypeSpecificInfo
import org.knora.webapi.slice.resources.repo.model.FormattedTextValueType
import org.knora.webapi.slice.resources.repo.model.ResourceReadyToCreate
import org.knora.webapi.slice.resources.repo.model.StandoffAttribute
import org.knora.webapi.slice.resources.repo.model.StandoffAttributeValue
import org.knora.webapi.slice.resources.repo.model.StandoffLinkValueInfo
import org.knora.webapi.slice.resources.repo.model.TypeSpecificValueInfo
import org.knora.webapi.slice.resources.repo.model.TypeSpecificValueInfo.*
import org.knora.webapi.slice.resources.repo.model.ValueInfo
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

trait ResourcesRepo {
  def createNewResource(
    dataGraphIri: InternalIri,
    resource: ResourceReadyToCreate,
    userIri: InternalIri,
    projectIri: InternalIri,
  ): Task[Unit]
}

final case class ResourcesRepoLive(triplestore: TriplestoreService) extends ResourcesRepo {

  def createNewResource(
    dataGraphIri: InternalIri,
    resource: ResourceReadyToCreate,
    userIri: InternalIri,
    projectIri: InternalIri,
  ): Task[Unit] =
    triplestore.query(ResourcesRepoLive.createNewResourceQuery(dataGraphIri, resource, projectIri, userIri))

}

object ResourcesRepoLive {
  val layer = ZLayer.derive[ResourcesRepoLive]

  private[service] def createNewResourceQuery(
    dataGraphIri: InternalIri,
    resourceToCreate: ResourceReadyToCreate,
    projectIri: InternalIri,
    creatorIri: InternalIri,
  ) = {
    val query: InsertDataQuery =
      Queries
        .INSERT_DATA()
        .into(iri(dataGraphIri.value))
        .prefix(KB.NS, RDF.NS, RDFS.NS, XSD.NS)

    val resourcePattern = CreateResourceQueryBuilder.buildResourcePattern(resourceToCreate, projectIri, creatorIri)
    query.insertData(resourcePattern)

    val valuePatterns = resourceToCreate.valueInfos.flatMap(
      CreateResourceQueryBuilder.buildValuePattern(_, resourceToCreate.resourceIri),
    )
    query.insertData(valuePatterns: _*)

    val standoffLinkPatterns = resourceToCreate.standoffLinks.flatMap { standoffLink =>
      CreateResourceQueryBuilder.buildStandoffLinkPattern(
        standoffLink,
        resourceToCreate.resourceIri,
        resourceToCreate.creationDate,
      )
    }
    query.insertData(standoffLinkPatterns: _*)

    Update(query.getQueryString())
  }

  private object CreateResourceQueryBuilder {
    def buildResourcePattern(
      resource: ResourceReadyToCreate,
      projectIri: InternalIri,
      creatorIri: InternalIri,
    ): TriplePattern =
      iri(resource.resourceIri.value)
        .isA(iri(resource.resourceClassIri.value))
        .andHas(RDFS.LABEL, literalOf(resource.resourceLabel))
        .andHas(KB.isDeleted, literalOf(false))
        .andHas(KB.attachedToUser, iri(creatorIri.value))
        .andHas(KB.attachedToProject, iri(projectIri.value))
        .andHas(KB.hasPermissions, literalOf(resource.permissions))
        .andHas(KB.creationDate, literalOfType(resource.creationDate.toString(), XSD.DATETIME))

    def buildStandoffLinkPattern(
      standoffLink: StandoffLinkValueInfo,
      resourceIri: InternalIri,
      resourceCreationDate: Instant,
    ): List[TriplePattern] =
      List(
        iri(resourceIri.value)
          .has(iri(standoffLink.linkPropertyIri.value), iri(standoffLink.linkTargetIri.value))
          .andHas(iri(standoffLink.linkPropertyIri.value + "Value"), iri(standoffLink.newLinkValueIri.value)),
        buildStandoffLinkPatternContent(standoffLink, resourceIri, resourceCreationDate),
      )

    private def buildStandoffLinkPatternContent(
      standoffLink: StandoffLinkValueInfo,
      resourceIri: InternalIri,
      resourceCreationDate: Instant,
    ): TriplePattern =
      iri(standoffLink.newLinkValueIri.value)
        .isA(KB.linkValue)
        .andHas(RDF.SUBJECT, iri(resourceIri.value))
        .andHas(RDF.PREDICATE, iri(standoffLink.linkPropertyIri.value))
        .andHas(RDF.OBJECT, iri(standoffLink.linkTargetIri.value))
        .andHas(KB.valueHasString, literalOf(standoffLink.linkTargetIri.value))
        .andHas(KB.valueHasRefCount, literalOf(standoffLink.newReferenceCount))
        .andHas(KB.isDeleted, literalOf(false))
        .andHas(KB.valueCreationDate, literalOfType(resourceCreationDate.toString(), XSD.DATETIME))
        .andHas(KB.attachedToUser, iri(standoffLink.newLinkValueCreator.value))
        .andHas(KB.hasPermissions, literalOf(standoffLink.newLinkValuePermissions))
        .andHas(KB.valueHasUUID, literalOf(standoffLink.valueUuid))

    def buildValuePattern(valueInfo: ValueInfo, resourceIri: InternalIri): List[TriplePattern] =
      List(
        iri(resourceIri.value).has(iri(valueInfo.propertyIri.value), iri(valueInfo.valueIri.value)),
        buildGeneralValuePattern(valueInfo),
      ) :::
        buildTypeSpecificValuePattern(
          valueInfo.value,
          valueInfo.valueIri.value,
          valueInfo.propertyIri.value,
          resourceIri.value,
        )

    private def buildGeneralValuePattern(valueInfo: ValueInfo): TriplePattern =
      iri(valueInfo.valueIri.value)
        .isA(iri(valueInfo.valueTypeIri.value))
        .andHas(KB.isDeleted, literalOf(false))
        .andHas(KB.valueHasString, literalOf(valueInfo.valueHasString))
        .andHas(KB.valueHasUUID, literalOf(UuidUtil.base64Encode(valueInfo.valueUUID)))
        .andHas(KB.attachedToUser, iri(valueInfo.creator.value))
        .andHas(KB.hasPermissions, literalOf(valueInfo.permissions))
        .andHas(KB.valueHasOrder, literalOf(valueInfo.valueHasOrder))
        .andHas(KB.valueCreationDate, literalOfType(valueInfo.creationDate.toString(), XSD.DATETIME))
        .andHasOptional(KB.valueHasComment, valueInfo.comment.map(literalOf))

    private def buildTypeSpecificValuePattern(
      value: TypeSpecificValueInfo,
      valueIri: String,
      propertyIri: String,
      resourceIri: String,
    ): List[TriplePattern] =
      value match
        case v: LinkValueInfo =>
          buildLinkValuePatterns(v, valueIri, propertyIri, resourceIri)
        case UnformattedTextValueInfo(valueHasLanguage) =>
          List(
            iri(valueIri)
              .has(KB.hasTextValueType, KB.UnformattedText)
              .andHasOptional(KB.valueHasLanguage, valueHasLanguage.map(literalOf)),
          )
        case v: FormattedTextValueInfo =>
          buildFormattedTextValuePatterns(v, valueIri)
        case IntegerValueInfo(valueHasInteger) =>
          List(iri(valueIri).has(KB.valueHasInteger, literalOf(valueHasInteger)))
        case DecimalValueInfo(valueHasDecimal) =>
          List(iri(valueIri).has(KB.valueHasDecimal, literalOfType(valueHasDecimal.toString(), XSD.DECIMAL)))
        case BooleanValueInfo(valueHasBoolean) =>
          List(iri(valueIri).has(KB.valueHasBoolean, literalOf(valueHasBoolean)))
        case UriValueInfo(valueHasUri) =>
          List(iri(valueIri).has(KB.valueHasUri, literalOfType(valueHasUri, XSD.ANYURI)))
        case v: DateValueInfo =>
          buildDateValuePattern(v, valueIri)
        case ColorValueInfo(valueHasColor) =>
          List(iri(valueIri).has(KB.valueHasColor, literalOf(valueHasColor)))
        case GeomValueInfo(valueHasGeometry) =>
          List(iri(valueIri).has(KB.valueHasGeometry, literalOf(valueHasGeometry)))
        case v: FileValueTypeSpecificInfo =>
          List(buildFileValuePattern(v, valueIri))
        case HierarchicalListValueInfo(valueHasListNode) =>
          List(iri(valueIri).has(KB.valueHasListNode, iri(valueHasListNode.value)))
        case IntervalValueInfo(valueHasIntervalStart, valueHasIntervalEnd) =>
          List(
            iri(valueIri)
              .has(KB.valueHasIntervalStart, literalOfType(valueHasIntervalStart.toString(), XSD.DECIMAL))
              .andHas(KB.valueHasIntervalEnd, literalOfType(valueHasIntervalEnd.toString(), XSD.DECIMAL)),
          )
        case TimeValueInfo(valueHasTimeStamp) =>
          List(iri(valueIri).has(KB.valueHasTimeStamp, literalOfType(valueHasTimeStamp.toString(), XSD.DATETIME)))
        case GeonameValueInfo(valueHasGeonameCode) =>
          List(iri(valueIri).has(KB.valueHasGeonameCode, literalOf(valueHasGeonameCode)))

    private def buildLinkValuePatterns(
      v: LinkValueInfo,
      valueIri: String,
      propertyIri: String,
      resourceIri: String,
    ): List[TriplePattern] =
      List(
        iri(resourceIri).has(iri(propertyIri.stripSuffix("Value")), iri(v.referredResourceIri.value)),
        iri(valueIri)
          .has(RDF.SUBJECT, iri(resourceIri))
          .andHas(RDF.PREDICATE, iri(propertyIri.stripSuffix("Value")))
          .andHas(RDF.OBJECT, iri(v.referredResourceIri.value))
          .andHas(KB.valueHasRefCount, literalOf(1)),
      )

    private def buildFormattedTextValuePatterns(v: FormattedTextValueInfo, valueIri: String): List[TriplePattern] =
      val txtTypeIri = v.textValueType match
        case FormattedTextValueType.StandardMapping  => KB.FormattedText
        case FormattedTextValueType.CustomMapping(_) => KB.CustomFormattedText
      val valuePattern =
        iri(valueIri)
          .has(KB.valueHasMapping, iri(v.mappingIri.value))
          .andHas(KB.hasTextValueType, txtTypeIri)
          .andHas(KB.valueHasMaxStandoffStartIndex, literalOf(v.maxStandoffStartIndex))
          .andHasOptional(KB.valueHasLanguage, v.valueHasLanguage.map(literalOf))
      List(valuePattern) ::: v.standoff.map { standoffTagInfo =>
        valuePattern.andHas(KB.valueHasStandoff, iri(standoffTagInfo.standoffTagInstanceIri.value))
        iri(standoffTagInfo.standoffTagInstanceIri.value)
          .isA(iri(standoffTagInfo.standoffTagClassIri.value))
          .andHasOptional(KB.standoffTagHasEndIndex, standoffTagInfo.endIndex.map(i => literalOf(i)))
          .andHasOptional(KB.standoffTagHasStartParent, standoffTagInfo.startParentIri.map(v => iri(v.value)))
          .andHasOptional(KB.standoffTagHasEndParent, standoffTagInfo.endParentIri.map(v => iri(v.value)))
          .andHasOptional(KB.standoffTagHasOriginalXMLID, standoffTagInfo.originalXMLID.map(literalOf))
          .andHas(standoffAttributeLiterals(standoffTagInfo.attributes): _*)
          .andHas(KB.standoffTagHasStartIndex, literalOf(standoffTagInfo.startIndex))
          .andHas(KB.standoffTagHasUUID, literalOf(UuidUtil.base64Encode(standoffTagInfo.uuid)))
          .andHas(KB.standoffTagHasStart, literalOf(standoffTagInfo.startPosition))
          .andHas(KB.standoffTagHasEnd, literalOf(standoffTagInfo.endPosition))
      }.toList

    private def standoffAttributeLiterals(attributes: Seq[StandoffAttribute]): List[RdfPredicateObjectList] =
      attributes.map { attribute =>
        val v = attribute.value match
          case StandoffAttributeValue.IriAttribute(value)               => iri(value.value)
          case StandoffAttributeValue.UriAttribute(value)               => literalOfType(value, XSD.ANYURI)
          case StandoffAttributeValue.InternalReferenceAttribute(value) => iri(value.value)
          case StandoffAttributeValue.StringAttribute(value)            => literalOf(value)
          case StandoffAttributeValue.IntegerAttribute(value)           => literalOf(value)
          case StandoffAttributeValue.DecimalAttribute(value)           => literalOfType(value.toString(), XSD.DECIMAL)
          case StandoffAttributeValue.BooleanAttribute(value)           => literalOf(value)
          case StandoffAttributeValue.TimeAttribute(value)              => literalOfType(value.toString(), XSD.DATETIME)
        val p = iri(attribute.propertyIri.value)
        predicateObjectList(p, v)
      }.toList

    private def buildDateValuePattern(v: DateValueInfo, valueIri: String): List[TriplePattern] =
      List(
        iri(valueIri)
          .has(KB.valueHasStartJDN, literalOf(v.valueHasStartJDN))
          .andHas(KB.valueHasEndJDN, literalOf(v.valueHasEndJDN))
          .andHas(KB.valueHasStartPrecision, literalOf(v.valueHasStartPrecision.toString()))
          .andHas(KB.valueHasEndPrecision, literalOf(v.valueHasEndPrecision.toString()))
          .andHas(KB.valueHasCalendar, literalOf(v.valueHasCalendar.toString())),
      )

    private def buildFileValuePattern(v: FileValueTypeSpecificInfo, valueIri: String): TriplePattern = {
      val result = iri(valueIri)
        .has(KB.internalFilename, literalOf(v.fileValue.internalFilename))
        .andHas(KB.internalMimeType, literalOf(v.fileValue.internalMimeType))
        .andHasOptional(KB.originalFilename, v.fileValue.originalFilename.map(literalOf))
        .andHasOptional(KB.originalMimeType, v.fileValue.originalMimeType.map(literalOf))
        .andHasOptional(KB.hasCopyrightHolder, v.fileValue.copyrightHolder.map(_.value).map(literalOf))
        .andHasOptional(KB.hasLicense, v.fileValue.licenseIri.map(_.value).map(iri))
      v.fileValue.authorship.foreach(_.map(_.value).foreach(result.andHas(KB.hasAuthorship, _)))

      v match {
        case _: OtherFileValueInfo              => result
        case v: StillImageFileValueInfo         => result.andHas(KB.dimX, literalOf(v.dimX)).andHas(KB.dimY, literalOf(v.dimY))
        case v: StillImageExternalFileValueInfo => result.andHas(KB.externalUrl, literalOf(v.externalUrl))
        case v: DocumentFileValueInfo =>
          result
            .andHasOptional(KB.dimX, v.dimX.map(i => literalOf(i)))
            .andHasOptional(KB.dimY, v.dimY.map(i => literalOf(i)))
            .andHasOptional(KB.pageCount, v.pageCount.map(i => literalOf(i)))
      }
    }
  }
}

/**
 * Extends the `TriplePattern` class to add the `andHasOptional` method.
 *
 * This method allows adding an optional triple pattern to the existing triple pattern.
 *
 * @param p The RDF predicate of the optional triple pattern.
 * @param o The RDF object of the optional triple pattern.
 * @return A new `TriplePattern` object that represents the combined triple pattern.
 */
extension (tp: TriplePattern)
  def andHasOptional(p: RdfPredicate, o: Option[RdfObject]): TriplePattern =
    o.fold(tp)(o => tp.andHas(p, o))

/**
 * Extends the `Iri` class to add the `hasOptional` method.
 *
 * This method allows optionally creating a triple if the object is defined.
 *
 * @param p The RDF predicate of the optional triple pattern.
 * @param o The RDF object of the optional triple pattern.
 * @return An optional `TriplePattern` object that represents the combined triple pattern.
 */
extension (iri: Iri)
  def hasOptional(p: RdfPredicate, o: Option[RdfObject]): Option[TriplePattern] =
    o.map(o => iri.has(p, o))
