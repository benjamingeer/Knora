/*
 * Copyright © 2021 - 2023 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.role.sharedtestdata

import java.util.UUID

import dsp.role.domain.Role
import dsp.role.domain.RoleUser
import dsp.valueobjects.Id
import dsp.valueobjects.Permission
import dsp.valueobjects.Role._

/**
 * Contains shared role test data.
 */
object RoleTestData {
  val uuid1 = UUID.randomUUID()
  val uuid2 = UUID.randomUUID()

  val id1          = Id.RoleId.make()
  val name1        = LangString.make("Name", "en")
  val description1 = LangString.make("Description", "en")
  val users1       = List(RoleUser(Id.UserId.make(uuid1).fold(e => throw e.head, v => v)))
  val permission1  = Permission.make(Permission.View)

  val role1 = for {
    id          <- id1
    name        <- name1
    description <- description1
    permission  <- permission1

    role <- Role.make(
              id,
              name,
              description,
              users = users1,
              permission
            )
  } yield role

  val id2          = Id.RoleId.make()
  val name2        = LangString.make("Name 2", "en")
  val description2 = LangString.make("Description 2", "en")
  val users2       = List(RoleUser(Id.UserId.make(uuid2).fold(e => throw e.head, v => v)))
  val permission2  = Permission.make(Permission.Admin)

  val role2 = for {
    id          <- id2
    name        <- name2
    description <- description2
    permission  <- permission2

    role <- Role.make(
              id,
              name,
              description,
              users = users2,
              permission
            )
  } yield role
}
