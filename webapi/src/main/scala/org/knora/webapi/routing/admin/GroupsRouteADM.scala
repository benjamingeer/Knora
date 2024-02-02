/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.PathMatcher
import org.apache.pekko.http.scaladsl.server.Route
import zio.*
import zio.prelude.Validation

import dsp.errors.BadRequestException
import dsp.errors.ValidationException
import dsp.valueobjects.Group.*
import dsp.valueobjects.Iri
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages.*
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM.*
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

/**
 * Provides a routing function for API routes that deal with groups.
 */

final case class GroupsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator & StringFormatter & MessageRelay]
) extends KnoraRoute(routeData, runtime)
    with GroupsADMJsonProtocol {

  private val groupsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "groups")

  override def makeRoute: Route =
    createGroup() ~
      updateGroup() ~
      changeGroupStatus() ~
      deleteGroup()

  /**
   * Creates a group.
   */
  private def createGroup(): Route = path(groupsBasePath) {
    post {
      entity(as[CreateGroupApiRequestADM]) { apiRequest => requestContext =>
        val id: Validation[Throwable, Option[GroupIri]] = apiRequest.id
          .map(id => Validation.fromEither(GroupIri.from(id).map(Some(_))).mapError(BadRequestException(_)))
          .getOrElse(Validation.succeed(None))
        val name: Validation[Throwable, GroupName]                 = GroupName.make(apiRequest.name)
        val descriptions: Validation[Throwable, GroupDescriptions] = GroupDescriptions.make(apiRequest.descriptions)
        val project: Validation[Throwable, ProjectIri] = Validation
          .fromEither(ProjectIri.from(apiRequest.project))
          .mapError(ValidationException.apply)
        val status: Validation[Throwable, GroupStatus]     = Validation.succeed(GroupStatus.make(apiRequest.status))
        val selfjoin: Validation[Throwable, GroupSelfJoin] = GroupSelfJoin.make(apiRequest.selfjoin)
        val payloadValidation: Validation[Throwable, GroupCreatePayloadADM] =
          Validation.validateWith(id, name, descriptions, project, status, selfjoin)(GroupCreatePayloadADM)

        val requestTask = for {
          payload        <- payloadValidation.toZIO
          requestingUser <- Authenticator.getUserADM(requestContext)
          uuid           <- RouteUtilZ.randomUuid()
        } yield GroupCreateRequestADM(payload, requestingUser, uuid)
        runJsonRouteZ(requestTask, requestContext)
      }
    }
  }

  /**
   * Updates basic group information.
   */
  private def updateGroup(): Route = path(groupsBasePath / Segment) { value =>
    put {
      entity(as[ChangeGroupApiRequestADM]) { apiRequest => requestContext =>
        val requestTask = for {
          _ <- ZIO
                 .fail(
                   BadRequestException(
                     "The status property is not allowed to be set for this route. Please use the change status route."
                   )
                 )
                 .when(apiRequest.status.nonEmpty)
          name             = GroupName.make(apiRequest.name)
          descriptions     = GroupDescriptions.make(apiRequest.descriptions)
          status           = Validation.succeed(apiRequest.status.map(GroupStatus.make))
          selfjoin         = GroupSelfJoin.make(apiRequest.selfjoin)
          validatedPayload = Validation.validateWith(name, descriptions, status, selfjoin)(GroupUpdatePayloadADM)
          iri <- Iri
                   .validateAndEscapeIri(value)
                   .toZIO
                   .orElseFail(BadRequestException(s"Invalid group IRI $value"))
          payload        <- validatedPayload.toZIO
          requestingUser <- Authenticator.getUserADM(requestContext)
          uuid           <- RouteUtilZ.randomUuid()
        } yield GroupChangeRequestADM(iri, payload, requestingUser, uuid)
        runJsonRouteZ(requestTask, requestContext)
      }
    }
  }

  /**
   * Updates the group's status.
   */
  private def changeGroupStatus(): Route =
    path(groupsBasePath / Segment / "status") { value =>
      put {
        entity(as[ChangeGroupApiRequestADM]) { apiRequest => requestContext =>
          val requestTask = for {
            /**
             * The api request is already checked at time of creation.
             * See case class. Depending on the data sent, we are either
             * doing a general update or status change. Since we are in
             * the status change route, we are only interested in the
             * value of the status property
             */
            _ <- ZIO
                   .fail(BadRequestException("The status property is not allowed to be empty."))
                   .when(apiRequest.status.isEmpty)
            iri <- Iri
                     .validateAndEscapeIri(value)
                     .toZIO
                     .orElseFail(BadRequestException(s"Invalid group IRI $value"))
            requestingUser <- Authenticator.getUserADM(requestContext)
            uuid           <- RouteUtilZ.randomUuid()
          } yield GroupChangeStatusRequestADM(iri, apiRequest, requestingUser, uuid)
          runJsonRouteZ(requestTask, requestContext)
        }
      }
    }

//  RESPONSE
//  {
//    "group": {
//      "descriptions": [ {
//      "value": "A group for thing searchers.",
//      "language": "en"
//    }
//      ],
//      "id": "http://rdfh.ch/groups/0001/thing-searcher",
//      "name": "Thing searcher",
//      "project": {
//      "description": [ {
//      "value": "Anything Project"
//    }
//      ],
//      "id": "http://rdfh.ch/projects/0001",
//      "keywords": [
//      "arbitrary test data",
//      "things"
//      ],
//      "logo": null,
//      "longname": "Anything Project",
//      "ontologies": [
//      "http://0.0.0.0:3333/ontology/0001/freetest/v2",
//      "http://0.0.0.0:3333/ontology/0001/minimal/v2",
//      "http://0.0.0.0:3333/ontology/0001/anything/v2",
//      "http://0.0.0.0:3333/ontology/0001/something/v2",
//      "http://0.0.0.0:3333/ontology/0001/sequences/v2"
//      ],
//      "selfjoin": false,
//      "shortcode": "0001",
//      "shortname": "anything",
//      "status": true
//    },
//      "selfjoin": true,
//      "status": false
//    }
//  }

  /**
   * Deletes a group (sets status to false).
   */
  private def deleteGroup(): Route = path(groupsBasePath / Segment) { groupIri =>
    delete { ctx =>
      val task = for {
        r           <- getIriUserUuid(groupIri, ctx)
        changeStatus = ChangeGroupApiRequestADM(status = Some(false))
      } yield GroupChangeStatusRequestADM(r.iri, changeStatus, r.user, r.uuid)
      runJsonRouteZ(task, ctx)
    }
  }
}
