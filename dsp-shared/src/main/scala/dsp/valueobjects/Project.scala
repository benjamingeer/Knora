/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation

import dsp.errors.BadRequestException
import dsp.errors.ValidationException
import scala.util.matching.Regex

object Project {
  // A regex sub-pattern for project IDs, which must consist of 4 hexadecimal digits.
  private val ProjectIDPattern: String =
    """\p{XDigit}{4,4}"""

  // A regex for matching a string containing the project ID.
  private val ProjectIDRegex: Regex = ("^" + ProjectIDPattern + "$").r

  // TODO-mpro: longname, description, keywords, logo are missing enhanced validation

  /**
   * Project ShortCode value object.
   */
  sealed abstract case class ShortCode private (value: String)
  object ShortCode { self =>
    def make(value: String): Validation[ValidationException, ShortCode] =
      if (value.isEmpty) {
        Validation.fail(ValidationException(ProjectErrorMessages.ShortcodeMissing))
      } else {
        ProjectIDRegex.matches(value.toUpperCase) match {
          case false => Validation.fail(ValidationException(ProjectErrorMessages.ShortcodeInvalid(value)))
          case true  => Validation.succeed(new ShortCode(value) {})
        }
      }

    def make(value: Option[String]): Validation[ValidationException, Option[ShortCode]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * Project ShortName value object.
   */
  sealed abstract case class ShortName private (value: String)
  object ShortName { self =>
    def make(value: String): Validation[Throwable, ShortName] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(ProjectErrorMessages.ShortNameMissing))
      } else {
        val validatedValue = Validation(
          V2ProjectIriValidation.validateAndEscapeProjectShortname(
            value,
            throw BadRequestException(ProjectErrorMessages.ShortNameInvalid)
          )
        )
        validatedValue.map(new ShortName(_) {})
      }

    def make(value: Option[String]): Validation[Throwable, Option[ShortName]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * Project Name value object.
   * (Formerly `Longname`)
   */
  // TODO-BL: [domain-model] this should be multi-lang-string, I suppose; needs real validation once value constraints are defined
  sealed abstract case class Name private (value: String)
  object Name { self =>
    def make(value: String): Validation[ValidationException, Name] =
      if (value.isEmpty) {
        Validation.fail(ValidationException(ProjectErrorMessages.NameMissing))
      } else {
        Validation.succeed(new Name(value) {})
      }

    def make(value: Option[String]): Validation[ValidationException, Option[Name]] =
      value match {
        case None    => Validation.succeed(None)
        case Some(v) => self.make(v).map(Some(_))
      }
  }

  /**
   * ProjectDescription value object.
   */
  // TODO-BL: [domain-model] should probably be MultiLangString; should probably be called `Description` as it's clear that it's part of Project
  sealed abstract case class ProjectDescription private (value: Seq[V2.StringLiteralV2]) // make it plural
  object ProjectDescription { self =>
    def make(value: Seq[V2.StringLiteralV2]): Validation[ValidationException, ProjectDescription] =
      if (value.isEmpty) {
        Validation.fail(ValidationException(ProjectErrorMessages.ProjectDescriptionsMissing))
      } else {
        Validation.succeed(new ProjectDescription(value) {})
      }

    def make(value: Option[Seq[V2.StringLiteralV2]]): Validation[ValidationException, Option[ProjectDescription]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * Project Keywords value object.
   */
  sealed abstract case class Keywords private (value: Seq[String])
  object Keywords { self =>
    def make(value: Seq[String]): Validation[Throwable, Keywords] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(ProjectErrorMessages.KeywordsMissing))
      } else {
        Validation.succeed(new Keywords(value) {})
      }

    def make(value: Option[Seq[String]]): Validation[Throwable, Option[Keywords]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * Project Logo value object.
   */
  sealed abstract case class Logo private (value: String)
  object Logo { self =>
    def make(value: String): Validation[Throwable, Logo] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(ProjectErrorMessages.LogoMissing))
      } else {
        Validation.succeed(new Logo(value) {})
      }
    def make(value: Option[String]): Validation[Throwable, Option[Logo]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * ProjectSelfjoin value object.
   */
  sealed abstract case class ProjectSelfJoin private (value: Boolean)
  object ProjectSelfJoin { self =>
    def make(value: Boolean): Validation[Throwable, ProjectSelfJoin] =
      Validation.succeed(new ProjectSelfJoin(value) {})

    def make(value: Option[Boolean]): Validation[Throwable, Option[ProjectSelfJoin]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * ProjectStatus value object.
   */
  sealed abstract case class ProjectStatus private (value: Boolean)
  object ProjectStatus { self =>
    def make(value: Boolean): Validation[Throwable, ProjectStatus] =
      Validation.succeed(new ProjectStatus(value) {})

    def make(value: Option[Boolean]): Validation[Throwable, Option[ProjectStatus]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }
}

object ProjectErrorMessages {
  val ShortcodeMissing           = "Shortcode cannot be empty."
  val ShortcodeInvalid           = (shortCode: String) => s"Shortcode is invalid: $shortCode"
  val ShortNameMissing           = "Shortname cannot be empty."
  val ShortNameInvalid           = "Shortname is invalid."
  val NameMissing                = "Longname cannot be empty."
  val NameInvalid                = "Longname is invalid."
  val ProjectDescriptionsMissing = "Description cannot be empty."
  val ProjectDescriptionsInvalid = "Description is invalid."
  val KeywordsMissing            = "Keywords cannot be empty."
  val KeywordsInvalid            = "Keywords are invalid."
  val LogoMissing                = "Logo cannot be empty."
  val LogoInvalid                = "Logo is invalid."
}
