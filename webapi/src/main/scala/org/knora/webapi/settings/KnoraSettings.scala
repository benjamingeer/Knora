/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.settings

import java.io.File
import java.nio.file.{Files, Paths}
import java.time.Instant

import akka.ConfigurationException
import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.event.LoggingAdapter
import com.typesafe.config.{Config, ConfigObject, ConfigValue}
import org.knora.webapi.exceptions.{FeatureToggleException, FileWriteException}
import org.knora.webapi.util.cache.CacheUtil.KnoraCacheConfig

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * Reads application settings that come from `application.conf`.
 */
class KnoraSettingsImpl(config: Config, log: LoggingAdapter) extends Extension {

    import KnoraSettings._

    // print config
    val printExtendedConfig: Boolean = config.getBoolean("app.print-extended-config")

    // used for communication inside the knora stack
    val internalKnoraApiHost: String = config.getString("app.knora-api.internal-host")
    val internalKnoraApiPort: Int = config.getInt("app.knora-api.internal-port")
    val internalKnoraApiBaseUrl: String = "http://" + internalKnoraApiHost + (if (internalKnoraApiPort != 80) ":" + internalKnoraApiPort else "")

    // used for communication between the outside and the knora stack, e.g., browser
    val externalKnoraApiProtocol: String = config.getString("app.knora-api.external-protocol")
    val externalKnoraApiHost: String = config.getString("app.knora-api.external-host")
    val externalKnoraApiPort: Int = config.getInt("app.knora-api.external-port")
    val externalKnoraApiHostPort: String = externalKnoraApiHost + (if (externalKnoraApiPort != 80) ":" + externalKnoraApiPort else "")
    val externalKnoraApiBaseUrl: String = externalKnoraApiProtocol + "://" + externalKnoraApiHost + (if (externalKnoraApiPort != 80) ":" + externalKnoraApiPort else "")

    // If the external hostname is localhost, include the configured external port number in ontology IRIs for manual testing.
    val externalOntologyIriHostAndPort: String = if (externalKnoraApiHost == "0.0.0.0" || externalKnoraApiHost == "localhost") {
        externalKnoraApiHostPort
    } else {
        // Otherwise, don't include any port number in IRIs, so the IRIs will work both with http
        // and with https.
        externalKnoraApiHost
    }

    val salsah1BaseUrl: String = config.getString("app.salsah1.base-url")
    val salsah1ProjectIconsBasePath: String = config.getString("app.salsah1.project-icons-basepath")

    val tmpDataDir: String = config.getString("app.tmp-datadir")
    val dataDir: String = config.getString("app.datadir")

    // try to create the directories
    if (!Files.exists(Paths.get(tmpDataDir))) {
        try {
            val _tmpDataDir = new File(tmpDataDir)
            _tmpDataDir.mkdir()
        } catch {
            case e: Throwable => throw FileWriteException(s"Tmp data directory $tmpDataDir could not be created: ${e.getMessage}")
        }
    }

    // try to create the directories
    if (!Files.exists(Paths.get(dataDir))) {
        try {
            val _dataDir = new File(dataDir)
            _dataDir.mkdir()
        } catch {
            case e: Throwable => throw FileWriteException(s"Tmp data directory $tmpDataDir could not be created: ${e.getMessage}")
        }
    }

    val imageMimeTypes: Set[String] = config.getList("app.sipi.image-mime-types").iterator.asScala.map {
        mType: ConfigValue => mType.unwrapped.toString
    }.toSet

    val documentMimeTypes: Set[String] = config.getList("app.sipi.document-mime-types").iterator.asScala.map {
        mType: ConfigValue => mType.unwrapped.toString
    }.toSet

    val textMimeTypes: Set[String] = config.getList("app.sipi.text-mime-types").iterator.asScala.map {
        mType: ConfigValue => mType.unwrapped.toString
    }.toSet

    val internalSipiProtocol: String = config.getString("app.sipi.internal-protocol")
    val internalSipiHost: String = config.getString("app.sipi.internal-host")
    val internalSipiPort: Int = config.getInt("app.sipi.internal-port")
    val internalSipiBaseUrl: String = internalSipiProtocol + "://" + internalSipiHost + (if (internalSipiPort != 80) ":" + internalSipiPort else "")

