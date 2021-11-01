/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import java.util.UUID
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import io.swagger.annotations._

import javax.ws.rs.Path
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.{BadRequestException, ForbiddenException}
import org.knora.webapi.feature.{Feature, FeatureFactoryConfig}
import org.knora.webapi.messages.admin.responder.listsmessages.ListsErrorMessagesADM.{
  LIST_CREATE_PERMISSION_ERROR,
  LIST_NODE_CREATE_PERMISSION_ERROR
}
import org.knora.webapi.messages.admin.responder.listsmessages.NodeCreatePayloadADM.{
  ChildNodeCreatePayloadADM,
  ListCreatePayloadADM
}
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.admin.responder.valueObjects.{
  Comments,
  Labels,
  ListIRI,
  ListName,
  Position,
  ProjectIRI
}
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}

import scala.concurrent.Future

object OldListsRouteADMFeature {
  val ListsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "lists")
}

/**
 * A [[Feature]] that provides the old list admin API route.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the route.
 */
@Api(value = "lists (old endpoint)", produces = "application/json")
@Path("/admin/lists")
class OldListsRouteADMFeature(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Feature
    with Authenticator
    with ListADMJsonProtocol {

  import OldListsRouteADMFeature._

  def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    getLists(featureFactoryConfig) ~
      createList(featureFactoryConfig) ~
      getListOrNode(featureFactoryConfig) ~
      updateList(featureFactoryConfig) ~
      createListChildNode(featureFactoryConfig) ~
      getListInfo(featureFactoryConfig) ~
      getListNodeInfo(featureFactoryConfig)

  /* return all lists optionally filtered by project */
  @ApiOperation(value = "Get lists", nickname = "getlists", httpMethod = "GET", response = classOf[ListsGetResponseADM])
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  /* return all lists optionally filtered by project */
  private def getLists(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath) {
    get {
      /* return all lists */
      parameters("projectIri".?) { maybeProjectIri: Option[IRI] => requestContext =>
        val projectIri =
          stringFormatter.validateAndEscapeOptionalIri(
            maybeProjectIri,
            throw BadRequestException(s"Invalid param project IRI: $maybeProjectIri")
          )

        val requestMessage: Future[ListsGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield ListsGetRequestADM(
          projectIri = projectIri,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }
  }

  /* create a new list (root node) */
  @ApiOperation(
    value = "Add new list",
    nickname = "addList",
    httpMethod = "POST",
    response = classOf[ListGetResponseADM]
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "\"list\" to create",
        required = true,
        dataTypeClass = classOf[CreateNodeApiRequestADM],
        paramType = "body"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  private def createList(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath) {
    post {
      /* create a list */
      entity(as[CreateNodeApiRequestADM]) { apiRequest => requestContext =>
        val maybeId: Option[ListIRI] = apiRequest.id match {
          case Some(value) => Some(ListIRI.create(value).fold(e => throw e, v => v))
          case None        => None
        }

        val maybeName: Option[ListName] = apiRequest.name match {
          case Some(value) => Some(ListName.create(value).fold(e => throw e, v => v))
          case None        => None
        }

        val projectIri = ProjectIRI.create(apiRequest.projectIri).fold(e => throw e, v => v)

        val createRootNodePayloadADM: ListCreatePayloadADM = ListCreatePayloadADM(
          id = maybeId,
          projectIri,
          name = maybeName,
          labels = Labels.create(apiRequest.labels).fold(e => throw e, v => v),
          comments = Comments.create(apiRequest.comments).fold(e => throw e, v => v)
        )

//        println("AAA-createList", createRootNodePayloadADM)

        val requestMessage: Future[ListCreateRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )

          // check if the requesting user is allowed to perform operation
          _ = if (
            !requestingUser.permissions.isProjectAdmin(projectIri.value) && !requestingUser.permissions.isSystemAdmin
          ) {
            // not project or a system admin
            throw ForbiddenException(LIST_CREATE_PERMISSION_ERROR)
          }
        } yield ListCreateRequestADM(
          createRootNode = createRootNodePayloadADM,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }
  }

  /* get a list */
  @Path("/{IRI}")
  @ApiOperation(value = "Get a list", nickname = "getlist", httpMethod = "GET", response = classOf[ListGetResponseADM])
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  private def getListOrNode(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) { iri =>
    get {
      /* return a list (a graph with all list nodes) */
      requestContext =>
        val listIri =
          stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

        val requestMessage: Future[ListGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield ListGetRequestADM(
          iri = listIri,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
    }
  }

  /**
   * update list
   */
  @Path("/{IRI}")
  @ApiOperation(
    value = "Update basic list information",
    nickname = "putList",
    httpMethod = "PUT",
    response = classOf[RootNodeInfoGetResponseADM]
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "\"list\" to update",
        required = true,
        dataTypeClass = classOf[ChangeNodeInfoApiRequestADM],
        paramType = "body"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  private def updateList(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) { iri =>
    put {
      /* update existing list node (either root or child) */
      entity(as[ChangeNodeInfoApiRequestADM]) { apiRequest => requestContext =>
        val listIri = ListIRI.create(apiRequest.listIri).fold(e => throw e, v => v)
        val projectIri = ProjectIRI.create(apiRequest.projectIri).fold(e => throw e, v => v)

        val maybeHasRootNode: Option[ListIRI] = apiRequest.hasRootNode match {
          case Some(value) => Some(ListIRI.create(value).fold(e => throw e, v => v))
          case None        => None
        }

        val maybeName: Option[ListName] = apiRequest.name match {
          case Some(value) => Some(ListName.create(value).fold(e => throw e, v => v))
          case None        => None
        }

        val maybePosition: Option[Position] = apiRequest.position match {
          case Some(value) => Some(Position.create(value).fold(e => throw e, v => v))
          case None        => None
        }

        val maybeLabels: Option[Labels] = apiRequest.labels match {
          case Some(value) => Some(Labels.create(value).fold(e => throw e, v => v))
          case None        => None
        }

        val maybeComments: Option[Comments] = apiRequest.comments match {
          case Some(value) => Some(Comments.create(value).fold(e => throw e, v => v))
          case None        => None
        }

        val changeNodeInfoPayloadADM: NodeInfoChangePayloadADM = NodeInfoChangePayloadADM(
          listIri,
          projectIri,
          hasRootNode = maybeHasRootNode,
          position = maybePosition,
          name = maybeName,
          labels = maybeLabels,
          comments = maybeComments
        )

        val requestMessage: Future[NodeInfoChangeRequestADM] = for {
          requestingUser <- getUserADM(requestContext, featureFactoryConfig)
          // check if the requesting user is allowed to perform operation
          _ = if (
            !requestingUser.permissions.isProjectAdmin(projectIri.value) && !requestingUser.permissions.isSystemAdmin
          ) {
            // not project or a system admin
            throw ForbiddenException(LIST_NODE_CREATE_PERMISSION_ERROR)
          }
        } yield NodeInfoChangeRequestADM(
          listIri = listIri.value,
          changeNodeRequest = changeNodeInfoPayloadADM,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }
  }

  /**
   * create a new child node
   */
  @Path("/{IRI}")
  @ApiOperation(
    value = "Add new node",
    nickname = "addListNode",
    httpMethod = "POST",
    response = classOf[ChildNodeInfoGetResponseADM]
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "\"node\" to create",
        required = true,
        dataTypeClass = classOf[CreateNodeApiRequestADM],
        paramType = "body"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  private def createListChildNode(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) {
    iri =>
      post {
        /* add node to existing list node. the existing list node can be either the root or a child */
        entity(as[CreateNodeApiRequestADM]) { apiRequest => requestContext =>
          val maybeId: Option[ListIRI] = apiRequest.id match {
            case Some(value) => Some(ListIRI.create(value).fold(e => throw e, v => v))
            case None        => None
          }

          val maybeParentNodeIri: Option[ListIRI] = apiRequest.parentNodeIri match {
            case Some(value) => Some(ListIRI.create(value).fold(e => throw e, v => v))
            case None        => None
          }

          val projectIri = ProjectIRI.create(apiRequest.projectIri).fold(e => throw e, v => v)

          val maybeName: Option[ListName] = apiRequest.name match {
            case Some(value) => Some(ListName.create(value).fold(e => throw e, v => v))
            case None        => None
          }

          val maybePosition: Option[Position] = apiRequest.position match {
            case Some(value) => Some(Position.create(value).fold(e => throw e, v => v))
            case None        => None
          }

          // allows to omit comments / send empty comments creating child node
          val maybeComments = if (apiRequest.comments.isEmpty) {
            None
          } else {
            Some(Comments.create(apiRequest.comments).fold(e => throw e, v => v))
          }

          val createChildNodeRequest: ChildNodeCreatePayloadADM = ChildNodeCreatePayloadADM(
            id = maybeId,
            parentNodeIri = maybeParentNodeIri,
            projectIri,
            name = maybeName,
            position = maybePosition,
            labels = Labels.create(apiRequest.labels).fold(e => throw e, v => v),
            comments = maybeComments
          )

          val requestMessage: Future[ListChildNodeCreateRequestADM] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )

            // check if the requesting user is allowed to perform operation
            _ = if (
              !requestingUser.permissions.isProjectAdmin(projectIri.value) && !requestingUser.permissions.isSystemAdmin
            ) {
              // not project or a system admin
              throw ForbiddenException(LIST_CREATE_PERMISSION_ERROR)
            }
          } yield ListChildNodeCreateRequestADM(
            createChildNodeRequest = createChildNodeRequest,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
        }
      }
  }

  private def getListInfo(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / "infos" / Segment) {
    iri =>
      get {
        /* return information about a list (without children) */
        requestContext =>
          val listIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))
          val requestMessage: Future[ListNodeInfoGetRequestADM] = for {
            requestingUser <- getUserADM(requestContext, featureFactoryConfig)
          } yield ListNodeInfoGetRequestADM(
            iri = listIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
      }
  }

  private def getListNodeInfo(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ListsBasePath / "nodes" / Segment) { iri =>
      get {
        /* return information about a single node (without children) */
        requestContext =>
          val listIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

          val requestMessage: Future[ListNodeInfoGetRequestADM] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield ListNodeInfoGetRequestADM(
            iri = listIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
      }
    }
}
