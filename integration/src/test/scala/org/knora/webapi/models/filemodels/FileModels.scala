/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models.filemodels

import java.time.Instant
import java.util.UUID

import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateValueInNewResourceV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*

sealed abstract case class UploadFileRequest private (
  fileType: FileType,
  internalFilename: String,
  label: String,
  resourceIRI: Option[String] = None,
  copyrightHolder: Option[CopyrightHolder] = None,
  authorship: Option[List[Authorship]] = None,
  licenseIri: Option[LicenseIri] = None,
) { self =>

  /**
   * Create a JSON-LD serialization of the request. This can be used for e2e and integration tests.
   *
   * @param shortcode    the project's shortcode. Optional.
   * @param ontologyName the name of the ontology to be prefixed to the class name. Defaults to `"knora-api"`
   * @param className    the class name of the resource. Optional.
   * @param ontologyIRI  IRI of the ontology, to which the prefix should resolve. Optional.
   * @return JSON-LD serialization of the request.
   */
  def toJsonLd(
    shortcode: Shortcode = Shortcode.unsafeFrom("0001"),
    ontologyName: String = "knora-api",
    className: Option[String] = None,
    ontologyIRI: Option[String] = None,
  ): String = {
    val fileValuePropertyName = FileModelUtil.getFileValuePropertyName(fileType)
    val fileValueType         = FileModelUtil.getFileValueType(fileType)
    val context               = FileModelUtil.getJsonLdContext(ontologyName, ontologyIRI)
    val classNameWithDefaults = className match {
      case Some(v) => v
      case None    => FileModelUtil.getDefaultClassName(fileType)
    }
    val resorceIRIOrEmptyString = resourceIRI match {
      case Some(v) => s""" "@id": "$v",\n  """
      case None    => ""
    }

    val copyrightHolderJson =
      copyrightHolder.map(ca => s""","knora-api:hasCopyrightHolder" : "${ca.value}"""").getOrElse("")
    val authorshipJson = authorship
      .filter(_.nonEmpty)
      .map(a => s""","knora-api:hasAuthorship" : [ ${a.map(_.value).mkString("\"", "\",\"", "\"")} ]""")
      .getOrElse("")

    val jsonLd =
      s"""{
         |  $resorceIRIOrEmptyString"@type" : "$ontologyName:$classNameWithDefaults",
         |  "$fileValuePropertyName" : {
         |    "@type" : "$fileValueType",
         |    "knora-api:fileValueHasFilename" : "$internalFilename"
         |    $copyrightHolderJson
         |    $authorshipJson
         |    ${licenseIri
          .map(u => s""","knora-api:hasLicense" : { "@id" : "${u.value}" }""")
          .getOrElse("")}
         |  },
         |  "knora-api:attachedToProject" : {
         |    "@id" : "http://rdfh.ch/projects/$shortcode"
         |  },
         |  "rdfs:label" : "$label",
         |  $context}""".stripMargin
    jsonLd
  }

  /**
   * Represents the present [[UploadFileRequest]] as a [[CreateResourceV2]].
   *
   * Various custom values can be supplied. If not, reasonable default values for testing purposes will be used.
   *
   * @param resourceIri             the custom IRI of the resource. Optional. Defaults to None. If None, a random IRI is generated
   * @param comment                 comment. Optional.
   * @param internalMimeType        internal mime type as determined by SIPI. Optional.
   * @param originalMimeType        original mime type previous to uploading to SIPI. Optional.
   * @param originalFilename        original filename previous to uploading to SIPI. Optional.
   * @param customValueIri          custom IRI for the value. Optional. Defaults to None.
   *                                If None, an IRI will be generated.
   * @param customValueUUID         custom UUID for the value. Optional. Defaults to None.
   *                                If None, a UUID will be generated.
   * @param customValueCreationDate custom creation date for the value. Optional. Defaults to None.
   *                                If None, the current instant will be used.
   * @param valuePermissions        custom permissions for the value. Optional. Defaults to None.
   *                                If `None`, the default permissions will be used.
   * @param resourcePermissions     permissions for the resource. Optional. If none, the default permissions are used.
   * @param resourceCreationDate    custom creation date of the resource. Optional.
   * @param valuePropertyIRI        property IRI of the value. Optional.
   * @param resourceClassIRI        resource class IRI. Optional.
   * @param project                 the project to which the resource belongs. Optional. Defaults to None.
   *                                If None, [[SharedTestDataADM.anythingProject]] is used.
   * @return a [[CreateResourceV2]] representation of the [[UploadFileRequest]]
   */
  def toMessage(
    resourceIri: Option[String] = None,
    internalMimeType: Option[String] = None,
    originalFilename: Option[String] = None,
    originalMimeType: Option[String] = None,
    comment: Option[String] = None,
    customValueIri: Option[SmartIri] = None,
    customValueUUID: Option[UUID] = None,
    customValueCreationDate: Option[Instant] = None,
    valuePermissions: Option[String] = None,
    resourcePermissions: Option[String] = None,
    resourceCreationDate: Option[Instant] = None,
    resourceClassIRI: Option[SmartIri] = None,
    valuePropertyIRI: Option[SmartIri] = None,
    project: Option[Project] = None,
  ): CreateResourceV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val projectOrDefault =
      project.getOrElse(SharedTestDataADM.anythingProject)
    val resourceIRIOrDefault =
      resourceIri.getOrElse(stringFormatter.makeRandomResourceIri(projectOrDefault.shortcode))
    val resourceClassIRIOrDefault: SmartIri =
      resourceClassIRI.getOrElse(FileModelUtil.getFileRepresentationClassIri(fileType))
    val fileValuePropertyIRIOrDefault: SmartIri =
      valuePropertyIRI.getOrElse(FileModelUtil.getFileRepresentationPropertyIri(fileType))
    val valueContent = FileModelUtil.getFileValueContent(
      fileType = fileType,
      internalFilename = internalFilename,
      internalMimeType = internalMimeType,
      originalFilename = originalFilename,
      originalMimeType = originalMimeType,
      comment = comment,
      copyrightHolder = self.copyrightHolder,
      authorship = self.authorship,
      licenseIri = self.licenseIri,
    )

    val values = List(
      CreateValueInNewResourceV2(
        valueContent = valueContent,
        customValueIri = customValueIri,
        customValueUUID = customValueUUID,
        customValueCreationDate = customValueCreationDate,
        permissions = valuePermissions,
      ),
    )

    CreateResourceV2(
      resourceIri = Some(resourceIRIOrDefault.toSmartIri),
      resourceClassIri = resourceClassIRIOrDefault,
      label = label,
      values = Map(fileValuePropertyIRIOrDefault -> values),
      projectADM = projectOrDefault,
      permissions = resourcePermissions,
      creationDate = resourceCreationDate,
    )
  }
}

