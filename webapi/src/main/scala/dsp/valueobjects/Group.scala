/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation

import dsp.errors.BadRequestException

object Group {

  /**
   * GroupName value object.
   */
  sealed abstract case class GroupName private (value: String)
  object GroupName { self =>
    def make(value: String): Validation[BadRequestException, GroupName] =
      if (value.isEmpty) Validation.fail(BadRequestException(GroupErrorMessages.GroupNameMissing))
      else
        Validation
          .fromOption(Iri.toSparqlEncodedString(value))
          .mapError(_ => BadRequestException(GroupErrorMessages.GroupNameInvalid))
          .map(new GroupName(_) {})

    def make(value: Option[String]): Validation[Throwable, Option[GroupName]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * GroupDescriptions value object.
   */
  sealed abstract case class GroupDescriptions private (value: Seq[V2.StringLiteralV2])
  object GroupDescriptions { self =>
    def make(value: Seq[V2.StringLiteralV2]): Validation[BadRequestException, GroupDescriptions] =
      if (value.isEmpty) Validation.fail(BadRequestException(GroupErrorMessages.GroupDescriptionsMissing))
      else {
        val validatedDescriptions = value.map(d =>
          Validation
            .fromOption(Iri.toSparqlEncodedString(d.value))
            .mapError(_ => BadRequestException(GroupErrorMessages.GroupDescriptionsInvalid))
            .map(s => V2.StringLiteralV2(s, d.language))
        )
        Validation.validateAll(validatedDescriptions).map(new GroupDescriptions(_) {})
      }

    def make(value: Option[Seq[V2.StringLiteralV2]]): Validation[BadRequestException, Option[GroupDescriptions]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * GroupStatus value object.
   */
  sealed abstract case class GroupStatus private (value: Boolean)
  object GroupStatus { self =>
    def make(value: Boolean): Validation[Throwable, GroupStatus] =
      Validation.succeed(new GroupStatus(value) {})
    def make(value: Option[Boolean]): Validation[Throwable, Option[GroupStatus]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * GroupSelfJoin value object.
   */
  sealed abstract case class GroupSelfJoin private (value: Boolean)
  object GroupSelfJoin { self =>
    def make(value: Boolean): Validation[Throwable, GroupSelfJoin] =
      Validation.succeed(new GroupSelfJoin(value) {})
    def make(value: Option[Boolean]): Validation[Throwable, Option[GroupSelfJoin]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }
}

object GroupErrorMessages {
  val GroupNameMissing         = "Group name cannot be empty."
  val GroupNameInvalid         = "Group name is invalid."
  val GroupDescriptionsMissing = "Group description cannot be empty."
  val GroupDescriptionsInvalid = "Group description is invalid."
}
