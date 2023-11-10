/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache.api

import zio.*
import zio.macros.accessible

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceStatusResponse

/**
 * Cache Service Interface
 */
@accessible
trait CacheService {
  def putUserADM(value: UserADM): Task[Unit]
  def getUserADM(identifier: UserIdentifierADM): Task[Option[UserADM]]
  def putProjectADM(value: ProjectADM): Task[Unit]
  def getProjectADM(identifier: ProjectIdentifierADM): Task[Option[ProjectADM]]
  def putStringValue(key: String, value: String): Task[Unit]
  def getStringValue(key: String): Task[Option[String]]
  def removeValues(keys: Set[String]): Task[Unit]
  def flushDB(requestingUser: UserADM): Task[Unit]
  val getStatus: UIO[CacheServiceStatusResponse]
}

/**
 * Cache Service companion object using [[Accessible]].
 * To use, simply call `Companion(_.someMethod)`, to return a ZIO
 * effect that requires the Service in its environment.
 *
 * Example:
 * {{{
 *   trait CacheService {
 *     def ping(): Task[CacheServiceStatusResponse]
 *   }
 *
 *   object CacheService extends Accessible[CacheService]
 *
 *   val example: ZIO[CacheService, Nothing, Unit] =
 *     for {
 *       _  <- CacheService(_.ping())
 *     } yield ()
 * }}}
 */
