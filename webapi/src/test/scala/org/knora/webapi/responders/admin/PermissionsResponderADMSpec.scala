/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.responders.admin

import java.util.UUID

import akka.actor.Status.{Failure, Success}
import akka.testkit.ImplicitSender
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.exceptions.DuplicateValueException
import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.{OntologyConstants, StringFormatter}
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM._
import org.knora.webapi.sharedtestdata.SharedPermissionsTestData._
import org.knora.webapi.sharedtestdata.SharedTestDataADM.{ANYTHING_PROJECT_IRI, _}
import org.knora.webapi.sharedtestdata.{SharedOntologyTestDataADM, SharedTestDataADM, SharedTestDataV1}
import org.knora.webapi.util.cache.CacheUtil
import org.scalatest.PrivateMethodTester

import scala.collection.Map
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


object PermissionsResponderADMSpec {

    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}


/**
  * This spec is used to test the [[PermissionsResponderADM]] actor.
  */
class PermissionsResponderADMSpec extends CoreSpec(PermissionsResponderADMSpec.config) with ImplicitSender with PrivateMethodTester {
    private val stringFormatter = StringFormatter.getGeneralInstance

    private val rootUser = SharedTestDataADM.rootUser
    private val multiuserUser = SharedTestDataADM.multiuserUser

    private val responderUnderTest = new PermissionsResponderADM(responderData)

    /* define private method access */
    private val userAdministrativePermissionsGetADM = PrivateMethod[Future[Map[IRI, Set[PermissionADM]]]]('userAdministrativePermissionsGetADM)
    private val defaultObjectAccessPermissionsForGroupsGetADM = PrivateMethod[Future[Set[PermissionADM]]]('defaultObjectAccessPermissionsForGroupsGetADM)

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "test_data/responders.admin.PermissionsResponderV1Spec/additional_permissions-data.ttl", name = "http://www.knora.org/data/permissions"),
        RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    "The PermissionsResponderADM" when {

        "queried about the permission profile" should {

            "return the permissions profile (root user)" in {
                responderManager ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.rootUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.rootUser.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = true,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.rootUser.permissionData)
            }

            "return the permissions profile (multi group user)" in {
                responderManager ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.multiuserUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.multiuserUser.groups,
                    isInProjectAdminGroups = Seq(INCUNABULA_PROJECT_IRI, IMAGES_PROJECT_IRI),
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.multiuserUser.permissionData)
            }

            "return the permissions profile (incunabula project admin user)" in {
                responderManager ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.incunabulaProjectAdminUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.incunabulaProjectAdminUser.groups,
                    isInProjectAdminGroups = Seq(INCUNABULA_PROJECT_IRI),
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.incunabulaProjectAdminUser.permissionData)
            }

