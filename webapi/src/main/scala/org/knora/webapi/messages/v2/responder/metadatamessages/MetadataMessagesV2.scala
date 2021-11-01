/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.metadatamessages

import java.util.UUID

import org.knora.webapi.IRI
import org.knora.webapi.exceptions.{BadRequestException, ForbiddenException}
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.messages.v2.responder._

/**
 * An abstract trait for messages that can be sent to `ResourcesResponderV2`.
 */
sealed trait MetadataResponderRequestV2 extends KnoraRequestV2 {

  /**
   * The user that made the request.
   */
  def requestingUser: UserADM
}

/**
 * Requests metadata about a project. A successful response will be a [[MetadataGetResponseV2]].
 *
 * @param projectADM           the project for which metadata is requested.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 */
case class MetadataGetRequestV2(
  projectADM: ProjectADM,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM
) extends MetadataResponderRequestV2 {
  val projectIri: IRI = projectADM.id

  // Ensure that the project isn't the system project or the shared ontologies project.
  if (
    projectIri == OntologyConstants.KnoraAdmin.SystemProject || projectIri == OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject
  ) {
    throw BadRequestException(s"Metadata cannot be requested from project <$projectIri>")
  }
}

/**
 * Represents metadata about a project.
 *
 * @param turtle project metadata in Turtle format.
 */
case class MetadataGetResponseV2(turtle: String) extends KnoraTurtleResponseV2

/**
 * A request to create or update metadata about a project. If metadata already exists
 * for the project, it will be replaced by the metadata in this message. A successful response
 * will be a [[SuccessResponseV2]].
 *
 * @param rdfModel             the project metadata to be stored.
 * @param projectADM           the project.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 * @param apiRequestID         the API request ID.
 */
case class MetadataPutRequestV2(
  rdfModel: RdfModel,
  projectADM: ProjectADM,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends KnoraRdfModelRequestV2
    with MetadataResponderRequestV2 {

  /**
   * The project IRI.
   */
  val projectIri: IRI = projectADM.id

  // Check if the requesting user is allowed to create project metadata.
  if (!requestingUser.permissions.isSystemAdmin && !requestingUser.permissions.isProjectAdmin(projectIri)) {
    // Not a system or project admin, so not allowed.
    throw ForbiddenException("Project metadata can only be updated by a system or project admin")
  }

  // Ensure that the project isn't the system project or the shared ontologies project.
  if (
    projectIri == OntologyConstants.KnoraAdmin.SystemProject || projectIri == OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject
  ) {
    throw BadRequestException(s"Metadata cannot be created in project <$projectIri>")
  }

  // Don't allow named graphs.
  if (rdfModel.getContexts.nonEmpty) {
    throw BadRequestException("A project metadata request cannot contain named graphs")
  }
}
