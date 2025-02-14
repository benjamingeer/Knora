/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.test.*

object LegalInfoModelSpec extends ZIOSpecDefault {

  private val authorshipSuite = suite("Authorship")(
    test("pass a valid object and successfully create value object") {
      val validAuthorship = "Jane Doe"
      assertTrue(Authorship.from(validAuthorship).map(_.value).contains(validAuthorship))
    },
    test("pass an invalid object and return an error") {
      val invalidAuthorship = "Jane \n Doe"
      assertTrue(Authorship.from(invalidAuthorship) == Left("Authorship must not contain line breaks."))
    },
    test("pass an invalid object and return an error") {
      val invalidAuthorship = "a" * 1001
      assertTrue(Authorship.from(invalidAuthorship) == Left("Authorship must be maximum 1000 characters long."))
    },
    test("pass an invalid object and return an error") {
      val invalidAuthorship = ""
      assertTrue(Authorship.from(invalidAuthorship) == Left("Authorship cannot be empty."))
    },
  )

  private val licenseIriSuite = suite("LicenseIri")(
    test("pass a valid object and successfully create value object") {
      val validIri = "http://rdfh.ch/licenses/i6xBpZn4RVOdOIyTezEumw"
      assertTrue(LicenseIri.from(validIri).map(_.value).contains(validIri))
    },
    test("pass an invalid object and return an error") {
      val invalidIri = "http://rdfh.ch/licenses/invalid"
      assertTrue(LicenseIri.from(invalidIri) == Left("Invalid license IRI"))
    },
    test("make new returns a valid IRI") {
      val newIri = LicenseIri.makeNew
      assertTrue(LicenseIri.from(newIri.value).contains(newIri))
    },
  )

  val spec: Spec[Any, Nothing] = suite("Copyright And Licenses Model")(
    authorshipSuite,
    licenseIriSuite,
  )
}
