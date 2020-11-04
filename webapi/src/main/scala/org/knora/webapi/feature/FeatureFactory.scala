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

package org.knora.webapi.feature

import akka.http.scaladsl.model.{HttpHeader, HttpResponse}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.RequestContext
import org.knora.webapi.exceptions.{BadRequestException, FeatureToggleException}
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.settings.KnoraSettings.FeatureToggleBaseConfig
import org.knora.webapi.settings.KnoraSettingsImpl

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

/**
 * A tagging trait for module-specific factories that produce implementations of features.
 */
trait FeatureFactory

/**
 * A tagging trait for classes that implement features returned by feature factories.
 */
trait Feature

/**
 * A tagging trait for case objects representing feature versions.
 */
trait Version

/**
 * A trait representing the state of a feature toggle.
 */
sealed trait FeatureToggleState

/**
 * Indicates that a feature toggle is off.
 */
case object Off extends FeatureToggleState

/**
 * Indicates that a feature toggle is on.
 *
 * @param version the configured version of the toggle.
 */
case class On(version: Int) extends FeatureToggleState

/**
 * Represents a feature toggle.
 *
 * @param featureName the name of the feature toggle.
 * @param state       the state of the feature toggle.
 */
case class FeatureToggle(featureName: String,
                         state: FeatureToggleState) {

    /**
     * Returns `true` if this feature toggle is enabled.
     */
    def isEnabled: Boolean = {
        state match {
            case On(_) => true
            case Off => false
        }
    }

    /**
     * Gets a required version number, checks that it is a supported version, and converts it to
     * a case object for use in matching.
     *
     * @param versionObjects case objects representing the supported versions of the feature, in ascending
     *                       order by version number.
     * @tparam T a sealed trait implemented by the case objects that represent supported versions of the feature.
     * @return the version number.
     */
    def checkVersion[T <: Version](versionObjects: T*): T = {
        state match {
            case Off => throw FeatureToggleException(s"Feature toggle $featureName is not enabled")

            case On(version) =>
                if (version < 1 || version > versionObjects.size) {
                    throw FeatureToggleException(s"Invalid version number $version for toggle $featureName")
                }

                // Return the case object whose position in the sequence corresponds to the configured version.
                // This relies on the fact that version numbers must be an ascending sequence of consecutive
                // integers starting from 1.
                versionObjects(version - 1)
        }
    }
}

object FeatureToggle {
    /**
     * The name of the HTTP request header containing feature toggles.
     */
    val REQUEST_HEADER: String = "X-Knora-Feature-Toggles"
    val REQUEST_HEADER_LOWERCASE: String = REQUEST_HEADER.toLowerCase

    /**
     * The name of the HTTP response header indicating which feature toggles
     * are enabled.
     */
    val RESPONSE_HEADER: String = "X-Knora-Feature-Toggles-Enabled"
    val RESPONSE_HEADER_LOWERCASE: String = RESPONSE_HEADER.toLowerCase

    /**
     * Constructs a default [[FeatureToggle]] from a [[FeatureToggleBaseConfig]].
     *
     * @param baseConfig a feature toggle's base configuration.
     * @return a [[FeatureToggle]] representing the feature's default setting.
     */
    def fromBaseConfig(baseConfig: FeatureToggleBaseConfig): FeatureToggle = {
        FeatureToggle(
            featureName = baseConfig.featureName,
            state = if (baseConfig.enabledByDefault) {
                On(baseConfig.defaultVersion)
            } else {
                Off
            }
        )
    }

    /**
     * Constructs a feature toggle from non-base configuration.
     *
     * @param featureName  the name of the feature.
     * @param isEnabled    `true` if the feature should be enabled.
     * @param maybeVersion the version of the feature that should be used.
     * @param baseConfig   the base configuration of the toggle.
     * @return a [[FeatureToggle]] for the toggle.
     */
    def apply(featureName: String,
              isEnabled: Boolean,
              maybeVersion: Option[Int],
              baseConfig: FeatureToggleBaseConfig)(implicit stringFormatter: StringFormatter): FeatureToggle = {
        if (!baseConfig.overrideAllowed) {
            throw BadRequestException(s"Feature toggle $featureName cannot be overridden")
        }

        for (version: Int <- maybeVersion) {
            if (!baseConfig.availableVersions.contains(version)) {
                throw BadRequestException(s"Feature toggle $featureName has no version $version")
            }
        }

        val state: FeatureToggleState = (isEnabled, maybeVersion) match {
            case (true, Some(definedVersion)) => On(definedVersion)
            case (false, None) => Off
            case (true, None) => throw BadRequestException(s"You must specify a version number to enable feature toggle $featureName")
            case (false, Some(_)) => throw BadRequestException(s"You cannot specify a version number when disabling feature toggle $featureName")
        }

        FeatureToggle(
            featureName = featureName,
            state = state
        )
    }
}

