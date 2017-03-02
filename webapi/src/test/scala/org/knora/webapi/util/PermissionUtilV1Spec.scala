/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.v1.responder.permissionmessages.{PermissionType, PermissionV1}
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.{CoreSpec, IRI, OntologyConstants, SharedAdminTestData}

import scala.collection.Map

object PermissionUtilV1Spec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}
class PermissionUtilV1Spec extends CoreSpec("PermissionUtilSpec") with ImplicitSender with Authenticator {

    val permissionLiteral = "RV knora-admin:UnknownUser|V knora-admin:KnownUser|M knora-admin:ProjectMember|CR knora-admin:Creator"

    val parsedPermissionLiteral = Map(
        "RV" -> Set(OntologyConstants.KnoraAdmin.UnknownUser),
        "V" -> Set(OntologyConstants.KnoraAdmin.KnownUser),
        "M" -> Set(OntologyConstants.KnoraAdmin.ProjectMember),
        "CR" -> Set(OntologyConstants.KnoraAdmin.Creator)
    )

    "PermissionUtil " should {

        "return user's max permission for a specific resource (incunabula normal project member user)" in {
            PermissionUtilV1.getUserPermissionV1(
                subjectIri = "http://data.knora.org/00014b43f902",
                subjectCreator = "http://data.knora.org/users/91e19f1e01",
                subjectProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                subjectPermissionLiteral = Some(permissionLiteral),
                userProfile = SharedAdminTestData.incunabulaMemberUser
            ) should equal(Some(6)) // modify permission
        }

        "return user's max permission for a specific resource (incunabula project admin user)" in {
            PermissionUtilV1.getUserPermissionV1(
                subjectIri = "http://data.knora.org/00014b43f902",
                subjectCreator = "http://data.knora.org/users/91e19f1e01",
                subjectProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                subjectPermissionLiteral = Some(permissionLiteral),
                userProfile = SharedAdminTestData.incunabulaProjectAdminUser
            ) should equal(Some(8)) // change rights permission
        }

        "return user's max permission for a specific resource (incunabula creator user)" in {
            PermissionUtilV1.getUserPermissionV1(
                subjectIri = "http://data.knora.org/00014b43f902",
                subjectCreator = "http://data.knora.org/users/91e19f1e01",
                subjectProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                subjectPermissionLiteral = Some(permissionLiteral),
                userProfile = SharedAdminTestData.incunabulaCreatorUser
            ) should equal(Some(8)) // change rights permission
        }

        "return user's max permission for a specific resource (root user)" in {
            PermissionUtilV1.getUserPermissionV1(
                subjectIri = "http://data.knora.org/00014b43f902",
                subjectCreator = "http://data.knora.org/users/91e19f1e01",
                subjectProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                subjectPermissionLiteral = Some(permissionLiteral),
                userProfile = SharedAdminTestData.rootUser
            ) should equal(Some(8)) // change rights permission
        }

        "return user's max permission for a specific resource (normal user)" in {
            PermissionUtilV1.getUserPermissionV1(
                subjectIri = "http://data.knora.org/00014b43f902",
                subjectCreator = "http://data.knora.org/users/91e19f1e01",
                subjectProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                subjectPermissionLiteral = Some(permissionLiteral),
                userProfile = SharedAdminTestData.normalUser
            ) should equal(Some(2)) // restricted view permission
        }

        "return user's max permission for a specific resource (anonymous user)" in {
            PermissionUtilV1.getUserPermissionV1(
                subjectIri = "http://data.knora.org/00014b43f902",
                subjectCreator = "http://data.knora.org/users/91e19f1e01",
                subjectProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                subjectPermissionLiteral = Some(permissionLiteral),
                userProfile = SharedAdminTestData.anonymousUser
            ) should equal(Some(1)) // restricted view permission
        }

        "return user's max permission from assertions for a specific resource" in {
            val assertions: Seq[(IRI, String)] = Seq(
                (OntologyConstants.KnoraAdmin.AttachedToUser, "http://data.knora.org/users/91e19f1e01"),
                (OntologyConstants.KnoraAdmin.AttachedToProject, SharedAdminTestData.INCUNABULA_PROJECT_IRI),
                (OntologyConstants.KnoraBase.HasPermissions, permissionLiteral)
            )
            PermissionUtilV1.getUserPermissionV1FromAssertions(
                subjectIri = "http://data.knora.org/00014b43f902",
                assertions = assertions,
                userProfile = SharedAdminTestData.incunabulaMemberUser
            ) should equal(Some(6)) // modify permissions
        }

        "return user's max permission on link value with value props (1)" ignore {

            "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"


        }

        "return user's max permission on link value with value props (2)" ignore {

            /* ?OLD?
            "incunabula:partOf"
            "V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember|D knora-admin:Owner"
            "http://data.knora.org/users/91e19f1e01"


            PermissionUtilV1.getUserPermissionOnLinkValueV1WithValueProps(
                linkValueIri = "http://data.knora.org/00014b43f902/values/a3a1ec4d-84b5-4769-83ab-319a1cfcf8a3",
                predicateIri = "incunabula:partOf",
                valueProps = ,
                userProfile = SharedAdminTestData.incunabulaUser
            ) should equal(Some(5))
            */
        }



        "return user's max permission on link value" ignore {

        }

        "return parsed permissions string as 'Map[IRI, Set[String]]" in {
            PermissionUtilV1.parsePermissions(Some(permissionLiteral)) should equal(parsedPermissionLiteral)
        }


        "return parsed permissions string as 'Set[PermissionV1]'" in {
            val hasPermissionsString = "M knora-admin:Creator,knora-admin:ProjectMember|V knora-admin:KnownUser,http://data.knora.org/groups/customgroup|RV knora-admin:UnknownUser"

            val permissionsSet = Set(
                PermissionV1.modifyPermission(OntologyConstants.KnoraAdmin.Creator),
                PermissionV1.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
                PermissionV1.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
                PermissionV1.viewPermission("http://data.knora.org/groups/customgroup"),
                PermissionV1.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser)
            )

            PermissionUtilV1.parsePermissionsWithType(Some(hasPermissionsString), PermissionType.OAP) should equal(permissionsSet)
        }

