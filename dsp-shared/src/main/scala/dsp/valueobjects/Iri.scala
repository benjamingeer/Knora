/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import org.apache.commons.validator.routines.UrlValidator
import zio.prelude.Validation

import scala.util.Try

import dsp.errors.BadRequestException
import dsp.errors.ValidationException

sealed trait Iri {
  val value: String
}
object Iri {

  // A validator for URLs
  val urlValidator =
    new UrlValidator(
      Array("http", "https"),       // valid URL schemes
      UrlValidator.ALLOW_LOCAL_URLS // local URLs are URL-encoded IRIs as part of the whole URL
    )

  /**
   * Returns `true` if a string is an IRI.
   *
   * @param s the string to be checked.
   * @return `true` if the string is an IRI.
   */
  def isIri(s: String): Boolean =
    urlValidator.isValid(s)

  /**
   * GroupIri value object.
   */
  sealed abstract case class GroupIri private (value: String) extends Iri
  object GroupIri { self =>
    def make(value: String): Validation[Throwable, GroupIri] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(IriErrorMessages.GroupIriMissing))
      } else {
        val isUuid: Boolean = V2UuidValidation.hasUuidLength(value.split("/").last)

        if (!V2IriValidation.isKnoraGroupIriStr(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.GroupIriInvalid))
        } else if (isUuid && !V2UuidValidation.isUuidVersion4Or5(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
        } else {
          val validatedValue = Validation(
            V2IriValidation.validateAndEscapeIri(value, throw BadRequestException(IriErrorMessages.GroupIriInvalid))
          )

          validatedValue.map(new GroupIri(_) {})
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[GroupIri]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * ListIri value object.
   */
  sealed abstract case class ListIri private (value: String) extends Iri
  object ListIri { self =>
    def make(value: String): Validation[Throwable, ListIri] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(IriErrorMessages.ListIriMissing))
      } else {
        val isUuid: Boolean = V2UuidValidation.hasUuidLength(value.split("/").last)

        if (!V2IriValidation.isKnoraListIriStr(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.ListIriInvalid))
        } else if (isUuid && !V2UuidValidation.isUuidVersion4Or5(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
        } else {
          val validatedValue = Validation(
            V2IriValidation.validateAndEscapeIri(
              value,
              throw BadRequestException(IriErrorMessages.ListIriInvalid)
            )
          )

          validatedValue.map(new ListIri(_) {})
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[ListIri]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * ProjectIri value object.
   */
  sealed abstract case class ProjectIri private (value: String) extends Iri
  object ProjectIri { self =>
    def make(value: String): Validation[ValidationException, ProjectIri] =
      if (value.isEmpty) {
        Validation.fail(ValidationException(IriErrorMessages.ProjectIriMissing))
      } else {
        if (!V2IriValidation.isKnoraProjectIriStr(value)) {
          Validation.fail(ValidationException(IriErrorMessages.ProjectIriInvalid))
        } else if (!V2IriValidation.isKnoraBuiltInProjectIriStr(value) && !V2UuidValidation.isUuidVersion4Or5(value)) {
          Validation.fail(ValidationException(IriErrorMessages.UuidVersionInvalid))
        } else {
          val eitherValue = Try(
            V2IriValidation.validateAndEscapeProjectIri(
              value,
              throw ValidationException(IriErrorMessages.ProjectIriInvalid)
            )
          ).toEither.left.map(_.asInstanceOf[ValidationException])
          val validatedValue = Validation.fromEither(eitherValue)

          validatedValue.map(new ProjectIri(_) {})
        }
      }

    def make(value: Option[String]): Validation[ValidationException, Option[ProjectIri]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * Base64Uuid value object.
   * This is base64 encoded UUID version without paddings.
   *
   * @param value to validate.
   */
  sealed abstract case class Base64Uuid private (value: String)
  object Base64Uuid {
    def make(value: String): Validation[ValidationException, Base64Uuid] =
      if (value.isEmpty) {
        Validation.fail(ValidationException(IriErrorMessages.UuidMissing))
      } else if (!V2UuidValidation.hasUuidLength(value)) {
        Validation.fail(ValidationException(IriErrorMessages.UuidInvalid(value)))
      } else if (!V2UuidValidation.isUuidVersion4Or5(value)) {
        Validation.fail(ValidationException(IriErrorMessages.UuidVersionInvalid))
      } else Validation.succeed(new Base64Uuid(value) {})
  }

  /**
   * RoleIri value object.
   */
  sealed abstract case class RoleIri private (value: String) extends Iri
  object RoleIri {
    def make(value: String): Validation[Throwable, RoleIri] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(IriErrorMessages.RoleIriMissing))
      } else {
        val isUuid: Boolean = V2UuidValidation.hasUuidLength(value.split("/").last)

        if (!V2IriValidation.isKnoraRoleIriStr(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.RoleIriInvalid(value)))
        } else if (isUuid && !V2UuidValidation.isUuidVersion4Or5(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
        } else {
          val validatedValue = Validation(
            V2IriValidation.validateAndEscapeIri(
              value,
              throw BadRequestException(IriErrorMessages.RoleIriInvalid(value))
            )
          )

          validatedValue.map(new RoleIri(_) {})
        }
      }
  }

  /**
   * UserIri value object.
   */
  sealed abstract case class UserIri private (value: String) extends Iri
  object UserIri {
    def make(value: String): Validation[Throwable, UserIri] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(IriErrorMessages.UserIriMissing))
      } else {
        val isUuid: Boolean = V2UuidValidation.hasUuidLength(value.split("/").last)

        if (!V2IriValidation.isKnoraUserIriStr(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.UserIriInvalid(value)))
        } else if (isUuid && !V2UuidValidation.isUuidVersion4Or5(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
        } else {
          val validatedValue = Validation(
            V2IriValidation.validateAndEscapeUserIri(
              value,
              throw BadRequestException(IriErrorMessages.UserIriInvalid(value))
            )
          )

          validatedValue.map(new UserIri(_) {})
        }
      }
  }

  /**
   * PropertyIri value object.
   */
  sealed abstract case class PropertyIri private (value: String) extends Iri
  object PropertyIri {
    def make(value: String): Validation[Throwable, PropertyIri] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(IriErrorMessages.PropertyIriMissing))
      } else {
        // TODO all the following needs to be checked when validating a property iri (see string formatter for the implementations of these methods)
        // if (
        //   !(propertyIri.isKnoraApiV2EntityIri &&
        //     propertyIri.getOntologySchema.contains(ApiV2Complex) &&
        //     propertyIri.getOntologyFromEntity == externalOntologyIri)
        // ) {
        //   throw BadRequestException(s"Invalid property IRI: $propertyIri")
        // }
        Validation.succeed(new PropertyIri(value) {})
      }
  }
}

object IriErrorMessages {
  val GroupIriMissing    = "Group IRI cannot be empty."
  val GroupIriInvalid    = "Group IRI is invalid."
  val ListIriMissing     = "List IRI cannot be empty."
  val ListIriInvalid     = "List IRI is invalid"
  val ProjectIriMissing  = "Project IRI cannot be empty."
  val ProjectIriInvalid  = "Project IRI is invalid."
  val RoleIriMissing     = "Role IRI cannot be empty."
  val RoleIriInvalid     = (iri: String) => s"Role IRI: $iri is invalid."
  val UserIriMissing     = "User IRI cannot be empty."
  val UserIriInvalid     = (iri: String) => s"User IRI: $iri is invalid."
  val UuidMissing        = "UUID cannot be empty"
  val UuidInvalid        = (uuid: String) => s"'$uuid' is not a UUID"
  val UuidVersionInvalid = "Invalid UUID used to create IRI. Only versions 4 and 5 are supported."
  val PropertyIriMissing = "Property IRI cannot be empty."
}
