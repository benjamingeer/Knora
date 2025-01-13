/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.StringValueCompanion.*
import org.knora.webapi.slice.common.StringValueCompanion.maxLength
import org.knora.webapi.slice.common.Value.StringValue

final case class CopyrightAttribution private (override val value: String) extends StringValue
object CopyrightAttribution extends StringValueCompanion[CopyrightAttribution] {
  def from(str: String): Either[String, CopyrightAttribution] =
    fromValidations(
      "Copyright Attribution",
      CopyrightAttribution.apply,
      List(nonEmpty, noLineBreaks, maxLength(1_000)),
    )(str)
}

final case class LicenseText private (override val value: String) extends StringValue
object LicenseText extends StringValueCompanion[LicenseText] {
  def from(str: String): Either[String, LicenseText] =
    fromValidations("License text", LicenseText.apply, List(nonEmpty, maxLength(100_000)))(str)
}

final case class LicenseUri private (override val value: String) extends StringValue
object LicenseUri extends StringValueCompanion[LicenseUri] {
  def from(str: String): Either[String, LicenseUri] =
    fromValidations("License URI", LicenseUri.apply, List(nonEmpty, isUri))(str)
}
