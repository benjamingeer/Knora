/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import org.eclipse.rdf4j.model.Namespace
import org.eclipse.rdf4j.model.impl.SimpleNamespace
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.InsertDataQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.iri
import zio.*

import java.time.Instant

import dsp.constants.SalsahGui.IRI
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.twirl.NewLinkValueInfo
import org.knora.webapi.messages.twirl.NewValueInfo
import org.knora.webapi.messages.twirl.TypeSpecificValueInfo
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

case class ResourceReadyToCreate(
  resourceIri: IRI,
  resourceClassIri: IRI,
  resourceLabel: String,
  creationDate: Instant,
  permissions: String,
  newValueInfos: Seq[NewValueInfo],
  linkUpdates: Seq[NewLinkValueInfo],
)

trait ResourcesRepo {
  def createNewResource(
    dataGraphIri: InternalIri,
    resource: ResourceReadyToCreate,
    userIri: IRI,
    projectIri: IRI,
  ): Task[Unit]
}

final case class ResourcesRepoLive(triplestore: TriplestoreService) extends ResourcesRepo {

  def createNewResource(
    dataGraphIri: InternalIri,
    resource: ResourceReadyToCreate,
    userIri: IRI,
    projectIri: IRI,
  ): Task[Unit] =
    triplestore.query(
      ResourcesRepoLive.createNewResourceQueryTwirl(
        dataGraphIri,
        resource,
        projectIri,
        userIri,
      ),
    )

}

object ResourcesRepoLive {
  val layer = ZLayer.derive[ResourcesRepoLive]

  private[service] def createNewResourceQueryTwirl(
    dataGraphIri: InternalIri,
    resourceToCreate: ResourceReadyToCreate,
    projectIri: IRI,
    creatorIri: IRI,
  ): Update =
    Update(
      sparql.v2.txt.createNewResource(
        dataNamedGraph = dataGraphIri.value,
        projectIri = projectIri,
        creatorIri = creatorIri,
        creationDate = resourceToCreate.creationDate,
        resourceIri = resourceToCreate.resourceIri,
        resourceClassIri = resourceToCreate.resourceClassIri,
        resourceLabel = resourceToCreate.resourceLabel,
        permissions = resourceToCreate.permissions,
        linkUpdates = resourceToCreate.linkUpdates,
        newValueInfos = resourceToCreate.newValueInfos,
      ),
    )

  private[service] def createNewResourceQuery(
    dataGraphIri: InternalIri,
    resourceToCreate: ResourceReadyToCreate,
    projectIri: IRI,
    creatorIri: IRI,
  ) = {
    val graph = iri(dataGraphIri.value)

    val query: InsertDataQuery =
      Queries
        .INSERT_DATA()
        .into(graph)
        .prefix(KnoraBaseVocab.NS, RDF.NS, RDFS.NS, XSD.NS)

    val resourcePattern =
      Rdf
        .iri(resourceToCreate.resourceIri)
        .isA(iri(resourceToCreate.resourceClassIri))
        .andHas(RDFS.LABEL, Rdf.literalOf(resourceToCreate.resourceLabel))
        .andHas(KnoraBaseVocab.isDeleted, Rdf.literalOf(false))
        .andHas(KnoraBaseVocab.attachedToUser, iri(creatorIri))
        .andHas(KnoraBaseVocab.attachedToProject, iri(projectIri))
        .andHas(KnoraBaseVocab.hasPermissions, Rdf.literalOf(resourceToCreate.permissions))
        .andHas(KnoraBaseVocab.creationDate, Rdf.literalOfType(resourceToCreate.creationDate.toString(), XSD.DATETIME))

    query.insertData(resourcePattern)

    for (newValueInfo <- resourceToCreate.newValueInfos) {
      resourcePattern.andHas(iri(newValueInfo.propertyIri), Rdf.iri(newValueInfo.valueIri))
      query.insertData(valuePatternForCreateNewResource(newValueInfo))
    }

    Update(query.getQueryString())
  }

