/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import zio._
import zio.http._
import zio.http.model.Status
import zio.mock.Expectation
import zio.test._

import java.net.URLEncoder
import java.nio.file

import dsp.valueobjects.V2
import org.knora.webapi.TestDataFactory
import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.middleware.AuthenticationMiddleware
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectKeywordsGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsKeywordsGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.responders.admin.ProjectADMRestServiceMock
import org.knora.webapi.slice.admin.api.model.ProjectDataGetResponseADM
import org.knora.webapi.slice.admin.api.service.ProjectADMRestService

object ProjectsRouteZSpec extends ZIOSpecDefault {

  /**
   * Paths
   */
  private val basePathProjects: Path          = !! / "admin" / "projects"
  private val basePathProjectsIri: Path       = !! / "admin" / "projects" / "iri"
  private val basePathProjectsShortname: Path = !! / "admin" / "projects" / "shortname"
  private val basePathProjectsShortcode: Path = !! / "admin" / "projects" / "shortcode"

  /**
   * Creates a [[ProjectADM]] with empty content or optionally with a given ID.
   */
  private def getProjectADM(id: String = "") =
    ProjectADM(
      id = id,
      shortname = "",
      shortcode = "",
      longname = None,
      description = Seq(V2.StringLiteralV2("", None)),
      keywords = Seq.empty,
      logo = None,
      ontologies = Seq.empty,
      status = true,
      selfjoin = false
    )

  /**
   * Returns a ZIO effect that requires a [[ProjectADMRestService]] (so that a mock can be provided) that applies the
   * provided [[Request]] to the `routes` of a [[ProjectsRouteZ]], returning a [[Response]].
   */
  private def applyRoutes(request: Request): ZIO[ProjectADMRestService, Option[Nothing], Response] = ZIO
    .serviceWithZIO[ProjectsRouteZ](_.route.apply(request))
    .provideSome[ProjectADMRestService](
      AppConfig.layer,
      AuthenticationMiddleware.layer,
      AuthenticatorService.mock(Some(KnoraSystemInstances.Users.SystemUser)),
      ProjectsRouteZ.layer
    )

  /**
   * URL encodes a string, assuming utf-8
   */
  private def encode(iri: String) = URLEncoder.encode(iri, "utf-8")

  def spec: Spec[TestEnvironment with Scope, Any] = suite("ProjectsRouteZ")(
    getProjectsSpec,
    getSingleProjectSpec,
    createProjectSpec,
    deleteProjectSpec,
    updateProjectSpec,
    getAllDataSpec,
    getProjectMembersSpec,
    getProjectAdminsSpec,
    getKeywordsSpec,
    getKeywordsByProjectSpec,
    getProjectRestrictedViewSettings
  )

  val getProjectsSpec: Spec[Any, Serializable] = test("get all projects") {
    val request        = Request.get(url = URL(basePathProjects))
    val expectedResult = Expectation.value[ProjectsGetResponseADM](ProjectsGetResponseADM(Seq(getProjectADM())))
    val mockService    = ProjectADMRestServiceMock.GetProjects(expectedResult).toLayer
    for {
      response <- applyRoutes(request).provide(mockService)
      body     <- response.body.asString
    } yield assertTrue(true)
  }

