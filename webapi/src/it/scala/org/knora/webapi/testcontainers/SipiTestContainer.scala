package org.knora.webapi.testcontainers

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import zio._

import java.net.NetworkInterface
import java.net.UnknownHostException
import java.nio.file.Paths
import scala.jdk.CollectionConverters._

import org.knora.webapi.http.version.BuildInfo

final case class SipiTestContainer(container: GenericContainer[Nothing])

object SipiTestContainer {

  /**
   * A functional effect that initiates a Sipi Testcontainer
   */
  val acquire: UIO[GenericContainer[Nothing]] = ZIO.attemptBlocking {
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
    sipiContainer.withEnv("CLEAN_TMP_DIR_USER", "clean_tmp_dir_user")
    sipiContainer.withEnv("CLEAN_TMP_DIR_PW", "clean_tmp_dir_pw")

    sipiContainer.withCommand("--config=/sipi/config/sipi.docker-config.lua")

    sipiContainer.withClasspathResourceMapping(
      "/sipi.docker-config.lua",
      "/sipi/config/sipi.docker-config.lua",
      BindMode.READ_ONLY
    )

    val incunabulaImageDirPath =
      Paths.get("..", "sipi/images/0803/in/cu/incunabula_0000000002.jp2")
    sipiContainer.withFileSystemBind(
      incunabulaImageDirPath.toString(),
      "/sipi/images/0803/in/cu/incunabula_0000000002.jp2",
      BindMode.READ_ONLY
    )

    sipiContainer.start()

    // Create '/sipi/images/tmp' folder inside running container
    sipiContainer.execInContainer("mkdir", "/sipi/images/tmp")
    sipiContainer.execInContainer("chmod", "777", "/sipi/images/tmp")

    sipiContainer
  }.orDie.zipLeft(ZIO.logInfo(">>> Acquire Sipi TestContainer <<<"))

  def release(container: GenericContainer[Nothing]): UIO[Unit] = ZIO.attemptBlocking {
    container.stop()
  }.orDie.zipLeft(ZIO.logInfo(">>> Release Sipi TestContainer <<<"))

  val layer: ZLayer[Any, Nothing, SipiTestContainer] =
    ZLayer.scoped {
      for {
        tc <- ZIO.acquireRelease(acquire)(release)
      } yield SipiTestContainer(tc)
    }
}
