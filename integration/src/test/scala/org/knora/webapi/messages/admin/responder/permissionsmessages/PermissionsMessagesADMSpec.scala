/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.permissionsmessages

import zio.ZIO

import java.util.UUID

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import org.knora.webapi.CoreSpec
import org.knora.webapi.responders.admin.PermissionsResponder
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.*
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM2.*
import org.knora.webapi.slice.admin.api.service.PermissionRestService
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA

/**
 * This spec is used to test subclasses of the [[PermissionsResponderRequestADM]] class.
 */
class PermissionsMessagesADMSpec extends CoreSpec {

  "Administrative Permission Get Requests" should {

    "return 'BadRequest' if the supplied permission IRI for AdministrativePermissionForIriGetRequestADM is not valid" in {
      val permissionIri = "invalid-permission-IRI"
      val caught = intercept[BadRequestException](
        AdministrativePermissionForIriGetRequestADM(
          administrativePermissionIri = permissionIri,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID(),
        ),
      )
      assert(caught.getMessage === s"Invalid permission IRI: $permissionIri.")
    }
  }

  "Administrative Permission Create Requests" should {
    "return 'BadRequest' if the supplied project IRI for AdministrativePermissionCreateRequestADM is not valid" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            forProject = "invalid-project-IRI",
            forGroup = KnoraGroupRepo.builtIn.ProjectMember.id.value,
            hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectAdminAll)),
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, "Project IRI is invalid.")
    }

    "return 'BadRequest' if the supplied group IRI for AdministrativePermissionCreateRequestADM is not valid" in {
      val groupIri = "invalid-group-iri"
      val exit = UnsafeZioRun.run(
        PermissionRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri,
            forGroup = groupIri,
            hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectAdminAll)),
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, s"Invalid group IRI $groupIri")
    }

    "return 'BadRequest' if the supplied permission IRI for AdministrativePermissionCreateRequestADM is not valid" in {
      val permissionIri = "invalid-permission-IRI"
      val exit = UnsafeZioRun.run(
        PermissionRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            id = Some(permissionIri),
            forProject = SharedTestDataADM.imagesProjectIri,
            forGroup = KnoraGroupRepo.builtIn.ProjectMember.id.value,
            hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectAdminAll)),
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, s"Invalid permission IRI: $permissionIri.")
    }

    "return 'BadRequest' if the no permissions supplied for AdministrativePermissionCreateRequestADM" in {
      val invalidName = "Delete"
      val hasPermissions = Set(
        PermissionADM(
          name = invalidName,
          additionalInformation = None,
          permissionCode = None,
        ),
      )
      val exit = UnsafeZioRun.run(
        PermissionRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri,
            forGroup = KnoraGroupRepo.builtIn.ProjectMember.id.value,
            hasPermissions = hasPermissions,
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](
        exit,
        s"Invalid value for name parameter of hasPermissions: $invalidName, it should be one of " +
          s"${Permission.Administrative.allTokens.mkString(", ")}",
      )
    }

    "return 'BadRequest' if the a permissions supplied for AdministrativePermissionCreateRequestADM had invalid name" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri,
            forGroup = KnoraGroupRepo.builtIn.ProjectMember.id.value,
            hasPermissions = Set.empty[PermissionADM],
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, "Permissions needs to be supplied.")
    }

    "return 'ForbiddenException' if the user requesting AdministrativePermissionCreateRequestADM is not system or project admin" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createAdministrativePermission(
          CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri,
            forGroup = KnoraGroupRepo.builtIn.ProjectMember.id.value,
            hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectAdminAll)),
          ),
          SharedTestDataADM.imagesReviewerUser,
        ),
      )
      assertFailsWithA[ForbiddenException](
        exit,
        "You are logged in with username 'images-reviewer-user', but only a system administrator or project administrator has permissions for this operation.",
      )
    }

  }

  "Object Access Permission Get Requests" should {
    "return 'BadRequest' if the supplied resource IRI for ObjectAccessPermissionsForResourceGetADM is not a valid KnoraResourceIri" in {
      val caught = intercept[BadRequestException](
        ObjectAccessPermissionsForResourceGetADM(
          resourceIri = SharedTestDataADM.customValueIRI,
          requestingUser = SharedTestDataADM.anythingAdminUser,
        ),
      )
      // a value IRI is given instead of a resource IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid resource IRI: ${SharedTestDataADM.customValueIRI}")
    }

    "return 'BadRequest' if the supplied resource IRI for ObjectAccessPermissionsForValueGetADM is not a valid KnoraValueIri" in {
      val caught = intercept[BadRequestException](
        ObjectAccessPermissionsForValueGetADM(
          valueIri = SharedTestDataADM.customResourceIRI,
          requestingUser = SharedTestDataADM.anythingAdminUser,
        ),
      )
      // a resource IRI is given instead of a value IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid value IRI: ${SharedTestDataADM.customResourceIRI}")
    }
  }

  "Default Object Access Permission Get Requests" should {

    "return 'BadRequest' if the supplied project IRI for DefaultObjectAccessPermissionGetADM is not valid" in {
      val projectIri = "invalid-project-IRI"
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = projectIri,
          groupIri = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      assert(caught.getMessage === s"Invalid project IRI $projectIri")
    }

    "return 'BadRequest' if the supplied resourceClass IRI for DefaultObjectAccessPermissionGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          resourceClassIri = Some(SharedTestDataADM.customResourceIRI),
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      // a resource IRI is given instead of a resource class IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid resource class IRI: ${SharedTestDataADM.customResourceIRI}")
    }

    "return 'BadRequest' if the supplied property IRI for DefaultObjectAccessPermissionGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          propertyIri = Some(SharedTestDataADM.customValueIRI),
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      // a value IRI is given instead of a property IRI, exception should be thrown.
      assert(caught.getMessage === s"Invalid property IRI: ${SharedTestDataADM.customValueIRI}")
    }

    "return 'BadRequest' if both group and resource class are supplied for DefaultObjectAccessPermissionGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          groupIri = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
          resourceClassIri = Some(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      assert(caught.getMessage === s"Not allowed to supply groupIri and resourceClassIri together.")
    }

    "return 'BadRequest' if both group and property are supplied for DefaultObjectAccessPermissionGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          groupIri = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
          propertyIri = Some(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY_LocalHost),
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      assert(caught.getMessage === s"Not allowed to supply groupIri and propertyIri together.")
    }

    "return 'BadRequest' if no group, resourceClassIri or propertyIri are supplied for DefaultObjectAccessPermissionGetADM is not valid" in {
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          requestingUser = SharedTestDataADM.imagesUser01,
        ),
      )
      assert(
        caught.getMessage === s"Either a group, a resource class, a property, or a combination of resource class and property must be given.",
      )
    }

    "return 'ForbiddenException' if requesting user of DefaultObjectAccessPermissionGetRequestADM is not system or project admin" in {
      val caught = intercept[ForbiddenException](
        DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.imagesProjectIri,
          groupIri = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
          requestingUser = SharedTestDataADM.imagesUser02,
        ),
      )
      assert(
        caught.getMessage === s"Default object access permissions can only be queried by system and project admin.",
      )
    }

    "return 'BadRequest' if the supplied permission IRI for DefaultObjectAccessPermissionForIriGetRequestADM is not valid" in {
      val permissionIri = "invalid-permission-IRI"
      val caught = intercept[BadRequestException](
        DefaultObjectAccessPermissionForIriGetRequestADM(
          defaultObjectAccessPermissionIri = permissionIri,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID(),
        ),
      )
      assert(caught.getMessage === s"Invalid permission IRI: $permissionIri.")
    }
  }

  "Default Object Access Permission Create Requests" should {
    "return 'BadRequest' if the supplied project IRI for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val forProject = "invalid-project-IRI"
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = forProject,
            forGroup = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
            hasPermissions = Set(
              PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
            ),
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, s"Project IRI is invalid.")
    }

    "return 'BadRequest' if the supplied group IRI for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val groupIri = "invalid-group-iri"
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri,
            forGroup = Some(groupIri),
            hasPermissions = Set(
              PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
            ),
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, s"Invalid group IRI $groupIri")
    }

    "return 'BadRequest' if the supplied custom permission IRI for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val permissionIri = "invalid-permission-IRI"
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            id = Some(permissionIri),
            forProject = SharedTestDataADM.imagesProjectIri,
            forGroup = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
            hasPermissions = Set(
              PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
            ),
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, s"Invalid permission IRI: $permissionIri.")
    }

    "return 'BadRequest' if the no permissions supplied for DefaultObjectAccessPermissionCreateRequestADM" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.imagesProjectIri,
            forGroup = Some(SharedTestDataADM.thingSearcherGroup.id),
            hasPermissions = Set.empty[PermissionADM],
          ),
          SharedTestDataADM.imagesUser01,
        ),
      )
      assertFailsWithA[BadRequestException](exit, "Permissions needs to be supplied.")
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission with invalid name" in {
      val hasPermissions = Set(
        PermissionADM(
          name = "invalid",
          additionalInformation = Some(KnoraGroupRepo.builtIn.Creator.id.value),
          permissionCode = Some(8),
        ),
      )
      val exit =
        UnsafeZioRun.run(ZIO.serviceWithZIO[PermissionsResponder](_.verifyHasPermissionsDOAP(hasPermissions)))
      assertFailsWithA[BadRequestException](
        exit,
        "Invalid value for name parameter of hasPermissions: invalid, it should be one of " +
          s"${Permission.ObjectAccess.allTokens.mkString(", ")}",
      )
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission with invalid code" in {
      val invalidCode = 10
      val hasPermissions = Set(
        PermissionADM(
          name = Permission.ObjectAccess.ChangeRights.token,
          additionalInformation = Some(KnoraGroupRepo.builtIn.Creator.id.value),
          permissionCode = Some(invalidCode),
        ),
      )

      val exit =
        UnsafeZioRun.run(ZIO.serviceWithZIO[PermissionsResponder](_.verifyHasPermissionsDOAP(hasPermissions)))
      assertFailsWithA[BadRequestException](
        exit,
        s"Invalid value for permissionCode parameter of hasPermissions: $invalidCode, it should be one of " +
          s"${Permission.ObjectAccess.allCodes.mkString(", ")}",
      )
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission with inconsistent code and name" in {
      val hasPermissions = Set(
        PermissionADM(
          name = Permission.ObjectAccess.ChangeRights.token,
          additionalInformation = Some(KnoraGroupRepo.builtIn.Creator.id.value),
          permissionCode = Some(Permission.ObjectAccess.View.code),
        ),
      )

      val exit =
        UnsafeZioRun.run(ZIO.serviceWithZIO[PermissionsResponder](_.verifyHasPermissionsDOAP(hasPermissions)))
      assertFailsWithA[BadRequestException](
        exit,
        s"Given permission code 2 and permission name CR are not consistent.",
      )
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission without any code or name" in {

      val hasPermissions = Set(
        PermissionADM(
          name = "",
          additionalInformation = Some(KnoraGroupRepo.builtIn.Creator.id.value),
          permissionCode = None,
        ),
      )

      val exit =
        UnsafeZioRun.run(ZIO.serviceWithZIO[PermissionsResponder](_.verifyHasPermissionsDOAP(hasPermissions)))
      assertFailsWithA[BadRequestException](
        exit,
        s"One of permission code or permission name must be provided for a default object access permission.",
      )
    }

    "not create a DefaultObjectAccessPermission for project and property if hasPermissions set contained permission without additionalInformation parameter" in {

      val hasPermissions = Set(
        PermissionADM(
          name = Permission.ObjectAccess.ChangeRights.token,
          additionalInformation = None,
          permissionCode = Some(8),
        ),
      )
      val exit =
        UnsafeZioRun.run(ZIO.serviceWithZIO[PermissionsResponder](_.verifyHasPermissionsDOAP(hasPermissions)))
      assertFailsWithA[BadRequestException](
        exit,
        s"additionalInformation of a default object access permission type cannot be empty.",
      )
    }

    "return 'ForbiddenException' if the user requesting DefaultObjectAccessPermissionCreateRequestADM is not system or project Admin" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.anythingProjectIri,
            forGroup = Some(SharedTestDataADM.thingSearcherGroup.id),
            hasPermissions =
              Set(PermissionADM.from(Permission.ObjectAccess.RestrictedView, SharedTestDataADM.thingSearcherGroup.id)),
          ),
          SharedTestDataADM.anythingUser2,
        ),
      )
      assertFailsWithA[ForbiddenException](
        exit,
        "You are logged in with username 'anything.user02', but only a system administrator or project administrator has permissions for this operation.",
      )
    }

    "return 'BadRequest' if the both group and resource class are supplied for DefaultObjectAccessPermissionCreateRequestADM" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = anythingProjectIri,
            forGroup = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
            forResourceClass = Some(ANYTHING_THING_RESOURCE_CLASS_LocalHost),
            hasPermissions = Set(
              PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
            ),
          ),
          SharedTestDataADM.rootUser,
        ),
      )
      assertFailsWithA[BadRequestException](exit, "Not allowed to supply groupIri and resourceClassIri together.")
    }

    "return 'BadRequest' if the both group and property are supplied for DefaultObjectAccessPermissionCreateRequestADM" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = anythingProjectIri,
            forGroup = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
            forProperty = Some(ANYTHING_HasDate_PROPERTY_LocalHost),
            hasPermissions = Set(
              PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
            ),
          ),
          SharedTestDataADM.rootUser,
        ),
      )
      assertFailsWithA[BadRequestException](exit, "Not allowed to supply groupIri and propertyIri together.")
    }

    "return 'BadRequest' if propertyIri supplied for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = anythingProjectIri,
            forProperty = Some(SharedTestDataADM.customValueIRI),
            hasPermissions = Set(
              PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
            ),
          ),
          SharedTestDataADM.rootUser,
        ),
      )
      assertFailsWithA[BadRequestException](exit, s"Invalid property IRI: ${SharedTestDataADM.customValueIRI}")
    }

    "return 'BadRequest' if resourceClassIri supplied for DefaultObjectAccessPermissionCreateRequestADM is not valid" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = anythingProjectIri,
            forResourceClass = Some(ANYTHING_THING_RESOURCE_CLASS_LocalHost),
            hasPermissions = Set(
              PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
            ),
          ),
          SharedTestDataADM.rootUser,
        ),
      )
      assertFailsWithA[BadRequestException](
        exit,
        s"Invalid resource class IRI: $ANYTHING_THING_RESOURCE_CLASS_LocalHost",
      )
    }

    "return 'BadRequest' if neither a group, nor a resource class, nor a property is supplied for DefaultObjectAccessPermissionCreateRequestADM" in {
      val exit = UnsafeZioRun.run(
        PermissionRestService.createDefaultObjectAccessPermission(
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = anythingProjectIri,
            hasPermissions = Set(
              PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
            ),
          ),
          SharedTestDataADM.rootUser,
        ),
      )
      assertFailsWithA[BadRequestException](
        exit,
        "Either a group, a resource class, a property, or a combination of resource class and property must be given.",
      )
    }
  }

  "querying the user's 'PermissionsDataADM' with 'hasPermissionFor'" should {
    "return true if the user is allowed to create a resource (root user)" in {

      val projectIri       = incunabulaProjectIri
      val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

      val result =
        SharedTestDataADM.rootUser.permissions.hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri)

      result should be(true)
    }

    "return true if the user is allowed to create a resource (project admin user)" in {

      val projectIri       = incunabulaProjectIri
      val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

      val result = SharedTestDataADM.incunabulaProjectAdminUser.permissions
        .hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri)

      result should be(true)
    }

    "return true if the user is allowed to create a resource (project member user)" in {

      val projectIri       = incunabulaProjectIri
      val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

      val result = SharedTestDataADM.incunabulaMemberUser.permissions
        .hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri)

      result should be(true)
    }

    "return false if the user is not allowed to create a resource" in {
      val projectIri       = incunabulaProjectIri
      val resourceClassIri = s"$INCUNABULA_ONTOLOGY_IRI#book"

      val result =
        SharedTestDataADM.normalUser.permissions.hasPermissionFor(ResourceCreateOperation(resourceClassIri), projectIri)

      result should be(false)
    }

    "return true if the user is allowed to create a resource (ProjectResourceCreateRestrictedPermission)" in {
      val projectIri                = imagesProjectIri
      val allowedResourceClassIri01 = s"$IMAGES_ONTOLOGY_IRI#bild"
      val allowedResourceClassIri02 = s"$IMAGES_ONTOLOGY_IRI#bildformat"

      val result1 = SharedTestDataADM.imagesReviewerUser.permissions
        .hasPermissionFor(ResourceCreateOperation(allowedResourceClassIri01), projectIri)
      result1 should be(true)

      val result2 = SharedTestDataADM.imagesReviewerUser.permissions
        .hasPermissionFor(ResourceCreateOperation(allowedResourceClassIri02), projectIri)
      result2 should be(true)
    }

    "return false if the user is not allowed to create a resource (ProjectResourceCreateRestrictedPermission)" in {
      val projectIri                 = imagesProjectIri
      val notAllowedResourceClassIri = s"$IMAGES_ONTOLOGY_IRI#person"

      val result = SharedTestDataADM.imagesReviewerUser.permissions
        .hasPermissionFor(ResourceCreateOperation(notAllowedResourceClassIri), projectIri)
      result should be(false)
    }
  }

  "querying the user's 'PermissionsProfileV1' with 'hasProjectAdminAllPermissionFor'" should {

    "return true if the user has the 'ProjectAdminAllPermission' (incunabula project admin user)" in {
      val projectIri = incunabulaProjectIri
      val result     = SharedTestDataADM.incunabulaProjectAdminUser.permissions.hasProjectAdminAllPermissionFor(projectIri)

      result should be(true)
    }

    "return false if the user has the 'ProjectAdminAllPermission' (incunabula member user)" in {
      val projectIri = incunabulaProjectIri
      val result     = SharedTestDataADM.incunabulaMemberUser.permissions.hasProjectAdminAllPermissionFor(projectIri)

      result should be(false)
    }
  }

  "given the permission IRI" should {
    "not get permission if invalid IRI given" in {
      val permissionIri = "invalid-iri"
      val caught = intercept[BadRequestException](
        PermissionByIriGetRequestADM(
          permissionIri = permissionIri,
          requestingUser = SharedTestDataADM.imagesUser02,
        ),
      )
      assert(caught.getMessage === s"Invalid permission IRI: $permissionIri.")
    }
  }
}