  val getSingleProjectSpec: Spec[Any, Serializable] = suite("get a single project by identifier")(
    test("get a project by IRI") {
      val iri        = "http://rdfh.ch/projects/0001"
      val identifier = TestDataFactory.projectIriIdentifier(iri)
      val request    = Request.get(url = URL(basePathProjectsIri / encode(iri)))
      val mockService: ULayer[ProjectADMRestService] = ProjectADMRestServiceMock
        .GetSingleProject(
          assertion = Assertion.equalTo(identifier),
          result = Expectation.valueF[ProjectIdentifierADM, ProjectGetResponseADM](id =>
            ProjectGetResponseADM(getProjectADM(ProjectIdentifierADM.getId(id)))
          )
        )
        .toLayer
      for {
        response <- applyRoutes(request).provide(mockService)
        body     <- response.body.asString
      } yield assertTrue(body.contains(iri))
    },
    test("return a BadRequest Exception if project IRI is invalid") {
      val iri     = "http://rdfh.ch/project/0001"
      val user    = KnoraSystemInstances.Users.SystemUser
      val request = Request.get(url = URL(basePathProjectsIri / encode(iri)))

      for {
        response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Project IRI is invalid."}""")
    },
    test("get a project by shortname") {
      val shortname  = "someProject"
      val identifier = TestDataFactory.projectShortnameIdentifier(shortname)
      val request    = Request.get(url = URL(basePathProjectsShortname / shortname))
      val mockService: ULayer[ProjectADMRestService] = ProjectADMRestServiceMock
        .GetSingleProject(
          assertion = Assertion.equalTo(identifier),
          result = Expectation.valueF[ProjectIdentifierADM, ProjectGetResponseADM](id =>
            ProjectGetResponseADM(getProjectADM(ProjectIdentifierADM.getId(id)))
          )
        )
        .toLayer
      for {
        response <- applyRoutes(request).provide(mockService)
        body     <- response.body.asString
      } yield assertTrue(body.contains(shortname))
    },
    test("return a BadRequest Exception if shortname is invalid") {
      val shortname = "short name"
      val user      = KnoraSystemInstances.Users.SystemUser
      val request   = Request.get(url = URL(basePathProjectsShortname / shortname))

      for {
        response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Shortname is invalid: short name"}""")
    },
    test("get a project by shortcode") {
      val shortcode  = "0001"
      val identifier = TestDataFactory.projectShortcodeIdentifier(shortcode)
      val request    = Request.get(url = URL(basePathProjectsShortcode / shortcode))
      val mockService: ULayer[ProjectADMRestService] = ProjectADMRestServiceMock
        .GetSingleProject(
          assertion = Assertion.equalTo(identifier),
          result = Expectation.valueF[ProjectIdentifierADM, ProjectGetResponseADM](id =>
            ProjectGetResponseADM(getProjectADM(ProjectIdentifierADM.getId(id)))
          )
        )
        .toLayer
      for {
        response <- applyRoutes(request).provide(mockService)
        body     <- response.body.asString
      } yield assertTrue(body.contains(shortcode))
    },
    test("return a BadRequest Exception if shortcode is invalid") {
      val shortcode = "XY"
      val user      = KnoraSystemInstances.Users.SystemUser
      val request   = Request.get(url = URL(basePathProjectsShortcode / shortcode))

      for {
        response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: ShortCode is invalid: XY"}""")
    }
  )

