/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.settings

import akka.ConfigurationException
import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import com.typesafe.config.Config
import com.typesafe.config.ConfigValue
import com.typesafe.scalalogging.Logger

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import dsp.errors.FileWriteException
import dsp.valueobjects.User
import org.knora.webapi.util.cache.CacheUtil.KnoraCacheConfig

/**
 * Reads application settings that come from `application.conf`.
 */
class KnoraSettingsImpl(config: Config, log: Logger) extends Extension {

  // print config
  val printExtendedConfig: Boolean = config.getBoolean("app.print-extended-config")

  // used for communication inside the knora stack
  val internalKnoraApiHost: String = config.getString("app.knora-api.internal-host")
  val internalKnoraApiPort: Int    = config.getInt("app.knora-api.internal-port")
  val internalKnoraApiBaseUrl: String =
    "http://" + internalKnoraApiHost + (if (internalKnoraApiPort != 80)
                                          ":" + internalKnoraApiPort
                                        else "")

  // used for communication between the outside and the knora stack, e.g., browser
  val externalKnoraApiProtocol: String = config.getString("app.knora-api.external-protocol")
  val externalKnoraApiHost: String     = config.getString("app.knora-api.external-host")
  val externalKnoraApiPort: Int        = config.getInt("app.knora-api.external-port")
  val externalKnoraApiHostPort: String = externalKnoraApiHost + (if (externalKnoraApiPort != 80)
                                                                   ":" + externalKnoraApiPort
                                                                 else "")
  val externalKnoraApiBaseUrl: String =
    externalKnoraApiProtocol + "://" + externalKnoraApiHost + (if (externalKnoraApiPort != 80)
                                                                 ":" + externalKnoraApiPort
                                                               else "")

  /**
   * If the external hostname is localhost or 0.0.0.0, include the configured
   * external port number in ontology IRIs for manual testing.
   */
  val externalOntologyIriHostAndPort: String =
    if (externalKnoraApiHost == "0.0.0.0" || externalKnoraApiHost == "localhost") {
      externalKnoraApiHostPort
    } else {
      // Otherwise, don't include any port number in IRIs, so the IRIs will work both with http
      // and with https.
      externalKnoraApiHost
    }

  val salsah1BaseUrl: String              = config.getString("app.salsah1.base-url")
  val salsah1ProjectIconsBasePath: String = config.getString("app.salsah1.project-icons-basepath")

  val tmpDataDir: String = config.getString("app.tmp-datadir")
  val dataDir: String    = config.getString("app.datadir")

  // try to create the directories
  if (!Files.exists(Paths.get(tmpDataDir))) {
    try {
      Files.createDirectories(Paths.get(tmpDataDir))
    } catch {
      case e: Throwable =>
        throw FileWriteException(s"Tmp data directory $tmpDataDir could not be created: ${e.getMessage}")
    }
  }

  // try to create the directories
  if (!Files.exists(Paths.get(dataDir))) {
    try {
      Files.createDirectories(Paths.get(dataDir))
    } catch {
      case e: Throwable =>
        throw FileWriteException(s"Tmp data directory $tmpDataDir could not be created: ${e.getMessage}")
    }
  }

  val imageMimeTypes: Set[String] = config
    .getList("app.sipi.image-mime-types")
    .iterator
    .asScala
    .map { mType: ConfigValue =>
      mType.unwrapped.toString
    }
    .toSet

  val documentMimeTypes: Set[String] = config
    .getList("app.sipi.document-mime-types")
    .iterator
    .asScala
    .map { mType: ConfigValue =>
      mType.unwrapped.toString
    }
    .toSet

  val textMimeTypes: Set[String] = config
    .getList("app.sipi.text-mime-types")
    .iterator
    .asScala
    .map { mType: ConfigValue =>
      mType.unwrapped.toString
    }
    .toSet

  val audioMimeTypes: Set[String] = config
    .getList("app.sipi.audio-mime-types")
    .iterator
    .asScala
    .map { mType: ConfigValue =>
      mType.unwrapped.toString
    }
    .toSet

  val videoMimeTypes: Set[String] = config
    .getList("app.sipi.video-mime-types")
    .iterator
    .asScala
    .map { mType: ConfigValue =>
      mType.unwrapped.toString
    }
    .toSet

  val archiveMimeTypes: Set[String] = config
    .getList("app.sipi.archive-mime-types")
    .iterator
    .asScala
    .map { mType: ConfigValue =>
      mType.unwrapped.toString
    }
    .toSet

  val internalSipiProtocol: String = config.getString("app.sipi.internal-protocol")
  val internalSipiHost: String     = config.getString("app.sipi.internal-host")
  val internalSipiPort: Int        = config.getInt("app.sipi.internal-port")
  val internalSipiBaseUrl: String = internalSipiProtocol + "://" + internalSipiHost + (if (internalSipiPort != 80)
                                                                                         ":" + internalSipiPort
                                                                                       else "")

  val sipiTimeout: FiniteDuration = getFiniteDuration("app.sipi.timeout", config)

