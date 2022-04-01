/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cacheservice.impl

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectIdentifierADM}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM}
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.store.cacheservice.settings.CacheServiceSettings
import org.knora.webapi.{TestContainerRedis, UnitSpec}

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect.{ignore, timeout}
import org.knora.webapi.store.cacheservice.api.CacheService
import org.knora.webapi.store.cacheservice.config.RedisConfig

/**
 * This spec is used to test [[org.knora.webapi.store.cacheservice.impl.CacheServiceRedisImpl]].
 * Adding the [[TestContainerRedis.PortConfig]] config will start the Redis container and make it
 * available to the test.
 */
object CacheRedisImplSpec extends DefaultRunnableSpec {

  StringFormatter.initForTest()
  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val user: UserADM       = SharedTestDataADM.imagesUser01
  private val project: ProjectADM = SharedTestDataADM.imagesProject

  def spec = suite("CacheRedisImplSpec")(
    test("successfully store a user and retrieve by IRI") {
      for {
        _             <- CacheService(_.putUserADM(user))
        retrievedUser <- CacheService(_.getUserADM(UserIdentifierADM(maybeIri = Some(user.id))))
      } yield assert(retrievedUser)(equalTo(Some(user)))
    } +
      test("successfully store a user and retrieve by USERNAME")(
        for {
          _             <- CacheService(_.putUserADM(user))
          retrievedUser <- CacheService(_.getUserADM(UserIdentifierADM(maybeUsername = Some(user.username))))
        } yield assert(retrievedUser)(equalTo(Some(user)))
      ) +
      test("successfully store a user and retrieve by EMAIL")(
        for {
          _             <- CacheService(_.putUserADM(user))
          retrievedUser <- CacheService(_.getUserADM(UserIdentifierADM(maybeEmail = Some(user.email))))
        } yield assert(retrievedUser)(equalTo(Some(user)))
      ) +
      test("successfully store a project and retrieve by IRI")(
        for {
          _                <- CacheService(_.putProjectADM(project))
          retrievedProject <- CacheService(_.getProjectADM(ProjectIdentifierADM(maybeIri = Some(project.id))))
        } yield assert(retrievedProject)(equalTo(Some(project)))
      ) +
      test("successfully store a project and retrieve by SHORTCODE")(
        for {
          _ <- CacheService(_.putProjectADM(project))
          retrievedProject <-
            CacheService(_.getProjectADM(ProjectIdentifierADM(maybeShortcode = Some(project.shortcode))))
        } yield assert(retrievedProject)(equalTo(Some(project)))
      ) +
      test("successfully store a project and retrieve by SHORTNAME")(
        for {
          _ <- CacheService(_.putProjectADM(project))
          retrievedProject <-
            CacheService(_.getProjectADM(ProjectIdentifierADM(maybeShortname = Some(project.shortname))))
        } yield assert(retrievedProject)(equalTo(Some(project)))
      )
  ).provideShared(CacheServiceRedisImpl.layer, RedisConfig.hardcoded)
}
