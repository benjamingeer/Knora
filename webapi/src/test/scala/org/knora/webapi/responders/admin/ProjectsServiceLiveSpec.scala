/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio._
import zio.mock._
import zio.test._

import dsp.valueobjects.V2._
import org.knora.webapi.TestDataFactory
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.SystemUser
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectUpdateRequest
import org.knora.webapi.slice.admin.api.service.ProjectADMRestService
import org.knora.webapi.slice.admin.api.service.ProjectsADMRestServiceLive
import org.knora.webapi.slice.admin.domain.service.DspIngestClientMock
import org.knora.webapi.slice.admin.domain.service.ProjectExportServiceStub
import org.knora.webapi.slice.admin.domain.service.ProjectExportStorageServiceLive
import org.knora.webapi.slice.admin.domain.service.ProjectImportServiceLive

object ProjectsServiceLiveSpec extends ZIOSpecDefault {

  /**
   * Represents a [[ProjectADM]] with empty content
   */
  private val projectADM: ProjectADM =
    ProjectADM(
      id = "",
      shortname = "",
      shortcode = "",
      longname = None,
      description = Seq(StringLiteralV2("", None)),
      keywords = Seq.empty,
      logo = None,
      ontologies = Seq.empty,
      status = true,
      selfjoin = false
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ProjectsService")(
      getAllProjectsSpec,
      getProjectByIdSpec,
      createProjectSpec,
      deleteProjectSpec,
      updateProjectSpec,
      getProjectMembers,
      getProjectAdmins,
      getKeywordsSpec,
      getKeywordsByProjectIri,
      getProjectRestrictedViewSettings
    ).provide(StringFormatter.test)

  private def projectServiceLayer(exp: Expectation[ProjectsResponderADM]): ULayer[ProjectADMRestService] =
    ZLayer.make[ProjectADMRestService](
      ProjectsADMRestServiceLive.layer,
      exp.toLayer,
      org.knora.webapi.slice.common.api.RestPermissionServiceLive.layer,
      ProjectExportServiceStub.layer,
      org.knora.webapi.slice.admin.domain.service.KnoraProjectRepoInMemory.layer,
      ProjectImportServiceLive.layer,
      ProjectExportStorageServiceLive.layer,
      AppConfig.layer,
      DspIngestClientMock.layer
    )

  val getAllProjectsSpec: Spec[Any, Throwable] = test("get all projects") {
    val expectedResponse = ProjectsGetResponseADM(Seq(projectADM))
    val mockResponder    = ProjectsResponderADMMock.ProjectsGetRequestADM(Expectation.value(expectedResponse))
    for {
      _ <- ProjectADMRestService.listAllProjects().provide(projectServiceLayer(mockResponder))
    } yield assertCompletes
  }

  val getProjectByIdSpec: Spec[Any, Throwable] = suite("get single project by identifier")(
    test("get project by IRI") {
      val iri        = "http://rdfh.ch/projects/0001"
      val identifier = TestDataFactory.projectIriIdentifier(iri)
      val mockResponder = ProjectsResponderADMMock.GetSingleProjectADMRequest(
        assertion = Assertion.equalTo(identifier),
        result = Expectation.value(ProjectGetResponseADM(projectADM))
      )
      for {
        _ <- ProjectADMRestService.findProject(identifier).provide(projectServiceLayer(mockResponder))
      } yield assertCompletes
    },
    test("get project by shortname") {
      val shortname  = "someProject"
      val identifier = TestDataFactory.projectShortnameIdentifier(shortname)
      val mockResponder = ProjectsResponderADMMock.GetSingleProjectADMRequest(
        assertion = Assertion.equalTo(identifier),
        result = Expectation.value(ProjectGetResponseADM(projectADM))
      )
      for {
        _ <- ProjectADMRestService.findProject(identifier).provide(projectServiceLayer(mockResponder))
      } yield assertCompletes
    },
    test("get project by shortcode") {
      val shortcode  = "0001"
      val identifier = TestDataFactory.projectShortcodeIdentifier(shortcode)
      val mockResponder = ProjectsResponderADMMock.GetSingleProjectADMRequest(
        assertion = Assertion.equalTo(identifier),
        result = Expectation.value(ProjectGetResponseADM(projectADM))
      )
      for {
        _ <- ProjectADMRestService.findProject(identifier).provide(projectServiceLayer(mockResponder))
      } yield assertCompletes
    }
  )

  val createProjectSpec: Spec[Any, Throwable] = test("create a project") {
    val payload = ProjectCreateRequest(
      None,
      TestDataFactory.projectShortname("newproject"),
      TestDataFactory.projectShortcode("3333"),
      Some(TestDataFactory.projectName("project longname")),
      TestDataFactory.projectDescription(Seq(StringLiteralV2("updated project description", Some("en")))),
      TestDataFactory.projectKeywords(Seq("test", "kewords")),
      None,
      TestDataFactory.projectStatus(true),
      TestDataFactory.projectSelfJoin(true)
    )

    for {
      uuid <- ZIO.random.flatMap(_.nextUUID)
      _    <- TestRandom.feedUUIDs(uuid)
      mockResponder =
        ProjectsResponderADMMock.ProjectCreateRequestADM(
          assertion = Assertion.equalTo(payload, SystemUser, uuid),
          result = Expectation.value(ProjectOperationResponseADM(projectADM))
        )
      _ <-
        ProjectADMRestService.createProject(payload, SystemUser).provide(projectServiceLayer(mockResponder))
    } yield assertCompletes
  }

