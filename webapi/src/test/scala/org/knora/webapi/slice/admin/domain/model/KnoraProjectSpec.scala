/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.Scope
import zio.test.*

import scala.util.Random

import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*

/**
 * This spec is used to test the [[org.knora.webapi.slice.admin.domain.model.KnoraProject]] value objects creation.
 */
object KnoraProjectSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment & Scope, Nothing] = suite("KnoraProjectSpec")(
    projectIriSuite,
    shortcodeTest,
    shortnameTest,
    longnameTest,
    descriptionTest,
    keywordsTest,
    logoTest,
    projectStatusTest,
    projectSelfJoinTest,
  )

  private val projectIriSuite = suite("ProjectIri")(
    test("pass an empty value and return an error") {
      assertTrue(ProjectIri.from("") == Left("Project IRI cannot be empty."))
    },
    test("pass an invalid value and return an error") {
      assertTrue(ProjectIri.from("not an iri") == Left("Project IRI is invalid."))
    },
    test("pass a valid project IRI and successfully create value object") {
      val validIris =
        Gen.fromIterable(
          Seq(
            "http://rdfh.ch/projects/0001",
            "http://rdfh.ch/projects/CwQ8hXF9Qlm1gl2QE6pTpg",
            "http://www.knora.org/ontology/knora-admin#SystemProject",
            "http://www.knora.org/ontology/knora-admin#DefaultSharedOntologiesProject",
          ),
        )
      check(validIris)(iri => assertTrue(ProjectIri.unsafeFrom(iri).value == iri))
    },
  )

  private val shortcodeTest = suite("Shortcode")(
    test("pass an empty value and return an error") {
      assertTrue(Shortcode.from("") == Left("Shortcode cannot be empty."))
    },
    test("pass an invalid value and return an error") {
      val invalidShortcodes = Gen.fromIterable(Seq("123", "000G", "12345", "aa"))
      check(invalidShortcodes) { shortcode =>
        assertTrue(Shortcode.from(shortcode) == Left(s"Shortcode is invalid: $shortcode"))
      }
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(Shortcode.from("FFFF").map(_.value).contains("FFFF"))
    },
  )

  private val shortnameTest = suite("Shortname")(
    test("pass an empty value and return validation error") {
      assertTrue(Shortname.from("") == Left("Shortname cannot be empty."))
    },
    test("pass invalid values and return validation error") {
      val gen = Gen.fromIterable(
        Seq(
          "invalid:shortname",
          "-invalidshortname",
          "_invalidshortname",
          ".invalidshortname",
          "invalid/shortname",
          "invalid@shortname",
          "1invalidshortname",
          " invalidshortname",
          "invalid shortname",
          "a",
          "ab",
          "just-21char-shortname",
        ),
      )
      check(gen) { param =>
        assertTrue(Shortname.from(param) == Left(s"Shortname is invalid: $param"))
      }
    },
    test("pass valid values and successfully create value objects") {
      val gen = Gen.fromIterable(
        Seq(
          "DefaultSharedOntologiesProject",
          "valid-shortname",
          "valid_shortname",
          "validshortname-",
          "validshortname_",
          "abc",
          "a20charLongShortname",
        ),
      )
      check(gen)(param => assertTrue(Shortname.from(param).map(_.value).contains(param)))
    },
  )

  private val longnameTest = suite("Longname")(
    test("pass invalid Longname and expect an error to be returned") {
      val invalidNames =
        Gen.stringBounded(0, 2)(Gen.printableChar) ++ Gen.stringBounded(257, 300)(Gen.printableChar)
      check(invalidNames) { name =>
        assertTrue(Longname.from(name) == Left("Longname must be 3 to 256 characters long."))
      }
    },
    test("pass a valid value and successfully create value object") {
      val validNames = Gen.stringBounded(3, 256)(Gen.printableChar)
      check(validNames) { name =>
        assertTrue(Longname.from(name).map(_.value).contains(name))
      }
    },
  )

  private val descriptionTest = suite("Description")(
    test("pass an object containing too short Description and expect an error to be returned") {
      assertTrue(
        Description.from(StringLiteralV2.from("Ab", Some("en"))) ==
          Left("Description must be 3 to 40960 characters long."),
      )
    },
    test("pass an object containing too long Description and expect an error to be returned") {
      assertTrue(
        Description.from(StringLiteralV2.from(new Random().nextString(40961), Some("en"))) ==
          Left("Description must be 3 to 40960 characters long."),
      )
    },
    test("pass a valid object and successfully create value object") {
      assertTrue(
        Description
          .from(StringLiteralV2.from(value = "Valid project description", language = Some("en")))
          .map(_.value) ==
          Right(StringLiteralV2.from(value = "Valid project description", language = Some("en"))),
      )
    },
  )

  private val keywordsTest = suite("Keywords")(
    test("pass an empty object and return an error") {
      val invalidKeywords =
        Gen.fromIterable(Seq("ThisIs65CharactersKeywordThatShouldFailTheTestSoItHasToBeThatLong", "12", "1"))
      check(invalidKeywords) { keyword =>
        assertTrue(Keyword.from(keyword) == Left("Keyword must be 3 to 64 characters long."))
      }
    },
    test("pass a valid object and successfully create value object") {
      assertTrue(Keyword.from("validKeyword").map(_.value).contains("validKeyword"))
    },
  )

  private val logoTest = suite("Logo")(
    test("pass an empty object and return an error") {
      assertTrue(Logo.from("") == Left("Logo cannot be empty."))
    },
    test("pass a valid object and successfully create value object") {
      val validLogo = "/foo/bar/baz.jpg"
      assertTrue(Logo.from(validLogo).map(_.value).contains(validLogo))
    },
  )

  private val projectStatusTest = suite("ProjectStatus")(
    test("pass a valid object and successfully create value object") {
      assertTrue(
        Status.from(true) == Status.Active,
        Status.from(false) == Status.Inactive,
      )
    },
    test("value should be the correct boolean") {
      assertTrue(Status.Active.value, !Status.Inactive.value)
    },
  )

  private val projectSelfJoinTest = suite("ProjectSelfJoin")(
    test("pass a valid object and successfully create value object") {
      assertTrue(
        SelfJoin.from(true) == SelfJoin.CanJoin,
        SelfJoin.from(false) == SelfJoin.CannotJoin,
      )
    },
    test("value should be the correct boolean") {
      assertTrue(SelfJoin.CanJoin.value, !SelfJoin.CannotJoin.value)
    },
  )
}
