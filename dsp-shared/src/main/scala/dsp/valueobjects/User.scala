/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.errors.BadRequestException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder
import zio._
import zio.prelude.Validation

import scala.util.matching.Regex
import java.security.SecureRandom

object User {

  // TODO-mpro: password, givenname, familyname are missing enhanced validation

  /**
   * Username value object.
   */
  sealed abstract case class Username private (value: String)
  object Username { self =>

    /**
     * A regex that matches a valid username
     * - 4 - 50 characters long
     * - Only contains alphanumeric characters, underscore and dot.
     * - Underscore and dot can't be at the end or start of a username
     * - Underscore or dot can't be used multiple times in a row
     */
    private val UsernameRegex: Regex =
      """^(?=.{4,50}$)(?![_.])(?!.*[_.]{2})[a-zA-Z0-9._]+(?<![_.])$""".r

    def make(value: String): Validation[Throwable, Username] =
      if (value.isEmpty) {
        // remove exception return just the error
        Validation.fail(BadRequestException(UserErrorMessages.UsernameMissing))
      } else {
        UsernameRegex.findFirstIn(value) match {
          case Some(value) => Validation.succeed(new Username(value) {})
          case None        => Validation.fail(BadRequestException(UserErrorMessages.UsernameInvalid))
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[Username]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }

    /**
     * Creates a value object even if it doesn't pass the validation. It logs an error in case of an invalid input.
     * Only use this in the repo layer when creating value objects from possibly deprecated data that can't be updated or in tests.
     *
     * @param value the String the username should be created from
     */
    def unsafeMake(value: String): Validation[Throwable, Username] =
      Username
        .make(value)
        .fold(
          e => {
            ZIO.logError(UserErrorMessages.UsernameInvalid)
            Validation.succeed(new Username(value) {})
          },
          v => Validation.succeed(v)
        )
  }

  /**
   * Email value object.
   */
  sealed abstract case class Email private (value: String)
  object Email { self =>
    private val EmailRegex: Regex = """^.+@.+$""".r

    def make(value: String): Validation[Throwable, Email] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(UserErrorMessages.EmailMissing))
      } else {
        EmailRegex.findFirstIn(value) match {
          case Some(value) => Validation.succeed(new Email(value) {})
          case None        => Validation.fail(BadRequestException(UserErrorMessages.EmailInvalid))
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[Email]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }

    /**
     * Creates a value object even if it doesn't pass the validation. It logs an error in case of an invalid input.
     * Only use this in the repo layer when creating value objects from possibly deprecated data that can't be updated or in tests.
     *
     * @param value the String the email should be created from
     */
    def unsafeMake(value: String): Validation[Throwable, Email] =
      Email
        .make(value)
        .fold(
          e => {
            ZIO.logError(UserErrorMessages.EmailInvalid)
            Validation.succeed(new Email(value) {})
          },
          v => Validation.succeed(v)
        )
  }