  // needs to have the StringFormatter in the environment because the [[ChangeProjectApiRequestADM]] needs it
  val deleteProjectSpec: Spec[StringFormatter, Throwable] = test("delete a project") {
    val iri                  = "http://rdfh.ch/projects/0001"
    val projectIri           = TestDataFactory.projectIri(iri)
    val projectStatus        = Some(TestDataFactory.projectStatus(false))
    val projectUpdatePayload = ProjectUpdateRequest(status = projectStatus)
    for {
      uuid <- ZIO.random.flatMap(_.nextUUID)
      _    <- TestRandom.feedUUIDs(uuid)
      mockResponder = ProjectsResponderADMMock.ChangeBasicInformationRequestADM(
                        assertion = Assertion.equalTo(projectIri, projectUpdatePayload, SystemUser, uuid),
                        result = Expectation.value(ProjectOperationResponseADM(projectADM))
                      )
      _ <- ProjectADMRestService
             .deleteProject(IriIdentifier.from(projectIri), SystemUser)
             .provide(projectServiceLayer(mockResponder))
    } yield assertCompletes
  }

  val updateProjectSpec: Spec[Any, Throwable] = test("update a project") {
    val iri        = "http://rdfh.ch/projects/0001"
    val projectIri = TestDataFactory.projectIri(iri)
    val projectUpdatePayload = ProjectUpdateRequest(
      Some(TestDataFactory.projectShortname("usn")),
      Some(TestDataFactory.projectName("updated project longname")),
      Some(TestDataFactory.projectDescription(Seq(StringLiteralV2("updated project description", Some("en"))))),
      Some(TestDataFactory.projectKeywords(Seq("updated", "kewords"))),
      Some(TestDataFactory.projectLogo("../updatedlogo.png")),
      Some(TestDataFactory.projectStatus(true)),
      Some(TestDataFactory.projectSelfJoin(true))
    )
    for {
      uuid <- ZIO.random.flatMap(_.nextUUID)
      _    <- TestRandom.feedUUIDs(uuid)
      mockResponder = ProjectsResponderADMMock.ChangeBasicInformationRequestADM(
                        assertion = Assertion.equalTo(projectIri, projectUpdatePayload, SystemUser, uuid),
                        result = Expectation.value(ProjectOperationResponseADM(projectADM))
                      )
      _ <- ProjectADMRestService
             .updateProject(IriIdentifier.from(projectIri), projectUpdatePayload, SystemUser)
             .provide(projectServiceLayer(mockResponder))
    } yield assertCompletes
  }

  val getProjectMembers: Spec[Any, Throwable] = suite("get all members of a project")(
    test("get members by project IRI") {
      val iri        = "http://rdfh.ch/projects/0001"
      val identifier = TestDataFactory.projectIriIdentifier(iri)
      val mockResponder = ProjectsResponderADMMock.ProjectMembersGetRequestADM(
        assertion = Assertion.equalTo(identifier, SystemUser),
        result = Expectation.value(ProjectMembersGetResponseADM(Seq.empty[UserADM]))
      )
      for {
        _ <- ProjectADMRestService
               .getProjectMembers(SystemUser, identifier)
               .provide(projectServiceLayer(mockResponder))
      } yield assertCompletes
    },
    test("get members by project shortname") {
      val shortname  = "shortname"
      val identifier = TestDataFactory.projectShortnameIdentifier(shortname)
      val mockResponder = ProjectsResponderADMMock.ProjectMembersGetRequestADM(
        assertion = Assertion.equalTo(identifier, SystemUser),
        result = Expectation.value(ProjectMembersGetResponseADM(Seq.empty[UserADM]))
      )
      for {
        _ <- ProjectADMRestService
               .getProjectMembers(SystemUser, identifier)
               .provide(projectServiceLayer(mockResponder))
      } yield assertCompletes
    },
    test("get members by project shortcode") {
      val shortcode  = "0001"
      val identifier = TestDataFactory.projectShortcodeIdentifier(shortcode)
      val mockResponder = ProjectsResponderADMMock.ProjectMembersGetRequestADM(
        assertion = Assertion.equalTo(identifier, SystemUser),
        result = Expectation.value(ProjectMembersGetResponseADM(Seq.empty[UserADM]))
      )
      for {
        _ <- ProjectADMRestService
               .getProjectMembers(SystemUser, identifier)
               .provide(projectServiceLayer(mockResponder))
      } yield assertCompletes
    }
  )

