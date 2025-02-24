/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models.standoffmodels

import spray.json.*
import spray.json.DefaultJsonProtocol.*

import java.util.UUID

import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.messages.v2.responder.standoffmessages.CreateMappingRequestMetadataV2
import org.knora.webapi.messages.v2.responder.standoffmessages.CreateMappingRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.CreateMappingRequestXMLV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM2.anythingProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User

sealed abstract case class DefineStandoffMapping private (
  mappingName: String,
  projectIRI: String,
  label: String,
) {

  /**
   * Create a JSON-LD serialization of the request. This can be used for e2e tests.
   *
   * @return JSON-LD serialization of the request that can be processed by the API V2.
   */
  def toJSONLD(): String =
    Map(
      "knora-api:mappingHasName" -> mappingName.toJson,
      "knora-api:attachedToProject" -> Map(
        JsonLDKeywords.ID -> projectIRI,
      ).toJson,
      "rdfs:label" -> label.toJson,
      JsonLDKeywords.CONTEXT -> Map(
        "rdfs"      -> OntologyConstants.Rdfs.RdfsPrefixExpansion,
        "knora-api" -> OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion,
      ).toJson,
    ).toJson.prettyPrint

  /**
   * Create a [[CreateMappingRequestV2]] message representation of the request. This can be used in unit tests.
   *
   * @param xml                  the mapping XML.
   *
   * @param user                 the user issuing the request.
   * @return a [[CreateMappingRequestV2]] message representation of the request that can be processed by an Akka actor.
   */
  def toMessage(
    xml: String,
    user: User,
  ): CreateMappingRequestV2 = {
    val mappingMetadata = CreateMappingRequestMetadataV2(
      label = label,
      projectIri = ProjectIri.unsafeFrom(projectIRI),
      mappingName = mappingName,
    )
    CreateMappingRequestV2(
      metadata = mappingMetadata,
      xml = CreateMappingRequestXMLV2(xml),
      requestingUser = user,
      apiRequestID = UUID.randomUUID(),
    )
  }

}

/**
 * Helper object for creating a custom standoff mapping.
 *
 * Can be instantiated by calling `DefineStandoffMapping.make()`.
 *
 * To generate a JSON-LD request, call `.toJsonLd`.
 *
 * To generate a [[CreateMappingRequestV2]] message, call `.toMessage`
 */
object DefineStandoffMapping {

  /**
   * Smart constructor for instantiating a [[DefineStandoffMapping]] request.
   *
   * @param mappingName the name of the mapping.
   * @param projectIRI  the IRI of the project to which the mapping gets attached. Optional.
   *                    If not provided, the "anything" project will be used.
   * @param label       the rdfs:label of the mapping. Optional.
   * @return a [[DefineStandoffMapping]] object.
   */
  def make(
    mappingName: String,
    projectIRI: Option[String] = None,
    label: Option[String] = None,
  ): DefineStandoffMapping =
    new DefineStandoffMapping(
      mappingName = mappingName,
      projectIRI = projectIRI match {
        case Some(iri) => iri
        case None      => anythingProjectIri
      },
      label = label match {
        case Some(v) => v
        case None    => "custom mapping"
      },
    ) {}
}

object XXX {}
