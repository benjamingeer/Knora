package dsp.config

import com.typesafe.config.ConfigFactory
import zio._
import zio.config._
import zio.config.magnolia.descriptor
import zio.config.typesafe.TypesafeConfigSource

import dsp.valueobjects.User._

/**
 * Configuration
 */
final case class AppConfig(
  dspApi: DspApi,
  bcryptPasswordStrength: PasswordStrength
)

final case class DspApi(
  internalHost: String,
  internalPort: Int,
  externalHost: String,
  externalPort: Int
)

object AppConfig {

  /**
   * Reads in the applicaton configuration using ZIO-Config. ZIO-Config is capable of loading
   * the Typesafe-Config format. Reads the 'app' configuration from 'application.conf'.
   */
  private val source: ConfigSource =
    TypesafeConfigSource.fromTypesafeConfig(ZIO.attempt(ConfigFactory.load().getConfig("app").resolve))

  /**
   * Instantiates the config class hierarchy using the data from the 'app' configuration from 'application.conf'.
   */
  private val configFromSource: IO[ReadError[String], AppConfig] = read(
    descriptor[AppConfig].mapKey(toKebabCase) from source
  )

  /**
   * Application configuration from application.conf
   */
  val live: ULayer[AppConfig] = ZLayer(configFromSource.orDie)
}
