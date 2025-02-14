/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.NonEmptyChunk

import scala.util.matching.Regex

import dsp.valueobjects.Iri
import dsp.valueobjects.Iri.isIri
import dsp.valueobjects.IriErrorMessages
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.StringFormatter.IriDomain
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.repo.service.EntityWithId
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value
import org.knora.webapi.slice.common.Value.BooleanValue
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.WithFrom

case class KnoraProject(
  id: ProjectIri,
  shortname: Shortname,
  shortcode: Shortcode,
  longname: Option[Longname],
  description: NonEmptyChunk[Description],
  keywords: List[Keyword],
  logo: Option[Logo],
  status: Status,
  selfjoin: SelfJoin,
  restrictedView: RestrictedView,
) extends EntityWithId[ProjectIri]

object KnoraProject {

  final case class ProjectIri private (override val value: String) extends StringValue {
    def isBuiltInProjectIri: Boolean = ProjectIri.isBuiltInProjectIri(value)
    def isRegularProjectIri: Boolean = !isBuiltInProjectIri
  }

  object ProjectIri extends StringValueCompanion[ProjectIri] {

    private val BuiltInProjects: Seq[String] =
      Seq(
        "http://www.knora.org/ontology/knora-admin#SystemProject",
        "http://www.knora.org/ontology/knora-admin#DefaultSharedOntologiesProject",
      )

    /**
     * Explanation of the project IRI regex:
     * * `^` asserts the start of the string.
     * * `http://rdfh\.ch/projects/` matches the specified prefix.
     * * `[a-zA-Z0-9_-]{4,40}` matches any alphanumeric character, hyphen, or underscore between 4 and 40 times.
     */
    private lazy val projectIriRegEx = """^http://rdfh\.ch/projects/[a-zA-Z0-9_-]{4,40}$""".r

    /**
     * Returns `true` if an IRI string looks like a Knora project IRI
     *
     * @param iri the IRI to be checked.
     */
    private def isProjectIri(iri: String): Boolean =
      (isIri(iri) && isRegularProjectIri(iri)) || isBuiltInProjectIri(iri)

    private def isRegularProjectIri(iri: String) = projectIriRegEx.matches(iri)

    private def isBuiltInProjectIri(iri: String): Boolean = BuiltInProjects.contains(iri)

    def from(str: String): Either[String, ProjectIri] = str match {
      case str if str.isEmpty        => Left(IriErrorMessages.ProjectIriMissing)
      case str if !isProjectIri(str) => Left(IriErrorMessages.ProjectIriInvalid)
      case _                         => Right(ProjectIri(str))
    }

    def makeNew: ProjectIri = {
      val uuid = UuidUtil.makeRandomBase64EncodedUuid
      unsafeFrom(s"http://$IriDomain/projects/$uuid")
    }
  }

  /**
   * A [[Shortcode]] is a human readable identifier for a project.
   * Its value must be is a 4-digit hexadecimal number with letters in upper case.
   *
   * @param value the valid shortcode.
   */
  final case class Shortcode private (override val value: String) extends StringValue

  object Shortcode extends StringValueCompanion[Shortcode] {

    private val shortcodeRegex: Regex = "^\\p{XDigit}{4}$".r

    def from(value: String): Either[String, Shortcode] = value match {
      case value if shortcodeRegex.matches(value.toUpperCase) => Right(Shortcode(value.toUpperCase()))
      case _ if value.isEmpty                                 => Left("Shortcode cannot be empty.")
      case _                                                  => Left(s"Shortcode is invalid: $value")
    }
  }

  final case class Shortname private (override val value: String) extends StringValue

  object Shortname extends StringValueCompanion[Shortname] {

    private val shortnameRegex: Regex = "^[a-zA-Z][a-zA-Z0-9_-]{2,19}$".r

    def from(value: String): Either[String, Shortname] = value match {
      case _ if value.isEmpty                             => Left("Shortname cannot be empty.")
      case _ if value == "DefaultSharedOntologiesProject" => Right(Shortname(value))
      case _                                              => shortnameRegex.findFirstIn(value).toRight(s"Shortname is invalid: $value").map(Shortname.apply)
    }
  }

  final case class Longname private (override val value: String) extends StringValue

  object Longname extends StringValueCompanion[Longname] {

    private val longnameRegex: Regex = "^.{3,256}$".r

    def from(value: String): Either[String, Longname] =
      if (longnameRegex.matches(value)) Right(Longname(value))
      else Left("Longname must be 3 to 256 characters long.")
  }

  final case class Description private (override val value: StringLiteralV2) extends Value[StringLiteralV2]

  object Description extends WithFrom[StringLiteralV2, Description] {

    def unsafeFrom(text: String, lang: Option[String]): Description =
      Description.from(StringLiteralV2.from(text, lang)).fold(e => throw new IllegalArgumentException(e), identity)

    def from(literal: StringLiteralV2): Either[String, Description] =
      if (literal.value.length >= 3 && literal.value.length <= 40960) Right(Description(literal))
      else Left("Description must be 3 to 40960 characters long.")
  }

  final case class Keyword private (override val value: String) extends StringValue

  object Keyword extends StringValueCompanion[Keyword] {

    private val keywordRegex: Regex = "^.{3,64}$".r
    def from(str: String): Either[String, Keyword] =
      if (keywordRegex.matches(str)) Right(Keyword(str))
      else Left("Keyword must be 3 to 64 characters long.")
  }

  final case class Logo private (override val value: String) extends StringValue

  object Logo extends StringValueCompanion[Logo] {

    def from(str: String): Either[String, Logo] =
      if (str.isEmpty) Left("Logo cannot be empty.")
      else Right(Logo(str))
  }

  sealed trait Status extends BooleanValue

  object Status {

    case object Active   extends Status { val value = true  }
    case object Inactive extends Status { val value = false }

    def from(value: Boolean): Status = if (value) Active else Inactive
  }

  sealed trait SelfJoin extends BooleanValue

  object SelfJoin {

    case object CanJoin    extends SelfJoin { val value = true  }
    case object CannotJoin extends SelfJoin { val value = false }

    def from(value: Boolean): SelfJoin = if (value) CanJoin else CannotJoin
  }

}
