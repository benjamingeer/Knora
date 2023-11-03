/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.NonEmptyChunk

import dsp.valueobjects.Project.Shortcode
import dsp.valueobjects.Project.Shortname
import dsp.valueobjects.V2.StringLiteralV2
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

case class KnoraProject(
  id: InternalIri,
  shortname: Shortname,
  shortcode: Shortcode,
  longname: Option[String],
  description: NonEmptyChunk[StringLiteralV2],
  keywords: List[String],
  logo: Option[String],
  status: Boolean,
  selfjoin: Boolean,
  ontologies: List[InternalIri]
)
