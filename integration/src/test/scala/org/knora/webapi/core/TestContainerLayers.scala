/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfig.AppConfigurations
import org.knora.webapi.testcontainers.DspIngestTestContainer
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testcontainers.SharedVolumes
import org.knora.webapi.testcontainers.SipiTestContainer

object TestContainerLayers {

  val all: ULayer[
    SharedVolumes.Images & SharedVolumes.Temp & DspIngestTestContainer & FusekiTestContainer & SipiTestContainer &
      AppConfigurations,
  ] =
    SharedVolumes.layer >+> DspIngestTestContainer.layer >+> FusekiTestContainer.layer >+> SipiTestContainer.layer >+>
      AppConfigForTestContainers.testcontainers

  val fusekiOnly: ULayer[FusekiTestContainer & AppConfigurations] =
    FusekiTestContainer.layer >+> AppConfigForTestContainers.fusekiOnlyTestcontainer

  private object AppConfigForTestContainers {

    private def alterFusekiAndSipiPort(
      oldConfig: AppConfig,
      fusekiContainer: FusekiTestContainer,
      sipiContainer: SipiTestContainer,
      dspIngestContainer: DspIngestTestContainer,
    ): UIO[AppConfig] = {

      val newFusekiPort    = fusekiContainer.getFirstMappedPort
      val newSipiPort      = sipiContainer.getFirstMappedPort
      val newDspIngestPort = dspIngestContainer.getFirstMappedPort

      val alteredFuseki = oldConfig.triplestore.fuseki.copy(port = newFusekiPort)

      val alteredTriplestore = oldConfig.triplestore.copy(fuseki = alteredFuseki)
      val alteredSipi        = oldConfig.sipi.copy(internalPort = newSipiPort)
      val alteredDspIngest   = oldConfig.dspIngest.copy(baseUrl = s"http://localhost:$newDspIngestPort")

      val newConfig: AppConfig =
        oldConfig.copy(
          allowReloadOverHttp = true,
          triplestore = alteredTriplestore,
          sipi = alteredSipi,
          dspIngest = alteredDspIngest,
        )

      ZIO.succeed(newConfig)
    }

    private def alterFusekiPort(
      oldConfig: AppConfig,
      fusekiContainer: FusekiTestContainer,
    ): UIO[AppConfig] = {

      val newFusekiPort = fusekiContainer.getFirstMappedPort

      val alteredFuseki = oldConfig.triplestore.fuseki.copy(port = newFusekiPort)

      val alteredTriplestore = oldConfig.triplestore.copy(fuseki = alteredFuseki)

      val newConfig: AppConfig = oldConfig.copy(triplestore = alteredTriplestore)

      ZIO.succeed(newConfig)
    }

    /**
     * Altered AppConfig with ports from TestContainers for DSP-Ingest, Fuseki and Sipi.
     */
    val testcontainers
      : ZLayer[DspIngestTestContainer & FusekiTestContainer & SipiTestContainer, Nothing, AppConfigurations] = {
      val appConfigLayer = ZLayer {
        for {
          appConfig          <- AppConfig.parseConfig
          fusekiContainer    <- ZIO.service[FusekiTestContainer]
          sipiContainer      <- ZIO.service[SipiTestContainer]
          dspIngestContainer <- ZIO.service[DspIngestTestContainer]
          alteredConfig      <- alterFusekiAndSipiPort(appConfig, fusekiContainer, sipiContainer, dspIngestContainer)
        } yield alteredConfig
      }
      AppConfig
        .projectAppConfigurations(appConfigLayer)
        .tap(_ => ZIO.logInfo(">>> AppConfig for Fuseki and Sipi Testcontainers Initialized <<<"))
    }

    /**
     * Altered AppConfig with ports from TestContainers for Fuseki.
     */
    val fusekiOnlyTestcontainer: ZLayer[FusekiTestContainer, Nothing, AppConfigurations] = {
      val appConfigLayer = ZLayer {
        for {
          appConfig       <- AppConfig.parseConfig
          fusekiContainer <- ZIO.service[FusekiTestContainer]
          alteredConfig   <- alterFusekiPort(appConfig, fusekiContainer)
        } yield alteredConfig
      }
      AppConfig
        .projectAppConfigurations(appConfigLayer)
        .tap(_ => ZIO.logInfo(">>> AppConfig for Fuseki only Testcontainers Initialized <<<"))
    }
  }
}