  val getProjectAdmins: Spec[Any, Throwable] = suite("get all project admins of a project")(
    test("get project admins by project IRI") {
      val iri        = "http://rdfh.ch/projects/0001"
      val identifier = TestDataFactory.projectIriIdentifier(iri)
      val mockResponder = ProjectsResponderADMMock.ProjectAdminMembersGetRequestADM(
        assertion = Assertion.equalTo(identifier, SystemUser),
        result = Expectation.value(ProjectAdminMembersGetResponseADM(Seq.empty[UserADM]))
      )
      for {
        _ <- ProjectADMRestService
               .getProjectAdminMembers(SystemUser, identifier)
               .provide(projectServiceLayer(mockResponder))
      } yield assertCompletes
    },
    test("get project admins by project shortname") {
      val shortname  = "shortname"
      val identifier = TestDataFactory.projectShortnameIdentifier(shortname)
      val mockResponder = ProjectsResponderADMMock.ProjectAdminMembersGetRequestADM(
        assertion = Assertion.equalTo(identifier, SystemUser),
        result = Expectation.value(ProjectAdminMembersGetResponseADM(Seq.empty[UserADM]))
      )
      for {
        _ <- ProjectADMRestService
               .getProjectAdminMembers(SystemUser, identifier)
               .provide(projectServiceLayer(mockResponder))
      } yield assertCompletes
    },
    test("get project admins by project shortcode") {
      val shortcode  = "0001"
      val identifier = TestDataFactory.projectShortcodeIdentifier(shortcode)
      val mockResponder = ProjectsResponderADMMock.ProjectAdminMembersGetRequestADM(
        assertion = Assertion.equalTo(identifier, SystemUser),
        result = Expectation.value(ProjectAdminMembersGetResponseADM(Seq.empty[UserADM]))
      )
      for {
        _ <- ProjectADMRestService
               .getProjectAdminMembers(SystemUser, identifier)
               .provide(projectServiceLayer(mockResponder))
      } yield assertCompletes
    }
  )

  val getKeywordsSpec: Spec[Any, Throwable] = test("get keywords of all projects") {
    val mockResponder = ProjectsResponderADMMock.ProjectsKeywordsGetRequestADM(
      Expectation.value(ProjectsKeywordsGetResponseADM(Seq.empty[String]))
    )
    for {
      _ <- ProjectADMRestService
             .listAllKeywords()
             .provide(projectServiceLayer(mockResponder))
    } yield assertCompletes
  }

  val getKeywordsByProjectIri: Spec[Any, Throwable] = test("get keywords of a single project by project IRI") {
    val iri        = "http://rdfh.ch/projects/0001"
    val projectIri = TestDataFactory.projectIri(iri)
    val mockResponder = ProjectsResponderADMMock.ProjectKeywordsGetRequestADM(
      assertion = Assertion.equalTo(projectIri),
      result = Expectation.value(ProjectKeywordsGetResponseADM(Seq.empty[String]))
    )
    for {
      _ <- ProjectADMRestService
             .getKeywordsByProjectIri(projectIri)
             .provide(projectServiceLayer(mockResponder))
    } yield assertCompletes
  }

  val getProjectRestrictedViewSettings: Spec[Any, Throwable] = suite("get the restricted view settings of a project")(
    test("get settings by project IRI") {
      val iri        = "http://rdfh.ch/projects/0001"
      val identifier = TestDataFactory.projectIriIdentifier(iri)
      val settings   = ProjectRestrictedViewSettingsADM(Some("!512,512"), Some("path_to_image"))
      val mockResponder = ProjectsResponderADMMock.ProjectRestrictedViewSettingsGetRequestADM(
        assertion = Assertion.equalTo(identifier),
        result = Expectation.value(ProjectRestrictedViewSettingsGetResponseADM(settings))
      )
      for {
        _ <- ProjectADMRestService
               .getProjectRestrictedViewSettings(identifier)
               .provide(projectServiceLayer(mockResponder))
      } yield assertCompletes
    },
    test("get settings by project shortname") {
      val shortname  = "someProject"
      val identifier = TestDataFactory.projectShortnameIdentifier(shortname)
      val settings   = ProjectRestrictedViewSettingsADM(Some("!512,512"), Some("path_to_image"))
      val mockResponder = ProjectsResponderADMMock.ProjectRestrictedViewSettingsGetRequestADM(
        assertion = Assertion.equalTo(identifier),
        result = Expectation.value(ProjectRestrictedViewSettingsGetResponseADM(settings))
      )
      for {
        _ <- ProjectADMRestService
               .getProjectRestrictedViewSettings(identifier)
               .provide(projectServiceLayer(mockResponder))
      } yield assertCompletes
    },
    test("get settings by project shortcode") {
      val shortcode  = "0001"
      val identifier = TestDataFactory.projectShortcodeIdentifier(shortcode)
      val settings   = ProjectRestrictedViewSettingsADM(Some("!512,512"), Some("path_to_image"))
      val mockResponder = ProjectsResponderADMMock.ProjectRestrictedViewSettingsGetRequestADM(
        assertion = Assertion.equalTo(identifier),
        result = Expectation.value(ProjectRestrictedViewSettingsGetResponseADM(settings))
      )
      for {
        _ <- ProjectADMRestService
               .getProjectRestrictedViewSettings(identifier)
               .provide(projectServiceLayer(mockResponder))
      } yield assertCompletes
    }
  )
}
