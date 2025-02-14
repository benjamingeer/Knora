/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.json.JsonCodec
import zio.prelude.Validation

import dsp.valueobjects.UuidUtil
import org.knora.webapi.slice.admin.api.Codecs.ZioJsonCodec
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.StringValueCompanion.*
import org.knora.webapi.slice.common.StringValueCompanion.maxLength
import org.knora.webapi.slice.common.Value.StringValue

final case class CopyrightHolder private (override val value: String) extends StringValue
object CopyrightHolder extends StringValueCompanion[CopyrightHolder] {
  given JsonCodec[CopyrightHolder] = ZioJsonCodec.stringCodec(CopyrightHolder.from)
  def from(str: String): Either[String, CopyrightHolder] =
    fromValidations(
      "Copyright Holder",
      CopyrightHolder.apply,
      List(nonEmpty, noLineBreaks, maxLength(1_000)),
    )(str)
}

final case class Authorship private (override val value: String) extends StringValue
object Authorship extends StringValueCompanion[Authorship] {
  given JsonCodec[LicenseIri] = ZioJsonCodec.stringCodec(LicenseIri.from)
  def from(str: String): Either[String, Authorship] =
    fromValidations("Authorship", Authorship.apply, List(nonEmpty, noLineBreaks, maxLength(1_000)))(str)
}

final case class LicenseIri private (override val value: String) extends StringValue
object LicenseIri extends StringValueCompanion[LicenseIri] {
  given JsonCodec[LicenseIri] = ZioJsonCodec.stringCodec(LicenseIri.from)

  /**
   * Explanation of the IRI regex:
   * * `^` asserts the start of the string.
   * * `http://rdfh\.ch/licenses/` matches the specified prefix.
   * * `[A-Za-z0-9_-]{22}` matches any base64 encoded UUID (22 characters long).
   */
  private lazy val licenseIriRegEx = """^http://rdfh\.ch/licenses/[A-Za-z0-9_-]{22}$""".r

  private def isLicenseIri(iri: String) = licenseIriRegEx.matches(iri)

  def from(str: String): Either[String, LicenseIri] = str match {
    case str if !isLicenseIri(str) => Left("Invalid license IRI")
    case _                         => Right(LicenseIri(str))
  }

  def makeNew: LicenseIri = {
    val uuid = UuidUtil.makeRandomBase64EncodedUuid
    unsafeFrom(s"http://rdfh.ch/licenses/$uuid")
  }
}
