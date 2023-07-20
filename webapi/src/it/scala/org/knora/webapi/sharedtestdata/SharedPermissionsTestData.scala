/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.sharedtestdata

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ObjectAccessPermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM._
import org.knora.webapi.sharedtestdata.SharedTestDataV1._

/* Helper case classes */
case class ap(iri: String, p: AdministrativePermissionADM)
case class oap(iri: String, p: ObjectAccessPermissionADM)
case class doap(iri: String, p: DefaultObjectAccessPermissionADM)

/**
 * This object holds data representations for the data in 'test_data/project_data/permissions-data.ttl'.
 */
object SharedPermissionsTestData {

  /**
   * **********************************
   */
  /** Knora System Permissions        * */
  /**
   * **********************************
   */
  val perm001_d1: doap =
    doap(
      iri = "http://rdfh.ch/permissions/0000/xshpLswURHOJEbHXGKVvYg",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/0000/xshpLswURHOJEbHXGKVvYg",
        forProject = OntologyConstants.KnoraAdmin.SystemProject,
        forResourceClass = Some(OntologyConstants.KnoraBase.LinkObj),
        hasPermissions = Set(
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
        )
      )
    )

  val perm001_d2: doap =
    doap(
      iri = "http://rdfh.ch/permissions/0000/tPtW0E6gT2ezsqhSdE8e2g",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/0000/tPtW0E6gT2ezsqhSdE8e2g",
        forProject = OntologyConstants.KnoraAdmin.SystemProject,
        forResourceClass = Some(OntologyConstants.KnoraBase.Region),
        hasPermissions = Set(
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
        )
      )
    )

  val perm001_d3: doap =
    doap(
      iri = "http://rdfh.ch/permissions/0000/KMjKHCNQQmC4uHPQwlEexw",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/0000/KMjKHCNQQmC4uHPQwlEexw",
        forProject = OntologyConstants.KnoraAdmin.SystemProject,
        forProperty = Some(OntologyConstants.KnoraBase.HasStillImageFileValue),
        hasPermissions = Set(
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
        )
      )
    )

  /**
   * **********************************
   */
  /** Images Demo Project Permissions * */
  /**
   * **********************************
   */
  val perm002_a1: ap =
    ap(
      iri = "http://rdfh.ch/permissions/00FF/QYdrY7O6QD2VR30oaAt3Yg",
      p = AdministrativePermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/QYdrY7O6QD2VR30oaAt3Yg",
        forProject = imagesProjectIri,
        forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
        hasPermissions = Set(
          PermissionADM.ProjectResourceCreateAllPermission
        )
      )
    )

  val perm002_a2: ap =
    ap(
      iri = "http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ",
      p = AdministrativePermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ",
        forProject = imagesProjectIri,
        forGroup = OntologyConstants.KnoraAdmin.ProjectAdmin,
        hasPermissions = Set(
          PermissionADM.ProjectResourceCreateAllPermission,
          PermissionADM.ProjectAdminAllPermission
        )
      )
    )

  val perm002_a3: ap =
    ap(
      iri = "http://rdfh.ch/permissions/00FF/6eeT4ezSRjO21Im2Dm12Qg",
      p = AdministrativePermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/6eeT4ezSRjO21Im2Dm12Qg",
        forProject = imagesProjectIri,
        forGroup = "http://rdfh.ch/groups/00FF/images-reviewer",
        hasPermissions = Set(
          PermissionADM.projectResourceCreateRestrictedPermission(s"$IMAGES_ONTOLOGY_IRI#bild"),
          PermissionADM.projectResourceCreateRestrictedPermission(s"$IMAGES_ONTOLOGY_IRI#bildformat")
        )
      )
    )

  val perm0003_a4: doap = doap(
    iri = "http://rdfh.ch/permissions/00FF/PNTn7ZvsS_OabbexCxr_Eg",
    p = DefaultObjectAccessPermissionADM(
      iri = "http://rdfh.ch/permissions/00FF/PNTn7ZvsS_OabbexCxr_Eg",
      forProject = imagesProjectIri,
      forGroup = Some("http://rdfh.ch/groups/00FF/images-reviewer"),
      hasPermissions = Set(PermissionADM.deletePermission(OntologyConstants.KnoraAdmin.Creator))
    )
  )

  val perm002_d1: doap =
    doap(
      iri = "http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA",
        forProject = imagesProjectIri,
        forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
        hasPermissions = Set(
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser)
        )
      )
    )

  val perm002_d2: doap =
    doap(
      iri = "http://rdfh.ch/permissions/00FF/9XTMKHm_ScmwtgDXbF6Onw",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/9XTMKHm_ScmwtgDXbF6Onw",
        forProject = imagesProjectIri,
        forGroup = Some(OntologyConstants.KnoraAdmin.KnownUser),
        hasPermissions = Set(
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser)
        )
      )
    )

  /**
   * **********************************
   */
  /** Incunabula Project Permissions  * */
  /**
   * **********************************
   */
  val perm003_a1: ap =
    ap(
      iri = "http://rdfh.ch/permissions/00FF/kJ_xFUUJQLS9eJ3S9PazXQ",
      p = AdministrativePermissionADM(
        iri = "http://rdfh.ch/permissions/003-a1",
        forProject = SharedTestDataV1.incunabulaProjectIri,
        forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
        hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
      )
    )

  val perm003_a2: ap =
    ap(
      iri = "http://rdfh.ch/permissions/00FF/OySsjGn8QSqIpXUiSYnSSQ",
      p = AdministrativePermissionADM(
        iri = "http://rdfh.ch/permissions/003-a2",
        forProject = SharedTestDataV1.incunabulaProjectIri,
        forGroup = OntologyConstants.KnoraAdmin.ProjectAdmin,
        hasPermissions = Set(
          PermissionADM.ProjectResourceCreateAllPermission,
          PermissionADM.ProjectAdminAllPermission
        )
      )
    )

  val perm003_o1: oap =
    oap(
      iri = "http://rdfh.ch/0803/00014b43f902", // incunabula:page
      p = ObjectAccessPermissionADM(
        forResource = Some("http://rdfh.ch/0803/00014b43f902"),
        hasPermissions = Set(
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
        )
      )
    )

  val perm003_o2: oap =
    oap(
      iri = "http://rdfh.ch/0803/00014b43f902/values/1ad3999ad60b", // knora-base:TextValue
      p = ObjectAccessPermissionADM(
        forValue = Some("http://rdfh.ch/0803/00014b43f902/values/1ad3999ad60b"),
        hasPermissions = Set(
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.UnknownUser),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator)
        )
      )
    )

  val perm003_d1: doap =
    doap(
      iri = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ",
        forProject = SharedTestDataV1.incunabulaProjectIri,
        forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
        hasPermissions = Set(
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
        )
      )
    )

  val perm003_d2: doap =
    doap(
      iri = "http://rdfh.ch/permissions/00FF/sdHG20U6RoiwSu8MeAT1vA",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/sdHG20U6RoiwSu8MeAT1vA",
        forProject = SharedTestDataV1.incunabulaProjectIri,
        forResourceClass = Some(INCUNABULA_BOOK_RESOURCE_CLASS),
        hasPermissions = Set(
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
        )
      )
    )

  val perm003_d3: doap =
    doap(
      iri = "http://rdfh.ch/permissions/00FF/V7Fc9gnTRHWPN1xVXYVt9A",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/V7Fc9gnTRHWPN1xVXYVt9A",
        forProject = SharedTestDataV1.incunabulaProjectIri,
        forResourceClass = Some(INCUNABULA_PAGE_RESOURCE_CLASS),
        hasPermissions = Set(
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser)
        )
      )
    )

  val perm003_d4: doap =
    doap(
      iri = "http://rdfh.ch/permissions/00FF/T12XnPXxQ42jBMIf6RK1pg",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/T12XnPXxQ42jBMIf6RK1pg",
        forProject = SharedTestDataV1.incunabulaProjectIri,
        forProperty = Some(INCUNABULA_PartOf_Property),
        hasPermissions = Set(
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
        )
      )
    )

  val perm003_d5: doap =
    doap(
      iri = "http://rdfh.ch/permissions/00FF/5r5B_SJzTuCf8Hwj3MZmgw",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/5r5B_SJzTuCf8Hwj3MZmgw",
        forProject = SharedTestDataV1.incunabulaProjectIri,
        forResourceClass = Some(INCUNABULA_PAGE_RESOURCE_CLASS),
        forProperty = Some(INCUNABULA_PartOf_Property),
        hasPermissions = Set(
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember)
        )
      )
    )

  /**
   * *********************************
   */
  /** Anything Project Permissions   * */
  /**
   * *********************************
   */
  val perm005_a1: ap =
    ap(
      iri = "http://rdfh.ch/permissions/00FF/XFozeICsTE2gHSOsm4ZMIw",
      p = AdministrativePermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/XFozeICsTE2gHSOsm4ZMIw",
        forProject = SharedTestDataV1.anythingProjectIri,
        forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
        hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
      )
    )

  val perm005_a2: ap =
    ap(
      iri = "http://rdfh.ch/permissions/00FF/bsVy3VaOStWq_t8dvVMrdA",
      p = AdministrativePermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/bsVy3VaOStWq_t8dvVMrdA",
        forProject = SharedTestDataV1.anythingProjectIri,
        forGroup = OntologyConstants.KnoraAdmin.ProjectAdmin,
        hasPermissions = Set(
          PermissionADM.ProjectResourceCreateAllPermission,
          PermissionADM.ProjectAdminAllPermission
        )
      )
    )

  val perm005_d1: doap =
    doap(
      iri = "http://rdfh.ch/permissions/00FF/ui0_8nxjSEibtn2hQpCJVQ",
      p = DefaultObjectAccessPermissionADM(
        iri = "http://rdfh.ch/permissions/00FF/ui0_8nxjSEibtn2hQpCJVQ",
        forProject = SharedTestDataV1.anythingProjectIri,
        forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
        hasPermissions = Set(
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
          PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
        )
      )
    )
}