    val sipiTimeout: FiniteDuration = getFiniteDuration("app.sipi.timeout", config)

    val externalSipiProtocol: String = config.getString("app.sipi.external-protocol")
    val externalSipiHost: String = config.getString("app.sipi.external-host")
    val externalSipiPort: Int = config.getInt("app.sipi.external-port")
    val externalSipiBaseUrl: String = externalSipiProtocol + "://" + externalSipiHost + (if (externalSipiPort != 80) ":" + externalSipiPort else "")
    val sipiFileServerPrefix: String = config.getString("app.sipi.file-server-path")
    val externalSipiIIIFGetUrl: String = externalSipiBaseUrl
    val sipiFileMetadataRouteV2: String = config.getString("app.sipi.v2.file-metadata-route")
    val sipiMoveFileRouteV2: String = config.getString("app.sipi.v2.move-file-route")
    val sipiDeleteTempFileRouteV2: String = config.getString("app.sipi.v2.delete-temp-file-route")

    val arkResolver: String = config.getString("app.ark.resolver")
    val arkAssignedNumber: Int = config.getInt("app.ark.assigned-number")

    val caches: Vector[KnoraCacheConfig] = config.getList("app.caches").iterator.asScala.map {
        cacheConfigItem: ConfigValue =>
            val cacheConfigMap = cacheConfigItem.unwrapped.asInstanceOf[java.util.HashMap[String, Any]].asScala
            KnoraCacheConfig(cacheConfigMap("cache-name").asInstanceOf[String],
                cacheConfigMap("max-elements-in-memory").asInstanceOf[Int],
                cacheConfigMap("overflow-to-disk").asInstanceOf[Boolean],
                cacheConfigMap("eternal").asInstanceOf[Boolean],
                cacheConfigMap("time-to-live-seconds").asInstanceOf[Int],
                cacheConfigMap("time-to-idle-seconds").asInstanceOf[Int])
    }.toVector


    val defaultTimeout: FiniteDuration = getFiniteDuration("app.default-timeout", config)

    val dumpMessages: Boolean = config.getBoolean("app.dump-messages")
    val showInternalErrors: Boolean = config.getBoolean("app.show-internal-errors")
    val maxResultsPerSearchResultPage: Int = config.getInt("app.max-results-per-search-result-page")
    val standoffPerPage: Int = config.getInt("app.standoff-per-page")
    val defaultIconSizeDimX: Int = config.getInt("app.gui.default-icon-size.dimX")
    val defaultIconSizeDimY: Int = config.getInt("app.gui.default-icon-size.dimY")

    val v2ResultsPerPage: Int = config.getInt("app.v2.resources-sequence.results-per-page")
    val searchValueMinLength: Int = config.getInt("app.v2.fulltext-search.search-value-min-length")

    val defaultGraphDepth: Int = config.getInt("app.v2.graph-route.default-graph-depth")
    val maxGraphDepth: Int = config.getInt("app.v2.graph-route.max-graph-depth")
    val maxGraphBreadth: Int = config.getInt("app.v2.graph-route.max-graph-breadth")

    val triplestoreType: String = config.getString("app.triplestore.dbtype")
    val triplestoreHost: String = config.getString("app.triplestore.host")

    val triplestoreQueryTimeout: FiniteDuration = getFiniteDuration("app.triplestore.query-timeout", config)
    val triplestoreUpdateTimeout: FiniteDuration = getFiniteDuration("app.triplestore.update-timeout", config)

    val triplestoreUseHttps: Boolean = config.getBoolean("app.triplestore.use-https")

    val triplestoreAutoInit: Boolean = config.getBoolean("app.triplestore.auto-init")

    val triplestorePort: Int = triplestoreType match {
        case TriplestoreTypes.HttpGraphDBSE | TriplestoreTypes.HttpGraphDBFree => config.getInt("app.triplestore.graphdb.port")
        case TriplestoreTypes.HttpFuseki => config.getInt("app.triplestore.fuseki.port")
        case _ => 9999
    }

