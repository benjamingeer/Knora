/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.domain.AppState
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceStatusNOK
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceStatusOK
import org.knora.webapi.messages.store.sipimessages.IIIFServiceStatusNOK
import org.knora.webapi.messages.store.sipimessages.IIIFServiceStatusOK
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.util.cache.CacheUtil

/**
 * The application bootstrapper
 */
object AppServer {

  /**
   * Checks if the TriplestoreService is running and the repository is properly initialized.
   */
  private val checkTriplestoreService: ZIO[TriplestoreService with AppConfig, Nothing, Unit] =
    for {
      ts     <- ZIO.service[TriplestoreService]
      status <- ts.checkTriplestore().map(_.triplestoreStatus)
      _ <- status match {
             case TriplestoreStatus.Available(msg)      => ZIO.logInfo(msg)
             case TriplestoreStatus.NotInitialized(msg) => ZIO.die(new Exception(msg))
             case TriplestoreStatus.Unavailable(msg)    => ZIO.die(new Exception(msg))
           }
    } yield ()

  /**
   * Initiates repository upgrade if `requiresRepository` is `true` an logs the result.
   */
  private def upgradeRepository(requiresRepository: Boolean): ZIO[RepositoryUpdater, Nothing, Unit] =
    if (requiresRepository)
      ZIO
        .service[RepositoryUpdater]
        .flatMap(svc => svc.maybeUpgradeRepository)
        .flatMap(response => ZIO.logInfo(response.message))
    else
      ZIO.unit

  /* Initiates building of all caches */
  private val buildAllCaches: ZIO[ActorSystem, Nothing, Unit] =
    ZIO
      .service[ActorSystem]
      .map(_.settings)
      .flatMap(settings => ZIO.attempt(CacheUtil.createCaches(settings.caches)))
      .orDie

  /* Initiates population of the ontology caches if `requiresRepository` is `true` */
  private def populateOntologyCaches(requiresRepository: Boolean): ZIO[AppRouter, Nothing, Unit] =
    if (requiresRepository)
      ZIO
        .service[AppRouter]
        .flatMap(svc => svc.populateOntologyCaches)
    else
      ZIO.unit

  /* Checks if the IIIF service is running */
  private def checkIIIFService(requiresIIIFService: Boolean): ZIO[IIIFService, Nothing, Unit] =
    if (requiresIIIFService)
      ZIO
        .service[IIIFService]
        .flatMap(svc => svc.getStatus())
        .flatMap(status =>
          status match {
            case IIIFServiceStatusOK =>
              ZIO.logInfo("IIIF service running")
            case IIIFServiceStatusNOK =>
              ZIO.logError("IIIF service not running") *> ZIO.die(new Exception("IIIF service not running"))
          }
        )
    else
      ZIO.unit

  /* Checks if the Cache service is running */
  private val checkCacheService =
    ZIO
      .service[CacheService]
      .flatMap(svc => svc.getStatus)
      .flatMap(status =>
        status match {
          case CacheServiceStatusNOK =>
            ZIO.logError("Cache service not running.") *> ZIO.die(new Exception("Cache service not running."))
          case CacheServiceStatusOK =>
            ZIO.logInfo("Cache service running.")
        }
      )

  /**
   * Prints the welcome message
   */
  private val printBanner: ZIO[AppConfig with HttpServer, Nothing, Unit] =
    for {
      config <- ZIO.service[AppConfig]
      server <- ZIO.service[HttpServer]
      _ <-
        ZIO.logInfo(
          s"DSP-API Server started: ${config.knoraApi.internalKnoraApiBaseUrl}"
        )

      _ = if (config.allowReloadOverHttp) {
            ZIO.logWarning("Resetting DB over HTTP is turned ON")
          }
    } yield ()

  /**
   * Initiates the startup sequence of the DSP-API server.
   *
   * @param requiresRepository  if `true`, check if it is running, run upgrading and loading ontology cache.
   *                                If `false`, check if it is running but don't run upgrading AND loading ontology cache.
   * @param requiresIIIFService if `true`, ensure that the IIIF service is started.
   */
  def start(
    requiresRepository: Boolean,
    requiresIIIFService: Boolean
  ) =
    for {
      state <- ZIO.service[State]
      _     <- state.set(AppState.WaitingForTriplestore)
      _     <- checkTriplestoreService
      _     <- state.set(AppState.TriplestoreReady)
      _     <- state.set(AppState.UpdatingRepository)
      _     <- upgradeRepository(requiresRepository)
      _     <- state.set(AppState.RepositoryUpToDate)
      _     <- state.set(AppState.CreatingCaches)
      _     <- buildAllCaches
      _     <- state.set(AppState.CachesReady)
      _     <- state.set(AppState.LoadingOntologies)
      _     <- populateOntologyCaches(requiresRepository)
      _     <- state.set(AppState.OntologiesReady)
      _     <- state.set(AppState.WaitingForIIIFService)
      _     <- checkIIIFService(requiresIIIFService)
      _     <- state.set(AppState.IIIFServiceReady)
      _     <- state.set(AppState.WaitingForCacheService)
      _     <- checkCacheService
      _     <- state.set(AppState.CacheServiceReady)
      _     <- printBanner
      _     <- state.set(AppState.Running)
    } yield ()
}
