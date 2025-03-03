/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.*
import zio.test.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.testcontainers.DspIngestTestContainer
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testcontainers.SipiTestContainer

object TestContainerLayersSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("TestContainerLayers")(
    test("successfully provide the adapted application configuration for using with test containers") {
      for {
        appConfig          <- ZIO.service[AppConfig]
        sipiContainer      <- ZIO.service[SipiTestContainer]
        dspIngestContainer <- ZIO.service[DspIngestTestContainer]
      } yield {
        assertTrue(
          appConfig.sipi.internalPort == sipiContainer.getFirstMappedPort,
          appConfig.dspIngest.baseUrl.endsWith(dspIngestContainer.getFirstMappedPort.toString),
        )
      }
    },
  ).provide(TestContainerLayers.all)
}
