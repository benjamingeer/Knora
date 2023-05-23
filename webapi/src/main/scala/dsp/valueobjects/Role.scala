/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation

import dsp.errors.BadRequestException
import dsp.valueobjects.LanguageCode

object Role {

  /**
   * LangString value object.
   *
   * @param value the [[String]] the value to be validated
   * @param isoCode the language ISO code to be validated
   */
  sealed abstract case class LangString private (value: String, isoCode: String)
  object LangString {
    private def isIsoCodeSupported(isoCode: String): Boolean =
      LanguageCode.SupportedLanguageCodes.contains(isoCode.toLowerCase) // should only lower case be supported?

    def make(value: String, isoCode: String): Validation[Throwable, LangString] =
      if (value.isEmpty)
        Validation.fail(BadRequestException(RoleErrorMessages.LangStringValueMissing))
      else if (isoCode.isEmpty)
        Validation.fail(BadRequestException(RoleErrorMessages.LangStringIsoCodeMissing))
      else if (!isIsoCodeSupported(isoCode))
        Validation.fail(BadRequestException(RoleErrorMessages.LangStringIsoCodeInvalid(isoCode)))
      else
        Validation
          .fromOption(Iri.toSparqlEncodedString(value))
          .mapError(_ => BadRequestException(RoleErrorMessages.LangStringValueInvalid(value)))
          .map(new LangString(_, isoCode) {})
  }
}

/**
 * Permission value object.
 *
 * @param value the value to be validated
 */
sealed abstract case class Permission private (value: String)
object Permission {
  val View: String   = "view"
  val Create: String = "create"
  val Modify: String = "modify"
  val Delete: String = "delete"
  val Admin: String  = "admin"
  val availablePermissions: Set[String] = Set(
    View,
    Create,
    Modify,
    Delete,
    Admin
  )

  def isPermissionAvailable(permission: String): Boolean =
    availablePermissions.contains(permission.toLowerCase)

  def make(value: String): Validation[Throwable, Permission] =
    if (value.isEmpty) {
      Validation.fail(
        BadRequestException(RoleErrorMessages.PermissionMissing)
      )
    } else if (!isPermissionAvailable(value)) {
      Validation.fail(
        BadRequestException(RoleErrorMessages.PermissionInvalid(value))
      )
    } else {
      Validation.succeed(new Permission(value) {})
    }
}

object RoleErrorMessages {
  val LangStringValueMissing   = "Value cannot be empty."
  val LangStringValueInvalid   = (value: String) => s"String value: $value is invalid."
  val LangStringIsoCodeMissing = "Language ISO code cannot be empty."
  val LangStringIsoCodeInvalid = (isoCode: String) => s"Language ISO code: $isoCode is not suporrted."
  val PermissionMissing        = "Permission cannot be empty."
  val PermissionInvalid        = (value: String) => s"Permission: $value is invalid."
}
