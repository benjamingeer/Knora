/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.spray.{jsonBody => sprayJsonBody}
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectKeywordsGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectRestrictedViewSettingsGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsADMJsonProtocol
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsKeywordsGetResponseADM
import org.knora.webapi.routing.BaseEndpoints
import org.knora.webapi.routing.PathVariables.projectIri
import org.knora.webapi.routing.PathVariables.projectShortcode
import org.knora.webapi.routing.PathVariables.projectShortname

final case class ProjectsEndpoints(
  baseEndpoints: BaseEndpoints
) extends ProjectsADMJsonProtocol {

  private val projectsBase           = "admin" / "projects"
  private val projectsByIri          = projectsBase / "iri" / projectIri
  private val projectsByShortcode    = projectsBase / "shortcode" / projectShortcode
  private val projectsByShortname    = projectsBase / "shortname" / projectShortname
  private val keywords               = "Keywords"
  private val restrictedViewSettings = "RestrictedViewSettings"
  private val tags                   = List("Projects", "Admin API")

  val getAdminProjects = baseEndpoints.publicEndpoint.get
    .in(projectsBase)
    .out(sprayJsonBody[ProjectsGetResponseADM])
    .description("Returns all projects.")
    .tags(tags)

  val getAdminProjectsKeywords = baseEndpoints.publicEndpoint.get
    .in(projectsBase / keywords)
    .out(sprayJsonBody[ProjectsKeywordsGetResponseADM])
    .description("Returns all unique keywords for all projects as a list.")
    .tags(tags)

  val getAdminProjectsByProjectIri = baseEndpoints.publicEndpoint.get
    .in(projectsByIri)
    .out(sprayJsonBody[ProjectGetResponseADM])
    .description("Returns a single project identified through the IRI.")
    .tags(tags)

  val getAdminProjectsByProjectShortcode = baseEndpoints.publicEndpoint.get
    .in(projectsByShortcode)
    .out(sprayJsonBody[ProjectGetResponseADM])
    .description("Returns a single project identified through the shortcode.")
    .tags(tags)

  val getAdminProjectsByProjectShortname = baseEndpoints.publicEndpoint.get
    .in(projectsByShortname)
    .out(sprayJsonBody[ProjectGetResponseADM])
    .description("Returns a single project identified through the shortname.")
    .tags(tags)

  val getAdminProjectsKeywordsByProjectIri = baseEndpoints.publicEndpoint.get
    .in(projectsByIri / keywords)
    .out(sprayJsonBody[ProjectKeywordsGetResponseADM])
    .description("Returns all keywords for a single project.")
    .tags(tags)

  val getAdminProjectsByProjectIriRestrictedViewSettings = baseEndpoints.publicEndpoint.get
    .in(projectsByIri / restrictedViewSettings)
    .out(sprayJsonBody[ProjectRestrictedViewSettingsGetResponseADM])
    .description("Returns the project's restricted view settings identified through the IRI.")
    .tags(tags)

  val getAdminProjectsByProjectShortcodeRestrictedViewSettings = baseEndpoints.publicEndpoint.get
    .in(projectsByShortcode / restrictedViewSettings)
    .out(sprayJsonBody[ProjectRestrictedViewSettingsGetResponseADM])
    .description("Returns the project's restricted view settings identified through the shortcode.")
    .tags(tags)

  val getAdminProjectsByProjectShortnameRestrictedViewSettings = baseEndpoints.publicEndpoint.get
    .in(projectsByShortname / restrictedViewSettings)
    .out(sprayJsonBody[ProjectRestrictedViewSettingsGetResponseADM])
    .description("Returns the project's restricted view settings identified through the shortname.")
    .tags(tags)
}

object ProjectsEndpoints {
  val layer = ZLayer.derive[ProjectsEndpoints]
}
