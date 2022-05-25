/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation
// sealed abstract case class LangString private (value: String, iso: String)
// object LangString { self =>
//   def make(value: String, iso: String): Validation[String, LangString] =
//     if(value.isEmpty || iso.isEmpty) {
//       Validation.fail("Value and Iso cannot be empty.")
//     } else {
//       // new LangString(_, _) {}
//     }
// }

sealed trait Group
object Group {

  /**
   * GroupName value object.
   */
  sealed abstract case class GroupName private (value: String)
  object GroupName { self =>
    def make(value: String): Validation[Throwable, GroupName] =
      if (value.isEmpty) {
        Validation.fail(V2.BadRequestException(GroupErrorMessages.GroupNameMissing))
      } else {
        val validatedValue = Validation(
          V2IriValidation.toSparqlEncodedString(
            value,
            throw V2.BadRequestException(GroupErrorMessages.GroupNameInvalid)
          )
        )

        validatedValue.map(new GroupName(_) {})
      }

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
    def make(value: Seq[V2.StringLiteralV2]): Validation[Throwable, GroupDescriptions] =
      if (value.isEmpty) {
        Validation.fail(V2.BadRequestException(GroupErrorMessages.GroupDescriptionMissing))
      } else {
        val validatedDescriptions = Validation(value.map { description =>
          val validatedDescription =
            V2IriValidation.toSparqlEncodedString(
              description.value,
              throw V2.BadRequestException(GroupErrorMessages.GroupDescriptionInvalid)
            )
          V2.StringLiteralV2(value = validatedDescription, language = description.language)
        })
        validatedDescriptions.map(new GroupDescriptions(_) {})
      }

    def make(value: Option[Seq[V2.StringLiteralV2]]): Validation[Throwable, Option[GroupDescriptions]] =
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
  val GroupNameMissing        = "Group name cannot be empty."
  val GroupNameInvalid        = "Group name is invalid."
  val GroupDescriptionMissing = "Group description cannot be empty." // make it plural
  val GroupDescriptionInvalid = "Group description is invalid."      // make it plural
}