        "build a 'PermissionV1' object" in {
            PermissionUtilV1.buildPermissionObject(
                name = OntologyConstants.KnoraBase.ProjectResourceCreateRestrictedPermission,
                iris = Set("1", "2", "3")
            ) should equal(
                Set(
                    PermissionV1.projectResourceCreateRestrictedPermission("1"),
                    PermissionV1.projectResourceCreateRestrictedPermission("2"),
                    PermissionV1.projectResourceCreateRestrictedPermission("3")
                )
            )
        }

        "remove duplicate permissions" in {

            val duplicatedPermissions = Seq(
                PermissionV1.restrictedViewPermission("1"),
                PermissionV1.restrictedViewPermission("1"),
                PermissionV1.restrictedViewPermission("2"),
                PermissionV1.changeRightsPermission("2"),
                PermissionV1.changeRightsPermission("3"),
                PermissionV1.changeRightsPermission("3")
            )

            val deduplicatedPermissions = Set(
                PermissionV1.restrictedViewPermission("1"),
                PermissionV1.restrictedViewPermission("2"),
                PermissionV1.changeRightsPermission("2"),
                PermissionV1.changeRightsPermission("3")
            )

            val result = PermissionUtilV1.removeDuplicatePermissions(duplicatedPermissions)
            result.size should equal(deduplicatedPermissions.size)
            result should contain allElementsOf deduplicatedPermissions

        }

        "remove lesser permissions" in {
            val withLesserPermissions = Set(
                PermissionV1.restrictedViewPermission("1"),
                PermissionV1.viewPermission("1"),
                PermissionV1.modifyPermission("2"),
                PermissionV1.changeRightsPermission("1"),
                PermissionV1.deletePermission("2")
            )

            val withoutLesserPermissions = Set(
                PermissionV1.changeRightsPermission("1"),
                PermissionV1.deletePermission("2")
            )

            val result = PermissionUtilV1.removeLesserPermissions(withLesserPermissions, PermissionType.OAP)
            result.size should equal(withoutLesserPermissions.size)
            result should contain allElementsOf withoutLesserPermissions
        }

        "create permissions string" in {
            val permissions = Set(
                PermissionV1.changeRightsPermission("1"),
                PermissionV1.deletePermission("2"),
                PermissionV1.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
                PermissionV1.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
                PermissionV1.viewPermission(OntologyConstants.KnoraAdmin.KnownUser)
            )

            val permissionsString = "CR knora-admin:Creator,1|D 2|M knora-admin:ProjectMember|V knora-admin:KnownUser"

            val result = PermissionUtilV1.formatPermissions(permissions, PermissionType.OAP)
            result should equal(Some(permissionsString))

        }
    }
}
