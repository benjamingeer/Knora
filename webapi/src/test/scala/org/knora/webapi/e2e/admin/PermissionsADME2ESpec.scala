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

package org.knora.webapi.e2e.admin

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionADM
import org.knora.webapi.sharedtestdata.{SharedOntologyTestDataADM, SharedTestDataADM, SharedTestDataV1}
import org.knora.webapi.util.AkkaHttpUtils
import spray.json._

import scala.concurrent.duration._

object PermissionsADME2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing the 'v1/permissions' route.
  *
  * This spec tests the 'v1/store' route.
  */
class PermissionsADME2ESpec extends E2ESpec(PermissionsADME2ESpec.config) with TriplestoreJsonProtocol {

    "The Permissions Route ('admin/permissions')" when {

        "getting permissions" should {
            "return a group's administrative permissions" in {

                val projectIri = java.net.URLEncoder.encode(SharedTestDataV1.imagesProjectInfo.id, "utf-8")
                val groupIri = java.net.URLEncoder.encode(OntologyConstants.KnoraAdmin.ProjectMember, "utf-8")
                val request = Get(baseApiUrl + s"/admin/permissions/ap/$projectIri/$groupIri") ~> addCredentials(BasicHttpCredentials(
                    SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
                val response = singleAwaitingRequest(request, 1.seconds)
                logger.debug("==>> " + response.toString)
                assert(response.status === StatusCodes.OK)
                val result = AkkaHttpUtils.httpResponseToJson(response).fields("administrative_permission").asJsObject.fields
                val iri = result.getOrElse("iri", throw DeserializationException("The expected field 'iri' is missing.")).convertTo[String]
                assert(iri == "http://rdfh.ch/permissions/00FF/a1")
            }

            "return a project's administrative permissions" in {

                val projectIri = java.net.URLEncoder.encode(SharedTestDataV1.imagesProjectInfo.id, "utf-8")
                val request = Get(baseApiUrl + s"/admin/permissions/ap/$projectIri") ~> addCredentials(BasicHttpCredentials(
                    SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
                val response = singleAwaitingRequest(request, 1.seconds)
                logger.debug("==>> " + response.toString)
                assert(response.status === StatusCodes.OK)
                val result = AkkaHttpUtils.httpResponseToJson(response).fields("administrative_permissions")
                result.asInstanceOf[JsArray].elements.size should be (3)
            }

            "return a project's default object access permissions" in {

                val projectIri = java.net.URLEncoder.encode(SharedTestDataV1.imagesProjectInfo.id, "utf-8")
                val request = Get(baseApiUrl + s"/admin/permissions/doap/$projectIri") ~> addCredentials(BasicHttpCredentials(
                    SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
                val response = singleAwaitingRequest(request, 1.seconds)
                logger.debug("==>> " + response.toString)
                assert(response.status === StatusCodes.OK)
                val result = AkkaHttpUtils.httpResponseToJson(response).fields("default_object_access_permissions")
                result.asInstanceOf[JsArray].elements.size should be (3)
            }

            "return a project's all permissions" in {

                val projectIri = java.net.URLEncoder.encode(SharedTestDataV1.imagesProjectInfo.id, "utf-8")
                val request = Get(baseApiUrl + s"/admin/permissions/$projectIri") ~> addCredentials(BasicHttpCredentials(
                    SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
                val response = singleAwaitingRequest(request, 1.seconds)
                logger.debug("==>> " + response.toString)
                assert(response.status === StatusCodes.OK)
                val result = AkkaHttpUtils.httpResponseToJson(response).fields("permissions")
                result.asInstanceOf[JsArray].elements.size should be (6)
            }

        }

        "creating permissions" should {
            "create an administrative access permission" in {

                val request = Post(baseApiUrl + s"/admin/permissions/ap", HttpEntity(ContentTypes.`application/json`, SharedTestDataADM.createAdministrativePermissionRequest)) ~> addCredentials(BasicHttpCredentials(
                    SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                assert(response.status === StatusCodes.OK)

                val result = AkkaHttpUtils.httpResponseToJson(response).fields("administrative_permission").asJsObject.fields
                val groupIri = result.getOrElse("forGroup", throw DeserializationException("The expected field 'forGroup' is missing.")).convertTo[String]
                assert(groupIri == "http://rdfh.ch/groups/0001/thing-searcher")
                val projectIri = result.getOrElse("forProject", throw DeserializationException("The expected field 'forProject' is missing.")).convertTo[String]
                assert(projectIri == "http://rdfh.ch/projects/0001")
                val permissions = result.getOrElse("hasPermissions", throw DeserializationException("The expected field 'hasPermissions' is missing.")).toString()
                assert(permissions.contains("ProjectAdminGroupAllPermission"))
            }

            "create a default object access permission" in {

                val request = Post(baseApiUrl + s"/admin/permissions/doap", HttpEntity(ContentTypes.`application/json`, SharedTestDataADM.createDefaultObjectAccessPermissionRequest)) ~> addCredentials(BasicHttpCredentials(
                    SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                assert(response.status === StatusCodes.OK)

                val result = AkkaHttpUtils.httpResponseToJson(response).fields("default_object_access_permission").asJsObject.fields
                val groupIri = result.getOrElse("forGroup", throw DeserializationException("The expected field 'forGroup' is missing.")).convertTo[String]
                assert(groupIri == "http://rdfh.ch/groups/0001/thing-searcher")
                val projectIri = result.getOrElse("forProject", throw DeserializationException("The expected field 'forProject' is missing.")).convertTo[String]
                assert(projectIri == "http://rdfh.ch/projects/0001")
                val permissions = result.getOrElse("hasPermissions", throw DeserializationException("The expected field 'hasPermissions' is missing.")).toString()
                assert(permissions.contains("http://www.knora.org/ontology/knora-admin#ProjectMember"))
            }

            "create a default object access permission with a custom IRI" in {

                val request = Post(baseApiUrl + s"/admin/permissions/doap", HttpEntity(ContentTypes.`application/json`,
                    SharedTestDataADM.createDefaultObjectAccessPermissionWithCustomIriRequest)) ~> addCredentials(BasicHttpCredentials(
                    SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                assert(response.status === StatusCodes.OK)

                val result = AkkaHttpUtils.httpResponseToJson(response).fields("default_object_access_permission").asJsObject.fields
                val permissionIri = result.getOrElse("iri", throw DeserializationException("The expected field 'iri' is missing.")).convertTo[String]
                assert(permissionIri == "http://rdfh.ch/permissions/00FF/DOAP-with-customIri")
                val forResourceClassIRI = result.getOrElse("forResourceClass", throw DeserializationException("The expected field 'forResourceClass' is missing.")).convertTo[String]
                assert(forResourceClassIRI == SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS)
                val projectIri = result.getOrElse("forProject", throw DeserializationException("The expected field 'forProject' is missing.")).convertTo[String]
                assert(projectIri == "http://rdfh.ch/projects/00FF")
                val permissions = result.getOrElse("hasPermissions", throw DeserializationException("The expected field 'hasPermissions' is missing.")).toString()
                assert(permissions.contains("http://www.knora.org/ontology/knora-admin#ProjectMember"))
            }
        }
    }
}
