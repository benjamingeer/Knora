/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models.filemodels

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.resources.IiifImageRequestUrl

object FileModelUtil {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  def getFileRepresentationClassIri(fileType: FileType): SmartIri = fileType match {
    case FileType.DocumentFile(_, _, _)     => OntologyConstants.KnoraApiV2Complex.DocumentRepresentation.toSmartIri
    case FileType.StillImageFile(_, _)      => OntologyConstants.KnoraApiV2Complex.StillImageRepresentation.toSmartIri
    case FileType.StillImageExternalFile(_) => OntologyConstants.KnoraApiV2Complex.StillImageRepresentation.toSmartIri
    case FileType.MovingImageFile(_, _)     => OntologyConstants.KnoraApiV2Complex.MovingImageRepresentation.toSmartIri
    case FileType.TextFile                  => OntologyConstants.KnoraApiV2Complex.TextRepresentation.toSmartIri
    case FileType.AudioFile                 => OntologyConstants.KnoraApiV2Complex.AudioRepresentation.toSmartIri
    case FileType.ArchiveFile               => OntologyConstants.KnoraApiV2Complex.ArchiveRepresentation.toSmartIri
  }

  def getFileRepresentationPropertyIri(fileType: FileType): SmartIri = fileType match {
    case FileType.DocumentFile(_, _, _)     => OntologyConstants.KnoraApiV2Complex.HasDocumentFileValue.toSmartIri
    case FileType.StillImageFile(_, _)      => OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri
    case FileType.StillImageExternalFile(_) => OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri
    case FileType.MovingImageFile(_, _)     => OntologyConstants.KnoraApiV2Complex.HasMovingImageFileValue.toSmartIri
    case FileType.TextFile                  => OntologyConstants.KnoraApiV2Complex.HasTextFileValue.toSmartIri
    case FileType.AudioFile                 => OntologyConstants.KnoraApiV2Complex.HasAudioFileValue.toSmartIri
    case FileType.ArchiveFile               => OntologyConstants.KnoraApiV2Complex.HasArchiveFileValue.toSmartIri
  }

  def getFileValuePropertyName(fileType: FileType): String = fileType match {
    case FileType.DocumentFile(_, _, _)     => "knora-api:hasDocumentFileValue"
    case FileType.StillImageFile(_, _)      => "knora-api:hasStillImageFileValue"
    case FileType.StillImageExternalFile(_) => "knora-api:hasStillImageFileValue"
    case FileType.MovingImageFile(_, _)     => "knora-api:hasMovingImageFileValue"
    case FileType.TextFile                  => "knora-api:hasTextFileValue"
    case FileType.AudioFile                 => "knora-api:hasAudioFileValue"
    case FileType.ArchiveFile               => "knora-api:hasArchiveFileValue"
  }

  def getDefaultClassName(fileType: FileType): String = fileType match {
    case FileType.DocumentFile(_, _, _)     => "DocumentRepresentation"
    case FileType.StillImageFile(_, _)      => "StillImageRepresentation"
    case FileType.StillImageExternalFile(_) => "StillImageRepresentation"
    case FileType.MovingImageFile(_, _)     => "MovingImageRepresentation"
    case FileType.TextFile                  => "TextRepresentation"
    case FileType.AudioFile                 => "AudioRepresentation"
    case FileType.ArchiveFile               => "ArchiveRepresentation"
  }

  def getFileValueType(fileType: FileType): String = fileType match {
    case FileType.DocumentFile(_, _, _)     => "knora-api:DocumentFileValue"
    case FileType.StillImageFile(_, _)      => "knora-api:StillImageFileValue"
    case FileType.StillImageExternalFile(_) => "knora-api:StillImageExternalFileValue"
    case FileType.MovingImageFile(_, _)     => "knora-api:MovingImageFileValue"
    case FileType.TextFile                  => "knora-api:TextFileValue"
    case FileType.AudioFile                 => "knora-api:AudioFileValue"
    case FileType.ArchiveFile               => "knora-api:ArchiveFileValue"
  }

  def getFileValueTypeIRI(fileType: FileType): SmartIri = fileType match {
    case FileType.DocumentFile(_, _, _) => OntologyConstants.KnoraApiV2Complex.DocumentFileValue.toSmartIri
    case FileType.StillImageFile(_, _)  => OntologyConstants.KnoraApiV2Complex.StillImageFileValue.toSmartIri
    case FileType.StillImageExternalFile(_) =>
      OntologyConstants.KnoraApiV2Complex.StillImageExternalFileValue.toSmartIri
    case FileType.MovingImageFile(_, _) => OntologyConstants.KnoraApiV2Complex.MovingImageFileValue.toSmartIri
    case FileType.TextFile              => OntologyConstants.KnoraApiV2Complex.TextFileValue.toSmartIri
    case FileType.AudioFile             => OntologyConstants.KnoraApiV2Complex.AudioFileValue.toSmartIri
    case FileType.ArchiveFile           => OntologyConstants.KnoraApiV2Complex.AudioFileValue.toSmartIri
  }