    val triplestoreDatabaseName: String = triplestoreType match {
        case TriplestoreTypes.HttpGraphDBSE | TriplestoreTypes.HttpGraphDBFree => config.getString("app.triplestore.graphdb.repository-name")
        case TriplestoreTypes.HttpFuseki => config.getString("app.triplestore.fuseki.repository-name")
        case _ => ""
    }

    val triplestoreUsername: String = triplestoreType match {
        case TriplestoreTypes.HttpGraphDBSE | TriplestoreTypes.HttpGraphDBFree => config.getString("app.triplestore.graphdb.username")
        case TriplestoreTypes.HttpFuseki => config.getString("app.triplestore.fuseki.username")
        case _ => ""
    }

    val triplestorePassword: String = triplestoreType match {
        case TriplestoreTypes.HttpGraphDBSE | TriplestoreTypes.HttpGraphDBFree => config.getString("app.triplestore.graphdb.password")
        case TriplestoreTypes.HttpFuseki => config.getString("app.triplestore.fuseki.password")
        case _ => ""
    }

    //used in the store package
    val tripleStoreConfig: Config = config.getConfig("app.triplestore")

    private val fakeTriplestore: String = config.getString("app.triplestore.fake-triplestore")
    val prepareFakeTriplestore: Boolean = fakeTriplestore == "prepare"
    val useFakeTriplestore: Boolean = fakeTriplestore == "use"
    val fakeTriplestoreDataDir: File = new File(config.getString("app.triplestore.fake-triplestore-data-dir"))

    val skipAuthentication: Boolean = config.getBoolean("app.skip-authentication")

    val jwtSecretKey: String = config.getString("app.jwt-secret-key")
    val jwtLongevity: FiniteDuration = getFiniteDuration("app.jwt-longevity", config)

    val cookieDomain: String = config.getString("app.cookie-domain")

    val fallbackLanguage: String = config.getString("user.default-language")

    val profileQueries: Boolean = config.getBoolean("app.triplestore.profile-queries")

    val routesToReject: Seq[String] = config.getList("app.routes-to-reject").iterator.asScala.map {
        mType: ConfigValue => mType.unwrapped.toString
    }.toSeq

    val allowReloadOverHTTP: Boolean = config.getBoolean("app.allow-reload-over-http")

    val bcryptPasswordStrength: Int = config.getInt("app.bcrypt-password-strength")

    // Cache Service
    val cacheServiceEnabled: Boolean = config.getBoolean("app.cache-service.enabled")
    val cacheServiceRedisHost: String = config.getString("app.cache-service.redis.host")
    val cacheServiceRedisPort: Int = config.getInt("app.cache-service.redis.port")

    // Client test data service

    val collectClientTestData: Boolean = if (config.hasPath("app.client-test-data-service.collect-client-test-data")) {
        config.getBoolean("app.client-test-data-service.collect-client-test-data")
    } else {
        false
    }

    val clientTestDataRedisHost: Option[String] = if (config.hasPath("app.client-test-data-service.redis.host")) {
        Some(config.getString("app.client-test-data-service.redis.host"))
    } else {
        None
    }

    val clientTestDataRedisPort: Option[Int] = if (config.hasPath("app.client-test-data-service.redis.port")) {
        Some(config.getInt("app.client-test-data-service.redis.port"))
    } else {
        None
    }

    private def getFiniteDuration(path: String, underlying: Config): FiniteDuration = Duration(underlying.getString(path)) match {
        case x: FiniteDuration ⇒ x
        case _ ⇒ throw new ConfigurationException(s"Config setting '$path' must be a finite duration")
    }

    val prometheusEndpoint: Boolean = config.getBoolean("app.monitoring.prometheus-endpoint")

    val upgradeDownloadDir: Option[String] = if (config.hasPath("app.upgrade.download-dir")) {
        Some(config.getString("app.upgrade.download-dir"))
    } else {
        None
    }

