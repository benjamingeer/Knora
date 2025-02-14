/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import zio.prelude.Validation
import zio.prelude.ZValidation

import java.net.URI
import scala.util.Try

import org.knora.webapi.slice.common.Value.IntValue
import org.knora.webapi.slice.common.Value.StringValue

trait Value[A] extends Any {
  def value: A
  override def toString: String = value.toString
}

object Value {
  type BooleanValue = Value[Boolean]
  type StringValue  = Value[String]
  type IntValue     = Value[Int]
}

trait WithFrom[-I, +A] {

  def from(in: I): Either[String, A]

  final def unsafeFrom(in: I): A =
    from(in).fold(e => throw new IllegalArgumentException(e), identity)
}

trait StringValueCompanion[A <: StringValue] extends WithFrom[String, A]
object StringValueCompanion {

  def nonEmpty: String => Validation[String, String] =
    value => Validation.fromPredicateWith(s"cannot be empty")(value)((str: String) => str.nonEmpty)

  def noLineBreaks: String => Validation[String, String] =
    value => Validation.fromPredicateWith(s"must not contain line breaks")(value)((str: String) => !str.contains("\n"))

  def maxLength(max: Int): String => Validation[String, String] =
    value =>
      Validation.fromPredicateWith(s"must be maximum $max characters long")(value)((str: String) => str.length <= max)

  def isUri: String => Validation[String, String] =
    value => Validation.fromTry(Try(URI.create(value))).as(value).mapError(_ => s"is not a valid URI")

  def fromValidations[A](
    typ: String,
    make: String => A,
    validations: List[String => Validation[String, String]],
  ): String => Either[String, A] = value =>
    ZValidation
      .validateAll(validations.map(_(value)))
      .as(make(value))
      .toEither
      .left
      .map(_.mkString(s"$typ ", ", ", "."))
}

trait IntValueCompanion[A <: IntValue] extends WithFrom[Int, A]

object ToValidation {
  def validateOneWithFrom[A, B <: Value[?], E](
    a: A,
    validator: A => Either[String, B],
    err: String => E,
  ): Validation[E, B] =
    Validation.fromEither(validator(a)).mapError(err.apply)

  /**
   * Helper function to validate an optional value using a validator function.
   *
   * If the value is None, the validation will succeed with None.
   *
   * If the value is Some, the validation will use the validator function to validate the value.
   * If the validation succeeds, the validated value will be wrapped in Some.
   * If the validation fails, the error message will be mapped using the err function and the validation will fail.
   *
   * @param maybeA the optional value to validate
   * @param validator the validator function
   * @param err the error function to map the error message
   *
   * @return a Validation containing the validated value or a failed Validation containing the error constructed by err
   */
  def validateOptionWithFrom[A, B <: Value[?], E](
    maybeA: Option[A],
    validator: A => Either[String, B],
    err: String => E,
  ): Validation[E, Option[B]] = maybeA match {
    case Some(a) => Validation.fromEither(validator(a)).map(Some(_)).mapError(err.apply)
    case None    => Validation.succeed(None)
  }
}