  private def valuePatternForCreateNewResource(newValueInfo: NewValueInfo): TriplePattern = {
    import TypeSpecificValueInfo.*

    val valuePattern =
      Rdf
        .iri(newValueInfo.valueIri)
        .isA(iri(newValueInfo.valueTypeIri))
        .andHas(KnoraBaseVocab.isDeleted, Rdf.literalOf(false))
        .andHas(KnoraBaseVocab.valueHasString, Rdf.literalOf(newValueInfo.valueHasString))
        .andHas(KnoraBaseVocab.valueHasUUID, Rdf.literalOf(UuidUtil.base64Encode(newValueInfo.valueUUID)))
        .andHas(KnoraBaseVocab.attachedToUser, iri(newValueInfo.valueCreator))
        .andHas(KnoraBaseVocab.hasPermissions, Rdf.literalOf(newValueInfo.valuePermissions))
        .andHas(KnoraBaseVocab.valueHasOrder, Rdf.literalOf(newValueInfo.valueHasOrder))
        .andHas(
          KnoraBaseVocab.valueCreationDate,
          Rdf.literalOfType(newValueInfo.creationDate.toString(), XSD.DATETIME),
        )

    newValueInfo.comment.foreach(comment => valuePattern.andHas(KnoraBaseVocab.valueHasComment, Rdf.literalOf(comment)))

    newValueInfo.value match
      case LinkValueInfo(referredResourceIri)         => ???
      case UnformattedTextValueInfo(valueHasLanguage) => ???
      case FormattedTextValueInfo(valueHasLanguage, mappingIri, maxStandoffStartIndex, standoff) =>
        ???
      case IntegerValueInfo(valueHasInteger) =>
        valuePattern.andHas(KnoraBaseVocab.valueHasInteger, Rdf.literalOf(valueHasInteger))
      case DecimalValueInfo(valueHasDecimal) =>
        valuePattern.andHas(KnoraBaseVocab.valueHasDecimal, Rdf.literalOf(valueHasDecimal))
      case BooleanValueInfo(valueHasBoolean) =>
        valuePattern.andHas(KnoraBaseVocab.valueHasBoolean, Rdf.literalOf(valueHasBoolean))
      case UriValueInfo(valueHasUri) =>
        valuePattern.andHas(KnoraBaseVocab.valueHasUri, Rdf.literalOfType(valueHasUri, XSD.ANYURI))
      case DateValueInfo(startJDN, endJDN, startPrecision, endPrecision, calendar) =>
        valuePattern
          .andHas(KnoraBaseVocab.valueHasStartJDN, Rdf.literalOf(startJDN))
          .andHas(KnoraBaseVocab.valueHasEndJDN, Rdf.literalOf(endJDN))
          .andHas(KnoraBaseVocab.valueHasStartPrecision, Rdf.literalOf(startPrecision.toString()))
          .andHas(KnoraBaseVocab.valueHasEndPrecision, Rdf.literalOf(endPrecision.toString()))
          .andHas(KnoraBaseVocab.valueHasCalendar, Rdf.literalOf(calendar.toString()))
      case ColorValueInfo(valueHasColor) =>
        valuePattern.andHas(KnoraBaseVocab.valueHasColor, Rdf.literalOf(valueHasColor))
      case GeomValueInfo(valueHasGeometry) =>
        valuePattern.andHas(KnoraBaseVocab.valueHasGeometry, Rdf.literalOf(valueHasGeometry))
      case StillImageFileValueInfo(
            internalFilename,
            internalMimeType,
            originalFilename,
            originalMimeType,
            dimX,
            dimY,
          ) =>
        valuePattern
          .andHas(KnoraBaseVocab.internalFilename, Rdf.literalOf(internalFilename))
          .andHas(KnoraBaseVocab.internalMimeType, Rdf.literalOf(internalMimeType))
          .andHas(KnoraBaseVocab.dimX, Rdf.literalOf(dimX))
          .andHas(KnoraBaseVocab.dimY, Rdf.literalOf(dimY))
        originalFilename.foreach(filename =>
          valuePattern.andHas(KnoraBaseVocab.originalFilename, Rdf.literalOf(filename)),
        )
        originalMimeType.foreach(mimeType =>
          valuePattern.andHas(KnoraBaseVocab.originalMimeType, Rdf.literalOf(mimeType)),
        )
      case StillImageExternalFileValueInfo(
            internalFilename,
            internalMimeType,
            originalFilename,
            originalMimeType,
            externalUrl,
          ) =>
        valuePattern
          .andHas(KnoraBaseVocab.internalFilename, Rdf.literalOf(internalFilename))
          .andHas(KnoraBaseVocab.internalMimeType, Rdf.literalOf(internalMimeType))
          .andHas(KnoraBaseVocab.externalUrl, Rdf.literalOf(externalUrl))
        originalFilename.foreach(filename =>
          valuePattern.andHas(KnoraBaseVocab.originalFilename, Rdf.literalOf(filename)),
        )
        originalMimeType.foreach(mimeType =>
          valuePattern.andHas(KnoraBaseVocab.originalMimeType, Rdf.literalOf(mimeType)),
        )
      case DocumentFileValueInfo(
            internalFilename,
            internalMimeType,
            originalFilename,
            originalMimeType,
            dimX,
            dimY,
            pageCount,
          ) =>
        valuePattern
          .andHas(KnoraBaseVocab.internalFilename, Rdf.literalOf(internalFilename))
          .andHas(KnoraBaseVocab.internalMimeType, Rdf.literalOf(internalMimeType))
        originalFilename.foreach(filename =>
          valuePattern.andHas(KnoraBaseVocab.originalFilename, Rdf.literalOf(filename)),
        )
        originalMimeType.foreach(mimeType =>
          valuePattern.andHas(KnoraBaseVocab.originalMimeType, Rdf.literalOf(mimeType)),
        )
        dimX.foreach(x => valuePattern.andHas(KnoraBaseVocab.dimX, Rdf.literalOf(x)))
        dimY.foreach(y => valuePattern.andHas(KnoraBaseVocab.dimY, Rdf.literalOf(y)))
        pageCount.foreach(count => valuePattern.andHas(KnoraBaseVocab.pageCount, Rdf.literalOf(count)))
      case OtherFileValueInfo(internalFilename, internalMimeType, originalFilename, originalMimeType) =>
        ???
      case HierarchicalListValueInfo(valueHasListNode)                   => ???
      case IntervalValueInfo(valueHasIntervalStart, valueHasIntervalEnd) => ???
      case TimeValueInfo(valueHasTimeStamp)                              => ???
      case GeonameValueInfo(valueHasGeonameCode)                         => ???

    valuePattern
  }
}