/**
 * Helper object for creating a request to upload a file.
 *
 * Can be instantiated by calling `UploadFileRequest.make()`.
 *
 * To generate a JSON-LD request, call `.toJsonLd`.
 *
 * To generate a [[CreateResourceV2]] message, call `.toMessage`
 */
object UploadFileRequest {
  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  /**
   * Smart constructor for instantiating a [[UploadFileRequest]].
   *
   * @param fileType         the [[FileType]] of the resource.
   * @param internalFilename the internal file name assigned by SIPI.
   * @param label            the rdf:label
   * @return returns a [[UploadFileRequest]] object storing all information needed to generate a Message
   *         or JSON-LD serialization that can be used to generate the respective resource in the API.
   */
  def make(
    fileType: FileType,
    internalFilename: String,
    label: String = "test label",
    resourceIRI: Option[String] = None,
    copyrightHolder: Option[CopyrightHolder] = None,
    authorship: Option[List[Authorship]] = None,
    licenseIri: Option[LicenseIri] = None,
  ): UploadFileRequest =
    new UploadFileRequest(
      fileType,
      internalFilename,
      label,
      resourceIRI,
      copyrightHolder,
      authorship,
      licenseIri,
    ) {}
}

sealed abstract case class ChangeFileRequest private (
  fileType: FileType,
  internalFilename: String,
  resourceIRI: String,
  valueIRI: String,
  className: String,
  ontologyName: String,
) {

  /**
   * Create a JSON-LD serialization of the request. This can be used for e2e and integration tests.
   *
   * @return JSON-LD serialization of the request.
   */
  def toJsonLd: String = {
    val fileValuePropertyName = FileModelUtil.getFileValuePropertyName(fileType)
    val fileValueType         = FileModelUtil.getFileValueType(fileType)
    val context               = FileModelUtil.getJsonLdContext(ontologyName)

    s"""{
       |  "@id" : "$resourceIRI",
       |  "@type" : "$ontologyName:$className",
       |  "$fileValuePropertyName" : {
       |    "@id" : "$valueIRI",
       |    "@type" : "$fileValueType",
       |    "knora-api:fileValueHasFilename" : "$internalFilename"
       |  },
       |  $context
       |}""".stripMargin
  }
}

/**
 * Helper object for creating a request to change a file representation.
 *
 * Can be instantiated by calling `ChangeFileRequest.make()`.
 *
 * To generate a JSON-LD request, call `.toJsonLd`.
 */
object ChangeFileRequest {

  /**
   * Smart constructor for instantiating a [[ChangeFileRequest]].
   *
   * @param fileType         the [[FileType]] of the resource.
   * @param internalFilename the internal file name assigned by SIPI.
   * @param resourceIri      the IRI of the resource where a property is to change.
   * @param valueIri         the IRI of the value property to change.
   * @param className        the class name of the resource. Optional.
   * @param ontologyName     the name of the ontology to be prefixed to the class name. Defaults to `"knora-api"`
   * @return returns a [[ChangeFileRequest]] object storing all information needed to generate a Message
   *         or JSON-LD serialization that can be used to change the respective resource in the API.
   */
  def make(
    fileType: FileType,
    internalFilename: String,
    resourceIri: String,
    valueIri: String,
    className: Option[String] = None,
    ontologyName: String = "knora-api",
  ): ChangeFileRequest =
    new ChangeFileRequest(
      fileType = fileType,
      internalFilename = internalFilename,
      resourceIRI = resourceIri,
      valueIRI = valueIri,
      className = className.getOrElse(FileModelUtil.getDefaultClassName(fileType)),
      ontologyName = ontologyName,
    ) {}
}