  val createProjectSpec: Spec[Any, Serializable] = suite("create a project")(
    test("successfully create a project") {
      val projectCreatePayloadString =
        """|{
           |  "shortname": "newproject",
           |  "shortcode": "3333",
           |  "longname": "project longname",
           |  "description": [{"value": "project description", "language": "en"}],
           |  "keywords": ["test project"],
           |  "status": true,
           |  "selfjoin": false
           |}
           |""".stripMargin
      val body    = Body.fromString(projectCreatePayloadString)
      val request = Request.post(url = URL(basePathProjects), body = body)
      val user    = KnoraSystemInstances.Users.SystemUser

      val shortname   = TestDataFactory.projectShortName("newproject")
      val shortcode   = TestDataFactory.projectShortCode("3333")
      val longname    = TestDataFactory.projectName("project longname")
      val description = TestDataFactory.projectDescription(Seq(V2.StringLiteralV2("project description", Some("en"))))
      val keywords    = TestDataFactory.projectKeywords(Seq("test project"))
      val status      = TestDataFactory.projectStatus(true)
      val selfJoin    = TestDataFactory.projectSelfJoin(false)

      val projectCreatePayload = ProjectCreatePayloadADM(
        id = None,
        shortname = shortname,
        shortcode = shortcode,
        longname = Some(longname),
        description = description,
        keywords = keywords,
        logo = None,
        status = status,
        selfjoin = selfJoin
      )

      val expectedResult = Expectation.value[ProjectOperationResponseADM](ProjectOperationResponseADM(getProjectADM()))
      val mockService = ProjectADMRestServiceMock
        .CreateProject(
          assertion = Assertion.equalTo((projectCreatePayload, user)),
          result = expectedResult
        )
        .toLayer
      for {
        _ <- applyRoutes(request).provide(mockService)
      } yield assertTrue(true)
    },
    test("return a BadRequest Exception if input (payload) is invalid (wrong attribute)") {
      val projectCreatePayloadString =
        """|{
           |  "shortname": "new project",
           |  "shortcode": "3333",
           |  "longname": "project longname",
           |  "description": [{"value": "project description", "language": "en"}],
           |  "keywords": ["test project"],
           |  "status": true,
           |  "selfjoin": false
           |}
           |""".stripMargin
      val body    = Body.fromString(projectCreatePayloadString)
      val request = Request.post(url = URL(basePathProjects), body = body)
      val user    = KnoraSystemInstances.Users.SystemUser
      for {
        response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(
          bodyAsString == """{"error":"dsp.errors.BadRequestException: .shortname(Shortname is invalid: new project)"}"""
        )
    },
    test("return a BadRequest Exception if input (syntax) is invalid") {
      val projectCreatePayloadString =
        """|{
           |  "shortname": "newproject",
           |  "shortcode": "3333"
           |  "longname": "project longname",
           |  "description": [{"value": "project description", "language": "en"}],
           |  "keywords": ["test project"],
           |  "status": true,
           |  "selfjoin": false
           |}
           |""".stripMargin
      val body    = Body.fromString(projectCreatePayloadString)
      val request = Request.post(url = URL(basePathProjects), body = body)
      val user    = KnoraSystemInstances.Users.SystemUser
      for {
        response <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
      } yield assertTrue(response.status == Status.BadRequest)
    }
  )

  val deleteProjectSpec: Spec[Any, Serializable] = suite("delete a project")(
    test("successfully delete a project by IRI") {
      val iri            = "http://rdfh.ch/projects/0001"
      val projectIri     = TestDataFactory.projectIri(iri)
      val request        = Request.delete(url = URL(basePathProjectsIri / encode(projectIri.value)))
      val user           = KnoraSystemInstances.Users.SystemUser
      val expectedResult = Expectation.value[ProjectOperationResponseADM](ProjectOperationResponseADM(getProjectADM()))
      val mockService: ULayer[ProjectADMRestService] = ProjectADMRestServiceMock
        .DeleteProject(
          assertion = Assertion.equalTo(projectIri, user),
          result = expectedResult
        )
        .toLayer
      for {
        _ <- applyRoutes(request).provide(mockService)
      } yield assertTrue(true)
    },
    test("return a BadRequest Exception if project IRI is invalid") {
      val iri     = "http://rdfh.ch/project/0001"
      val user    = KnoraSystemInstances.Users.SystemUser
      val request = Request.delete(url = URL(basePathProjectsIri / encode(iri)))

      for {
        response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Project IRI is invalid."}""")
    }
  )

  val updateProjectSpec: Spec[Any, Serializable] = suite("update a project")(
    test("successfully update a project") {
      val projectIri         = TestDataFactory.projectIri("http://rdfh.ch/projects/0001")
      val updatedShortname   = TestDataFactory.projectShortName("usn")
      val updatedLongname    = TestDataFactory.projectName("updated project longname")
      val updatedDescription = TestDataFactory.projectDescription(Seq(V2.StringLiteralV2("updated desc", Some("en"))))
      val updatedKeywords    = TestDataFactory.projectKeywords(Seq("updated", "keywords"))
      val updatedLogo        = TestDataFactory.projectLogo("../logo.png")
      val projectStatus      = TestDataFactory.projectStatus(true)
      val selfJoin           = TestDataFactory.projectSelfJoin(true)

      val projectUpdatePayload = ProjectUpdatePayloadADM(
        shortname = Some(updatedShortname),
        longname = Some(updatedLongname),
        description = Some(updatedDescription),
        keywords = Some(updatedKeywords),
        logo = Some(updatedLogo),
        status = Some(projectStatus),
        selfjoin = Some(selfJoin)
      )

      val projectUpdatePayloadString =
        s"""|{
            |  "shortname": "${updatedShortname.value}",
            |  "longname": "${updatedLongname.value}",
            |  "description": [{"value": "updated desc", "language": "en"}],
            |  "keywords": ["updated", "keywords"],
            |  "logo": "${updatedLogo.value}",
            |  "status": ${projectStatus.value},
            |  "selfjoin": ${selfJoin.value}
            |}
            |""".stripMargin

      val body    = Body.fromString(projectUpdatePayloadString)
      val request = Request.put(url = URL(basePathProjectsIri / encode(projectIri.value)), body = body)
      val user    = KnoraSystemInstances.Users.SystemUser

      val expectedResult = Expectation.value[ProjectOperationResponseADM](ProjectOperationResponseADM(getProjectADM()))
      val mockService = ProjectADMRestServiceMock
        .UpdateProject(
          assertion = Assertion.equalTo((projectIri, projectUpdatePayload, user)),
          result = expectedResult
        )
        .toLayer
      for {
        _ <- applyRoutes(request).provide(mockService)
      } yield assertTrue(true)
    },
    test("return a BadRequest Exception if input (shortname) is invalid") {
      val projectIri                 = "http://rdfh.ch/projects/0001"
      val projectUpdatePayloadString = """{"shortname": "invalid shortname"}""".stripMargin
      val body                       = Body.fromString(projectUpdatePayloadString)
      val request                    = Request.put(url = URL(basePathProjectsIri / encode(projectIri)), body = body)
      val user                       = KnoraSystemInstances.Users.SystemUser

      for {
        response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(
          bodyAsString == """{"error":"dsp.errors.BadRequestException: .shortname(Shortname is invalid: invalid shortname)"}"""
        )
    },
    test("return a BadRequest Exception if input (syntax) is invalid") {
      val projectIri                 = "http://rdfh.ch/projects/0001"
      val projectUpdatePayloadString = """{"shortname":"usn" "longname":"updated longname"}""".stripMargin
      val body                       = Body.fromString(projectUpdatePayloadString)
      val request                    = Request.put(url = URL(basePathProjectsIri / encode(projectIri)), body = body)
      val user                       = KnoraSystemInstances.Users.SystemUser

      for {
        response <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
      } yield assertTrue(response.status == Status.BadRequest)
    },
    test("return a BadRequest Exception if project IRI is invalid") {
      val projectIri                 = "http://rdfh.ch/project/0001"
      val projectUpdatePayloadString = """{"shortname":"usn"}""".stripMargin
      val body                       = Body.fromString(projectUpdatePayloadString)
      val request                    = Request.put(url = URL(basePathProjectsIri / encode(projectIri)), body = body)
      val user                       = KnoraSystemInstances.Users.SystemUser

      for {
        response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Project IRI is invalid."}""")
    }
  )

  val getAllDataSpec: Spec[Any, Serializable] = suite("get all data")(
    test("successfully get all data") {
      val iri        = "http://rdfh.ch/projects/0001"
      val identifier = TestDataFactory.projectIriIdentifier(iri)
      val user       = KnoraSystemInstances.Users.SystemUser
      val request    = Request.get(url = URL(basePathProjectsIri / encode(iri) / "AllData"))
      val path       = file.Paths.get("getAllDataFile.trig")
      val testFile   = file.Files.createFile(path)

      val mockService: ULayer[ProjectADMRestService] = ProjectADMRestServiceMock
        .GetAllProjectData(
          assertion = Assertion.equalTo(identifier, user),
          result = Expectation
            .value[ProjectDataGetResponseADM](ProjectDataGetResponseADM(testFile))
        )
        .toLayer
      for {
        response <- applyRoutes(request).provide(mockService)
        body     <- response.body.asString
      } yield assertTrue(true)
    },
    test("return a BadRequest Exception if project IRI is invalid") {
      val iri     = "http://rdfh.ch/project/0001"
      val user    = KnoraSystemInstances.Users.SystemUser
      val request = Request.get(url = URL(basePathProjectsIri / encode(iri) / "AllData"))
      val path    = file.Paths.get("...")

      for {
        response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Project IRI is invalid."}""")
    }
  )

  val getProjectMembersSpec: Spec[Any, Serializable] = suite("get all members of a project")(
    test("get all members by project IRI") {
      val iri        = "http://rdfh.ch/projects/0001"
      val identifier = TestDataFactory.projectIriIdentifier(iri)
      val user       = KnoraSystemInstances.Users.SystemUser
      val request    = Request.get(url = URL(basePathProjectsIri / encode(iri) / "members"))
      val mockService: ULayer[ProjectADMRestService] = ProjectADMRestServiceMock
        .GetProjectMembers(
          assertion = Assertion.equalTo((identifier, user)),
          result = Expectation.value[ProjectMembersGetResponseADM](
            ProjectMembersGetResponseADM(Seq.empty[UserADM])
          )
        )
        .toLayer
      for {
        response <- applyRoutes(request).provide(mockService)
        body     <- response.body.asString
      } yield assertTrue(body == """{"members":[]}""")
    },
    test("return a BadRequest Exception if project IRI is invalid") {
      val iri     = "http://rdfh.ch/project/0001"
      val user    = KnoraSystemInstances.Users.SystemUser
      val request = Request.get(url = URL(basePathProjectsIri / encode(iri) / "members"))

      for {
        response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Project IRI is invalid."}""")
    },
    test("get all members by project shortcode") {
      val shortcode  = "0001"
      val identifier = TestDataFactory.projectShortcodeIdentifier(shortcode)
      val user       = KnoraSystemInstances.Users.SystemUser
      val request    = Request.get(url = URL(basePathProjectsShortcode / shortcode / "members"))
      val mockService: ULayer[ProjectADMRestService] = ProjectADMRestServiceMock
        .GetProjectMembers(
          assertion = Assertion.equalTo((identifier, user)),
          result = Expectation.value[ProjectMembersGetResponseADM](
            ProjectMembersGetResponseADM(Seq.empty[UserADM])
          )
        )
        .toLayer
      for {
        response <- applyRoutes(request).provide(mockService)
        body     <- response.body.asString
      } yield assertTrue(body == """{"members":[]}""")
    },
    test("return a BadRequest Exception if shortcode is invalid") {
      val shortcode = "XY"
      val user      = KnoraSystemInstances.Users.SystemUser
      val request   = Request.get(url = URL(basePathProjectsShortcode / shortcode / "members"))

      for {
        response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: ShortCode is invalid: XY"}""")
    },
    test("get all members by project shortname") {
      val shortname  = "someProject"
      val identifier = TestDataFactory.projectShortnameIdentifier(shortname)
      val user       = KnoraSystemInstances.Users.SystemUser
      val request    = Request.get(url = URL(basePathProjectsShortname / shortname / "members"))
      val mockService: ULayer[ProjectADMRestService] = ProjectADMRestServiceMock
        .GetProjectMembers(
          assertion = Assertion.equalTo((identifier, user)),
          result = Expectation.value[ProjectMembersGetResponseADM](
            ProjectMembersGetResponseADM(Seq.empty[UserADM])
          )
        )
        .toLayer
      for {
        response <- applyRoutes(request).provide(mockService)
        body     <- response.body.asString
      } yield assertTrue(body == """{"members":[]}""")
    },
    test("return a BadRequest Exception if shortname is invalid") {
      val shortname = "short name"
      val user      = KnoraSystemInstances.Users.SystemUser
      val request   = Request.get(url = URL(basePathProjectsShortname / shortname / "members"))

      for {
        response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Shortname is invalid: short name"}""")
    }
  )

  val getProjectAdminsSpec: Spec[Any, Serializable] = suite("get all project admins of a project")(
    test("get all project admins by project IRI") {
      val iri        = "http://rdfh.ch/projects/0001"
      val identifier = TestDataFactory.projectIriIdentifier(iri)
      val user       = KnoraSystemInstances.Users.SystemUser
      val request    = Request.get(url = URL(basePathProjectsIri / encode(iri) / "admin-members"))
      val mockService: ULayer[ProjectADMRestService] = ProjectADMRestServiceMock
        .GetProjectAdmins(
          assertion = Assertion.equalTo((identifier, user)),
          result = Expectation.value[ProjectAdminMembersGetResponseADM](
            ProjectAdminMembersGetResponseADM(Seq.empty[UserADM])
          )
        )
        .toLayer
      for {
        response <- applyRoutes(request).provide(mockService)
        body     <- response.body.asString
      } yield assertTrue(body == """{"members":[]}""")
    },
    test("return a BadRequest Exception if project IRI is invalid") {
      val iri     = "http://rdfh.ch/project/0001"
      val user    = KnoraSystemInstances.Users.SystemUser
      val request = Request.get(url = URL(basePathProjectsIri / encode(iri) / "admin-members"))

      for {
        response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Project IRI is invalid."}""")
    },
    test("get all project admins by project shortcode") {
      val shortcode  = "0001"
      val identifier = TestDataFactory.projectShortcodeIdentifier(shortcode)
      val user       = KnoraSystemInstances.Users.SystemUser
      val request    = Request.get(url = URL(basePathProjectsShortcode / shortcode / "admin-members"))
      val mockService: ULayer[ProjectADMRestService] = ProjectADMRestServiceMock
        .GetProjectAdmins(
          assertion = Assertion.equalTo((identifier, user)),
          result = Expectation.value[ProjectAdminMembersGetResponseADM](
            ProjectAdminMembersGetResponseADM(Seq.empty[UserADM])
          )
        )
        .toLayer
      for {
        response <- applyRoutes(request).provide(mockService)
        body     <- response.body.asString
      } yield assertTrue(body == """{"members":[]}""")
    },
    test("return a BadRequest Exception if shortcode is invalid") {
      val shortcode = "XY"
      val user      = KnoraSystemInstances.Users.SystemUser
      val request   = Request.get(url = URL(basePathProjectsShortcode / shortcode / "admin-members"))

      for {
        response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: ShortCode is invalid: XY"}""")
    },
    test("get all project admins by project shortname") {
      val shortname  = "someProject"
      val identifier = TestDataFactory.projectShortnameIdentifier(shortname)
      val user       = KnoraSystemInstances.Users.SystemUser
      val request    = Request.get(url = URL(basePathProjectsShortname / shortname / "admin-members"))
      val mockService: ULayer[ProjectADMRestService] = ProjectADMRestServiceMock
        .GetProjectAdmins(
          assertion = Assertion.equalTo((identifier, user)),
          result = Expectation.value[ProjectAdminMembersGetResponseADM](
            ProjectAdminMembersGetResponseADM(Seq.empty[UserADM])
          )
        )
        .toLayer
      for {
        response <- applyRoutes(request).provide(mockService)
        body     <- response.body.asString
      } yield assertTrue(body == """{"members":[]}""")
    },
    test("return a BadRequest Exception if shortname is invalid") {
      val shortname = "short name"
      val user      = KnoraSystemInstances.Users.SystemUser
      val request   = Request.get(url = URL(basePathProjectsShortname / shortname / "admin-members"))

      for {
        response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Shortname is invalid: short name"}""")
    }
  )

  val getKeywordsSpec: Spec[Any, Serializable] = test("get keywords of all projects") {
    val request = Request.get(url = URL(basePathProjects / "Keywords"))
    val expectedResult =
      Expectation.value[ProjectsKeywordsGetResponseADM](ProjectsKeywordsGetResponseADM(Seq.empty[String]))
    val mockService = ProjectADMRestServiceMock.GetKeywords(expectedResult).toLayer
    for {
      response <- applyRoutes(request).provide(mockService)
      body     <- response.body.asString
    } yield assertCompletes
  }

  val getKeywordsByProjectSpec: Spec[Any, Serializable] = suite("get all keywords of a specific project")(
    test("successfully get keywords") {
      val iri        = "http://rdfh.ch/projects/0001"
      val projectIri = TestDataFactory.projectIri(iri)
      val request    = Request.get(url = URL(basePathProjectsIri / encode(iri) / "Keywords"))
      val mockService: ULayer[ProjectADMRestService] = ProjectADMRestServiceMock
        .GetKeywordsByProjectIri(
          assertion = Assertion.equalTo(projectIri),
          result = Expectation.value[ProjectKeywordsGetResponseADM](
            ProjectKeywordsGetResponseADM(Seq.empty[String])
          )
        )
        .toLayer
      for {
        response <- applyRoutes(request).provide(mockService)
        body     <- response.body.asString
      } yield assertTrue(body == """{"keywords":[]}""")
    },
    test("return a BadRequest Exception if project IRI is invalid") {
      val iri     = "http://rdfh.ch/project/0001"
      val request = Request.get(url = URL(basePathProjectsIri / encode(iri) / "Keywords"))

      for {
        response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Project IRI is invalid."}""")
    }
  )

  val getProjectRestrictedViewSettings: Spec[Any, Serializable] =
    suite("get the restricted view settings of a project by project identifier")(
      test("successfully get the settings by project IRI") {
        val iri        = "http://rdfh.ch/projects/0001"
        val identifier = TestDataFactory.projectIriIdentifier(iri)
        val settings   = ProjectRestrictedViewSettingsADM(Some("!512,512"), Some("path_to_image"))
        val request    = Request.get(url = URL(basePathProjectsIri / encode(iri) / "RestrictedViewSettings"))
        val mockService: ULayer[ProjectADMRestService] = ProjectADMRestServiceMock
          .GetRestrictedViewSettings(
            assertion = Assertion.equalTo(identifier),
            result = Expectation.valueF[ProjectIdentifierADM, ProjectRestrictedViewSettingsGetResponseADM](id =>
              ProjectRestrictedViewSettingsGetResponseADM(settings)
            )
          )
          .toLayer
        for {
          response <- applyRoutes(request).provide(mockService)
          body     <- response.body.asString
        } yield assertTrue(body.contains("!512,512"))
      },
      test("return a BadRequest Exception if project IRI is invalid") {
        val iri     = "http://rdfh.ch/project/0001"
        val user    = KnoraSystemInstances.Users.SystemUser
        val request = Request.get(url = URL(basePathProjectsIri / encode(iri) / "RestrictedViewSettings"))

        for {
          response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
          bodyAsString <- response.body.asString
        } yield assertTrue(response.status == Status.BadRequest) &&
          assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Project IRI is invalid."}""")
      },
      test("successfully get the settings by shortname") {
        val shortname  = "someProject"
        val identifier = TestDataFactory.projectShortnameIdentifier(shortname)
        val settings   = ProjectRestrictedViewSettingsADM(Some("!512,512"), Some("path_to_image"))
        val request    = Request.get(url = URL(basePathProjectsShortname / shortname / "RestrictedViewSettings"))
        val mockService: ULayer[ProjectADMRestService] = ProjectADMRestServiceMock
          .GetRestrictedViewSettings(
            assertion = Assertion.equalTo(identifier),
            result = Expectation.valueF[ProjectIdentifierADM, ProjectRestrictedViewSettingsGetResponseADM](id =>
              ProjectRestrictedViewSettingsGetResponseADM(settings)
            )
          )
          .toLayer
        for {
          response <- applyRoutes(request).provide(mockService)
          body     <- response.body.asString
        } yield assertTrue(body.contains("!512,512"))
      },
      test("return a BadRequest Exception if shortname is invalid") {
        val shortname = "short name"
        val user      = KnoraSystemInstances.Users.SystemUser
        val request   = Request.get(url = URL(basePathProjectsShortname / shortname / "RestrictedViewSettings"))

        for {
          response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
          bodyAsString <- response.body.asString
        } yield assertTrue(response.status == Status.BadRequest) &&
          assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Shortname is invalid: short name"}""")
      },
      test("successfully get the settings by shortcode") {
        val shortcode  = "0001"
        val identifier = TestDataFactory.projectShortcodeIdentifier(shortcode)
        val settings   = ProjectRestrictedViewSettingsADM(Some("!512,512"), Some("path_to_image"))
        val request    = Request.get(url = URL(basePathProjectsShortcode / shortcode / "RestrictedViewSettings"))
        val mockService: ULayer[ProjectADMRestService] = ProjectADMRestServiceMock
          .GetRestrictedViewSettings(
            assertion = Assertion.equalTo(identifier),
            result = Expectation.valueF[ProjectIdentifierADM, ProjectRestrictedViewSettingsGetResponseADM](id =>
              ProjectRestrictedViewSettingsGetResponseADM(settings)
            )
          )
          .toLayer
        for {
          response <- applyRoutes(request).provide(mockService)
          body     <- response.body.asString
        } yield assertTrue(body.contains("!512,512"))
      },
      test("return a BadRequest Exception if shortcode is invalid") {
        val shortcode = "XY"
        val user      = KnoraSystemInstances.Users.SystemUser
        val request   = Request.get(url = URL(basePathProjectsShortcode / shortcode / "RestrictedViewSettings"))

        for {
          response     <- applyRoutes(request).provide(ProjectADMRestServiceMock.empty)
          bodyAsString <- response.body.asString
        } yield assertTrue(response.status == Status.BadRequest) &&
          assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: ShortCode is invalid: XY"}""")
      }
    )
}