object KnoraBaseVocab {
  private val kb = "http://www.knora.org/ontology/knora-base#"

  val NS: Namespace = new SimpleNamespace("knora-base", kb)

  val isDeleted         = iri(kb + "isDeleted")
  val attachedToUser    = iri(kb + "attachedToUser")
  val attachedToProject = iri(kb + "attachedToProject")
  val hasPermissions    = iri(kb + "hasPermissions")
  val creationDate      = iri(kb + "creationDate")

  val valueHasString    = iri(kb + "valueHasString")
  val valueHasUUID      = iri(kb + "valueHasUUID")
  val valueHasComment   = iri(kb + "valueHasComment")
  val valueHasOrder     = iri(kb + "valueHasOrder")
  val valueCreationDate = iri(kb + "valueCreationDate")

  val valueHasInteger        = iri(kb + "valueHasInteger")
  val valueHasBoolean        = iri(kb + "valueHasBoolean")
  val valueHasDecimal        = iri(kb + "valueHasDecimal")
  val valueHasUri            = iri(kb + "valueHasUri")
  val valueHasStartJDN       = iri(kb + "valueHasStartJDN")
  val valueHasEndJDN         = iri(kb + "valueHasEndJDN")
  val valueHasStartPrecision = iri(kb + "valueHasStartPrecision")
  val valueHasEndPrecision   = iri(kb + "valueHasEndPrecision")
  val valueHasCalendar       = iri(kb + "valueHasCalendar")
  val valueHasColor          = iri(kb + "valueHasColor")
  val valueHasGeometry       = iri(kb + "valueHasGeometry")

  val internalFilename = iri(kb + "internalFilename")
  val internalMimeType = iri(kb + "internalMimeType")
  val originalFilename = iri(kb + "originalFilename")
  val originalMimeType = iri(kb + "originalMimeType")
  val dimX             = iri(kb + "dimX")
  val dimY             = iri(kb + "dimY")
  val externalUrl      = iri(kb + "externalUrl")
  val pageCount        = iri(kb + "pageCount")

}