    val featureToggles: Set[FeatureToggleBaseConfig] = if (config.hasPath(featureTogglesPath)) {
        Try {
            config.getObject(featureTogglesPath).asScala.toMap.map {
                case (featureName: String, featureConfigValue: ConfigValue) =>
                    val featureConfig: Config = featureConfigValue match {
                        case configObject: ConfigObject => configObject.toConfig
                        case _ => throw FeatureToggleException(s"The feature toggle configuration $featureName must be an object")
                    }

                    val description: String = featureConfig.getString(descriptionKey)
                    val availableVersions: Seq[Int] = featureConfig.getIntList(availableVersionsKey).asScala.map(_.intValue).toVector

                    if (availableVersions.isEmpty) {
                        throw FeatureToggleException(s"Feature toggle $featureName has no version numbers")
                    }

                    for ((version: Int, index: Int) <- availableVersions.zipWithIndex) {
                        if (version != index + 1) {
                            throw FeatureToggleException(s"The version numbers of feature toggle $featureName must be an ascending sequence of consecutive integers starting from 1")
                        }
                    }

                    val defaultVersion: Int = featureConfig.getInt(defaultVersionKey)

                    if (!availableVersions.contains(defaultVersion)) {
                        throw FeatureToggleException(s"Invalid default version number $defaultVersion for feature toggle $featureName")
                    }

                    val enabledByDefault: Boolean = featureConfig.getBoolean(enabledByDefaultKey)
                    val overrideAllowed: Boolean = featureConfig.getBoolean(overrideAllowedKey)

                    val expirationDate: Option[Instant] = if (featureConfig.hasPath(expirationDateKey)) {
                        val definedExpirationDate: Instant = Instant.parse(featureConfig.getString(expirationDateKey))

                        if (Instant.ofEpochMilli(System.currentTimeMillis).isAfter(definedExpirationDate)) {
                            log.warning(s"Feature toggle $featureName has expired")
                        }

                        Some(definedExpirationDate)
                    } else {
                        None
                    }

                    val developerEmails: Set[String] = featureConfig.getStringList(developerEmailsKey).asScala.toSet

                    FeatureToggleBaseConfig(
                        featureName = featureName,
                        description = description,
                        availableVersions = availableVersions,
                        defaultVersion = defaultVersion,
                        enabledByDefault = enabledByDefault,
                        overrideAllowed = overrideAllowed,
                        expirationDate = expirationDate,
                        developerEmails = developerEmails
                    )
            }.toSet
        } match {
            case Success(toggles) => toggles
            case Failure(ex) =>
                ex match {
                    case fte: FeatureToggleException => throw fte
                    case other => throw FeatureToggleException(s"Invalid feature toggle configuration: ${other.getMessage}", Some(ex))
                }
        }
    } else {
        Set.empty
    }
}

object KnoraSettings extends ExtensionId[KnoraSettingsImpl] with ExtensionIdProvider {

    override def lookup(): KnoraSettings.type = KnoraSettings

    override def createExtension(system: ExtendedActorSystem) =
        new KnoraSettingsImpl(system.settings.config, akka.event.Logging(system, this.getClass))

    /**
     * Java API: retrieve the Settings extension for the given system.
     */
    override def get(system: ActorSystem): KnoraSettingsImpl = super.get(system)

    val featureTogglesPath: String = "app.feature-toggles"
    val descriptionKey: String = "description"
    val availableVersionsKey: String = "available-versions"
    val developerEmailsKey: String = "developer-emails"
    val expirationDateKey: String = "expiration-date"
    val enabledByDefaultKey: String = "enabled-by-default"
    val defaultVersionKey: String = "default-version"
    val overrideAllowedKey: String = "override-allowed"

    /**
     * Represents the base configuration of a feature toggle.
     *
     * @param featureName       the name of the feature.
     * @param description       a description of the feature.
     * @param availableVersions the available versions of the feature.
     * @param defaultVersion    the version of the feature that should be enabled by default.
     * @param enabledByDefault  `true` if the feature should be enabled by default, `false` if it should be
     *                          disabled by default.
     * @param overrideAllowed   `true` if this configuration can be overridden, e.g. by per-request feature
     *                          toggle configuration.
     * @param expirationDate    the expiration date of the feature.
     * @param developerEmails   one or more email addresses of developers who can be contacted about the feature.
     */
    case class FeatureToggleBaseConfig(featureName: String,
                                       description: String,
                                       availableVersions: Seq[Int],
                                       defaultVersion: Int,
                                       enabledByDefault: Boolean,
                                       overrideAllowed: Boolean,
                                       expirationDate: Option[Instant],
                                       developerEmails: Set[String])

}
