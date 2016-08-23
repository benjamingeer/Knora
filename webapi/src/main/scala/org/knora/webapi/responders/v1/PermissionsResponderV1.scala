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

package org.knora.webapi.responders.v1

import akka.actor.Status
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.permissionmessages.PermissionType.PermissionType
import org.knora.webapi.messages.v1.responder.permissionmessages.PermissionsTemplate.PermissionsTemplate
import org.knora.webapi.messages.v1.responder.permissionmessages.{AdministrativePermissionV1, PermissionType, _}
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{KnoraIriUtil, MessageUtil}
import shapeless.get

import scala.concurrent.Future


/**
  * Provides information about Knora users to other responders.
  */
class PermissionsResponderV1 extends ResponderV1 {

    // Creates IRIs for new Knora user objects.
    val knoraIriUtil = new KnoraIriUtil

    /**
      * Receives a message extending [[org.knora.webapi.messages.v1.responder.permissionmessages.PermissionsResponderRequestV1]].
      * If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case AdministrativePermissionIrisForProjectGetRequestV1(projectIri, userProfileV1) => future2Message(sender(), getProjectAdministrativePermissionIrisV1(projectIri, userProfileV1), log)
        case AdministrativePermissionGetRequestV1(administrativePermissionIri) => future2Message(sender(), getAdministrativePermissionV1(administrativePermissionIri), log)
        case AdministrativePermissionCreateRequestV1(newAdministrativePermissionV1, userProfileV1) => future2Message(sender(), createAdministrativePermissionV1(newAdministrativePermissionV1, userProfileV1), log)
        case AdministrativePermissionDeleteRequestV1(administrativePermissionIri, userProfileV1) => future2Message(sender(), deleteAdministrativePermissionV1(administrativePermissionIri, userProfileV1), log)
        case DefaultObjectAccessPermissionIrisForProjectGetRequestV1(projectIri, userProfileV1) => future2Message(sender(), getProjectDefaultObjectAccessPermissionsV1(projectIri, userProfileV1), log)
        case DefaultObjectAccessPermissionGetRequestV1(defaultObjectAccessPermissionIri) => future2Message(sender(), getDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri), log)
        case DefaultObjectAccessPermissionCreateRequestV1(newDefaultObjectAccessPermissionV1, userProfileV1) => future2Message(sender(), createDefaultObjectAccessPermissionV1(newDefaultObjectAccessPermissionV1, userProfileV1), log)
        case DefaultObjectAccessPermissionDeleteRequestV1(defaultObjectAccessPermissionIri, userProfileV1) => future2Message(sender(), deleteDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri, userProfileV1), log)
        case TemplatePermissionsCreateRequestV1(projectIri, permissionsTemplate, userProfileV1) => future2Message(sender(), templatePermissionsCreateRequestV1(projectIri, permissionsTemplate, userProfileV1), log)
        case GetUserAdministrativePermissionsRequestV1(projectGroups) => future2Message(sender(), getUserAdministrativePermissionsRequestV1(projectGroups), log)
        case GetUserDefaultObjectAccessPermissionsRequestV1(projectGroups) => future2Message(sender(), getUserDefaultObjectAccessPermissionsRequestV1(projectGroups), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Gets all IRI's of all administrative permissions defined inside a project.
      *
      * @param forProject the IRI of the project.
      * @param userProfileV1 the [[UserProfileV1]] of the requesting user.
      * @return a list of IRIs of [[AdministrativePermissionV1]] objects.
      */
    private def getProjectAdministrativePermissionIrisV1(forProject: IRI, userProfileV1: UserProfileV1): Future[Seq[IRI]] = {
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjectAdministrativePermissions(
                triplestore = settings.triplestoreType,
                projectIri = forProject
            ).toString())
            //_ = log.debug(s"getProjectAdministrativePermissionsV1 - query: $sparqlQueryString")

            permissionsQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getProjectAdministrativePermissionsV1 - result: ${MessageUtil.toSource(permissionsQueryResponse)}")

            permissionsQueryResponseRows: Seq[VariableResultsRow] = permissionsQueryResponse.results.bindings

            apIris: List[String] = permissionsQueryResponseRows.map(_.rowMap("s")).toList
            //_ = log.debug(s"getProjectAdministrativePermissionsV1 - iris: $apIris")

