/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.config

import com.typesafe.config.ConfigFactory
import zio.*
import zio.config.*
import zio.config.magnolia.*
import zio.config.typesafe.*

import java.time.Duration

/**
 * Represents the configuration as defined in application.conf.
 */
final case class AppConfig(
  printExtendedConfig: Boolean,
  defaultTimeout: Duration,
  dumpMessages: Boolean,
  showInternalErrors: Boolean,
  bcryptPasswordStrength: Int,
  cookieDomain: String,
  allowReloadOverHttp: Boolean,
  fallbackLanguage: String,
  knoraApi: KnoraApi,
  sipi: Sipi,
  ark: Ark,
  salsah1: Salsah1,
  tmpDatadir: String,
  datadir: String,
  maxResultsPerSearchResultPage: Int,
  v2: V2,
  gui: Gui,
  routesToReject: List[String],
  triplestore: Triplestore,
  instrumentationServerConfig: InstrumentationServerConfig,
  jwt: JwtConfig,
  dspIngest: DspIngestConfig,
  features: Features,
) {
  val tmpDataDirPath: zio.nio.file.Path = zio.nio.file.Path(this.tmpDatadir)
}
final case class JwtConfig(secret: String, expiration: Duration, issuer: Option[String]) {
  def issuerAsString(): String = issuer.getOrElse(
    throw new IllegalStateException(
      "This should never happen, the issuer may be left blank in application.conf but the default is taken from external host and port.",
    ),
  )
}
final case class DspIngestConfig(baseUrl: String, audience: String)

final case class KnoraApi(
  internalHost: String,
  internalPort: Int,
  externalProtocol: String,
  externalHost: String,
  externalPort: Int,
  externalZioPort: Int,
) {
  val internalKnoraApiBaseUrl: String = "http://" + internalHost + (if (internalPort != 80)
                                                                      ":" + internalPort
                                                                    else "")
  val externalKnoraApiHostPort: String = externalHost + (if (externalPort != 80)
                                                           ":" + externalPort
                                                         else "")
  val externalKnoraApiBaseUrl: String = externalProtocol + "://" + externalHost + (if (externalPort != 80)
                                                                                     ":" + externalPort
                                                                                   else "")

  /**
   * If the external hostname is localhost or 0.0.0.0, include the configured
   * external port number in ontology IRIs for manual testing.
   */
  val externalOntologyIriHostAndPort: String =
    if (externalHost == "0.0.0.0" || externalHost == "localhost") {
      externalKnoraApiHostPort
    } else {
      // Otherwise, don't include any port number in IRIs, so the IRIs will work both with http
      // and with https.
      externalHost
    }
}

final case class Sipi(
  internalProtocol: String,
  internalHost: String,
  internalPort: Int,
  timeout: Duration,
  externalProtocol: String,
  externalHost: String,
  externalPort: Int,
  fileServerPath: String,
  moveFileRoute: String,
  deleteTempFileRoute: String,
  imageMimeTypes: List[String],
  documentMimeTypes: List[String],
  textMimeTypes: List[String],
  videoMimeTypes: List[String],
  audioMimeTypes: List[String],
  archiveMimeTypes: List[String],
) {
  val internalBaseUrl: String =
    internalProtocol + "://" + internalHost + (if (internalPort != 80) ":" + internalPort else "")
  val externalBaseUrl: String =
    externalProtocol + "://" + externalHost + (if (externalPort != 80) ":" + externalPort else "")
}

final case class Ark(
  resolver: String,
  assignedNumber: Int,
)

final case class Salsah1(
  baseUrl: String,
  projectIconsBasepath: String,
)

final case class V2(
  resourcesSequence: ResourcesSequence,
  fulltextSearch: FulltextSearch,
  graphRoute: GraphRoute,
)

final case class ResourcesSequence(
  resultsPerPage: Int,
)

final case class FulltextSearch(
  searchValueMinLength: Int,
)

final case class GraphRoute(
  defaultGraphDepth: Int,
  maxGraphDepth: Int,
  maxGraphBreadth: Int,
)

final case class Gui(
  defaultIconSize: DefaultIconSize,
)

final case class DefaultIconSize(
  dimX: Int,
  dimY: Int,
)

final case class Triplestore(
  dbtype: String,
  useHttps: Boolean,
  host: String,
  queryTimeout: Duration,
  gravsearchTimeout: Duration,
  maintenanceTimeout: Duration,
  autoInit: Boolean,
  fuseki: Fuseki,
  profileQueries: Boolean,
  isTestEnv: Boolean = false,
)

final case class Fuseki(
  port: Int,
  repositoryName: String,
  username: String,
  password: String,
  queryLoggingThreshold: Duration = Duration.ofMillis(1000),
  allowCompaction: Boolean = false,
)

final case class InstrumentationServerConfig(
  port: Int,
  interval: Duration,
)

final case class Features(
  allowEraseProjects: Boolean,
  disableLastModificationDateCheck: Boolean,
  triggerCompactionAfterProjectErasure: Boolean,
)

object AppConfig {
  type AppConfigurations = AppConfig & DspIngestConfig & Features & GraphRoute & InstrumentationServerConfig &
    JwtConfig & KnoraApi & Sipi & Triplestore

  val parseConfig: UIO[AppConfig] = {
    val descriptor = deriveConfig[AppConfig].mapKey(toKebabCase)
    val source     = TypesafeConfigProvider.fromTypesafeConfig(ConfigFactory.load().getConfig("app").resolve)
    read(descriptor from source).tap(logFeaturesEnabled).orDie
  }

  val layer: ULayer[AppConfigurations] =
    projectAppConfigurations(ZLayer.fromZIO(parseConfig))
      .tap(_ => ZIO.logInfo(">>> AppConfig Initialized <<<"))

  private def logFeaturesEnabled(c: AppConfig) = {
    val features = List(
      "ALLOW_ERASE_PROJECTS"                     -> c.features.allowEraseProjects,
      "DISABLE_LAST_MODIFICATION_DATE_CHECK"     -> c.features.disableLastModificationDateCheck,
      "TRIGGER_COMPACTION_AFTER_PROJECT_ERASURE" -> c.features.triggerCompactionAfterProjectErasure,
    ).collect { case (feature, enabled) if enabled => feature }
    ZIO.logInfo(s"Features enabled: ${features.mkString(", ")}").when(features.nonEmpty)
  }

  def projectAppConfigurations[R](appConfigLayer: URLayer[R, AppConfig]): URLayer[R, AppConfigurations] =
    appConfigLayer ++
      appConfigLayer.project(_.knoraApi) ++
      appConfigLayer.project(_.sipi) ++
      appConfigLayer.project(_.dspIngest) ++
      appConfigLayer.project(_.triplestore) ++
      appConfigLayer.project(_.instrumentationServerConfig) ++
      appConfigLayer.project(_.features) ++
      appConfigLayer.project { appConfig =>
        val jwtConfig = appConfig.jwt
        val issuerFromConfigOrDefault: Option[String] =
          jwtConfig.issuer.orElse(Some(appConfig.knoraApi.externalKnoraApiHostPort))
        jwtConfig.copy(issuer = issuerFromConfigOrDefault)
      } ++
      appConfigLayer.project(_.v2.graphRoute)
}
