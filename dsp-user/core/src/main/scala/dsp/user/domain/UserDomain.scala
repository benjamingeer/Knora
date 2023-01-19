/*
 * Copyright © 2021 - 2023 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.domain

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.prelude.Validation

import dsp.errors.ValidationException
import dsp.valueobjects.Id.UserId
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.User._

/**
 * Represents the user domain object.
 *
 * @param id          the ID of the user
 * @param givenName   the given name of the user
 * @param familyName  the family name of the user
 * @param username    the username of the user
 * @param email       the email of the user
 * @param password    the password of the user
 * @param language    the preferred language of the user
 * @param status      the status of the user
 * @param role        the role of the user
 */
final case class User private (
  id: UserId,
  givenName: GivenName,
  familyName: FamilyName,
  username: Username,
  email: Email,
  password: PasswordHash,
  language: LanguageCode,
  status: UserStatus
  // role: Role
) extends Ordered[User] { self =>

  /**
   * Allows to sort collections of [[User]]s. Sorting is done by the IRI.
   */
  def compare(that: User): Int = self.id.iri.toString().compareTo(that.id.iri.toString())

  /**
   * Update the username of a user
   *
   *  @param newValue  the new username
   *  @return the updated [[User]]
   */
  def updateUsername(newValue: Username): Validation[ValidationException, User] =
    User.make(
      self.id,
      self.givenName,
      self.familyName,
      newValue,
      self.email,
      self.password,
      self.language,
      self.status
    )

  /**
   * Update the email of a user
   *
   *  @param newValue  the new email
   *  @return the updated [[User]]
   */
  def updateEmail(newValue: Email): Validation[ValidationException, User] =
    User.make(
      self.id,
      self.givenName,
      self.familyName,
      self.username,
      newValue,
      self.password,
      self.language,
      self.status
    )

  /**
   * Update the given name of a user
   *
   *  @param newValue  the new given name
   *  @return the updated [[User]]
   */
  def updateGivenName(newValue: GivenName): Validation[ValidationException, User] =
    User.make(
      self.id,
      newValue,
      self.familyName,
      self.username,
      self.email,
      self.password,
      self.language,
      self.status
    )

  /**
   * Update the family name of a user
   *
   *  @param newValue  the new family name
   *  @return the updated [[User]]
   */
  def updateFamilyName(newValue: FamilyName): Validation[ValidationException, User] =
    User.make(
      self.id,
      self.givenName,
      newValue,
      self.username,
      self.email,
      self.password,
      self.language,
      self.status
    )

  /**
   * Update the password of a user
   *
   *  @param newValue  the new password
   *  @return the updated [[User]]
   */
  def updatePassword(newValue: PasswordHash): Validation[ValidationException, User] =
    User.make(
      self.id,
      self.givenName,
      self.familyName,
      self.username,
      self.email,
      newValue,
      self.language,
      self.status
    )

  /**
   * Update the language of a user
   *
   *  @param newValue  the new language
   *  @return the updated [[User]]
   */
  def updateLanguage(newValue: LanguageCode): Validation[ValidationException, User] =
    User.make(
      self.id,
      self.givenName,
      self.familyName,
      self.username,
      self.email,
      self.password,
      newValue,
      self.status
    )

  /**
   * Update the status of a user
   *
   *  @param id  the user's ID
   *  @param newValue  the new status
   *  @return the updated [[User]]
   */
  def updateStatus(newValue: UserStatus): Validation[ValidationException, User] =
    User.make(
      self.id,
      self.givenName,
      self.familyName,
      self.username,
      self.email,
      self.password,
      self.language,
      newValue
    )

}
object User {

  implicit val codec: JsonCodec[User] = DeriveJsonCodec.gen[User]

  def make(
    id: UserId,
    givenName: GivenName,
    familyName: FamilyName,
    username: Username,
    email: Email,
    password: PasswordHash,
    language: LanguageCode,
    status: UserStatus
    // role: Role
  ): Validation[ValidationException, User] =
    Validation.succeed(User(id, givenName, familyName, username, email, password, language, status))

}
