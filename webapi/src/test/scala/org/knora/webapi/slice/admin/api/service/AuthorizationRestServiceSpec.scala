/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.Exit
import zio.ZIO
import zio.test.*
import zio.test.Assertion.failsWithA

import dsp.errors.ForbiddenException
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.ProjectAdmin
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.SystemAdmin
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.SystemProject
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.repo.KnoraProjectRepoInMemory
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoInMemory
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.AuthorizationRestServiceLive

object AuthorizationRestServiceSpec extends ZIOSpecDefault {

  private val activeNormalUser =
    User("http://iri", "username", "email@example.com", "given name", "family name", status = true, "lang")

  private val inactiveNormalUser = activeNormalUser.copy(status = false)

  private val activeSystemAdmin =
    activeNormalUser.copy(permissions = PermissionsDataADM(Map(SystemProject -> List(SystemAdmin))))

  private val inactiveSystemAdmin = activeSystemAdmin.copy(status = false)

  val spec: Spec[Any, Any] = suite("RestPermissionService")(
    suite("given an inactive system admin")(
      test("isSystemAdmin should return true") {
        assertTrue(AuthorizationRestService.isSystemAdmin(inactiveSystemAdmin))
      },
      test("when ensureSystemAdmin fail with a ForbiddenException") {
        for {
          actual <- AuthorizationRestService.ensureSystemAdmin(inactiveSystemAdmin).exit
        } yield assertTrue(
          actual == Exit.fail(ForbiddenException("The account with username 'username' is not active.")),
        )
      },
    ),
    suite("given a active system admin")(
      test("isSystemAdmin should return true") {
        assertTrue(AuthorizationRestService.isSystemAdmin(activeSystemAdmin))
      },
      test("when ensureSystemAdmin succeed") {
        for {
          _ <- AuthorizationRestService.ensureSystemAdmin(activeSystemAdmin)
        } yield assertCompletes
      },
    ),
    suite("given an inactive normal user")(
      test("isSystemAdmin should return false") {
        assertTrue(!AuthorizationRestService.isSystemAdmin(inactiveNormalUser))
      },
      test("when ensureSystemAdmin fail with a ForbiddenException") {
        for {
          actual <- AuthorizationRestService.ensureSystemAdmin(inactiveNormalUser).exit
        } yield assertTrue(
          actual == Exit.fail(ForbiddenException("The account with username 'username' is not active.")),
        )
      },
    ),
    suite("given an active normal user")(
      test("isSystemAdmin should return false") {
        assertTrue(!AuthorizationRestService.isSystemAdmin(activeNormalUser))
      },
      test("when ensureSystemAdmin fail with a ForbiddenException") {
        for {
          actual <- AuthorizationRestService.ensureSystemAdmin(activeNormalUser).exit
        } yield assertTrue(
          actual == Exit.fail(
            ForbiddenException(
              "You are logged in with username 'username', but only a system administrator has permissions for this operation.",
            ),
          ),
        )
      },
      test(
        "and given a project for which the user is project admin when ensureSystemAdminOrProjectAdmin then succeed",
      ) {
        val project = TestDataFactory.someProject
        for {
          _ <- ZIO.serviceWithZIO[KnoraProjectRepoInMemory](_.save(project))
          userIsAdmin =
            activeNormalUser.copy(permissions = PermissionsDataADM(Map(project.id.value -> List(ProjectAdmin))))
          actualProject <- AuthorizationRestService.ensureSystemAdminOrProjectAdmin(userIsAdmin, project.id)
        } yield assertTrue(project == actualProject)
      },
      test(
        "and given the project does not exists for which the user is project admin when ensureSystemAdminOrProjectAdmin then succeed",
      ) {
        val project = TestDataFactory.someProject
        val userIsAdmin =
          activeNormalUser.copy(permissions = PermissionsDataADM(Map(project.id.value -> List(ProjectAdmin))))
        for {
          exit <- AuthorizationRestService.ensureSystemAdminOrProjectAdmin(userIsAdmin, project.id).exit
        } yield assert(exit)(failsWithA[ForbiddenException])
      },
      test(
        "and given a project for which the user is _not_ project admin  when ensureSystemAdminOrProjectAdmin then fail",
      ) {
        val project = TestDataFactory.someProject
        for {
          _             <- ZIO.serviceWithZIO[KnoraProjectRepoInMemory](_.save(project))
          userIsNotAdmin = activeNormalUser.copy(permissions = PermissionsDataADM(Map.empty))
          exit          <- AuthorizationRestService.ensureSystemAdminOrProjectAdmin(userIsNotAdmin, project.id).exit
        } yield assert(exit)(failsWithA[ForbiddenException])
      },
    ),
  ).provide(
    AuthorizationRestServiceLive.layer,
    KnoraProjectService.layer,
    KnoraProjectRepoInMemory.layer,
    KnoraGroupService.layer,
    KnoraGroupRepoInMemory.layer,
  )
}
