/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.test.*

object CopyrightAndLicensesModelSpec extends ZIOSpecDefault {

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

  private val licenseUriSuite = suite("LicenseUri")(
    test("pass a valid object and successfully create value object") {
      val validUri = "https://www.apache.org/licenses/LICENSE-2.0.html"
      assertTrue(LicenseUri.from(validUri).map(_.value).contains(validUri))
    },
    test("pass an invalid object and return an error") {
      val invalidUri = "not a uri"
      assertTrue(LicenseUri.from(invalidUri) == Left("License URI is not a valid URI."))
    },
  )

  val spec = suite("Copyright And Licenses Model")(
    authorshipSuite,
    licenseUriSuite,
  )
}
