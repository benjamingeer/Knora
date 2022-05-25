/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.valueobjects.List._
import zio.prelude.Validation
import zio.test._

/**
 * This spec is used to test the [[List]] value objects creation.
 */
object ListSpec extends ZIOSpecDefault {
  private val validName       = "Valid list name"
  private val invalidName     = "Invalid list name\r"
  private val validPosition   = 0
  private val invalidPosition = -2
  private val validLabel      = Seq(V2.StringLiteralV2(value = "Valid list label", language = Some("en")))
  private val invalidLabel    = Seq(V2.StringLiteralV2(value = "Invalid list label \r", language = Some("en")))
  private val validComment    = Seq(V2.StringLiteralV2(value = "Valid list comment", language = Some("en")))
  private val invalidComment  = Seq(V2.StringLiteralV2(value = "Invalid list comment \r", language = Some("en")))

  def spec = (listNameTest + positionTest + labelsTest + commentsTest)

  private val listNameTest = suite("ListSpec - ListName")(
    test("pass an empty value and throw an error") {
      assertTrue(ListName.make("") == Validation.fail(V2.BadRequestException(ListErrorMessages.ListNameMissing)))
      assertTrue(
        ListName.make(Some("")) == Validation.fail(V2.BadRequestException(ListErrorMessages.ListNameMissing))
      )
    } +
      test("pass an invalid value and throw an error") {
        assertTrue(
          ListName.make(invalidName) == Validation.fail(
            V2.BadRequestException(ListErrorMessages.ListNameInvalid)
          )
        )
        assertTrue(
          ListName.make(Some(invalidName)) == Validation.fail(
            V2.BadRequestException(ListErrorMessages.ListNameInvalid)
          )
        )
      } +
      test("pass a valid value and successfully create value object") {
        assertTrue(ListName.make(validName).toOption.get.value == validName)
        assertTrue(ListName.make(Option(validName)).getOrElse(null).get.value == validName)
      } +
      test("pass None") {
        assertTrue(
          ListName.make(None) == Validation.succeed(None)
        )
      }
  )

  private val positionTest = suite("ListSpec - Position")(
    test("pass an invalid value and throw an error") {
      assertTrue(
        Position.make(invalidPosition) == Validation.fail(
          V2.BadRequestException(ListErrorMessages.InvalidPosition)
        )
      )
      assertTrue(
        Position.make(Some(invalidPosition)) == Validation.fail(
          V2.BadRequestException(ListErrorMessages.InvalidPosition)
        )
      )
    } +
      test("pass a valid value and successfully create value object") {
        assertTrue(Position.make(validPosition).toOption.get.value == validPosition)
        assertTrue(Position.make(Option(validPosition)).getOrElse(null).get.value == validPosition)
      } +
      test("pass None") {
        assertTrue(
          Position.make(None) == Validation.succeed(None)
        )
      }
  )

  private val labelsTest = suite("ListSpec - Labels")(
    test("pass an empty object and throw an error") {
      assertTrue(
        Labels.make(Seq.empty) == Validation.fail(
          V2.BadRequestException(ListErrorMessages.LabelMissing)
        )
      )
      assertTrue(
        Labels.make(Some(Seq.empty)) == Validation.fail(
          V2.BadRequestException(ListErrorMessages.LabelMissing)
        )
      )
    } +
      test("pass an invalid object and throw an error") {
        assertTrue(
          Labels.make(invalidLabel) == Validation.fail(
            V2.BadRequestException(ListErrorMessages.LabelInvalid)
          )
        )
        assertTrue(
          Labels.make(Some(invalidLabel)) == Validation.fail(
            V2.BadRequestException(ListErrorMessages.LabelInvalid)
          )
        )
      } +
      test("pass a valid object and successfully create value object") {
        assertTrue(Labels.make(validLabel).toOption.get.value == validLabel)
        assertTrue(Labels.make(Option(validLabel)).getOrElse(null).get.value == validLabel)
      } +
      test("pass None") {
        assertTrue(
          Labels.make(None) == Validation.succeed(None)
        )
      }
  )

  private val commentsTest = suite("ListSpec - Comments")(
    test("pass an empty object and throw an error") {
      assertTrue(
        Comments.make(Seq.empty) == Validation.fail(
          V2.BadRequestException(ListErrorMessages.CommentMissing)
        )
      )
      assertTrue(
        Comments.make(Some(Seq.empty)) == Validation.fail(
          V2.BadRequestException(ListErrorMessages.CommentMissing)
        )
      )
    } +
      test("pass an invalid object and throw an error") {
        assertTrue(
          Comments.make(invalidComment) == Validation.fail(
            V2.BadRequestException(ListErrorMessages.CommentInvalid)
          )
        )
        assertTrue(
          Comments.make(Some(invalidComment)) == Validation.fail(
            V2.BadRequestException(ListErrorMessages.CommentInvalid)
          )
        )
      } +
      test("pass a valid object and successfully create value object") {
        assertTrue(Comments.make(validComment).toOption.get.value == validComment)
        assertTrue(Comments.make(Option(validComment)).getOrElse(null).get.value == validComment)
      } +
      test("pass None") {
        assertTrue(
          Comments.make(None) == Validation.succeed(None)
        )
      }
  )
}
