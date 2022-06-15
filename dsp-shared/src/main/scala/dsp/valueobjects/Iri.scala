/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation
import dsp.errors.BadRequestException

import java.util.UUID

/**
 * Stores the user ID, i.e. UUID and IRI of the user
 *
 * @param uuid the UUID of the user
 * @param iri the IRI of the user
 */
abstract case class UserId private (
  uuid: UUID,
  iri: Iri.UserIri
)

/**
 * Companion object for UserId. Contains factory methods for creating UserId instances.
 */
object UserId {

  /**
   * Generates a UserId instance with a new (random) UUID and a given IRI which is created from a prefix and the UUID.
   *
   * @return a new UserId instance
   */
  def fromIri(iri: Iri.UserIri): UserId = {
    val uuid: UUID = UUID.fromString(iri.value.split("/").last)
    new UserId(uuid, iri) {}
  }

  /**
   * Generates a UserId instance from a given UUID and an IRI which is created from a prefix and the UUID.
   *
   * @return a new UserId instance
   */
  def fromUuid(uuid: UUID): UserId = {
    val iri = Iri.UserIri.make("http://rdfh.ch/users/" + uuid.toString).fold(e => throw e.head, v => v)
    new UserId(uuid, iri) {}
  }

  /**
   * Generates a UserId instance with a new (random) UUID and an IRI which is created from a prefix and the UUID.
   *
   * @return a new UserId instance
   */
  def make(): UserId = {
    val uuid: UUID = UUID.randomUUID()
    val iri        = Iri.UserIri.make("http://rdfh.ch/users/" + uuid.toString).fold(e => throw e.head, v => v)
    new UserId(uuid, iri) {}
  }
}

sealed trait Iri
object Iri {

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
    def make(value: String): Validation[Throwable, ProjectIri] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(IriErrorMessages.ProjectIriMissing))
      } else {
        val isUuid: Boolean = V2UuidValidation.hasUuidLength(value.split("/").last)

        if (!V2IriValidation.isKnoraProjectIriStr(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.ProjectIriInvalid))
        } else if (isUuid && !V2UuidValidation.isUuidVersion4Or5(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
        } else {
          val validatedValue = Validation(
            V2IriValidation.validateAndEscapeProjectIri(
              value,
              throw BadRequestException(IriErrorMessages.ProjectIriInvalid)
            )
          )

          validatedValue.map(new ProjectIri(_) {})
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[ProjectIri]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * UserIri value object.
   */
  sealed abstract case class UserIri private (value: String) extends Iri
  object UserIri { self =>
    def make(value: String): Validation[Throwable, UserIri] =
      if (value.isEmpty) {
        Validation.fail(BadRequestException(IriErrorMessages.UserIriMissing))
      } else {
        val isUuid: Boolean = V2UuidValidation.hasUuidLength(value.split("/").last)

        if (!V2IriValidation.isKnoraUserIriStr(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.UserIriInvalid))
        } else if (isUuid && !V2UuidValidation.isUuidVersion4Or5(value)) {
          Validation.fail(BadRequestException(IriErrorMessages.UuidVersionInvalid))
        } else {
          val validatedValue = Validation(
            V2IriValidation.validateAndEscapeUserIri(
              value,
              throw BadRequestException(IriErrorMessages.UserIriInvalid)
            )
          )

          validatedValue.map(new UserIri(_) {})
        }
      }

    def make(value: Option[String]): Validation[Throwable, Option[UserIri]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
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
  val UserIriMissing     = "User IRI cannot be empty."
  val UserIriInvalid     = "User IRI is invalid."
  val UuidVersionInvalid = "Invalid UUID used to create IRI. Only versions 4 and 5 are supported."
}
