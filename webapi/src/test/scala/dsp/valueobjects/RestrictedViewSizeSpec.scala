/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.Scope
import zio.test.*

import org.knora.webapi.slice.admin.domain.model.RestrictedViewSize

/**
 * This spec is used to test the [[RestrictedViewSize]] value object creation.
 */
object RestrictedViewSizeSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment & Scope, Nothing] = suite("Size")(
    test("should succeed on passing percentage values") {
      val gen = Gen.int(1, 100)
      check(gen) { int =>
        val param = s"pct:$int"
        assertTrue(RestrictedViewSize.from(param).map(_.value) == Right(param))
      }
    },
    test("should succeed on passing the same x y dimensions") {
      val gen = Gen.int(1, 1000)
      check(gen) { integer =>
        val param = s"!$integer,$integer"
        assertTrue(RestrictedViewSize.from(param).map(_.value) == Right(param))
      }
    },
    test("should fail on passing negative x y dimensions") {
      val gen = Gen.int(-1000, -1)
      check(gen) { integer =>
        val param = s"!$integer,$integer"
        assertTrue(RestrictedViewSize.from(param).map(_.value) == Left(s"Invalid RestrictedViewSize: $param"))
      }
    },
    test("should fail on passing incorrect values") {
      val gen = Gen.fromIterable(Seq("!512,100", "pct:-1", "pct:0", "pct:101"))
      check(gen) { param =>
        assertTrue(RestrictedViewSize.from(param) == Left(s"Invalid RestrictedViewSize: $param"))
      }
    },
    test("should fail on passing empty value") {
      assertTrue(RestrictedViewSize.from("") == Left("RestrictedViewSize cannot be empty."))
    }
  )
}