  def getFileValueContent(
    fileType: FileType,
    internalFilename: String,
    internalMimeType: Option[String],
    originalFilename: Option[String],
    originalMimeType: Option[String],
    copyrightHolder: Option[CopyrightHolder] = None,
    authorship: Option[List[Authorship]] = None,
    licenseIri: Option[LicenseIri] = None,
    comment: Option[String] = None,
  ): FileValueContentV2 =
    fileType match {
      case FileType.DocumentFile(pageCount, dimX, dimY) =>
        DocumentFileValueContentV2(
          ontologySchema = ApiV2Complex,
          fileValue = FileValueV2(
            internalFilename = internalFilename,
            internalMimeType = internalMimeType.getOrElse("application/pdf"),
            originalFilename = originalFilename,
            originalMimeType = Some(originalMimeType.getOrElse("application/pdf")),
            copyrightHolder = copyrightHolder,
            authorship = authorship,
            licenseIri = licenseIri,
          ),
          pageCount = pageCount,
          dimX = dimX,
          dimY = dimY,
          comment = comment,
        )
      case FileType.StillImageFile(dimX, dimY) =>
        StillImageFileValueContentV2(
          ontologySchema = ApiV2Complex,
          fileValue = FileValueV2(
            internalFilename = internalFilename,
            internalMimeType = internalMimeType.getOrElse("image/jp2"),
            originalFilename = originalFilename,
            originalMimeType = originalMimeType,
            copyrightHolder = copyrightHolder,
            authorship = authorship,
            licenseIri = licenseIri,
          ),
          dimX = dimX,
          dimY = dimY,
          comment = comment,
        )
      case FileType.StillImageExternalFile(externalUrl) =>
        StillImageExternalFileValueContentV2(
          ontologySchema = ApiV2Complex,
          fileValue = FileValueV2(
            internalFilename = internalFilename,
            internalMimeType = internalMimeType.getOrElse("image/jp2"),
            originalFilename = originalFilename,
            originalMimeType = originalMimeType,
            copyrightHolder = copyrightHolder,
            authorship = authorship,
            licenseIri = licenseIri,
          ),
          externalUrl = externalUrl,
          comment = comment,
        )
      case FileType.MovingImageFile(_, _) =>
        MovingImageFileValueContentV2(
          ontologySchema = ApiV2Complex,
          fileValue = FileValueV2(
            internalFilename = internalFilename,
            internalMimeType = internalMimeType.get,
            originalFilename = originalFilename,
            originalMimeType = internalMimeType,
            copyrightHolder = copyrightHolder,
            authorship = authorship,
            licenseIri = licenseIri,
          ),
        )
      case FileType.TextFile =>
        TextFileValueContentV2(
          ontologySchema = ApiV2Complex,
          fileValue = FileValueV2(
            internalFilename = internalFilename,
            internalMimeType = internalMimeType.get,
            originalFilename = originalFilename,
            originalMimeType = internalMimeType,
            copyrightHolder = copyrightHolder,
            authorship = authorship,
            licenseIri = licenseIri,
          ),
        )
      case FileType.AudioFile =>
        AudioFileValueContentV2(
          ontologySchema = ApiV2Complex,
          fileValue = FileValueV2(
            internalFilename = internalFilename,
            internalMimeType = internalMimeType.get,
            originalFilename = originalFilename,
            originalMimeType = internalMimeType,
            copyrightHolder = copyrightHolder,
            authorship = authorship,
            licenseIri = licenseIri,
          ),
        )
      case FileType.ArchiveFile =>
        ArchiveFileValueContentV2(
          ontologySchema = ApiV2Complex,
          fileValue = FileValueV2(
            internalFilename = internalFilename,
            internalMimeType = internalMimeType.getOrElse("application/zip"),
            originalFilename = originalFilename,
            originalMimeType = internalMimeType,
            copyrightHolder = copyrightHolder,
            authorship = authorship,
            licenseIri = licenseIri,
          ),
          comment = comment,
        )
    }

  def getJsonLdContext(ontology: String, ontologyIRI: Option[String] = None): String = {
    val ontologies = ontology match {
      case "anything" =>
        FileModelConstants.defaultJsonLdContextMap + ("anything" -> "http://0.0.0.0:3333/ontology/0001/anything/v2#")
      case prefix =>
        ontologyIRI match {
          case Some(iri) => FileModelConstants.defaultJsonLdContextMap + (prefix -> iri)
          case None      => FileModelConstants.defaultJsonLdContextMap
        }
    }
    val lines = ontologies.toList
      .map(x => s"""    "${x._1}": "${x._2}\"""")
      .reduce((a, b) => a + ",\n" + b)
    s"""|"@context" : {
        |$lines
        |  }
        |""".stripMargin
  }
}

/**
 * Constants for use in FileModels.
 */
object FileModelConstants {
  val documentRepresentation    = "DocumentRepresentation"
  val textRepresentation        = "TextRepresentation"
  val stillImageRepresentation  = "StillImageRepresentation"
  val movingImageRepresentation = "MovingImageRepresentation"
  val audioRepresentation       = "AudioRepresentation"
  val archiveRepresentation     = "ArchiveRepresentation"
  val knoraApiPrefix            = "knora-api"
  val anythingShortcode         = "0001"
  val defaultJsonLdContextMap = Map(
    "rdf"       -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api" -> "http://api.knora.org/ontology/knora-api/v2#",
    "rdfs"      -> "http://www.w3.org/2000/01/rdf-schema#",
    "xsd"       -> "http://www.w3.org/2001/XMLSchema#",
  )
}

sealed trait FileType
object FileType {
  case class DocumentFile(
    pageCount: Option[Int] = Some(1),
    dimX: Option[Int] = Some(100),
    dimY: Option[Int] = Some(100),
  ) extends FileType
  case class StillImageFile(dimX: Int = 100, dimY: Int = 100)         extends FileType
  case class StillImageExternalFile(externalUrl: IiifImageRequestUrl) extends FileType
  case class MovingImageFile(dimX: Int = 100, dimY: Int = 100)        extends FileType
  case object TextFile                                                extends FileType
  case object AudioFile                                               extends FileType
  case object ArchiveFile                                             extends FileType
}