  /**
   * GivenName value object.
   */
  sealed abstract case class GivenName private (value: String)
  object GivenName { self =>
    def make(value: String): Validation[Throwable, GivenName] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(UserErrorMessages.GivenNameMissing))
      } else {
        Validation.succeed(new GivenName(value) {})
      }

    def make(value: Option[String]): Validation[Throwable, Option[GivenName]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }

    /**
     * Creates a value object even if it doesn't pass the validation. It logs an error in case of an invalid input.
     * Only use this in the repo layer when creating value objects from possibly deprecated data that can't be updated or in tests.
     *
     * @param value the String the GivenName should be created from
     */
    def unsafeMake(value: String): Validation[Throwable, GivenName] =
      GivenName
        .make(value)
        .fold(
          e => {
            ZIO.logError(UserErrorMessages.GivenNameInvalid)
            Validation.succeed(new GivenName(value) {})
          },
          v => Validation.succeed(v)
        )
  }

  /**
   * FamilyName value object.
   */
  sealed abstract case class FamilyName private (value: String)
  object FamilyName { self =>
    def make(value: String): Validation[Throwable, FamilyName] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(UserErrorMessages.FamilyNameMissing))
      } else {
        Validation.succeed(new FamilyName(value) {})
      }

    def make(value: Option[String]): Validation[Throwable, Option[FamilyName]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }

    /**
     * Creates a value object even if it doesn't pass the validation. It logs an error in case of an invalid input.
     * Only use this in the repo layer when creating value objects from possibly deprecated data that can't be updated or in tests.
     *
     * @param value the String the FamilyName should be created from
     */
    def unsafeMake(value: String): Validation[Throwable, FamilyName] =
      FamilyName
        .make(value)
        .fold(
          e => {
            ZIO.logError(UserErrorMessages.FamilyNameInvalid)
            Validation.succeed(new FamilyName(value) {})
          },
          v => Validation.succeed(v)
        )
  }

  /**
   * Password value object.
   */
  sealed abstract case class Password private (value: String)
  object Password { self =>
    private val PasswordRegex: Regex = """^[\s\S]*$""".r

    def make(value: String): Validation[Throwable, Password] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(UserErrorMessages.PasswordMissing))
      } else {
        PasswordRegex.findFirstIn(value) match {
          case Some(value) => Validation.succeed(new Password(value) {})
          case None        => Validation.fail(BadRequestException(UserErrorMessages.PasswordInvalid))
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[Password]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }

    /**
     * Creates a value object even if it doesn't pass the validation. It logs an error in case of an invalid input.
     * Only use this in the repo layer when creating value objects from possibly deprecated data that can't be updated or in tests.
     *
     * @param value the String the Password should be created from
     */
    def unsafeMake(value: String): Validation[Throwable, Password] =
      Password
        .make(value)
        .fold(
          e => {
            ZIO.logError(UserErrorMessages.PasswordInvalid)
            Validation.succeed(new Password(value) {})
          },
          v => Validation.succeed(v)
        )
  }

  /**
   * PasswordHash value object. Takes a string as input and hashes it.
   */
  sealed abstract case class PasswordHash private (value: String, passwordStrength: PasswordStrength) { self =>

    /**
     * Check password (in clear text). The password supplied in clear text is compared against the
     * stored hash.
     *
     * @param passwordString Password (clear text) to be checked
     * @return true if password matches, false otherwise
     */
    def matches(passwordString: String): Boolean =
      // check which type of hash we have
      if (self.value.startsWith("$e0801$")) {
        // SCrypt
        val encoder = new SCryptPasswordEncoder()
        encoder.matches(passwordString, self.value)
      } else if (self.value.startsWith("$2a$")) {
        // BCrypt
        val encoder = new BCryptPasswordEncoder()
        encoder.matches(passwordString, self.value)
      } else {
        ZIO.logError(UserErrorMessages.PasswordHashUnknown)
        false
      }

  }
  object PasswordHash {
    private val PasswordRegex: Regex = """^[\s\S]*$""".r

    def make(value: String, passwordStrength: PasswordStrength): Validation[Throwable, PasswordHash] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(UserErrorMessages.PasswordMissing))
      } else {
        PasswordRegex.findFirstIn(value) match {
          case Some(value) =>
            val encoder =
              new BCryptPasswordEncoder(
                passwordStrength.value,
                new SecureRandom()
              )
            val hashedValue = encoder.encode(value)
            Validation.succeed(new PasswordHash(hashedValue, passwordStrength) {})
          case None => Validation.fail(BadRequestException(UserErrorMessages.PasswordInvalid))
        }
      }

    /**
     * Creates a value object even if it doesn't pass the validation. It logs an error in case of an invalid input.
     * Only use this in the repo layer when creating value objects from possibly deprecated data that can't be updated or in tests.
     *
     * @param value the String the Password should be created from
     */
    def unsafeMake(value: String, passwordStrength: PasswordStrength): Validation[Throwable, PasswordHash] =
      PasswordHash
        .make(value, passwordStrength)
        .fold(
          e => {
            ZIO.logError(UserErrorMessages.PasswordInvalid)
            Validation.succeed(new PasswordHash(value, passwordStrength) {})
          },
          v => Validation.succeed(v)
        )
  }

  /**
   * PasswordStrength value object.
   */
  sealed abstract case class PasswordStrength private (value: Int)
  object PasswordStrength { self =>
    def make(value: Int): Validation[Throwable, PasswordStrength] =
      if (value < 4 || value > 31) {
        Validation.fail(BadRequestException(UserErrorMessages.PasswordStrengthInvalid))
      } else {
        Validation.succeed(new PasswordStrength(value) {})
      }
  }

  /**
   * UserStatus value object.
   */
  sealed abstract case class UserStatus private (value: Boolean)
  object UserStatus {
    def make(value: Boolean): Validation[Throwable, UserStatus] =
      Validation.succeed(new UserStatus(value) {})
  }

  /**
   * LanguageCode value object.
   */
  sealed abstract case class LanguageCode private (value: String)
  object LanguageCode { self =>
    def make(value: String): Validation[Throwable, LanguageCode] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(UserErrorMessages.LanguageCodeMissing))
      } else if (!V2.SupportedLanguageCodes.contains(value)) {
        Validation.fail(BadRequestException(UserErrorMessages.LanguageCodeInvalid))
      } else {
        Validation.succeed(new LanguageCode(value) {})
      }

    def make(value: Option[String]): Validation[Throwable, Option[LanguageCode]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * SystemAdmin value object.
   */
  sealed abstract case class SystemAdmin private (value: Boolean)
  object SystemAdmin {
    def make(value: Boolean): Validation[Throwable, SystemAdmin] =
      Validation.succeed(new SystemAdmin(value) {})
  }
}

object UserErrorMessages {
  val UsernameMissing         = "Username cannot be empty."
  val UsernameInvalid         = "Username is invalid."
  val EmailMissing            = "Email cannot be empty."
  val EmailInvalid            = "Email is invalid."
  val PasswordMissing         = "Password cannot be empty."
  val PasswordInvalid         = "Password is invalid."
  val PasswordStrengthInvalid = "PasswordStrength is invalid."
  val PasswordHashUnknown     = "The provided PasswordHash has an unknown format."
  val GivenNameMissing        = "GivenName cannot be empty."
  val GivenNameInvalid        = "GivenName is invalid."
  val FamilyNameMissing       = "FamilyName cannot be empty."
  val FamilyNameInvalid       = "FamilyName is invalid."
  val LanguageCodeMissing     = "LanguageCode cannot be empty."
  val LanguageCodeInvalid     = "LanguageCode is invalid."
}
