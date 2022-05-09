/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cacheservice.impl

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.store.cacheservice.api.CacheService
import zio.ZLayer
import zio.test.Assertion._
import zio.test._

/**
 * This spec is used to test [[org.knora.webapi.store.cacheservice.impl.CacheServiceInMemImpl]].
 */
object CacheInMemImplSpec extends ZIOSpec[CacheService] {

  StringFormatter.initForTest()
  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val user: UserADM = SharedTestDataADM.imagesUser01
  private val userWithApostrophe = UserADM(
    id = "http://rdfh.ch/users/aaaaaab71e7b0e01",
    username = "user_with_apostrophe",
    email = "userWithApostrophe@example.org",
    givenName = """M\\"Given 'Name""",
    familyName = """M\\tFamily Name""",
    status = true,
    lang = "en"
  )

  private val project: ProjectADM = SharedTestDataADM.imagesProject

  /**
   * Defines a layer which encompases all dependencies that are needed for
   * running the tests. `bootstrap` overrides the base layer of ZIOApp.
   */
  val bootstrap = ZLayer.make[CacheService](CacheServiceInMemImpl.layer)

  def spec = (userTests + projectTests + otherTests)

  val userTests = suite("CacheInMemImplSpec - user")(
    test("successfully store a user and retrieve by IRI") {
      for {
        _             <- CacheService.putUserADM(user)
        retrievedUser <- CacheService.getUserADM(UserIdentifierADM(maybeIri = Some(user.id)))
      } yield assertTrue(retrievedUser == Some(user))
    } +
      test("successfully store a user and retrieve by USERNAME")(
        for {
          _             <- CacheService.putUserADM(user)
          retrievedUser <- CacheService.getUserADM(UserIdentifierADM(maybeUsername = Some(user.username)))
        } yield assert(retrievedUser)(equalTo(Some(user)))
      ) +
      test("successfully store a user and retrieve by EMAIL")(
        for {
          _             <- CacheService.putUserADM(user)
          retrievedUser <- CacheService.getUserADM(UserIdentifierADM(maybeEmail = Some(user.email)))
        } yield assert(retrievedUser)(equalTo(Some(user)))
      ) +
      test("successfully store and retrieve a user with special characters in his name")(
        for {
          _             <- CacheService.putUserADM(userWithApostrophe)
          retrievedUser <- CacheService.getUserADM(UserIdentifierADM(maybeIri = Some(userWithApostrophe.id)))
        } yield assert(retrievedUser)(equalTo(Some(userWithApostrophe)))
      )
  )

  val projectTests = suite("CacheInMemImplSpec - project")(
    test("successfully store a project and retrieve by IRI")(
      for {
        _                <- CacheService.putProjectADM(project)
        retrievedProject <- CacheService.getProjectADM(ProjectIdentifierADM(maybeIri = Some(project.id)))
      } yield assert(retrievedProject)(equalTo(Some(project)))
    ) +
      test("successfully store a project and retrieve by SHORTCODE")(
        for {
          _ <- CacheService.putProjectADM(project)
          retrievedProject <-
            CacheService.getProjectADM(ProjectIdentifierADM(maybeShortcode = Some(project.shortcode)))
        } yield assert(retrievedProject)(equalTo(Some(project)))
      ) +
      test("successfully store a project and retrieve by SHORTNAME")(
        for {
          _ <- CacheService.putProjectADM(project)
          retrievedProject <-
            CacheService.getProjectADM(ProjectIdentifierADM(maybeShortname = Some(project.shortname)))
        } yield assert(retrievedProject)(equalTo(Some(project)))
      )
  )

  val otherTests = suite("CacheInMemImplSpec - other")(
    test("successfully store string value")(
      for {
        _              <- CacheService.putStringValue("my-new-key", "my-new-value")
        retrievedValue <- CacheService.getStringValue("my-new-key")
      } yield assert(retrievedValue)(equalTo(Some("my-new-value")))
    ) +
      test("successfully delete stored value")(
        for {
          _              <- CacheService.putStringValue("my-new-key", "my-new-value")
          _              <- CacheService.removeValues(Set("my-new-key"))
          retrievedValue <- CacheService.getStringValue("my-new-key")
        } yield assert(retrievedValue)(equalTo(None))
      )
  )
}
