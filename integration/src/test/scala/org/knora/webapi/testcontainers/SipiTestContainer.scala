/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testcontainers

import org.knora.webapi.http.version.BuildInfo
import org.knora.webapi.testcontainers.TestContainerOps.StartableOps
import org.testcontainers.containers.{BindMode, GenericContainer}
import org.testcontainers.utility.MountableFile
import zio.http.URL
import zio.nio.file.Path
import zio.{Task, URIO, URLayer, ZIO, ZLayer, http}

import java.net.{Inet6Address, InetAddress}

final class SipiTestContainer
    extends GenericContainer[SipiTestContainer](s"daschswiss/knora-sipi:${BuildInfo.version}") {

  def copyFileToImageFolderInContainer(prefix: String, filename: String): Task[Unit] = {
    val seg01  = filename.substring(0, 2).toLowerCase()
    val seg02  = filename.substring(2, 4).toLowerCase()
    val target = Path(s"/sipi/images/$prefix/$seg01/$seg02/$filename")
    copyTestFileToContainer(filename, target)
  }

  def copyTestFileToContainer(file: String, target: Path): Task[Unit] = {
    val resourceName  = s"sipi/testfiles/$file"
    val mountableFile = MountableFile.forClasspathResource(resourceName, 777)
    ZIO.attemptBlockingIO(copyFileToContainer(mountableFile, target.toFile.toString)) <* ZIO.logInfo(
      s"copied $resourceName to $target"
    )
  }

  def sipiBaseUrl: URL = {
    val urlString = s"http://${SipiTestContainer.localHostAddress}:$getFirstMappedPort"
    println(s"SIPI URL String: $urlString")
    val url = URL.decode(urlString).getOrElse(throw new IllegalStateException(s"Invalid URL $urlString"))
    println(s"SIPI URL: $url")
    url
  }
}

object SipiTestContainer {

  private val imagesDir = "/sipi/images"

  val localHostAddress: String = {
    val localhost = InetAddress.getLocalHost
    if (localhost.isInstanceOf[Inet6Address]) {
      s"[${localhost.getHostAddress}]"
    } else {
      localhost.getHostAddress
    }
  }

  def portAndHost: ZIO[SipiTestContainer, Nothing, (Int, String)] =
    ZIO.serviceWith[SipiTestContainer](c => (c.getFirstMappedPort, localHostAddress))

  def resolveUrl(path: http.Path): URIO[SipiTestContainer, URL] =
    ZIO.serviceWith[SipiTestContainer](_.sipiBaseUrl.path(path))

  def copyFileToImageFolderInContainer(prefix: String, filename: String): ZIO[SipiTestContainer, Throwable, Unit] =
    ZIO.serviceWithZIO[SipiTestContainer](_.copyFileToImageFolderInContainer(prefix, filename))

  def copyTestFileToContainer(file: String, target: Path): ZIO[SipiTestContainer, Throwable, Unit] =
    ZIO.serviceWithZIO[SipiTestContainer](_.copyTestFileToContainer(file, target))

  def make(imagesVolume: SharedVolumes.Images): SipiTestContainer =
    new SipiTestContainer()
      .withExposedPorts(1024)
      .withEnv("KNORA_WEBAPI_KNORA_API_EXTERNAL_HOST", "0.0.0.0")
      .withEnv("KNORA_WEBAPI_KNORA_API_EXTERNAL_PORT", "3333")
      .withEnv("SIPI_EXTERNAL_PROTOCOL", "http")
      .withEnv("SIPI_EXTERNAL_HOSTNAME", "0.0.0.0")
      .withEnv("SIPI_EXTERNAL_PORT", "1024")
      .withEnv("SIPI_WEBAPI_HOSTNAME", SipiTestContainer.localHostAddress)
      .withEnv("SIPI_WEBAPI_PORT", "3333")
      .withEnv("CLEAN_TMP_DIR_USER", "clean_tmp_dir_user")
      .withEnv("CLEAN_TMP_DIR_PW", "clean_tmp_dir_pw")
      .withCommand("--config=/sipi/config/sipi.docker-config.lua")
      .withClasspathResourceMapping(
        "/sipi.docker-config.lua",
        "/sipi/config/sipi.docker-config.lua",
        BindMode.READ_ONLY
      )
      .withFileSystemBind(imagesVolume.hostPath, imagesDir, BindMode.READ_WRITE)
      .withLogConsumer(frame => print("SIPI:" + frame.getUtf8String))

  private val initSipi = ZLayer.fromZIO(
    for {
      container <- ZIO.service[SipiTestContainer]
      _ <- ZIO.attemptBlocking {
             container.execInContainer("mkdir", s"$imagesDir/tmp")
             container.execInContainer("chmod", "777", s"$imagesDir/tmp")
           }

    } yield container
  )

  val layer: URLayer[SharedVolumes.Images, SipiTestContainer] = {
    val container = ZLayer.scoped(ZIO.service[SharedVolumes.Images].flatMap(make(_).toZio))
    (container >>> initSipi).orDie
  }
}