/**
 * An abstract class representing configuration for a [[FeatureFactory]] from a particular
 * configuration source.
 *
 * @param maybeParent if this [[FeatureFactoryConfig]] has no setting for a particular
 *                    feature toggle, it delegates to its parent.
 */
abstract class FeatureFactoryConfig(protected val maybeParent: Option[FeatureFactoryConfig]) {
    /**
     * Gets the base configuration for a feature toggle.
     *
     * @param featureName the name of the feature.
     * @return the toggle's base configuration.
     */
    protected[feature] def getBaseConfig(featureName: String): FeatureToggleBaseConfig

    /**
     * Gets the base configurations of all feature toggles.
     */
    protected[feature] def getAllBaseConfigs: Set[FeatureToggleBaseConfig]

    /**
     * Returns a feature toggle in the configuration source of this [[FeatureFactoryConfig]].
     *
     * @param featureName the name of a feature.
     * @return the configuration of the feature toggle in this [[FeatureFactoryConfig]]'s configuration
     *         source, or `None` if the source contains no configuration for that feature toggle.
     */
    protected[feature] def getLocalConfig(featureName: String): Option[FeatureToggle]

    /**
     * Returns an [[HttpHeader]] indicating which feature toggles are enabled.
     */
    def getHttpResponseHeader: Option[HttpHeader] = {
        // Get the set of toggles that are enabled.
        val enabledToggles: Set[String] = getAllBaseConfigs.map {
            baseConfig: FeatureToggleBaseConfig => getToggle(baseConfig.featureName)
        }.foldLeft(Set.empty[String]) {
            case (enabledToggles, featureToggle) =>
                featureToggle.state match {
                    case On(version) => enabledToggles + s"${featureToggle.featureName}:$version"
                    case Off => enabledToggles
                }
        }

        // Are any toggles enabled?
        if (enabledToggles.nonEmpty) {
            // Yes. Return a header.
            Some(RawHeader(FeatureToggle.RESPONSE_HEADER, enabledToggles.mkString(",")))
        } else {
            // No. Don't return a header.
            None
        }
    }

    /**
     * Adds an [[HttpHeader]] to an [[HttpResponse]] indicating which feature toggles are enabled.
     */
    def addHeaderToHttpResponse(httpResponse: HttpResponse): HttpResponse = {
        getHttpResponseHeader match {
            case Some(header) => httpResponse.withHeaders(header)
            case None => httpResponse
        }
    }

    /**
     * Returns a feature toggle, taking into account the base configuration
     * and the parent configuration.
     *
     * @param featureName the name of the feature.
     * @return the feature toggle.
     */
    @tailrec
    final def getToggle(featureName: String): FeatureToggle = {
        // Get the base configuration for the feature.
        val baseConfig: FeatureToggleBaseConfig = getBaseConfig(featureName)

        // Do we represent the base configuration?
        maybeParent match {
            case None =>
                // Yes. Return our setting.
                FeatureToggle.fromBaseConfig(baseConfig)

            case Some(parent) =>
                // No. Can the default setting be overridden?
                if (baseConfig.overrideAllowed) {
                    // Yes. Do we have a setting for this feature?
                    getLocalConfig(featureName) match {
                        case Some(setting) =>
                            // Yes. Return our setting.
                            setting

                        case None =>
                            // We don't have a setting for this feature. Delegate to the parent.
                            parent.getToggle(featureName)
                    }
                } else {
                    // The default setting can't be overridden. Return it.
                    FeatureToggle.fromBaseConfig(baseConfig)
                }
        }
    }
}

/**
 * A [[FeatureFactoryConfig]] that reads configuration from the application's configuration file.
 *
 * @param knoraSettings a [[KnoraSettingsImpl]] representing the configuration in the application's
 *                      configuration file.
 */
class KnoraSettingsFeatureFactoryConfig(knoraSettings: KnoraSettingsImpl) extends FeatureFactoryConfig(None) {
    private val baseConfigs: Map[String, FeatureToggleBaseConfig] = knoraSettings.featureToggles.map {
        baseConfig => baseConfig.featureName -> baseConfig
    }.toMap

    override protected[feature] def getBaseConfig(featureName: String): FeatureToggleBaseConfig = {
        baseConfigs.getOrElse(featureName, throw BadRequestException(s"No such feature: $featureName"))
    }

