/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.*

import org.knora.webapi.slice.admin.api.Codecs.TapirCodec
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.PermissionIri

object AdminPathVariables {

  val groupIriPathVar: EndpointInput.PathCapture[GroupIri] =
    path[GroupIri]
      .name("groupIri")
      .description("The IRI of a group. Must be URL-encoded.")
      .example(Examples.GroupExample.groupIri)

  val permissionIri: EndpointInput.PathCapture[PermissionIri] =
    path[PermissionIri]("permissionIri")
      .description("The IRI of a permission. Must be URL-encoded.")
      .example(PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA"))

  val projectIri: EndpointInput.PathCapture[ProjectIri] =
    path[ProjectIri]
      .name("projectIri")
      .description("The IRI of a project. Must be URL-encoded.")
      .example(ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"))

  val projectShortcode: EndpointInput.PathCapture[Shortcode] =
    path[Shortcode](TapirCodec.shortcode)
      .name("projectShortcode")
      .description("The shortcode of a project. Must be a 4 digit hexadecimal String.")
      .example(Shortcode.unsafeFrom("0001"))

  val projectShortname: EndpointInput.PathCapture[Shortname] =
    path[Shortname](TapirCodec.shortname)
      .name("projectShortname")
      .description("The shortname of a project.")
      .example(Shortname.unsafeFrom("someShortname"))
}