  val externalSipiProtocol: String = config.getString("app.sipi.external-protocol")
  val externalSipiHost: String     = config.getString("app.sipi.external-host")
  val externalSipiPort: Int        = config.getInt("app.sipi.external-port")
  val externalSipiBaseUrl: String = externalSipiProtocol + "://" + externalSipiHost + (if (externalSipiPort != 80)
                                                                                         ":" + externalSipiPort
                                                                                       else "")
  val sipiFileServerPrefix: String      = config.getString("app.sipi.file-server-path")
  val externalSipiIIIFGetUrl: String    = externalSipiBaseUrl
  val sipiFileMetadataRouteV2: String   = config.getString("app.sipi.v2.file-metadata-route")
  val sipiMoveFileRouteV2: String       = config.getString("app.sipi.v2.move-file-route")
  val sipiDeleteTempFileRouteV2: String = config.getString("app.sipi.v2.delete-temp-file-route")

  val arkResolver: String    = config.getString("app.ark.resolver")
  val arkAssignedNumber: Int = config.getInt("app.ark.assigned-number")

  val caches: Vector[KnoraCacheConfig] = config
    .getList("app.caches")
    .iterator
    .asScala
    .map { cacheConfigItem: ConfigValue =>
      val cacheConfigMap = cacheConfigItem.unwrapped.asInstanceOf[java.util.HashMap[String, Any]].asScala
      KnoraCacheConfig(
        cacheConfigMap("cache-name").asInstanceOf[String],
        cacheConfigMap("max-elements-in-memory").asInstanceOf[Int],
        cacheConfigMap("overflow-to-disk").asInstanceOf[Boolean],
        cacheConfigMap("eternal").asInstanceOf[Boolean],
        cacheConfigMap("time-to-live-seconds").asInstanceOf[Int],
        cacheConfigMap("time-to-idle-seconds").asInstanceOf[Int]
      )
    }
    .toVector

  val defaultTimeout: FiniteDuration = getFiniteDuration("app.default-timeout", config)

  val dumpMessages: Boolean              = config.getBoolean("app.dump-messages")
  val showInternalErrors: Boolean        = config.getBoolean("app.show-internal-errors")
  val maxResultsPerSearchResultPage: Int = config.getInt("app.max-results-per-search-result-page")
  val standoffPerPage: Int               = config.getInt("app.standoff-per-page")
  val defaultIconSizeDimX: Int           = config.getInt("app.gui.default-icon-size.dimX")
  val defaultIconSizeDimY: Int           = config.getInt("app.gui.default-icon-size.dimY")

  val v2ResultsPerPage: Int     = config.getInt("app.v2.resources-sequence.results-per-page")
  val searchValueMinLength: Int = config.getInt("app.v2.fulltext-search.search-value-min-length")

  val defaultGraphDepth: Int = config.getInt("app.v2.graph-route.default-graph-depth")
  val maxGraphDepth: Int     = config.getInt("app.v2.graph-route.max-graph-depth")
  val maxGraphBreadth: Int   = config.getInt("app.v2.graph-route.max-graph-breadth")

  val triplestoreType: String = config.getString("app.triplestore.dbtype")
  val triplestoreHost: String = config.getString("app.triplestore.host")

  val triplestoreUseHttps: Boolean = config.getBoolean("app.triplestore.use-https")

  val triplestoreAutoInit: Boolean = config.getBoolean("app.triplestore.auto-init")

  val triplestorePort: Int            = config.getInt("app.triplestore.fuseki.port")
  val triplestoreDatabaseName: String = config.getString("app.triplestore.fuseki.repository-name")
  val triplestoreUsername: String     = config.getString("app.triplestore.fuseki.username")
  val triplestorePassword: String     = config.getString("app.triplestore.fuseki.password")

  // used in the store package
  val tripleStoreConfig: Config = config.getConfig("app.triplestore")

  val jwtSecretKey: String         = config.getString("app.jwt-secret-key")
  val jwtLongevity: FiniteDuration = getFiniteDuration("app.jwt-longevity", config)

  val cookieDomain: String = config.getString("app.cookie-domain")

  val fallbackLanguage: String = config.getString("user.default-language")

  val profileQueries: Boolean = config.getBoolean("app.triplestore.profile-queries")

  val routesToReject: Seq[String] = config
    .getList("app.routes-to-reject")
    .iterator
    .asScala
    .map { mType: ConfigValue =>
      mType.unwrapped.toString
    }
    .toSeq

  val allowReloadOverHTTP: Boolean = config.getBoolean("app.allow-reload-over-http")

  val bcryptPasswordStrength =
    User.PasswordStrength
      .make(config.getInt("app.bcrypt-password-strength"))
      .fold(e => throw new ConfigurationException(e.head), v => v)

  // Client test data service

  val collectClientTestData: Boolean = if (config.hasPath("app.client-test-data-service.collect-client-test-data")) {
    config.getBoolean("app.client-test-data-service.collect-client-test-data")
  } else {
    false
  }

  private def getFiniteDuration(path: String, underlying: Config): FiniteDuration =
    Duration(underlying.getString(path)) match {
      case x: FiniteDuration => x
      case _                 => throw new ConfigurationException(s"Config setting '$path' must be a finite duration")
    }

  val prometheusEndpoint: Boolean = config.getBoolean("app.monitoring.prometheus-endpoint")

  val shaclShapesDir: Path = Paths.get(config.getString("app.shacl.shapes-dir"))
}

object KnoraSettings extends ExtensionId[KnoraSettingsImpl] with ExtensionIdProvider {

  override def lookup: KnoraSettings.type = KnoraSettings

  override def createExtension(system: ExtendedActorSystem) =
    new KnoraSettingsImpl(system.settings.config, Logger(this.getClass))

  /**
   * Java API: retrieve the Settings extension for the given system.
   */
  override def get(system: ActorSystem): KnoraSettingsImpl = super.get(system)
}