    override protected[feature] def getAllBaseConfigs: Set[FeatureToggleBaseConfig] = {
        baseConfigs.values.toSet
    }

    override protected[feature] def getLocalConfig(featureName: String): Option[FeatureToggle] = {
        Some(FeatureToggle.fromBaseConfig(getBaseConfig(featureName)))
    }
}

/**
 * An abstract class for feature factory configs that don't represent the base configuration.
 *
 * @param parent the parent config.
 */
abstract class OverridingFeatureFactoryConfig(parent: FeatureFactoryConfig) extends FeatureFactoryConfig(Some(parent)) {
    protected val featureToggles: Map[String, FeatureToggle]

    override protected[feature] def getBaseConfig(featureName: String): FeatureToggleBaseConfig = {
        parent.getBaseConfig(featureName)
    }

    override protected[feature] def getAllBaseConfigs: Set[FeatureToggleBaseConfig] = {
        parent.getAllBaseConfigs
    }

    override protected[feature] def getLocalConfig(featureName: String): Option[FeatureToggle] = {
        featureToggles.get(featureName)
    }
}

object RequestContextFeatureFactoryConfig {
    // Strings that we accept as Boolean true values.
    val TRUE_STRINGS: Set[String] = Set("true", "yes", "on")

    // Strings that we accept as Boolean false values.
    val FALSE_STRINGS: Set[String] = Set("false", "no", "off")
}

/**
 * A [[FeatureFactoryConfig]] that reads configuration from a header in an HTTP request.
 *
 * @param requestContext the HTTP request context.
 * @param parent         the parent [[FeatureFactoryConfig]].
 */
class RequestContextFeatureFactoryConfig(requestContext: RequestContext,
                                         parent: FeatureFactoryConfig)(implicit stringFormatter: StringFormatter) extends OverridingFeatureFactoryConfig(parent) {

    import FeatureToggle._
    import RequestContextFeatureFactoryConfig._

    private def invalidHeaderValue: Nothing = throw BadRequestException(s"Invalid value for header $REQUEST_HEADER")

    // Read feature toggles from an HTTP header.
    protected override val featureToggles: Map[String, FeatureToggle] = Try {
        // Was the feature toggle header submitted?
        requestContext.request.headers.find(_.lowercaseName == REQUEST_HEADER_LOWERCASE) match {
            case Some(featureToggleHeader: HttpHeader) =>
                // Yes. Parse it into comma-separated key-value pairs, each representing a feature toggle.
                featureToggleHeader.value.split(',').map {
                    headerValueItem: String =>
                        headerValueItem.split('=').map(_.trim) match {
                            case Array(featureNameAndVersionStr: String, isEnabledStr: String) =>
                                val featureNameAndVersion: Array[String] = featureNameAndVersionStr.split(':').map(_.trim)
                                val featureName: String = featureNameAndVersion.head

                                // Accept the boolean values that are accepted in application.conf.
                                val isEnabled: Boolean = if (TRUE_STRINGS.contains(isEnabledStr.toLowerCase)) {
                                    true
                                } else if (FALSE_STRINGS.contains(isEnabledStr.toLowerCase)) {
                                    false
                                } else {
                                    throw BadRequestException(s"Invalid boolean '$isEnabledStr' in feature toggle $featureName")
                                }

                                val maybeVersion: Option[Int] = featureNameAndVersion.drop(1).headOption.map {
                                    versionStr => stringFormatter.validateInt(versionStr, throw BadRequestException(s"Invalid version number '$versionStr' in feature toggle $featureName"))
                                }

                                featureName -> FeatureToggle(
                                    featureName = featureName,
                                    isEnabled = isEnabled,
                                    maybeVersion = maybeVersion,
                                    baseConfig = parent.getBaseConfig(featureName)
                                )

                            case _ => invalidHeaderValue
                        }
                }.toMap

            case None =>
                // No feature toggle header was submitted.
                Map.empty[String, FeatureToggle]
        }
    } match {
        case Success(parsedToggles) => parsedToggles

        case Failure(ex) =>
            ex match {
                case badRequest: BadRequestException => throw badRequest
                case _ => invalidHeaderValue
            }
    }
}

/**
 * A [[FeatureFactoryConfig]] with a fixed configuration, to be used in tests.
 *
 * @param testToggles the toggles to be used.
 */
class TestFeatureFactoryConfig(testToggles: Set[FeatureToggle], parent: FeatureFactoryConfig) extends OverridingFeatureFactoryConfig(parent) {
    protected override val featureToggles: Map[String, FeatureToggle] = testToggles.map {
        setting => setting.featureName -> setting
    }.toMap
}