            administrativePermissionIris = if (apIris.nonEmpty) {
                apIris
            } else {
                Vector.empty[IRI]
            }

        } yield administrativePermissionIris
    }

    /**
      * Gets a single administrative permission identified by it's IRI.
      *
      * @param administrativePermissionIRI the IRI of the administrative permission.
      *
      * @return a single [[AdministrativePermissionV1]] object.
      */
    private def getAdministrativePermissionV1(administrativePermissionIRI: IRI): Future[Option[AdministrativePermissionV1]] = {

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getAdministrativePermission(
                triplestore = settings.triplestoreType,
                administrativePermissionIri = administrativePermissionIRI
            ).toString())
            //_ = log.debug(s"getAdministrativePermissionV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getAdministrativePermissionV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            _ = log.debug(s"getAdministrativePermissionV1 - groupedResult: ${MessageUtil.toSource(groupedPermissionsQueryResponse)}")

            hasPermissions = parsePermissions(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.AP)

            _ = log.debug(s"getAdministrativePermissionV1 - hasPermissions: ${MessageUtil.toSource(hasPermissions)}")

            administrativePermission = if (groupedPermissionsQueryResponse.nonEmpty) {
                Some(AdministrativePermissionV1 (
                    forProject = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission $administrativePermissionIRI has no project attached")).head,
                    forGroup = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForGroup, throw InconsistentTriplestoreDataException(s"Permission $administrativePermissionIRI has no group attached")).head,
                    hasPermissions = hasPermissions
                ))
            } else {
                None
            }

        } yield administrativePermission
    }

    private def createAdministrativePermissionV1(newAdministrativePermissionV1: NewAdministrativePermissionV1, userProfileV1: UserProfileV1): Future[AdministrativePermissionOperationResponseV1] = ???

    private def deleteAdministrativePermissionV1(administrativePermissionIri: IRI, userProfileV1: UserProfileV1): Future[AdministrativePermissionOperationResponseV1] = ???

    /**
      * Gets all IRI's of all default object access permissions defined inside a project.
      *
      * @param forProject the IRI of the project.
      * @param userProfileV1 the [[UserProfileV1]] of the requesting user.
      * @return a list of IRIs of [[DefaultObjectAccessPermissionV1]] objects.
      */
    private def getProjectDefaultObjectAccessPermissionsV1(forProject: IRI, userProfileV1: UserProfileV1): Future[Seq[IRI]] = {

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjectDefaultObjectAccessPermissions(
                triplestore = settings.triplestoreType,
                projectIri = forProject
            ).toString())
            //_ = log.debug(s"getProjectDefaultObjectAccessPermissionsV1 - query: $sparqlQueryString")

            permissionsQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getProjectDefaultObjectAccessPermissionsV1 - result: ${MessageUtil.toSource(permissionsQueryResponse)}")

            permissionsQueryResponseRows: Seq[VariableResultsRow] = permissionsQueryResponse.results.bindings

            daopIris: List[String] = permissionsQueryResponseRows.map(_.rowMap("s")).toList
            //_ = log.debug(s"getProjectAdministrativePermissionsV1 - iris: $apIris")

            defaultObjectAccessPermissionIris = if (daopIris.nonEmpty) {
                daopIris
            } else {
                Vector.empty[IRI]
            }

        } yield defaultObjectAccessPermissionIris

    }

    /**
      * Gets a single default object access permission identified by it's IRI.
      *
      * @param defaultObjectAccessPermissionIri the IRI of the default object access permission.
      * @return a single [[DefaultObjectAccessPermissionV1]] object.
      */
    private def getDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri: IRI): Future[DefaultObjectAccessPermissionV1] = {
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getDefaultObjectAccessPermission(
                triplestore = settings.triplestoreType,
                defaultObjectAccessPermissionIri = defaultObjectAccessPermissionIri
            ).toString())
            //_ = log.debug(s"getDefaultObjectAccessPermissionV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getDefaultObjectAccessPermissionV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            //_ = log.debug(s"getDefaultObjectAccessPermissionV1 - groupedResult: ${MessageUtil.toSource(groupedPermissionsQueryResponse)}")


            // TODO: Handle IRI not found, i.e. should return Option
            defaultObjectAccessPermission = DefaultObjectAccessPermissionV1 (
                forProject = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission $defaultObjectAccessPermissionIri has no project")).head,
                forGroup = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForGroup, throw InconsistentTriplestoreDataException(s"Permission $defaultObjectAccessPermissionIri has no group")).head,
                forResourceClass = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForResourceClass, throw InconsistentTriplestoreDataException(s"Permission $defaultObjectAccessPermissionIri has no resource class")).head,
                forProperty = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProperty, throw InconsistentTriplestoreDataException(s"Permission $defaultObjectAccessPermissionIri has no property")).head,
                hasDefaultChangeRightsPermission = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.HasDefaultChangeRightsPermission, Vector.empty[IRI]),
                hasDefaultDeletePermission = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.HasDefaultDeletePermission, Vector.empty[IRI]),
                hasDefaultModifyPermission = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.HasDefaultModifyPermission, Vector.empty[IRI]),
                hasDefaultViewPermission = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.HasDefaultViewPermission, Vector.empty[IRI]),
                hasDefaultRestrictedViewPermission = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.HasDefaultRestrictedViewPermission, Vector.empty[IRI])
            )

        } yield defaultObjectAccessPermission
    }

    private def createDefaultObjectAccessPermissionV1(newDefaultObjectAccessPermissionV1: NewDefaultObjectAccessPermissionV1, userProfileV1: UserProfileV1): Future[DefaultObjectAccessPermissionOperationResponseV1] = ???

    private def deleteDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri: IRI, userProfileV1: UserProfileV1): Future[DefaultObjectAccessPermissionOperationResponseV1] = ???

    private def templatePermissionsCreateRequestV1(projectIri: IRI, permissionsTemplate: PermissionsTemplate, userProfileV1: UserProfileV1): Future[TemplatePermissionsCreateResponseV1] = {
        for {
            /* find and delete all administrative permissions */
            administrativePermissionsIris <- getProjectAdministrativePermissionIrisV1(projectIri, userProfileV1)
            _ = administrativePermissionsIris.foreach { iri =>
                deleteAdministrativePermissionV1(iri, userProfileV1)
            }

            /* find and delete all default object access permissions */
            defaultObjectAccessPermissionIris <- getProjectDefaultObjectAccessPermissionsV1(projectIri, userProfileV1)
            _ = defaultObjectAccessPermissionIris.foreach { iri =>
                deleteDefaultObjectAccessPermissionV1(iri, userProfileV1)
            }

            _ = if (permissionsTemplate == PermissionsTemplate.OPEN) {
                /* create administrative permissions */
                /* is the user a SystemAdmin then skip adding project admin permissions*/
                if (!userProfileV1.isSystemAdmin) {

                }

                /* create default object access permissions */
            }

            templatePermissionsCreateResponse = TemplatePermissionsCreateResponseV1(
                success = true,
                administrativePermissions = Vector.empty[AdministrativePermissionV1],
                defaultObjectAccessPermissions = Vector.empty[DefaultObjectAccessPermissionV1],
                msg = "OK"
            )

        } yield templatePermissionsCreateResponse

    }

    private def getUserAdministrativePermissionsRequestV1(projectGroups: Map[IRI, List[IRI]]): Future[Map[IRI, List[String]]] = {

        //ToDo: loop through each project the user is part of and retrieve the administrative permissions attached to each group he is in, calculate max permissions, package everything in a neat little object, and return it back
        Future(Map(
            "http://data.knora.org/projects/77275339" -> List(
                OntologyConstants.KnoraBase.ProjectResourceCreateAllPermission,
                OntologyConstants.KnoraBase.ProjectAdminAllPermission
            ),
            "http://data.knora.org/projects/images" -> List(
                OntologyConstants.KnoraBase.ProjectResourceCreateAllPermission,
                OntologyConstants.KnoraBase.ProjectAdminAllPermission
            ),
            "http://data.knora.org/projects/666" -> List(
                OntologyConstants.KnoraBase.ProjectResourceCreateAllPermission,
                OntologyConstants.KnoraBase.ProjectAdminAllPermission
            )
        ))
    }

    private def getUserDefaultObjectAccessPermissionsRequestV1(projectGroups: Map[IRI, List[IRI]]): Future[Map[IRI, List[String]]] = {

        //ToDo: loop through each project the user is part of and retrieve the default object access permissions attached to each group he is in, calculate max permissions, package everything in a neat little object, and return it back
        Future(Map("IRI" -> List("ProjectResourceCreateAllPermission")))
    }

    /**
      * -> Need this for reading permission literals.
      *
      * Parses the literal object of the predicate `knora-base:hasPermissions`.
      *
      * @param maybePermissionListStr the literal to parse.
      * @return a [[Map]] in which the keys are permission abbreviations in
      *         [[OntologyConstants.KnoraBase.ObjectAccessPermissionAbbreviations]], and the values are sets of
      *         user group IRIs.
      */
    private def parsePermissions(maybePermissionListStr: Option[String], permissionType: PermissionType): Map[String, Set[IRI]] = {
        maybePermissionListStr match {
            case Some(permissionListStr) =>
                val permissions: Seq[String] = permissionListStr.split(OntologyConstants.KnoraBase.PermissionListDelimiter)

                permissions.map {
                    permission =>
                        val splitPermission = permission.split(' ')
                        val abbreviation = splitPermission(0)

                        permissionType match {
                            case PermissionType.AP => {
                                if (!OntologyConstants.KnoraBase.AdministrativePermissionAbbreviations.contains(abbreviation)) {
                                    throw InconsistentTriplestoreDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                                }
                                val shortGroups = splitPermission(1).split(OntologyConstants.KnoraBase.GroupListDelimiter).toSet
                                val groups = shortGroups.map(_.replace(OntologyConstants.KnoraBase.KnoraBasePrefix, OntologyConstants.KnoraBase.KnoraBasePrefixExpansion))

                                (abbreviation, groups)
                            }
                            case PermissionType.DOAP => {
                                if (!OntologyConstants.KnoraBase.AdministrativePermissionAbbreviations.contains(abbreviation)) {
                                    throw InconsistentTriplestoreDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                                }
                                val shortGroups = splitPermission(1).split(OntologyConstants.KnoraBase.GroupListDelimiter).toSet
                                val groups = shortGroups.map(_.replace(OntologyConstants.KnoraBase.KnoraBasePrefix, OntologyConstants.KnoraBase.KnoraBasePrefixExpansion))

                                (abbreviation, groups)
                        }

                        if (permissionType == PermissionType.AP) {

                        } else if (permissionType == PermissionType.DOAP)
                                if (!OntologyConstants.KnoraBase.DefaultObjectAccessPermissionAbbreviations.contains(abbreviation))
                                    throw InconsistentTriplestoreDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                        }




                }.toMap

            case None => Map.empty[String, Set[IRI]]
        }
    }
}
