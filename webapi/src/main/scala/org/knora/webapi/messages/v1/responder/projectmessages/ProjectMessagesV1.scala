/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v1.responder.projectmessages

import org.knora.webapi.IRI

/**
 * Represents basic information about a project.
 *
 * @param id          The project's IRI.
 * @param shortname   The project's shortname. Needs to be system wide unique.
 * @param longname    The project's long name. Needs to be system wide unique.
 * @param description The project's description.
 * @param keywords    The project's keywords.
 * @param logo        The project's logo.
 * @param institution The project's institution.
 * @param ontologies  The project's ontologies.
 * @param status      The project's status.
 * @param selfjoin    The project's self-join status.
 */
case class ProjectInfoV1(
  id: IRI,
  shortname: String,
  shortcode: String,
  longname: Option[String],
  description: Option[String],
  keywords: Option[String],
  logo: Option[String],
  institution: Option[IRI],
  ontologies: Seq[IRI],
  status: Boolean,
  selfjoin: Boolean
)
