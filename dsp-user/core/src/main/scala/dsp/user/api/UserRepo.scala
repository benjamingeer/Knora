/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.api

import dsp.errors._
import dsp.user.domain._
import dsp.valueobjects.UserId
import zio._
import zio.macros.accessible

import java.util.UUID
import dsp.valueobjects.User._

/**
 * The trait (interface) for the user repository. The user repository is responsible for storing and retrieving users.
 * Needs to be used by the user repository implementations.
 */
@accessible // with this annotation we don't have to write the companion object ourselves
trait UserRepo {

  /**
   * Writes a user to the repository (used for both create and update).
   * If this fails (e.g. the triplestore is not available), it's a non-recovable error. That's why we need UIO.
   *   When used, we should do it like: ...store(...).orDie
   *
   * @param user the user to write
   * @return     Unit
   */
  def storeUser(user: User): UIO[UserId]

  /**
   * Gets all users from the repository.
   *
   * @return   a list of [[User]]
   */
  def getUsers(): UIO[List[User]]

  /**
   * Retrieves the user from the repository by ID.
   *
   * @param id the user's ID
   * @return an optional [[User]]
   */
  def getUserById(id: UserId): IO[Option[Nothing], User]

  /**
   * Retrieves the user from the repository by username.
   *
   * @param username username of the user.
   * @return an optional [[User]].
   */
  def getUserByUsername(username: Username): IO[Option[Nothing], User]

  /**
   * Retrieves the user from the repository by email.
   *
   * @param email email of the user.
   * @return an optional [[User]].
   */
  def getUserByEmail(email: Email): IO[Option[Nothing], User]

  /**
   * Checks if a username exists in the repo.
   *
   * @param username username of the user.
   * @return Unit in case of success
   */
  def checkUsernameExists(username: Username): IO[Option[Nothing], Unit]

  /**
   * Checks if an email exists in the repo.
   *
   * @param email email of the user.
   * @return Unit in case of success
   */
  def checkEmailExists(email: Email): IO[Option[Nothing], Unit]

  /**
   * Deletes a [[User]] from the repository by its [[UserId]].
   *
   * @param id the user ID
   * @return   Unit or None if not found
   */
  def deleteUser(id: UserId): IO[Option[Nothing], UserId]
}
