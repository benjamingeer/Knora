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
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoByIRIGetRequest, ProjectInfoResponseV1, ProjectInfoType, ProjectInfoV1}
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileByUsernameGetRequestV1, UserProfileGetRequestV1, UserProfileV1}
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse}
import org.knora.webapi.util.ActorUtil._

import scala.concurrent.Future

/**
  * Provides information about Knora users to other responders.
  */
class UsersResponderV1 extends ResponderV1 {

    /**
      * Receives a message extending [[org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1]], and returns a message of type [[UserProfileV1]]
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case UserProfileGetRequestV1(userIri) => future2Message(sender(), getUserProfileV1(userIri), log)
        case UserProfileByUsernameGetRequestV1(username) => future2Message(sender(), getUserProfileByUsernameV1(username), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Gets information about a Knora user, and returns it in a [[Option[UserProfileV1]].
      * @param userIri the IRI of the user.
      * @return a [[Option[UserProfileV1]] describing the user.
      */
    private def getUserProfileV1(userIri: IRI): Future[Option[UserProfileV1]] = {
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getUser(
                triplestore = settings.triplestoreType,
                userIri = userIri
            ).toString())
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            userDataV1 = UserDataV1(
                lang = groupedUserData.get(OntologyConstants.KnoraBase.PreferredLanguage) match {
                    case Some(langList) => langList.head
                    case None => settings.fallbackLanguage
                },
                user_id = Some(userIri),
                username = groupedUserData.get(OntologyConstants.KnoraBase.Username).map(_.head),
                firstname = groupedUserData.get(OntologyConstants.Foaf.GivenName).map(_.head),
                lastname = groupedUserData.get(OntologyConstants.Foaf.FamilyName).map(_.head),
                email = groupedUserData.get(OntologyConstants.KnoraBase.Email).map(_.head),
                password = groupedUserData.get(OntologyConstants.KnoraBase.Password).map(_.head),
                activeProject = groupedUserData.get(OntologyConstants.KnoraBase.UsersActiveProject).map(_.head)
            )

            groupIris = groupedUserData.get(OntologyConstants.KnoraBase.IsInGroup) match {
                case Some(groups) => groups
                case None => Vector.empty[IRI]
            }

            projectIris = groupedUserData.get(OntologyConstants.KnoraBase.IsInProject) match {
                case Some(projects) => projects
                case None => Vector.empty[IRI]
            }

            userProfileV1 = {
                if (groupedUserData.isEmpty) {
                    None
                } else {
                    Some(UserProfileV1(userDataV1, groupIris, projectIris))
                }
            }


        } yield userProfileV1
    }

    /**
      * Gets information about a Knora user, and returns it in a [[Option[UserProfileV1]].
      * @param username the username of the user.
      * @return a [[Option[UserProfileV1]] describing the user.
      */
    private def getUserProfileByUsernameV1(username: String): Future[Option[UserProfileV1]] = {
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getUserByUsername(
                triplestore = settings.triplestoreType,
                username = username
            ).toString())
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            //_ = println(MessageUtil.toSource(userDataQueryResponse))

            _ = if (userDataQueryResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"User $username not found")
            }

            userIri = userDataQueryResponse.getFirstRow.rowMap("s")

            groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            groupIris = groupedUserData.get(OntologyConstants.KnoraBase.IsInGroup) match {
                case Some(groups) => groups
                case None => Nil
            }

            projectIris: Seq[String] = groupedUserData.get(OntologyConstants.KnoraBase.IsInProject) match {
                case Some(projects) => projects
                case None => Nil
            }

            projectInfoFutures: Seq[Future[ProjectInfoV1]] = projectIris.map {
                projectIri => (responderManager ? ProjectInfoByIRIGetRequest(projectIri, ProjectInfoType.SHORT, None)).mapTo[ProjectInfoResponseV1] map (_.project_info)
            }

            projectInfos <- Future.sequence(projectInfoFutures)

            userDataV1 = UserDataV1(
                lang = groupedUserData.get(OntologyConstants.KnoraBase.PreferredLanguage) match {
                    case Some(langList) => langList.head
                    case None => settings.fallbackLanguage
                },
                user_id = Some(userIri),
                username = groupedUserData.get(OntologyConstants.KnoraBase.Username).map(_.head),
                firstname = groupedUserData.get(OntologyConstants.Foaf.GivenName).map(_.head),
                lastname = groupedUserData.get(OntologyConstants.Foaf.FamilyName).map(_.head),
                email = groupedUserData.get(OntologyConstants.KnoraBase.Email).map(_.head),
                password = groupedUserData.get(OntologyConstants.KnoraBase.Password).map(_.head),
                activeProject = groupedUserData.get(OntologyConstants.KnoraBase.UsersActiveProject).map(_.head),
                projects = if (projectIris.nonEmpty) Some(projectIris) else None,
                projects_info = projectInfos
            )

            userProfileV1 = {
                if (groupedUserData.isEmpty) {
                    None
                } else {
                    Some(UserProfileV1(userDataV1, groupIris, projectIris))
                }
            }

        } yield userProfileV1 // Some(UserProfileV1(userDataV1, groupIris, projectIris))
    }
}
