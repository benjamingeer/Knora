/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.test.*

object UserSpec extends ZIOSpecDefault {
  val spec: Spec[Any, Nothing] = suite("UserSpec")(
    test("test") {
      assertTrue(true)
    }
  )
}
