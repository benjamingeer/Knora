package org.knora.webapi.testcontainers

import org.knora.webapi.http.version.BuildInfo
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import zio._

import java.net.NetworkInterface
import java.net.UnknownHostException
import scala.jdk.CollectionConverters._

final case class SipiTestContainer(container: GenericContainer[Nothing])

object SipiTestContainer {

  /**
   * A functional effect that initiates a Sipi Testcontainer
   */
  val aquire: Task[GenericContainer[Nothing]] = ZIO.attemptBlocking {
    // get local IP address, which we need for SIPI
    val localIpAddress: String = NetworkInterface.getNetworkInterfaces.asScala.toSeq
      .filter(!_.isLoopback)
      .flatMap(_.getInetAddresses.asScala.toSeq.filter(_.getAddress.length == 4).map(_.toString))
      .headOption
      .getOrElse(throw new UnknownHostException("No suitable network interface found"))

    val sipiImageName: DockerImageName = DockerImageName.parse(s"daschswiss/knora-sipi:${BuildInfo.version}")
    val sipiContainer                  = new GenericContainer(sipiImageName)
    sipiContainer.withExposedPorts(1024)
    sipiContainer.withEnv("KNORA_WEBAPI_KNORA_API_EXTERNAL_HOST", "0.0.0.0")
    sipiContainer.withEnv("KNORA_WEBAPI_KNORA_API_EXTERNAL_PORT", "3333")
    sipiContainer.withEnv("SIPI_EXTERNAL_PROTOCOL", "http")
    sipiContainer.withEnv("SIPI_EXTERNAL_HOSTNAME", "0.0.0.0")
    sipiContainer.withEnv("SIPI_EXTERNAL_PORT", "1024")
    sipiContainer.withEnv("SIPI_WEBAPI_HOSTNAME", localIpAddress)
    sipiContainer.withEnv("SIPI_WEBAPI_PORT", "3333")

    sipiContainer.withCommand("--config=/sipi/config/sipi.docker-config.lua")

    // TODO: Needs https://github.com/scalameta/metals/issues/3623 to be resolved
    sipiContainer.withClasspathResourceMapping(
      // "/sipi/config/sipi.docker-config.lua"
      "/sipi.docker-config.lua",
      "/sipi/config/sipi.docker-config.lua",
      BindMode.READ_ONLY
    )
    sipiContainer.start()
    sipiContainer
  }.orDie.tap(_ => ZIO.debug(">>> Aquire Sipi TestContainer executed <<<"))

  def release(container: GenericContainer[Nothing]): URIO[Any, Unit] = ZIO.attemptBlocking {
    container.stop()
  }.orDie.tap(_ => ZIO.debug(">>> Release Sipi TestContainer executed <<<"))

  val layer: ZLayer[Any, Nothing, SipiTestContainer] = {
    ZLayer {
      for {
        // tc <- ZIO.acquireRelease(aquire)(release(_)).orDie
        tc <- aquire.orDie
      } yield SipiTestContainer(tc)
    }.tap(_ => ZIO.debug(">>> Sipi Test Container Initialized <<<"))
  }
}