            "return the permissions profile (incunabula creator user)" in {
                responderManager ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.incunabulaProjectAdminUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.incunabulaCreatorUser.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.incunabulaCreatorUser.permissionData)
            }

            "return the permissions profile (incunabula normal project member user)" in {
                responderManager ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.incunabulaProjectAdminUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.incunabulaMemberUser.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.incunabulaMemberUser.permissionData)
            }

            "return the permissions profile (images user 01)" in {
                responderManager ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.imagesUser01.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.imagesUser01.groups,
                    isInProjectAdminGroups = Seq(IMAGES_PROJECT_IRI),
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.imagesUser01.permissionData)
            }

            "return the permissions profile (images-reviewer-user)" in {
                responderManager ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.imagesReviewerUser.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.imagesReviewerUser.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.imagesReviewerUser.permissionData)
            }

            "return the permissions profile (anything user 01)" in {
                responderManager ! PermissionDataGetADM(
                    projectIris = SharedTestDataV1.anythingUser1.projects_info.keys.toSeq,
                    groupIris = SharedTestDataV1.anythingUser1.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = false,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(SharedTestDataV1.anythingUser1.permissionData)
            }

            "return user's administrative permissions (helper method used in queries before)" in {
                val f: Future[Map[IRI, Set[PermissionADM]]] = responderUnderTest invokePrivate userAdministrativePermissionsGetADM(multiuserUser.permissions.groupsPerProject)
                val result: Map[IRI, Set[PermissionADM]] = Await.result(f, 1.seconds)
                result should equal(multiuserUser.permissions.administrativePermissionsPerProject)
            }
        }

        "queried about administrative permissions " should {

            "return all AdministrativePermissions for project " in {
                responderManager ! AdministrativePermissionsForProjectGetRequestADM(
                    projectIri = IMAGES_PROJECT_IRI,
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                expectMsg(AdministrativePermissionsForProjectGetResponseADM(
                    Seq(perm002_a1.p, perm002_a3.p, perm002_a2.p)
                ))
            }

            "return AdministrativePermission for project and group" in {
                responderManager ! AdministrativePermissionForProjectGroupGetRequestADM(
                    projectIri = IMAGES_PROJECT_IRI,
                    groupIri = OntologyConstants.KnoraAdmin.ProjectMember,
                    requestingUser = rootUser
                )
                expectMsg(AdministrativePermissionForProjectGroupGetResponseADM(perm002_a1.p))
            }

            "return AdministrativePermission for IRI " in {
                responderManager ! AdministrativePermissionForIriGetRequestADM(
                    administrativePermissionIri = perm002_a1.iri,
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                expectMsg(AdministrativePermissionForIriGetResponseADM(perm002_a1.p))
            }

        }

        "asked to create an administrative permission" should {
            "fail and return a 'DuplicateValueException' when permission for project and group combination already exists" in {
                responderManager ! AdministrativePermissionCreateRequestADM(
                    createRequest = CreateAdministrativePermissionAPIRequestADM(
                        forProject = IMAGES_PROJECT_IRI,
                        forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
                        hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
                    ),
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                expectMsg(Failure(DuplicateValueException(s"Permission for project: '$IMAGES_PROJECT_IRI' and group: '${OntologyConstants.KnoraAdmin.ProjectMember}' combination already exists.")))
            }

            "create and return an administrative permission with a custom IRI" in {
                responderManager ! AdministrativePermissionCreateRequestADM(
                    createRequest = CreateAdministrativePermissionAPIRequestADM(
                        id = Some("http://rdfh.ch/permissions/0001/AP-with-customIri"),
                        forProject = ANYTHING_PROJECT_IRI,
                        forGroup = SharedTestDataADM.thingSearcherGroup.id,
                        hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
                    ),
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                val received: AdministrativePermissionCreateResponseADM = expectMsgType[AdministrativePermissionCreateResponseADM]
                assert(received.administrativePermission.iri == "http://rdfh.ch/permissions/0001/AP-with-customIri")
                assert(received.administrativePermission.forProject == ANYTHING_PROJECT_IRI)
                assert(received.administrativePermission.forGroup == SharedTestDataADM.thingSearcherGroup.id)
            }
        }

        "queried about object access permissions " should {

            "return object access permissions for a resource" in {
                responderManager ! ObjectAccessPermissionsForResourceGetADM(
                    resourceIri = perm003_o1.iri,
                    requestingUser = rootUser
                )
                expectMsg(Some(perm003_o1.p))
            }

            "return object access permissions for a value" in {
                responderManager ! ObjectAccessPermissionsForValueGetADM(
                    valueIri = perm003_o2.iri,
                    requestingUser = rootUser
                )
                expectMsg(Some(perm003_o2.p))
            }

        }

        "queried about default object access permissions " should {

            "return all DefaultObjectAccessPermissions for project" in {
                responderManager ! DefaultObjectAccessPermissionsForProjectGetRequestADM(
                    projectIri = IMAGES_PROJECT_IRI,
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )

                expectMsg(DefaultObjectAccessPermissionsForProjectGetResponseADM(
                    defaultObjectAccessPermissions = Seq(perm002_d1.p, perm0003_a4.p, perm002_d2.p)
                ))
            }

            "return DefaultObjectAccessPermission for IRI" in {
                responderManager ! DefaultObjectAccessPermissionForIriGetRequestADM(
                    defaultObjectAccessPermissionIri = perm002_d1.iri,
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                expectMsg(DefaultObjectAccessPermissionForIriGetResponseADM(
                    defaultObjectAccessPermission = perm002_d1.p
                ))
            }

            "return DefaultObjectAccessPermission for project and group" in {
                responderManager ! DefaultObjectAccessPermissionGetRequestADM(
                    projectIri = INCUNABULA_PROJECT_IRI,
                    groupIri = Some(OntologyConstants.KnoraAdmin.ProjectMember),
                    resourceClassIri = None,
                    propertyIri = None,
                    requestingUser = rootUser
                )
                expectMsg(DefaultObjectAccessPermissionGetResponseADM(
                    defaultObjectAccessPermission = perm003_d1.p
                ))
            }

            "return DefaultObjectAccessPermission for project and resource class ('incunabula:Page')" in {
                responderManager ! DefaultObjectAccessPermissionGetRequestADM(
                    projectIri = INCUNABULA_PROJECT_IRI,
                    groupIri = None,
                    resourceClassIri = Some(INCUNABULA_BOOK_RESOURCE_CLASS),
                    propertyIri = None,
                    requestingUser = rootUser
                )
                expectMsg(DefaultObjectAccessPermissionGetResponseADM(
                    defaultObjectAccessPermission = perm003_d2.p
                ))
            }

            "return DefaultObjectAccessPermission for project and property ('knora-base:hasStillImageFileValue') (system property)" in {
                responderManager ! DefaultObjectAccessPermissionGetRequestADM(
                    projectIri = INCUNABULA_PROJECT_IRI,
                    groupIri = None,
                    resourceClassIri = None,
                    propertyIri = Some(OntologyConstants.KnoraBase.HasStillImageFileValue),
                    requestingUser = rootUser
                )
                expectMsg(DefaultObjectAccessPermissionGetResponseADM(
                    defaultObjectAccessPermission = perm001_d3.p
                ))
            }

            "cache DefaultObjectAccessPermission" in {
                responderManager ! DefaultObjectAccessPermissionGetRequestADM(
                    projectIri = INCUNABULA_PROJECT_IRI,
                    groupIri = None,
                    resourceClassIri = None,
                    propertyIri = Some(OntologyConstants.KnoraBase.HasStillImageFileValue),
                    requestingUser = rootUser
                )
                expectMsg(DefaultObjectAccessPermissionGetResponseADM(
                    defaultObjectAccessPermission = perm001_d3.p
                ))

                val key = perm001_d3.p.cacheKey
                val maybePermission = CacheUtil.get[DefaultObjectAccessPermissionADM](PermissionsMessagesUtilADM.PermissionsCacheName, key)
                maybePermission should be (Some(perm001_d3.p))
            }
        }

        "asked to create a default object access permission" should {

            "create a DefaultObjectAccessPermission for project and group" in {
                responderManager ! DefaultObjectAccessPermissionCreateRequestADM(
                    createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
                        forProject = ANYTHING_PROJECT_IRI,
                        forGroup = Some(SharedTestDataADM.thingSearcherGroup.id),
                        hasPermissions = Set(PermissionADM.restrictedViewPermission(SharedTestDataADM.thingSearcherGroup.id))
                    ),
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                val received: DefaultObjectAccessPermissionCreateResponseADM = expectMsgType[DefaultObjectAccessPermissionCreateResponseADM]
                assert(received.defaultObjectAccessPermission.forProject == ANYTHING_PROJECT_IRI)
                assert(received.defaultObjectAccessPermission.forGroup.contains(SharedTestDataADM.thingSearcherGroup.id))
                assert(received.defaultObjectAccessPermission.hasPermissions.contains(PermissionADM.restrictedViewPermission(SharedTestDataADM.thingSearcherGroup.id)))
            }

            "create a DefaultObjectAccessPermission for project and group with custom IRI" in {
                responderManager ! DefaultObjectAccessPermissionCreateRequestADM(
                    createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
                        id = Some("http://rdfh.ch/permissions/0001/DOAP-with-customIri"),
                        forProject = ANYTHING_PROJECT_IRI,
                        forGroup = Some(OntologyConstants.KnoraAdmin.UnknownUser),
                        hasPermissions = Set(PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser))
                    ),
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                val received: DefaultObjectAccessPermissionCreateResponseADM = expectMsgType[DefaultObjectAccessPermissionCreateResponseADM]
                assert(received.defaultObjectAccessPermission.iri == "http://rdfh.ch/permissions/0001/DOAP-with-customIri")
                assert(received.defaultObjectAccessPermission.forProject == ANYTHING_PROJECT_IRI)
                assert(received.defaultObjectAccessPermission.forGroup.contains(OntologyConstants.KnoraAdmin.UnknownUser))
                assert(received.defaultObjectAccessPermission.hasPermissions.contains(PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser)))
            }

            "create a DefaultObjectAccessPermission for project and resource class" in {
                responderManager ! DefaultObjectAccessPermissionCreateRequestADM(
                    createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
                        forProject = IMAGES_PROJECT_IRI,
                        forResourceClass = Some(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
                        hasPermissions = Set(PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.KnownUser))
                    ),
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                val received: DefaultObjectAccessPermissionCreateResponseADM = expectMsgType[DefaultObjectAccessPermissionCreateResponseADM]
                assert(received.defaultObjectAccessPermission.forProject == IMAGES_PROJECT_IRI)
                assert(received.defaultObjectAccessPermission.forResourceClass.contains(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS))
                assert(received.defaultObjectAccessPermission.hasPermissions.contains(PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.KnownUser)))

            }

            "create a DefaultObjectAccessPermission for project and property" in {
                responderManager ! DefaultObjectAccessPermissionCreateRequestADM(
                    createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
                        forProject = IMAGES_PROJECT_IRI,
                        forProperty = Some(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
                        hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator))
                    ),
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                val received: DefaultObjectAccessPermissionCreateResponseADM = expectMsgType[DefaultObjectAccessPermissionCreateResponseADM]
                assert(received.defaultObjectAccessPermission.forProject == IMAGES_PROJECT_IRI)
                assert(received.defaultObjectAccessPermission.forProperty.contains(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY))
                assert(received.defaultObjectAccessPermission.hasPermissions.contains(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator)))
            }

            "fail and return a 'DuplicateValueException' when permission for project / group / resource class / property  combination already exists" in {
                responderManager ! DefaultObjectAccessPermissionCreateRequestADM(
                    createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
                        forProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
                        forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
                        hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember))
                    ),
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                expectMsg(Failure(DuplicateValueException(s"Default object access permission already exists.")))
            }
        }

        "asked to get all permissions" should {

            "return all permissions for 'image' project " in {
                responderManager ! PermissionsForProjectGetRequestADM(
                    projectIri = IMAGES_PROJECT_IRI,
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                val received: PermissionsForProjectGetResponseADM = expectMsgType[PermissionsForProjectGetResponseADM]
                received.allPermissions.size should be (8)
            }

            "return all permissions for 'incunabula' project " in {
                responderManager ! PermissionsForProjectGetRequestADM(
                    projectIri = INCUNABULA_PROJECT_IRI,
                    requestingUser = rootUser,
                    apiRequestID = UUID.randomUUID()
                )
                expectMsg(PermissionsForProjectGetResponseADM( allPermissions =
                    Set(PermissionInfoADM(perm003_a1.iri, OntologyConstants.KnoraAdmin.AdministrativePermission),
                        PermissionInfoADM(perm003_a2.iri, OntologyConstants.KnoraAdmin.AdministrativePermission),
                        PermissionInfoADM(perm003_d1.iri, OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission),
                        PermissionInfoADM(perm003_d2.iri, OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission),
                        PermissionInfoADM(perm003_d3.iri, OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission)
                    )
                ))
            }
        }

        "asked to delete a permission object " should {

            "delete an administrative permission " ignore {}

            "delete a default object access permission " ignore {}
        }

        "asked for default object access permissions 'string'" should {

            "return the default object access permissions 'string' for the 'knora-base:LinkObj' resource class (system resource class)" in {
                responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = INCUNABULA_PROJECT_IRI, resourceClassIri = OntologyConstants.KnoraBase.LinkObj,
                    targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'knora-base:hasStillImageFileValue' property (system property)" in {
                responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = INCUNABULA_PROJECT_IRI,
                    resourceClassIri = OntologyConstants.KnoraBase.StillImageRepresentation,
                    propertyIri = OntologyConstants.KnoraBase.HasStillImageFileValue,
                    targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("M knora-admin:Creator,knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'incunabula:book' resource class (project resource class)" in {
                responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = INCUNABULA_PROJECT_IRI, resourceClassIri = INCUNABULA_BOOK_RESOURCE_CLASS,
                    targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'incunabula:page' resource class (project resource class)" in {
                responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = INCUNABULA_PROJECT_IRI, resourceClassIri = INCUNABULA_PAGE_RESOURCE_CLASS,
                    targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'images:jahreszeit' property" in {
                responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = IMAGES_PROJECT_IRI,
                    resourceClassIri = s"$IMAGES_ONTOLOGY_IRI#bild",
                    propertyIri = s"$IMAGES_ONTOLOGY_IRI#jahreszeit",
                    targetUser = SharedTestDataADM.imagesUser01,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser"))
            }

            "return the default object access permissions 'string' for the 'anything:hasInterval' property" in {
                responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = ANYTHING_PROJECT_IRI,
                    resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                    propertyIri = "http://www.knora.org/ontology/0001/anything#hasInterval",
                    targetUser = SharedTestDataADM.anythingUser2,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'anything:Thing' class" in {
                responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = ANYTHING_PROJECT_IRI,
                    resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                    targetUser = SharedTestDataADM.anythingUser2,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'anything:Thing' class and 'anything:hasText' property" in {
                responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = ANYTHING_PROJECT_IRI,
                    resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                    propertyIri = "http://www.knora.org/ontology/0001/anything#hasText",
                    targetUser = SharedTestDataADM.anythingUser1,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator"))
            }

            "return the default object access permissions 'string' for the 'images:Bild' class and 'anything:hasText' property" in {
                responderManager ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = ANYTHING_PROJECT_IRI,
                    resourceClassIri = s"$IMAGES_ONTOLOGY_IRI#bild",
                    propertyIri = "http://www.knora.org/ontology/0001/anything#hasText",
                    targetUser = SharedTestDataADM.anythingUser2,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
            }

            "return the default object access permissions 'string' for the 'anything:Thing' resource class for the root user (system admin and not member of project)" in {
                responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = ANYTHING_PROJECT_IRI, resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                    targetUser = SharedTestDataADM.rootUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
                expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
            }

            "return a combined and max set of permissions (default object access permissions) defined on the supplied groups (helper method used in queries before)" in {
                val groups = List("http://rdfh.ch/groups/images-reviewer", s"${OntologyConstants.KnoraAdmin.ProjectMember}", s"${OntologyConstants.KnoraAdmin.ProjectAdmin}")
                val expected = Set(
                        PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
                        PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
                        PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember)
                    )
                val f: Future[Set[PermissionADM]] = responderUnderTest invokePrivate defaultObjectAccessPermissionsForGroupsGetADM(IMAGES_PROJECT_IRI, groups)
                val result: Set[PermissionADM] = Await.result(f, 1.seconds)
                result should equal(expected)
            }
        }
    }
}
