/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v1
import com.typesafe.scalalogging.LazyLogging
import zio._

import java.time.Instant
import java.util.UUID

import dsp.constants.SalsahGui
import dsp.errors._
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionsStringForPropertyGetADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionsStringForResourceClassGetADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionsStringResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ResourceCreateOperation
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.twirl.SparqlTemplateResourceToCreate
import org.knora.webapi.messages.util.GroupedProps._
import org.knora.webapi.messages.util._
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v1.responder.resourcemessages._
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologyMetadataGetByIriRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologyMetadataV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyMetadataV2
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueContentV2
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.v2.ResourceUtilV2
import org.knora.webapi.slice.ontology.domain.model.Cardinality._
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.util.ApacheLuceneSupport.MatchStringWhileTyping
import org.knora.webapi.util.ZioHelper

/**
 * Responds to requests for information about resources, and returns responses in Knora API v1 format.
 */
trait ResourcesResponderV1

final case class ResourcesResponderV1Live(
  appConfig: AppConfig,
  messageRelay: MessageRelay,
  triplestoreService: TriplestoreService,
  standoffTagUtilV2: StandoffTagUtilV2,
  valueUtilV1: ValueUtilV1,
  resourceUtilV2: ResourceUtilV2,
  implicit val stringFormatter: StringFormatter
) extends ResourcesResponderV1
    with MessageHandler
    with LazyLogging {

  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[ResourcesResponderRequestV1]

  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case ResourceInfoGetRequestV1(resourceIri, userProfile) =>
      getResourceInfoResponseV1(resourceIri, userProfile)
    case ResourceFullGetRequestV1(resourceIri, userProfile, getIncoming) =>
      getFullResponseV1(resourceIri, userProfile, getIncoming)
    case ResourceContextGetRequestV1(resourceIri, userProfile, resinfo) =>
      getContextResponseV1(resourceIri, userProfile, resinfo)
    case ResourceRightsGetRequestV1(resourceIri, userProfile) =>
      getRightsResponseV1(resourceIri, userProfile)
    case graphDataGetRequest: GraphDataGetRequestV1 => getGraphDataResponseV1(graphDataGetRequest)
    case ResourceSearchGetRequestV1(
          searchString: String,
          resourceIri: Option[IRI],
          numberOfProps: Int,
          limitOfResults: Int,
          userProfile: UserADM
        ) =>
      getResourceSearchResponseV1(searchString, resourceIri, numberOfProps, limitOfResults, userProfile)
    case ResourceCreateRequestV1(
          resourceTypeIri,
          label,
          values,
          file,
          projectIri,
          userProfile,
          apiRequestID
        ) =>
      createNewResource(
        resourceTypeIri,
        label,
        values,
        file,
        projectIri,
        userProfile,
        apiRequestID
      )
    case MultipleResourceCreateRequestV1(
          resourcesToCreate,
          projectIri,
          userProfile,
          apiRequestID
        ) =>
      createMultipleNewResources(resourcesToCreate, projectIri, userProfile, apiRequestID)
    case ResourceCheckClassRequestV1(resourceIri: IRI, owlClass: IRI, userProfile: UserADM) =>
      checkResourceClass(resourceIri, owlClass, userProfile)
    case PropertiesGetRequestV1(resourceIri: IRI, userProfile: UserADM) =>
      getPropertiesV1(resourceIri = resourceIri, userProfile = userProfile)
    case resourceDeleteRequest: ResourceDeleteRequestV1 => deleteResourceV1(resourceDeleteRequest)
    case ChangeResourceLabelRequestV1(resourceIri, label, userProfile, apiRequestID) =>
      changeResourceLabelV1(resourceIri, label, apiRequestID, userProfile)
    case UnexpectedMessageRequest() => ZIO.unit
    case InternalServerExceptionMessageRequest() =>
      ZIO.fail(UpdateNotPerformedException("thrown inside the ResourcesResponder"))
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  /**
   * Gets a graph of resources that are reachable via links to or from a given resource.
   *
   * @param graphDataGetRequest a [[GraphDataGetRequestV1]] specifying the characteristics of the graph.
   * @return a [[GraphDataGetResponseV1]] representing the requested graph.
   */
  private def getGraphDataResponseV1(graphDataGetRequest: GraphDataGetRequestV1): Task[GraphDataGetResponseV1] = {
    val userProfileV1 = graphDataGetRequest.userADM.asUserProfileV1

    /**
     * The internal representation of a node returned by a SPARQL query generated by the `getGraphData` template.
     *
     * @param nodeIri         the IRI of the node.
     * @param nodeClass       the IRI of the node's class.
     * @param nodeLabel       the node's label.
     * @param nodeCreator     the node's creator.
     * @param nodeProject     the node's project.
     * @param nodePermissions the node's permissions.
     */
    case class QueryResultNode(
      nodeIri: IRI,
      nodeClass: IRI,
      nodeLabel: String,
      nodeCreator: IRI,
      nodeProject: IRI,
      nodePermissions: String
    )

    /**
     * The internal representation of an edge returned by a SPARQL query generated by the `getGraphData` template.
     *
     * @param linkValueIri         the IRI of the link value.
     * @param sourceNodeIri        the IRI of the source node.
     * @param targetNodeIri        the IRI of the target node.
     * @param linkProp             the IRI of the link property.
     * @param linkValueCreator     the link value's creator.
     * @param sourceNodeProject    the project of the source node.
     * @param linkValuePermissions the link value's permissions.
     */
    case class QueryResultEdge(
      linkValueIri: IRI,
      sourceNodeIri: IRI,
      targetNodeIri: IRI,
      linkProp: IRI,
      linkValueCreator: IRI,
      sourceNodeProject: IRI,
      linkValuePermissions: String
    )

    /**
     * Represents results returned by a SPARQL query generated by the `getGraphData` template.
     *
     * @param nodes the nodes that were returned by the query.
     * @param edges the edges that were returned by the query.
     */
    case class GraphQueryResults(
      nodes: Set[QueryResultNode] = Set.empty[QueryResultNode],
      edges: Set[QueryResultEdge] = Set.empty[QueryResultEdge]
    )

    /**
     * Recursively queries outbound or inbound links from/to a resource.
     *
     * @param startNode      the node to use as the starting point of the query. The user is assumed to have permission
     *                       to see this node.
     * @param outbound       `true` to get outbound links, `false` to get inbound links.
     * @param depth          the maximum depth of the query.
     * @param traversedEdges edges that have already been traversed.
     * @return a [[GraphQueryResults]].
     */
    def traverseGraph(
      startNode: QueryResultNode,
      outbound: Boolean,
      depth: Int,
      traversedEdges: Set[QueryResultEdge] = Set.empty[QueryResultEdge]
    ): Task[GraphQueryResults] = {
      if (depth < 1) ZIO.fail(AssertionException("Depth must be at least 1"))

      for {
        // Get the direct links from/to the start node.
        sparql <- ZIO.attempt(
                    org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                      .getGraphData(
                        startNodeIri = startNode.nodeIri,
                        startNodeOnly = false,
                        outbound = outbound // true to query outbound edges, false to query inbound edges
                      )
                      .toString()
                  )

        response <- triplestoreService.sparqlHttpSelect(sparql)
        rows      = response.results.bindings

        // Did we get any results?
        recursiveResults <-
          if (rows.isEmpty) {
            // No. Return  nothing.
            ZIO.attempt(GraphQueryResults())
          } else {
            // Yes. Get the nodes from the query results.
            val otherNodes: Seq[QueryResultNode] = rows.map { row =>
              val rowMap = row.rowMap

              QueryResultNode(
                nodeIri = rowMap("node"),
                nodeClass = rowMap("nodeClass"),
                nodeLabel = rowMap("nodeLabel"),
                nodeCreator = rowMap("nodeCreator"),
                nodeProject = rowMap("nodeProject"),
                nodePermissions = rowMap("nodePermissions")
              )
            }.filter { node =>
              // Filter out the nodes that the user doesn't have permission to see.
              PermissionUtilADM
                .getUserPermissionV1(
                  entityIri = node.nodeIri,
                  entityCreator = node.nodeCreator,
                  entityProject = node.nodeProject,
                  entityPermissionLiteral = node.nodePermissions,
                  userProfile = userProfileV1
                )
                .nonEmpty
            }

            // Collect the IRIs of the nodes that the user has permission to see, including the start node.
            val visibleNodeIris = otherNodes.map(_.nodeIri).toSet + startNode.nodeIri

            // Get the edges from the query results.
            val edges: Set[QueryResultEdge] = rows.map { row =>
              val rowMap  = row.rowMap
              val nodeIri = rowMap("node")

              // The SPARQL query takes a start node and returns the other node in the edge.
              //
              // If we're querying outbound edges, the start node is the source node, and the other
              // node is the target node.
              //
              // If we're querying inbound edges, the start node is the target node, and the other
              // node is the source node.

              QueryResultEdge(
                linkValueIri = rowMap("linkValue"),
                sourceNodeIri = if (outbound) startNode.nodeIri else nodeIri,
                targetNodeIri = if (outbound) nodeIri else startNode.nodeIri,
                linkProp = rowMap("linkProp"),
                linkValueCreator = rowMap("linkValueCreator"),
                sourceNodeProject = if (outbound) startNode.nodeProject else rowMap("nodeProject"),
                linkValuePermissions = rowMap("linkValuePermissions")
              )
            }.filter { edge =>
              // Filter out the edges that the user doesn't have permission to see. To see an edge,
              // the user must have some permission on the link value and on the source and target
              // nodes.
              val hasPermission =
                visibleNodeIris.contains(edge.sourceNodeIri) && visibleNodeIris.contains(edge.targetNodeIri) &&
                  PermissionUtilADM
                    .getUserPermissionV1(
                      entityIri = edge.linkValueIri,
                      entityCreator = edge.linkValueCreator,
                      entityProject = edge.sourceNodeProject,
                      entityPermissionLiteral = edge.linkValuePermissions,
                      userProfile = userProfileV1
                    )
                    .nonEmpty

              // Filter out edges we've already traversed.
              val isRedundant = traversedEdges.contains(edge)

              hasPermission && !isRedundant
            }.toSet

            // Include only nodes that are reachable via edges that we're going to traverse (i.e. the user
            // has permission to see those edges, and we haven't already traversed them).
            val visibleNodeIrisFromEdges = edges.map(_.sourceNodeIri) ++ edges.map(_.targetNodeIri)
            val filteredOtherNodes       = otherNodes.filter(node => visibleNodeIrisFromEdges.contains(node.nodeIri))

            // Make a GraphQueryResults containing the resulting nodes and edges, including the start
            // node.
            val results = GraphQueryResults(nodes = filteredOtherNodes.toSet + startNode, edges = edges)

            // Have we reached the maximum depth?
            if (depth == 1) {
              // Yes. Just return the results we have.
              ZIO.attempt(results)
            } else {
              // No. Recursively get results for each of the nodes we found.

              val lowerResultFutures: Seq[Task[GraphQueryResults]] = filteredOtherNodes.map { node =>
                traverseGraph(
                  startNode = node,
                  outbound = outbound,
                  depth = depth - 1,
                  traversedEdges = traversedEdges ++ edges
                )
              }

              val lowerResultsFuture: Task[Seq[GraphQueryResults]] = ZIO.collectAll(lowerResultFutures)

              // Return those results plus the ones we found.

              for {
                lowerResultsSeq <- lowerResultsFuture
              } yield lowerResultsSeq.foldLeft(results) { case (acc, lowerResults) =>
                GraphQueryResults(
                  nodes = acc.nodes ++ lowerResults.nodes,
                  edges = acc.edges ++ lowerResults.edges
                )
              }
            }
          }
      } yield recursiveResults
    }

    for {
      // Get the start node.
      sparql <- ZIO.attempt(
                  org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                    .getGraphData(
                      startNodeIri = graphDataGetRequest.resourceIri,
                      startNodeOnly = true
                    )
                    .toString()
                )

      response <- triplestoreService.sparqlHttpSelect(sparql)
      rows      = response.results.bindings

      _ <-
        ZIO
          .fail(NotFoundException(s"Resource ${graphDataGetRequest.resourceIri} not found (it may have been deleted)"))
          .when(rows.isEmpty)

      firstRowMap = rows.head.rowMap

      startNode: QueryResultNode = QueryResultNode(
                                     nodeIri = firstRowMap("node"),
                                     nodeClass = firstRowMap("nodeClass"),
                                     nodeLabel = firstRowMap("nodeLabel"),
                                     nodeCreator = firstRowMap("nodeCreator"),
                                     nodeProject = firstRowMap("nodeProject"),
                                     nodePermissions = firstRowMap("nodePermissions")
                                   )

      // Make sure the user has permission to see the start node.
      permission =
        PermissionUtilADM
          .getUserPermissionV1(
            entityIri = startNode.nodeIri,
            entityCreator = startNode.nodeCreator,
            entityProject = startNode.nodeProject,
            entityPermissionLiteral = startNode.nodePermissions,
            userProfile = userProfileV1
          )
      _ <-
        ZIO
          .fail(
            ForbiddenException(
              s"User ${graphDataGetRequest.userADM.id} does not have permission to view resource ${graphDataGetRequest.resourceIri}"
            )
          )
          .when(permission.isEmpty)

      // Recursively get the graph containing outbound links.
      outboundQueryResults <- traverseGraph(
                                startNode = startNode,
                                outbound = true,
                                depth = graphDataGetRequest.depth
                              )

      // Recursively get the graph containing inbound links.
      inboundQueryResults <- traverseGraph(startNode = startNode, outbound = false, depth = graphDataGetRequest.depth)

      // Combine the outbound and inbound graphs into a single graph.
      nodes: Set[QueryResultNode] = outboundQueryResults.nodes ++ inboundQueryResults.nodes + startNode
      edges: Set[QueryResultEdge] = outboundQueryResults.edges ++ inboundQueryResults.edges

      // Get the labels of the resource classes and properties from the ontology responder.

      resourceClassIris = nodes.map(_.nodeClass)
      propertyIris      = edges.map(_.linkProp)

      entityInfoRequest = EntityInfoGetRequestV1(
                            resourceClassIris = resourceClassIris,
                            propertyIris = propertyIris,
                            userProfile = graphDataGetRequest.userADM
                          )
      entityInfoResponse <- messageRelay.ask[EntityInfoGetResponseV1](entityInfoRequest)

      // Convert each node to a GraphNodeV1 for the API response message.
      resultNodes <-
        ZIO.collectAll(nodes.map { node =>
          for {
            // Get the resource class's label from the ontology information.
            resourceClassLabel <-
              ZIO
                .fromOption(
                  entityInfoResponse
                    .resourceClassInfoMap(node.nodeClass)
                    .getPredicateObject(
                      OntologyConstants.Rdfs.Label,
                      Some(graphDataGetRequest.userADM.lang, appConfig.fallbackLanguage)
                    )
                )
                .orElseFail(InconsistentRepositoryDataException(s"Resource class ${node.nodeClass} has no rdfs:label"))
          } yield GraphNodeV1(node.nodeIri, node.nodeLabel, node.nodeClass, resourceClassLabel)
        }.toVector)

      // Convert each edge to a GraphEdgeV1 for the API response message.
      resultEdges <-
        ZIO.collectAll(
          edges.map { edge =>
            for {
              // Get the link property's label from the ontology information.
              propertyLabel <-
                ZIO
                  .fromOption(
                    entityInfoResponse
                      .propertyInfoMap(edge.linkProp)
                      .getPredicateObject(
                        OntologyConstants.Rdfs.Label,
                        Some(graphDataGetRequest.userADM.lang, appConfig.fallbackLanguage)
                      )
                  )
                  .orElseFail(InconsistentRepositoryDataException(s"Property ${edge.linkProp} has no rdfs:label"))
            } yield GraphEdgeV1(edge.sourceNodeIri, edge.targetNodeIri, edge.linkProp, propertyLabel)
          }.toVector
        )
    } yield GraphDataGetResponseV1(resultNodes, resultEdges)
  }

  /**
   * Returns an instance of [[ResourceInfoResponseV1]] describing a resource, in Knora API v1 format.
   *
   * @param resourceIri the IRI of the resource to be queried.
   * @param userProfile the profile of the user making the request.
   * @return a [[ResourceInfoResponseV1]] containing a representation of the resource.
   */
  private def getResourceInfoResponseV1(
    resourceIri: IRI,
    userProfile: UserADM
  ): Task[ResourceInfoResponseV1] =
    for {
      t <- getResourceInfoV1(
             resourceIri = resourceIri,
             userProfile = userProfile,
             queryOntology = true
           )
      userPermissions = t._1
      resInfo         = t._2

      result <-
        userPermissions match {
          case Some(_) => ZIO.succeed(ResourceInfoResponseV1(Some(resInfo), userPermissions))
          case None =>
            ZIO.fail(
              ForbiddenException(s"User ${userProfile.id} does not have permission to view resource $resourceIri")
            )
        }
    } yield result

  /**
   * Returns an instance of [[ResourceFullResponseV1]] representing a Knora resource
   * with its properties and their values, in Knora API v1 format.
   *
   * @param resourceIri the IRI of the resource to be queried.
   * @param userProfile the profile of the user making the request.
   * @param getIncoming if `true` (the default), queries the resource's inconing references.
   * @return a [[ResourceFullResponseV1]].
   */
  private def getFullResponseV1(
    resourceIri: IRI,
    userProfile: UserADM,
    getIncoming: Boolean = true
  ): Task[ResourceFullResponseV1] = {
    val userProfileV1 = userProfile.asUserProfileV1

    // Query resource info, resource properties, and incoming references in parallel.
    // See http://buransky.com/scala/scala-for-comprehension-with-concurrently-running-futures/

    // Get information about the properties that have values for this resource.
    val groupedPropsByTypeFuture: Task[GroupedPropertiesByType] = getGroupedProperties(resourceIri)

    // Get a resource info containing basic information about the resource. Do not query the ontology here, because we will query it below.
    val resourceInfoFuture = getResourceInfoV1(
      resourceIri = resourceIri,
      userProfile = userProfile,
      queryOntology = false
    )

    // Get information about the references pointing from other resources to this resource.
    val maybeIncomingRefsFuture: Task[Option[SparqlSelectResult]] = if (getIncoming) {
      for {
        incomingRefsSparql <- ZIO.attempt(
                                org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                                  .getIncomingReferences(
                                    resourceIri = resourceIri
                                  )
                                  .toString()
                              )
        response <- triplestoreService.sparqlHttpSelect(incomingRefsSparql)
      } yield Some(response)
    } else {
      ZIO.attempt(None)
    }

    for {
      // Get the resource info (minus ontology-based information) and the user's permissions on it.
      t                             <- resourceInfoFuture
      permissions                    = t._1
      resInfoWithoutQueryingOntology = t._2

      groupedPropsByType <- groupedPropsByTypeFuture

      // Get the types of all the resources that this resource links to.
      linkedResourceTypes = groupedPropsByType.groupedLinkProperties.groupedProperties.foldLeft(Set.empty[IRI]) {
                              case (acc, (_, propMap)) =>
                                val targetResourceTypes = propMap.valueObjects.foldLeft(Set.empty[IRI]) {
                                  case (resTypeAcc, (obj: IRI, objMap: ValueProps)) =>
                                    val resType = objMap.literalData.get(OntologyConstants.Rdf.Type) match {
                                      case Some(value: ValueLiterals) => value.literals
                                      case None                       => throw InconsistentRepositoryDataException(s"$obj has no rdf:type")
                                    }

                                    resTypeAcc ++ resType
                                }

                                acc ++ targetResourceTypes
                            }

      // Group incoming reference rows by the IRI of the referring resource, and construct an IncomingV1 for each one.

      maybeIncomingRefsResponse <- maybeIncomingRefsFuture

      incomingRefFutures: Vector[Task[Vector[IncomingV1]]] =
        maybeIncomingRefsResponse match {
          case Some(incomingRefsResponse) =>
            val incomingRefsResponseRows =
              incomingRefsResponse.results.bindings

            // Group the incoming reference query results by the IRI of the referring resource.
            val groupedByIncomingIri: Map[IRI, Seq[VariableResultsRow]] =
              incomingRefsResponseRows.groupBy(
                _.rowMap("referringResource")
              )

            groupedByIncomingIri.map {
              case (
                    incomingIri: IRI,
                    rows: Seq[VariableResultsRow]
                  ) =>
                // Make a resource info for each referring resource, and check the permissions on the referring resource.

                val rowsForResInfo =
                  rows.filterNot(row =>
                    ValuesValidator.optionStringToBoolean(row.rowMap.get("isLinkValue"), fallback = false)
                  )

                for {
                  t <-
                    makeResourceInfoV1(
                      resourceIri = incomingIri,
                      resInfoResponseRows = rowsForResInfo,
                      userProfile = userProfile,
                      queryOntology = false
                    )
                  incomingResPermission = t._1
                  incomingResInfo       = t._2

                  // Does the user have permission to see the referring resource?
                  incomingV1s <-
                    incomingResPermission match {
                      case Some(_) =>
                        // Yes. For each link from the referring resource, check whether the user has permission to see the link. If so, make an IncomingV1 for the link.

                        // Filter to get only the rows representing LinkValues.
                        val rowsWithLinkValues = rows.filter(row =>
                          ValuesValidator.optionStringToBoolean(row.rowMap.get("isLinkValue"), fallback = false)
                        )

                        // Group them by LinkValue IRI.
                        val groupedByLinkValue: Map[String, Seq[
                          VariableResultsRow
                        ]] =
                          rowsWithLinkValues.groupBy(
                            _.rowMap("obj")
                          )

                        // For each LinkValue, check whether the user has permission to see the link, and if so, make an IncomingV1.
                        val maybeIncomingV1sWithFuture: Iterable[
                          Task[Option[IncomingV1]]
                        ] = groupedByLinkValue.map {
                          case (
                                linkValueIri: IRI,
                                linkValueRows: Seq[
                                  VariableResultsRow
                                ]
                              ) =>
                            // Convert the rows representing the LinkValue to a ValueProps.
                            val linkValueProps = valueUtilV1.createValueProps(linkValueIri, linkValueRows)

                            // Convert the resulting ValueProps into a LinkValueV1 so we can check its rdf:predicate.

                            for {
                              apiValueV1 <- valueUtilV1.makeValueV1(
                                              linkValueProps,
                                              resInfoWithoutQueryingOntology.project_shortcode,
                                              userProfile
                                            )

                              linkValueV1 <-
                                apiValueV1 match {
                                  case linkValueV1: LinkValueV1 => ZIO.succeed(linkValueV1)
                                  case _ =>
                                    ZIO.fail(
                                      InconsistentRepositoryDataException(
                                        s"Expected $linkValueIri to be a knora-base:LinkValue, but its type is ${apiValueV1.valueTypeIri}"
                                      )
                                    )
                                }

                              // Check the permissions on the LinkValue.
                              linkValuePermission =
                                PermissionUtilADM
                                  .getUserPermissionWithValuePropsV1(
                                    valueIri = linkValueIri,
                                    valueProps = linkValueProps,
                                    entityProject = Some(
                                      incomingResInfo.project_id
                                    ),
                                    userProfile = userProfileV1
                                  )
                            } yield linkValuePermission match {
                              // Does the user have permission to see this link?
                              case Some(_) =>
                                // Yes. Make a Some containing an IncomingV1 for the link.
                                Some(
                                  IncomingV1(
                                    ext_res_id = ExternalResourceIDV1(
                                      id = incomingIri,
                                      pid = linkValueV1.predicateIri
                                    ),
                                    resinfo = incomingResInfo,
                                    value = incomingResInfo.firstproperty
                                  )
                                )

                              case None =>
                                // No. Make a None.
                                None
                            }

                        }

                        for {

                          // turn the Iterable of Futures into a Future of an Iterable
                          maybeIncomingV1s <- ZIO.collectAll(maybeIncomingV1sWithFuture)

                          // Filter out the Nones, which represent incoming links that the user doesn't have permission to see.
                        } yield maybeIncomingV1s.flatten.toVector

                      case None =>
                        // The user doesn't have permission to see the referring resource.
                        ZIO.attempt(Vector.empty[IncomingV1])
                    }
                } yield incomingV1s

            }.toVector

          case None => Vector.empty[Task[Vector[IncomingV1]]]
        }

      incomingRefsWithoutQueryingOntology <- ZIO.collectAll(incomingRefFutures).map(_.flatten)

      // Get the resource types of the incoming resources.
      incomingTypes: Set[IRI] = incomingRefsWithoutQueryingOntology.map(_.resinfo.restype_id).toSet

      // Ask the ontology responder for information about the ontology entities that we need information about.
      entityInfoResponse <- messageRelay
                              .ask[EntityInfoGetResponseV1](
                                EntityInfoGetRequestV1(
                                  resourceClassIris =
                                    incomingTypes ++ linkedResourceTypes + resInfoWithoutQueryingOntology.restype_id,
                                  propertyIris =
                                    groupedPropsByType.groupedOrdinaryValueProperties.groupedProperties.keySet ++ groupedPropsByType.groupedLinkProperties.groupedProperties.keySet,
                                  userProfile = userProfile
                                )
                              )

      // Add ontology-based information to the resource info.
      resourceTypeIri        = resInfoWithoutQueryingOntology.restype_id
      resourceTypeEntityInfo = entityInfoResponse.resourceClassInfoMap(resourceTypeIri)

      maybeResourceTypeIconSrc = resourceTypeEntityInfo.getPredicateObject(
                                   OntologyConstants.KnoraBase.ResourceIcon
                                 ) match {
                                   case Some(resClassIcon) =>
                                     Some(
                                       valueUtilV1.makeResourceClassIconURL(resourceTypeIri, resClassIcon)
                                     )
                                   case _ => None
                                 }

      resourceClassLabel = resourceTypeEntityInfo.getPredicateObject(
                             predicateIri = OntologyConstants.Rdfs.Label,
                             preferredLangs = Some(userProfile.lang, appConfig.fallbackLanguage)
                           )

      resInfo: ResourceInfoV1 = resInfoWithoutQueryingOntology.copy(
                                  restype_label = resourceClassLabel,
                                  restype_description = resourceTypeEntityInfo.getPredicateObject(
                                    predicateIri = OntologyConstants.Rdfs.Comment,
                                    preferredLangs = Some(userProfile.lang, appConfig.fallbackLanguage)
                                  ),
                                  restype_iconsrc = maybeResourceTypeIconSrc
                                )

      // Construct a ResourceDataV1.
      resData = ResourceDataV1(
                  rights = permissions,
                  restype_label = resourceClassLabel,
                  restype_name = resInfo.restype_id,
                  res_id = resourceIri,
                  iconsrc = maybeResourceTypeIconSrc
                )

      // Add ontology-based information to incoming references.
      incomingRefs = incomingRefsWithoutQueryingOntology.map { incoming =>
                       val incomingResourceTypeEntityInfo =
                         entityInfoResponse.resourceClassInfoMap(incoming.resinfo.restype_id)

                       incoming.copy(
                         resinfo = incoming.resinfo.copy(
                           restype_label = incomingResourceTypeEntityInfo.getPredicateObject(
                             predicateIri = OntologyConstants.Rdfs.Label,
                             preferredLangs = Some(userProfile.lang, appConfig.fallbackLanguage)
                           ),
                           restype_description = incomingResourceTypeEntityInfo.getPredicateObject(
                             predicateIri = OntologyConstants.Rdfs.Comment,
                             preferredLangs = Some(userProfile.lang, appConfig.fallbackLanguage)
                           ),
                           restype_iconsrc = incomingResourceTypeEntityInfo.getPredicateObject(
                             OntologyConstants.KnoraBase.ResourceIcon
                           ) match {
                             case Some(resClassIcon) =>
                               Some(valueUtilV1.makeResourceClassIconURL(incoming.resinfo.restype_id, resClassIcon))
                             case _ => None
                           }
                         )
                       )
                     }

      // Collect all property IRIs and their cardinalities for the queried resource's type, except the ones that point to LinkValue objects or FileValue objects,
      // which are not relevant in this API operation.
      propsAndCardinalities: Map[IRI, KnoraCardinalityInfo] =
        resourceTypeEntityInfo.knoraResourceCardinalities.filterNot { case (propertyIri, _) =>
          resourceTypeEntityInfo.linkValueProperties(propertyIri) || resourceTypeEntityInfo.fileValueProperties(
            propertyIri
          )
        }

      // Construct PropertyV1 objects for the properties that have data for this resource.
      propertiesWithData <- queryResults2PropertyV1s(
                              containingResourceIri = resourceIri,
                              projectShortcode = resInfoWithoutQueryingOntology.project_shortcode,
                              groupedPropertiesByType = groupedPropsByType,
                              propertyInfoMap = entityInfoResponse.propertyInfoMap,
                              resourceEntityInfoMap = entityInfoResponse.resourceClassInfoMap,
                              propsAndCardinalities = propsAndCardinalities,
                              userProfile = userProfile
                            )

      // Construct PropertyV1 objects representing properties that have no data for this resource, but are possible properties for the resource type.

      // To find out which properties are possible but have no data for this resource, subtract the set of properties with data from the set of possible properties.
      emptyPropsIris =
        propsAndCardinalities.keySet -- (groupedPropsByType.groupedOrdinaryValueProperties.groupedProperties.keySet ++ groupedPropsByType.groupedLinkProperties.groupedProperties.keySet)

      // Get information from the ontology about the properties that have no data for this resource.
      emptyPropsInfoResponse <- messageRelay.ask[EntityInfoGetResponseV1](
                                  EntityInfoGetRequestV1(propertyIris = emptyPropsIris, userProfile = userProfile)
                                )

      // Create a PropertyV1 for each of those properties.
      emptyProps <-
        ZIO.foreach(emptyPropsIris) { propertyIri =>
          val propertyEntityInfo = emptyPropsInfoResponse.propertyInfoMap(propertyIri)
          val guiOrder           = resourceTypeEntityInfo.knoraResourceCardinalities(propertyIri).guiOrder

          if (propertyEntityInfo.isLinkProp) {
            // It is a linking prop: its valuetype_id is knora-base:LinkValue.
            // It is restricted to the resource class that is given for knora-base:objectClassConstraint
            // for the given property which goes in the attributes that will be read by the GUI.
            for {
              prop <-
                ZIO
                  .fromOption(propertyEntityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint))
                  .orElseFail(
                    InconsistentRepositoryDataException(
                      s"Property $propertyIri has no knora-base:objectClassConstraint"
                    )
                  )
            } yield PropertyV1(
              pid = propertyIri,
              valuetype_id = Some(OntologyConstants.KnoraBase.LinkValue),
              guiorder = guiOrder,
              guielement = propertyEntityInfo
                .getPredicateObject(SalsahGui.GuiElementProp)
                .map(guiElementIri => SalsahGuiConversions.iri2SalsahGuiElement(guiElementIri)),
              label = propertyEntityInfo.getPredicateObject(
                predicateIri = OntologyConstants.Rdfs.Label,
                preferredLangs = Some(userProfile.lang, appConfig.fallbackLanguage)
              ),
              occurrence = Some(propsAndCardinalities(propertyIri).cardinality.toString),
              attributes = (propertyEntityInfo.getPredicateStringObjectsWithoutLang(
                SalsahGui.GuiAttribute
              ) + valueUtilV1.makeAttributeRestype(prop)).mkString(";"),
              value_rights = Nil
            )

          } else {
            ZIO.succeed(
              PropertyV1(
                pid = propertyIri,
                valuetype_id = propertyEntityInfo.getPredicateObject(
                  OntologyConstants.KnoraBase.ObjectClassConstraint
                ),
                guiorder = guiOrder,
                guielement = propertyEntityInfo
                  .getPredicateObject(SalsahGui.GuiElementProp)
                  .map(guiElementIri => SalsahGuiConversions.iri2SalsahGuiElement(guiElementIri)),
                label = propertyEntityInfo.getPredicateObject(
                  predicateIri = OntologyConstants.Rdfs.Label,
                  preferredLangs = Some(userProfile.lang, appConfig.fallbackLanguage)
                ),
                occurrence = Some(propsAndCardinalities(propertyIri).cardinality.toString),
                attributes = propertyEntityInfo
                  .getPredicateStringObjectsWithoutLang(SalsahGui.GuiAttribute)
                  .mkString(";"),
                value_rights = Nil
              )
            )
          }
        }

      // Add a fake property `__location__` if the resource has FileValues.
      properties: Seq[PropertyV1] =
        if (resInfo.locations.nonEmpty) {
          PropertyV1(
            pid = "__location__",
            valuetype_id = Some("-1"),
            guiorder = Some(Int.MaxValue),
            guielement = Some(SalsahGuiConversions.iri2SalsahGuiElement(SalsahGui.Fileupload)),
            values = Vector(IntegerValueV1(0)),
            value_ids = Vector("0"),
            comments = Vector(None),
            locations = resInfo.locations match {
              case Some(locations: Seq[LocationV1]) => locations
              case None                             => Nil
            },
            value_rights = Nil
          ) +: (propertiesWithData ++ emptyProps)
        } else {
          propertiesWithData ++ emptyProps
        }

      // Construct the API response. Return no data if the user has no view permissions on the queried resource.
      resFullResponse =
        if (resData.rights.nonEmpty) {
          ResourceFullResponseV1(
            resinfo = Some(resInfo),
            resdata = Some(resData),
            props = Some(PropsV1(properties)),
            incoming = incomingRefs,
            access = "OK"
          )
        } else {
          throw ForbiddenException(s"User ${userProfile.id} does not have permission to query resource $resourceIri")
        }
    } yield resFullResponse
  }

  /**
   * Returns an instance of [[ResourceContextResponseV1]] describing the context of a resource, in Knora API v1 format.
   *
   * @param resourceIri          the IRI of the resource to be queried.
   *
   * @param userProfile          the profile of the user making the request.
   * @param resinfo              a flag if resinfo should be retrieved or not.
   * @return a [[ResourceContextResponseV1]] describing the context of the resource.
   */
  private def getContextResponseV1(
    resourceIri: IRI,
    userProfile: UserADM,
    resinfo: Boolean
  ): Task[ResourceContextResponseV1] = {
    val userProfileV1 = userProfile.asUserProfileV1

    /**
     * Represents a source object (e.g. a page of a book).
     *
     * @param id             IRI of the source Object.
     * @param firstprop      first property of the source object.
     * @param seqnum         sequence number of the source object.
     * @param permissionCode the current user's permissions on the source object.
     * @param fileValue      the file value, if any, belonging to the source object.
     */
    case class SourceObject(
      id: IRI,
      firstprop: Option[String],
      seqnum: Option[Int],
      permissionCode: Option[Int],
      projectShortcode: String,
      fileValue: Option[StillImageFileValue]
    )

    /**
     * Represents a still image file value belonging to a source object (e.g., an image representation of a page).
     *
     * @param id             the file value IRI
     * @param permissionCode the current user's permission code on the file value.
     * @param image          a [[StillImageFileValueV1]]
     */
    case class StillImageFileValue(id: IRI, permissionCode: Option[Int], image: StillImageFileValueV1)

    /**
     * Creates a [[StillImageFileValue]] from a [[VariableResultsRow]] representing a row of context query results.
     * If the row doesn't contain a file value IRI, returns [[None]].
     *
     * @param row a [[VariableResultsRow]] representing a [[StillImageFileValueV1]].
     * @return a [[StillImageFileValue]].
     */
    def createStillImageFileValueFromResultRow(
      projectShortcode: String,
      row: VariableResultsRow
    ): Option[StillImageFileValue] = {
      // if the file value has no project, get the project from the source object
      val fileValueProject = row.rowMap("sourceObjectAttachedToProject")

      // The row may or may not contain a file value IRI.
      row.rowMap.get("fileValue") match {
        case Some(fileValueIri) =>
          val fileValuePermission = PermissionUtilADM.getUserPermissionV1(
            entityIri = fileValueIri,
            entityCreator = row.rowMap("fileValueAttachedToUser"),
            entityProject = fileValueProject,
            entityPermissionLiteral = row.rowMap("fileValuePermissions"),
            userProfile = userProfileV1
          )

          Some(
            StillImageFileValue(
              id = fileValueIri,
              permissionCode = fileValuePermission,
              image = StillImageFileValueV1(
                internalMimeType = row.rowMap("internalMimeType"),
                internalFilename = row.rowMap("internalFilename"),
                originalFilename = row.rowMap.get("originalFilename"),
                projectShortcode = projectShortcode,
                dimX = row.rowMap("dimX").toInt,
                dimY = row.rowMap("dimY").toInt
              )
            )
          )

        case None => None
      }
    }

    /**
     * Creates a [[SourceObject]] from a [[VariableResultsRow]].
     *
     * @param row a [[VariableResultsRow]] representing a [[SourceObject]].
     * @return a [[SourceObject]].
     */
    def createSourceObjectFromResultRow(projectShortcode: String, row: VariableResultsRow): SourceObject = {
      val sourceObjectIri     = row.rowMap("sourceObject")
      val sourceObjectOwner   = row.rowMap("sourceObjectAttachedToUser")
      val sourceObjectProject = row.rowMap("sourceObjectAttachedToProject")
      val sourceObjectLiteral = row.rowMap("sourceObjectPermissions")

      val sourceObjectPermissionCode = PermissionUtilADM.getUserPermissionV1(
        entityIri = sourceObjectIri,
        entityCreator = sourceObjectOwner,
        entityProject = sourceObjectProject,
        entityPermissionLiteral = sourceObjectLiteral,
        userProfile = userProfileV1
      )

      val linkValueIri         = row.rowMap("linkValue")
      val linkValueCreator     = row.rowMap("linkValueCreator")
      val linkValuePermissions = row.rowMap("linkValuePermissions")
      val linkValuePermissionCode = PermissionUtilADM.getUserPermissionV1(
        entityIri = linkValueIri,
        entityCreator = linkValueCreator,
        entityProject = sourceObjectProject,
        entityPermissionLiteral = linkValuePermissions,
        userProfile = userProfileV1
      )

      // Allow the user to see the link only if they have permission to see both the source object and the link value.
      val permissionCode = Seq(sourceObjectPermissionCode, linkValuePermissionCode).min

      SourceObject(
        id = row.rowMap("sourceObject"),
        firstprop = row.rowMap.get("firstprop"),
        seqnum = row.rowMap.get("seqnum").map(_.toInt),
        permissionCode = permissionCode,
        projectShortcode = projectShortcode,
        fileValue = createStillImageFileValueFromResultRow(projectShortcode, row)
      )
    }

    val userIri = userProfile.id

    for {
      // Get the resource info even if the user didn't ask for it, so we can check its permissions.
      t <- getResourceInfoV1(
             resourceIri = resourceIri,
             userProfile = userProfile,
             queryOntology = true
           )
      userPermission = t._1
      resInfoV1      = t._2

      _ = if (userPermission.isEmpty) {
            throw ForbiddenException(
              s"User $userIri does not have permission to query the context of resource $resourceIri"
            )
          }

      // If this resource is part of another resource, get its parent resource.
      isPartOfSparqlQuery = org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                              .isPartOf(
                                resourceIri = resourceIri
                              )
                              .toString()
      isPartOfResponse <- triplestoreService.sparqlHttpSelect(isPartOfSparqlQuery)

      t <-
        isPartOfResponse.results.bindings match {
          case rows if rows.nonEmpty =>
            val rowMap                    = rows.head.rowMap
            val containingResourceIri     = rowMap("containingResource")
            val containingResourceProject = rowMap("containingResourceProject")

            for {
              t <- getResourceInfoV1(
                     resourceIri = containingResourceIri,
                     userProfile = userProfile,
                     queryOntology = true
                   )
              containingResourcePermissionCode = t._1
              resInfoV1                        = t._2
              linkValueIri                     = rowMap("linkValue")
              linkValueCreator                 = rowMap("linkValueCreator")
              linkValuePermissions             = rowMap("linkValuePermissions")
              linkValuePermissionCode = PermissionUtilADM.getUserPermissionV1(
                                          entityIri = linkValueIri,
                                          entityCreator = linkValueCreator,
                                          entityProject = containingResourceProject,
                                          entityPermissionLiteral = linkValuePermissions,
                                          userProfile = userProfileV1
                                        )

              // Allow the user to see the link only if they have permission to see both the containing resource and the link value.
              permissionCode = Seq(containingResourcePermissionCode, linkValuePermissionCode).min

            } yield permissionCode match {
              case Some(_) => (Some(containingResourceIri), Some(resInfoV1))
              case None    => (None, None)
            }
          case _ => ZIO.attempt((None, None))
        }
      containingResourceIriOption: Option[IRI]          = t._1
      containingResInfoV1Option: Option[ResourceInfoV1] = t._2

      resourceContexts <-
        if (containingResInfoV1Option.isEmpty) {
          for {
            // Otherwise, do a SPARQL query that returns resources that are part of this resource (as indicated by knora-base:isPartOf).
            contextSparqlQuery <- ZIO.attempt(
                                    org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                                      .getContext(
                                        resourceIri = resourceIri
                                      )
                                      .toString()
                                  )

            contextQueryResponse         <- triplestoreService.sparqlHttpSelect(contextSparqlQuery)
            rows: Seq[VariableResultsRow] = contextQueryResponse.results.bindings

            // The results consist of one row per source object.
            sourceObjects: Seq[SourceObject] = rows.map { row: VariableResultsRow =>
                                                 val sourceObject: IRI = row.rowMap("sourceObject")
                                                 val projectShortcode: String = sourceObject.toSmartIri.getProjectCode
                                                   .getOrElse(
                                                     throw InconsistentRepositoryDataException(
                                                       s"Invalid resource IRI: $sourceObject"
                                                     )
                                                   )
                                                 createSourceObjectFromResultRow(projectShortcode, row)
                                               }

            // Filter the source objects by eliminating the ones that the user doesn't have permission to see.
            sourceObjectsWithPermissions = sourceObjects.filter(sourceObj => sourceObj.permissionCode.nonEmpty)

            contextItems: Seq[ResourceContextItemV1] = sourceObjectsWithPermissions.map { sourceObj: SourceObject =>
                                                         // Generate a IIIF preview URL from the full-size image.
                                                         val (
                                                           preview: Option[LocationV1],
                                                           locations: Option[Seq[LocationV1]]
                                                         ): (
                                                           Option[LocationV1],
                                                           Option[Seq[LocationV1]]
                                                         ) =
                                                           sourceObj.fileValue.find(fileVal =>
                                                             fileVal.permissionCode.nonEmpty
                                                           ) match {
                                                             case Some(full: StillImageFileValue) =>
                                                               val preview: LocationV1 =
                                                                 valueUtilV1.fileValueV12LocationV1(
                                                                   fullSizeImageFileValueToPreview(full.image)
                                                                 )
                                                               val fileVals: Seq[LocationV1] =
                                                                 createMultipleImageResolutions(full.image).map(
                                                                   valueUtilV1.fileValueV12LocationV1
                                                                 )
                                                               (Some(preview), Some(Vector(preview) ++ fileVals))

                                                             case None => (None, None)
                                                           }

                                                         ResourceContextItemV1(
                                                           res_id = sourceObj.id,
                                                           preview = preview,
                                                           locations = locations,
                                                           firstprop = sourceObj.firstprop
                                                         )
                                                       }

          } yield contextItems
        } else {
          ZIO.attempt(Nil)
        }

      resinfoV1WithRegionsOption <-
        if (resinfo) {

          for {
            //
            // check if there are regions pointing to this resource
            //
            regionSparqlQuery <- ZIO.attempt(
                                   org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                                     .getRegions(
                                       resourceIri = resourceIri
                                     )
                                     .toString()
                                 )
            regionQueryResponse <- triplestoreService.sparqlHttpSelect(regionSparqlQuery)
            regionRows           = regionQueryResponse.results.bindings

            regionPropertiesSequencedFutures: Seq[Task[PropsGetForRegionV1]] =
              regionRows.filter { regionRow =>
                val permissionCodeForRegion =
                  PermissionUtilADM
                    .getUserPermissionV1(
                      entityIri = regionRow.rowMap("region"),
                      entityCreator = regionRow.rowMap("owner"),
                      entityProject = regionRow.rowMap("project"),
                      entityPermissionLiteral = regionRow.rowMap(
                        "regionObjectPermissions"
                      ),
                      userProfile = userProfileV1
                    )

                // ignore regions the user has no permissions on
                permissionCodeForRegion.nonEmpty
              }.map { regionRow =>
                val regionIri =
                  regionRow.rowMap("region")
                val resClass = regionRow.rowMap(
                  "resclass"
                ) // possibly we deal with a subclass of knora-base:Region

                // get the properties for each region
                for {
                  propsV1 <-
                    getResourceProperties(
                      resourceIri = regionIri,
                      maybeResourceTypeIri = Some(resClass),
                      userProfile = userProfile
                    )

                  propsGetV1 = propsV1.map {
                                 // convert each PropertyV1 in a PropertyGetV1
                                 propV1: PropertyV1 =>
                                   convertPropertyV1toPropertyGetV1(
                                     propV1
                                   )
                               }

                  // get the icon for this region's resource class
                  entityInfoResponse <-
                    messageRelay
                      .ask[EntityInfoGetResponseV1](
                        EntityInfoGetRequestV1(
                          resourceClassIris = Set(resClass),
                          userProfile = userProfile
                        )
                      )

                  regionInfo: ClassInfoV1 =
                    entityInfoResponse
                      .resourceClassInfoMap(resClass)

                  resClassIcon: Option[String] =
                    regionInfo.predicates.get(
                      OntologyConstants.KnoraBase.ResourceIcon
                    ) match {
                      case Some(
                            predicateInfo: PredicateInfoV1
                          ) =>
                        Some(
                          valueUtilV1.makeResourceClassIconURL(
                            resClass,
                            predicateInfo.objects.headOption
                              .getOrElse(
                                throw InconsistentRepositoryDataException(
                                  s"resourceClass $resClass has no value for ${OntologyConstants.KnoraBase.ResourceIcon}"
                                )
                              )
                          )
                        )
                      case None => None
                    }

                } yield PropsGetForRegionV1(
                  properties = propsGetV1,
                  res_id = regionIri,
                  iconsrc = resClassIcon
                )

              }

            // turn sequenced Futures into one Future of a sequence
            regionProperties <- ZIO.collectAll(regionPropertiesSequencedFutures)

            resinfoWithRegions: Option[ResourceInfoV1] =
              if (regionProperties.nonEmpty) {
                // regions are given, append them to resinfo
                Some(resInfoV1.copy(regions = Some(regionProperties)))
              } else {
                // no regions given, just return resinfo
                Some(resInfoV1)
              }
          } yield resinfoWithRegions
        } else {
          // resinfo is not requested
          ZIO.attempt(None)
        }

      resourceContextV1 = containingResourceIriOption match {
                            case Some(_) =>
                              // This resource is part of another resource, so return the resource info of the parent.
                              ResourceContextV1(
                                resinfo = resinfoV1WithRegionsOption,
                                parent_res_id = containingResourceIriOption,
                                parent_resinfo = containingResInfoV1Option,
                                context = ResourceContextCodeV1.RESOURCE_CONTEXT_IS_PARTOF,
                                canonical_res_id = resourceIri
                              )

                            case None =>
                              if (resourceContexts.nonEmpty) {
                                // This resource has parts, so return information about the parts.
                                ResourceContextV1(
                                  res_id = Some(resourceContexts.map(_.res_id)),
                                  preview = Some(resourceContexts.map(_.preview)),
                                  locations = Some(resourceContexts.map(_.locations)),
                                  firstprop = Some(resourceContexts.map(_.firstprop)),
                                  region = Some(resourceContexts.map(_ => None)),
                                  canonical_res_id = resourceIri,
                                  resinfo = resinfoV1WithRegionsOption,
                                  resclass_name = Some("image"),
                                  context = ResourceContextCodeV1.RESOURCE_CONTEXT_IS_COMPOUND
                                )
                              } else {
                                // Indicate that neither of the above is true.
                                ResourceContextV1(
                                  resinfo = resinfoV1WithRegionsOption,
                                  canonical_res_id = resourceIri,
                                  context = ResourceContextCodeV1.RESOURCE_CONTEXT_NONE
                                )
                              }
                          }
    } yield ResourceContextResponseV1(resource_context = resourceContextV1)

  }

  /**
   * Returns an instance of [[ResourceRightsResponseV1]] describing the permissions on a resource for the current user, in Knora API v1 format.
   *
   * @param resourceIri the IRI of the resource to be queried.
   * @param userProfile the profile of the user making the request.
   * @return a [[ResourceRightsResponseV1]] describing the permissions on the resource.
   */
  private def getRightsResponseV1(resourceIri: IRI, userProfile: UserADM): Task[ResourceRightsResponseV1] =
    getResourceInfoV1(resourceIri, userProfile, queryOntology = false).map(_._1).map(ResourceRightsResponseV1)

  /**
   * Searches for resources matching the given criteria.
   *
   * @param searchString    the string to search for.
   * @param resourceTypeIri if set, restrict search to this resource type.
   * @param numberOfProps   the amount of describing properties to be returned for each found resource (e.g if set to two, for an incunabula book its title and creator would be returned).
   * @param limitOfResults  limits number of resources to be returned.
   * @param userProfile     the profile of the user making the request.
   * @return the resources matching the given criteria.
   */
  private def getResourceSearchResponseV1(
    searchString: String,
    resourceTypeIri: Option[IRI],
    numberOfProps: Int,
    limitOfResults: Int,
    userProfile: UserADM
  ): Task[ResourceSearchResponseV1] = {
    val userProfileV1 = userProfile.asUserProfileV1
    val searchPhrase  = MatchStringWhileTyping(searchString)

    for {

      searchResourcesSparql <- ZIO.attempt(
                                 org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                                   .getResourceSearchResult(
                                     searchPhrase = searchPhrase,
                                     restypeIriOption = resourceTypeIri,
                                     numberOfProps = numberOfProps,
                                     limitOfResults = limitOfResults,
                                     separator = StringFormatter.INFORMATION_SEPARATOR_ONE
                                   )
                                   .toString()
                               )

      searchResponse <- triplestoreService.sparqlHttpSelect(searchResourcesSparql)

      resultFutures: Seq[Task[ResourceSearchResultRowV1]] =
        searchResponse.results.bindings.map { row: VariableResultsRow =>
          val resourceIri         = row.rowMap("resourceIri")
          val resourceClass       = row.rowMap("resourceClass")
          val firstProp           = row.rowMap("firstProp")
          val attachedToUser      = row.rowMap("attachedToUser")
          val attachedToProject   = row.rowMap("attachedToProject")
          val resourcePermissions = row.rowMap("resourcePermissions")

          val permissionCode = PermissionUtilADM.getUserPermissionV1(
            entityIri = resourceIri,
            entityCreator = attachedToUser,
            entityProject = attachedToProject,
            entityPermissionLiteral = resourcePermissions,
            userProfile = userProfileV1
          )

          for {
            resourceClassInfoResponse <- messageRelay
                                           .ask[EntityInfoGetResponseV1](
                                             EntityInfoGetRequestV1(
                                               resourceClassIris = Set(resourceClass),
                                               userProfile = userProfile
                                             )
                                           )

            cardinalities: Map[IRI, KnoraCardinalityInfo] = resourceClassInfoResponse
                                                              .resourceClassInfoMap(resourceClass)
                                                              .knoraResourceCardinalities

            searchResultRow: ResourceSearchResultRowV1 =
              if (numberOfProps > 1) {
                // The client requested more than one property per resource that was found.

                val maybeValues: Option[String] = row.rowMap.get("values")
                maybeValues match {
                  case Some(valuesReturned) =>
                    val valueStrings = valuesReturned.split(StringFormatter.INFORMATION_SEPARATOR_ONE)
                    val properties   = row.rowMap("properties").split(StringFormatter.INFORMATION_SEPARATOR_ONE)
                    val valueOrders =
                      row.rowMap("valueOrders").split(StringFormatter.INFORMATION_SEPARATOR_ONE).map(_.toInt)

                    val guiOrders: Array[Int] = properties.map { propertyIri =>
                      cardinalities(propertyIri).guiOrder match {
                        case Some(order) => order
                        case None        => -1
                      }
                    }

                    // create a list of three tuples, sort it by guiOrder and valueOrder and return only string values
                    val values: Seq[String] = valueStrings
                      .lazyZip(guiOrders)
                      .lazyZip(valueOrders)
                      .toVector
                      .sortBy(row => (row._2, row._3))
                      .map(_._1)

                    // ?values is given: it is one string to be split by separator
                    val propValues = values.foldLeft(Vector(firstProp)) { case (acc, prop: String) =>
                      if (prop == firstProp || prop == acc.last) {
                        // in the SPARQL results, all values are returned four times because of inclusion of permissions. If already existent, ignore prop.
                        acc
                      } else {
                        acc :+ prop // append prop to List
                      }
                    }

                    ResourceSearchResultRowV1(
                      id = row.rowMap("resourceIri"),
                      value = propValues.slice(
                        0,
                        numberOfProps
                      ), // take only as many elements as were requested by the client.
                      rights = permissionCode
                    )

                  case None =>
                    logger.debug("more values were asked (numberOfProps > 1), but there were none to be found")
                    ResourceSearchResultRowV1(
                      id = row.rowMap("resourceIri"),
                      value = Vector(firstProp),
                      rights = permissionCode
                    )
                }
              } else {
                // ?firstProp is sufficient: the client requested just one property per resource that was found
                ResourceSearchResultRowV1(
                  id = row.rowMap("resourceIri"),
                  value = Vector(firstProp),
                  rights = permissionCode
                )
              }

          } yield searchResultRow

        }

      resources <- ZIO.collectAll(resultFutures)
      filteredResources = resources.filter(
                            _.rights.nonEmpty
                          ) // user must have permissions to see resource (must not be None)

    } yield ResourceSearchResponseV1(resources = filteredResources)
  }

  /**
   * Create multiple resources and attach the given values to them.
   *
   * @param resourcesToCreate    collection of ResourceRequests .
   * @param projectIri           IRI of the project .
   * @param apiRequestID         the the ID of the API request.
   *
   * @param requestingUser       the user making the request.
   * @return a [[MultipleResourceCreateResponseV1]] informing the client about the new resources.
   */
  private def createMultipleNewResources(
    resourcesToCreate: Seq[OneOfMultipleResourceCreateRequestV1],
    projectIri: IRI,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[MultipleResourceCreateResponseV1] = {
    // Convert all the image metadata in the request to FileValueContentV2 instances, so we
    // can use ResourceUtilV2.doSipiPostUpdate after updating the triplestore.
    val fileValueContentV2s: Seq[FileValueContentV2] = resourcesToCreate.flatMap { resourceToCreate =>
      resourceToCreate.file.map(_.toFileValueContentV2)
    }

    val updateFuture: Task[MultipleResourceCreateResponseV1] = for {
      // Get user's IRI and don't allow anonymous users to create resources.
      userIri <- ZIO.attempt {
                   if (requestingUser.isAnonymousUser) {
                     throw ForbiddenException("Anonymous users aren't allowed to create resources")
                   } else {
                     requestingUser.id
                   }
                 }

      // Get information about the project in which the resources will be created.
      projectInfoResponse <-
        messageRelay
          .ask[ProjectGetResponseADM](
            ProjectGetRequestADM(identifier =
              IriIdentifier
                .fromString(projectIri)
                .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
            )
          )

      projectADM = projectInfoResponse.project

      // Ensure that the project isn't the system project or the shared ontologies project.

      resourceProjectIri: IRI = projectADM.id

      _ =
        if (
          resourceProjectIri == OntologyConstants.KnoraAdmin.SystemProject || resourceProjectIri == OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject
        ) {
          throw BadRequestException(s"Resources cannot be created in project $resourceProjectIri")
        }

      // Ensure that the resource class isn't from a non-shared ontology in another project.

      namedGraph = StringFormatter.getGeneralInstance.projectDataNamedGraphV2(projectADM)

      // Create random IRIs for resources, collect in Map[clientResourceID, IRI]
      clientResourceIDsToResourceIris: Map[String, IRI] =
        new ErrorHandlingMap(
          toWrap = resourcesToCreate
            .map(resRequest =>
              resRequest.clientResourceID -> stringFormatter.makeRandomResourceIri(projectADM.shortcode)
            )
            .toMap,
          errorTemplateFun = { key: Any =>
            s"Resource $key is the target of a link, but was not provided in the request"
          },
          errorFun = { errorMsg =>
            throw BadRequestException(errorMsg)
          }
        )

      // Map each clientResourceID to its resource class IRI
      clientResourceIDsToResourceClasses: Map[String, IRI] =
        new ErrorHandlingMap(
          toWrap = resourcesToCreate.map(resRequest => resRequest.clientResourceID -> resRequest.resourceTypeIri).toMap,
          errorTemplateFun = { key: Any =>
            s"Resource $key is the target of a link, but was not provided in the request"
          },
          errorFun = { errorMsg =>
            throw BadRequestException(errorMsg)
          }
        )

      // Get ontology information about all the resource classes and properties used in the request.

      resourceClasses: Set[IRI] = resourcesToCreate.map(_.resourceTypeIri).toSet

      // Ensure that none of the resource classes is from a non-shared ontology in another project.

      resourceClassOntologyIris: Set[SmartIri] = resourceClasses.map(_.toSmartIri.getOntologyFromEntity)
      readOntologyMetadataV2 <- messageRelay
                                  .ask[ReadOntologyMetadataV2](
                                    OntologyMetadataGetByIriRequestV2(
                                      resourceClassOntologyIris,
                                      requestingUser
                                    )
                                  )

      _ = for (ontologyMetadata <- readOntologyMetadataV2.ontologies) {
            val ontologyProjectIri: IRI = ontologyMetadata.projectIri
              .getOrElse(
                throw InconsistentRepositoryDataException(s"Ontology ${ontologyMetadata.ontologyIri} has no project")
              )
              .toString

            if (
              resourceProjectIri != ontologyProjectIri && !(ontologyMetadata.ontologyIri.isKnoraBuiltInDefinitionIri || ontologyMetadata.ontologyIri.isKnoraSharedDefinitionIri)
            ) {
              throw BadRequestException(
                s"Cannot create a resource in project $resourceProjectIri with a resource class from ontology ${ontologyMetadata.ontologyIri}, which belongs to another project and is not shared"
              )
            }
          }

      resourceClassesEntityInfoResponse <- messageRelay
                                             .ask[EntityInfoGetResponseV1](
                                               EntityInfoGetRequestV1(
                                                 resourceClassIris = resourceClasses,
                                                 propertyIris = Set.empty[IRI],
                                                 userProfile = requestingUser
                                               )
                                             )

      allPropertyIris: Set[IRI] = resourceClassesEntityInfoResponse.resourceClassInfoMap.flatMap {
                                    case (_, resourceEntityInfo) =>
                                      resourceEntityInfo.knoraResourceCardinalities.keySet
                                  }.toSet

      propertyEntityInfoResponse <- messageRelay
                                      .ask[EntityInfoGetResponseV1](
                                        EntityInfoGetRequestV1(
                                          resourceClassIris = Set.empty[IRI],
                                          propertyIris = allPropertyIris,
                                          userProfile = requestingUser
                                        )
                                      )

      propertyEntityInfoMapsPerResource: Map[IRI, Map[IRI, PropertyInfoV1]] =
        resourceClassesEntityInfoResponse.resourceClassInfoMap.map { case (resourceClassIri, resourceEntityInfo) =>
          val propertyEntityInfoMapForResource: Map[IRI, PropertyInfoV1] =
            resourceEntityInfo.knoraResourceCardinalities.keySet.map { propertyIri =>
              (propertyIri, propertyEntityInfoResponse.propertyInfoMap(propertyIri))
            }.toMap

          (resourceClassIri, propertyEntityInfoMapForResource)
        }

      // Get the default object access permissions for all the resource classes and properties used in the request.

      defaultResourceClassAccessPermissionsFutures: Vector[Task[(IRI, String)]] =
        resourceClasses.toVector.map { resourceClassIri =>
          for {
            defaultObjectAccessPermissions <-
              messageRelay
                .ask[DefaultObjectAccessPermissionsStringResponseADM](
                  DefaultObjectAccessPermissionsStringForResourceClassGetADM(
                    projectIri = projectIri,
                    resourceClassIri = resourceClassIri,
                    targetUser = requestingUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                  )
                )

          } yield (resourceClassIri, defaultObjectAccessPermissions.permissionLiteral)
        }

      defaultResourceClassAccessPermissionsSeq <- ZIO.collectAll(defaultResourceClassAccessPermissionsFutures)
      defaultResourceClassAccessPermissionsMap =
        new ErrorHandlingMap(
          defaultResourceClassAccessPermissionsSeq.toMap,
          { key: IRI =>
            s"No default resource class access permissions found for resource class $key"
          }
        )

      defaultPropertyAccessPermissionsFutures: Map[IRI, Task[Map[IRI, String]]] =
        propertyEntityInfoMapsPerResource.map { case (resourceClassIri, propertyInfoMap) =>
          val propertyPermissionFutures = propertyInfoMap.keys.map { propertyIri =>
            for {
              defaultObjectAccessPermissions <- messageRelay
                                                  .ask[DefaultObjectAccessPermissionsStringResponseADM](
                                                    DefaultObjectAccessPermissionsStringForPropertyGetADM(
                                                      projectIri = projectIri,
                                                      resourceClassIri = resourceClassIri,
                                                      propertyIri = propertyIri,
                                                      targetUser = requestingUser,
                                                      requestingUser = KnoraSystemInstances.Users.SystemUser
                                                    )
                                                  )

            } yield (propertyIri, defaultObjectAccessPermissions.permissionLiteral)
          }

          val propertyPermissionsFuture: Task[Map[IRI, String]] =
            ZIO.collectAll(propertyPermissionFutures).map(_.toMap)
          (resourceClassIri, propertyPermissionsFuture)
        }

      defaultPropertyAccessPermissionsMapContents <- ZioHelper.sequence(defaultPropertyAccessPermissionsFutures)

      defaultPropertyAccessPermissionsMapToWrap: Map[IRI, Map[IRI, String]] =
        defaultPropertyAccessPermissionsMapContents.map {
          case (resourceClassIri: IRI, propertyPermissions: Map[IRI, String]) =>
            resourceClassIri -> new ErrorHandlingMap(
              propertyPermissions,
              { key: IRI =>
                s"No default access permissions found for property $key in resource class $resourceClassIri"
              }
            )
        }

      defaultPropertyAccessPermissionsMap: Map[IRI, Map[IRI, String]] =
        new ErrorHandlingMap(
          defaultPropertyAccessPermissionsMapToWrap,
          { key: IRI =>
            s"No default property access permissions found for resource class $key"
          }
        )

      resourceCreationFutures: Seq[Task[SparqlTemplateResourceToCreate]] =
        resourcesToCreate.map { resourceCreateRequest: OneOfMultipleResourceCreateRequestV1 =>
          val creationDate: Instant = resourceCreateRequest.creationDate.getOrElse(Instant.now)

          for {
            // Check user's PermissionProfile (part of UserADM) to see if the user has the permission to
            // create a new resource in the given project.
            defaultObjectAccessPermissions <-
              ZIO.attempt(defaultResourceClassAccessPermissionsMap(resourceCreateRequest.resourceTypeIri))

            _ = if (resourceCreateRequest.resourceTypeIri == OntologyConstants.KnoraBase.Resource) {
                  throw BadRequestException(
                    s"Instances of knora-base:Resource cannot be created, only instances of subclasses"
                  )
                }

            resourceIri = clientResourceIDsToResourceIris(resourceCreateRequest.clientResourceID)

            // Check every resource to be created with respect of ontology and cardinalities. Links are still
            // represented by LinkToClientIDUpdateV1 instances here.
            fileValues <- checkResource(
                            resourceClassIri = resourceCreateRequest.resourceTypeIri,
                            resourceClassInfo = resourceClassesEntityInfoResponse.resourceClassInfoMap(
                              resourceCreateRequest.resourceTypeIri
                            ),
                            propertyInfoMap = propertyEntityInfoMapsPerResource(resourceCreateRequest.resourceTypeIri),
                            values = resourceCreateRequest.values,
                            convertedFile = resourceCreateRequest.file,
                            clientResourceIDsToResourceClasses = clientResourceIDsToResourceClasses,
                            userProfile = requestingUser
                          )

            // Convert each LinkToClientIDUpdateV1 into a LinkUpdateV1.
            resourceValuesWithLinkTargetIris: Map[IRI, Seq[CreateValueV1WithComment]] =
              resourceCreateRequest.values.map { case (propertyIri, valuesWithComments) =>
                val valuesWithLinkTargetIris = valuesWithComments.map { valueToCreate =>
                  valueToCreate.updateValueV1 match {
                    case LinkToClientIDUpdateV1(clientIDForTargetResource) =>
                      CreateValueV1WithComment(
                        LinkUpdateV1(
                          targetResourceIri = clientResourceIDsToResourceIris(clientIDForTargetResource),
                          targetExists = false
                        )
                      )
                    case _ => valueToCreate
                  }
                }

                propertyIri -> valuesWithLinkTargetIris
              }

            // generate sparql for every resource
            generateSparqlForValuesResponse <-
              generateSparqlForValuesOfNewResource(
                projectIri = projectIri,
                resourceIri = resourceIri,
                resourceClassIri = resourceCreateRequest.resourceTypeIri,
                defaultPropertyAccessPermissions =
                  defaultPropertyAccessPermissionsMap(resourceCreateRequest.resourceTypeIri),
                values = resourceValuesWithLinkTargetIris,
                clientResourceIDsToResourceIris = clientResourceIDsToResourceIris,
                creationDate = creationDate,
                fileValues = fileValues,
                userProfile = requestingUser,
                apiRequestID = apiRequestID
              )

          } yield SparqlTemplateResourceToCreate(
            resourceIri = resourceIri,
            permissions = defaultObjectAccessPermissions,
            sparqlForValues = generateSparqlForValuesResponse.insertSparql,
            resourceClassIri = resourceCreateRequest.resourceTypeIri,
            resourceLabel = resourceCreateRequest.label,
            resourceCreationDate = creationDate
          )
        }

      // change sequence of futures to future of sequences
      sparqlTemplateResourcesToCreate <- ZIO.collectAll(resourceCreationFutures)

      // create a sparql query for all the resources to be created
      createMultipleResourcesSparql: String = generateSparqlForNewResources(
                                                resourcesToCreate = sparqlTemplateResourcesToCreate,
                                                projectIri = projectIri,
                                                namedGraph = namedGraph,
                                                creatorIri = userIri
                                              )

      // Do the update.
      _ <- triplestoreService.sparqlHttpUpdate(createMultipleResourcesSparql)

      // We don't query the newly created resources to verify that they and their values were actually created,
      // because this would be too expensive. In any case, since the update is done with INSERT DATA, i.e. there is no WHERE clause,
      // any failure should result in an HTTP error from the triplestore (SPARQL 1.1 Protocol §2.2.5, "Failure Responses").

      responses: Seq[OneOfMultipleResourcesCreateResponseV1] =
        resourcesToCreate.map { resourceToCreate: OneOfMultipleResourceCreateRequestV1 =>
          OneOfMultipleResourcesCreateResponseV1(
            clientResourceID = resourceToCreate.clientResourceID,
            resourceIri = clientResourceIDsToResourceIris(resourceToCreate.clientResourceID),
            label = resourceToCreate.label
          )
        }
    } yield MultipleResourceCreateResponseV1(responses, projectADM)

    resourceUtilV2.doSipiPostUpdate(updateFuture, fileValueContentV2s, requestingUser)
  }

  /**
   * Check the resource to be created.
   *
   * @param resourceClassIri                   type of resource.
   * @param resourceClassInfo                  ontology information about the resource class.
   * @param propertyInfoMap                    ontology information about the properties attached to the resource class.
   * @param values                             values to be created for resource. If `linkTargetsAlreadyExist` is true, any links must be represented as [[LinkUpdateV1]] instances.
   *                                           Otherwise, they must be represented as [[LinkToClientIDUpdateV1]] instances, so that appropriate error messages can
   *                                           be generated for links to missing resources.
   * @param convertedFile                      an already converted file to be attached to the resource.
   * @param clientResourceIDsToResourceClasses for each client resource ID, the IRI of the resource's class. Used only if `linkTargetsAlreadyExist` is false.
   * @param userProfile                        the profile of the user making the request.
   * @return a tuple (IRI, Vector[CreateValueV1WithComment]) containing the IRI of the resource and a collection of holders of [[UpdateValueV1]] and comment.
   */
  private def checkResource(
    resourceClassIri: IRI,
    resourceClassInfo: ClassInfoV1,
    propertyInfoMap: Map[IRI, PropertyInfoV1],
    values: Map[IRI, Seq[CreateValueV1WithComment]],
    convertedFile: Option[FileValueV1],
    clientResourceIDsToResourceClasses: Map[String, IRI] = new ErrorHandlingMap[IRI, IRI](
      toWrap = Map.empty[IRI, IRI],
      errorTemplateFun = { key =>
        s"Resource $key is the target of a link, but was not provided in the request"
      },
      errorFun = { errorMsg =>
        throw BadRequestException(errorMsg)
      }
    ),
    userProfile: UserADM
  ): Task[Option[(IRI, Vector[CreateValueV1WithComment])]] = {
    for {
      // Check that each submitted value is consistent with the knora-base:objectClassConstraint of the property that is supposed to
      // point to it.
      _ <- ZIO.collectAll {
             values.foldLeft(Vector.empty[Task[Unit]]) { case (acc, (propertyIri, valuesWithComments)) =>
               val propertyInfo =
                 propertyInfoMap.getOrElse(
                   propertyIri,
                   throw NotFoundException(
                     s"Property not found: $propertyIri"
                   )
                 )
               val propertyObjectClassConstraint =
                 propertyInfo
                   .getPredicateObject(
                     OntologyConstants.KnoraBase.ObjectClassConstraint
                   )
                   .getOrElse {
                     throw InconsistentRepositoryDataException(
                       s"Property $propertyIri has no knora-base:objectClassConstraint"
                     )
                   }

               acc ++ valuesWithComments.map { valueV1WithComment: CreateValueV1WithComment =>
                 valueV1WithComment.updateValueV1 match {
                   case LinkToClientIDUpdateV1(targetClientID) =>
                     // We're creating a link to a resource that doesn't exist yet, because it
                     // will be created in the same transaction. Check that it will belong to a
                     // suitable class.
                     val checkSubClassRequest = CheckSubClassRequestV1(
                       subClassIri = clientResourceIDsToResourceClasses(
                         targetClientID
                       ),
                       superClassIri = propertyObjectClassConstraint,
                       userProfile = userProfile
                     )

                     for {
                       subClassResponse <- messageRelay.ask[CheckSubClassResponseV1](checkSubClassRequest)

                       _ = if (!subClassResponse.isSubClass) {
                             throw OntologyConstraintException(
                               s"Resource $targetClientID cannot be the target of property $propertyIri, because it is not a member of OWL class $propertyObjectClassConstraint"
                             )
                           }
                     } yield ()

                   case linkUpdate: LinkUpdateV1 =>
                     // We're creating a link to an existing resource. Check that it belongs to a
                     // suitable class.
                     for {
                       checkTargetClassResponse <-
                         checkResourceClass(
                           resourceIri = linkUpdate.targetResourceIri,
                           owlClass = propertyObjectClassConstraint,
                           userProfile = userProfile
                         )

                       _ = if (!checkTargetClassResponse.isInClass) {
                             throw OntologyConstraintException(
                               s"Resource ${linkUpdate.targetResourceIri} cannot be the target of property $propertyIri, because it is not a member of OWL class $propertyObjectClassConstraint"
                             )
                           }
                     } yield ()

                   case otherValue =>
                     // We're creating an ordinary value. Check that its type is valid for the property's
                     // knora-base:objectClassConstraint.
                     valueUtilV1.checkValueTypeForPropertyObjectClassConstraint(
                       propertyIri = propertyIri,
                       propertyObjectClassConstraint = propertyObjectClassConstraint,
                       valueType = otherValue.valueTypeIri,
                       userProfile = userProfile
                     )
                 }
               }
             }
           }

      // Check that the resource class has a suitable cardinality for each submitted value.

      _ = values.foreach { case (propertyIri, valuesForProperty) =>
            val cardinalityInfo = resourceClassInfo.knoraResourceCardinalities.getOrElse(
              propertyIri,
              throw OntologyConstraintException(
                s"Resource class $resourceClassIri has no cardinality for property $propertyIri"
              )
            )

            if (
              (cardinalityInfo.cardinality == ZeroOrOne || cardinalityInfo.cardinality == ExactlyOne) && valuesForProperty.size > 1
            ) {
              throw OntologyConstraintException(
                s"Resource class $resourceClassIri does not allow more than one value for property $propertyIri"
              )
            }
          }

      // Check that no required values are missing.
      requiredProps: Set[IRI] = resourceClassInfo.knoraResourceCardinalities.filter { case (_, cardinalityInfo) =>
                                  cardinalityInfo.cardinality == ExactlyOne || cardinalityInfo.cardinality == AtLeastOne
                                }.keySet -- resourceClassInfo.linkValueProperties -- resourceClassInfo.fileValueProperties // exclude link value and file value properties from checking

      submittedPropertyIris = values.keySet

      _ = if (!requiredProps.subsetOf(submittedPropertyIris)) {
            val missingProps = (requiredProps -- submittedPropertyIris).mkString(", ")
            throw OntologyConstraintException(
              s"Values were not submitted for the following property or properties, which are required by resource class $resourceClassIri: $missingProps"
            )
          }

      // check if a file value is required by the ontology
      fileValues: Option[(IRI, Vector[CreateValueV1WithComment])] =
        if (resourceClassInfo.fileValueProperties.nonEmpty) {
          convertedFile match {
            case Some(converted) =>
              // TODO: check if the file type returned by Sipi corresponds to the expected fileValue property in resourceClassInfo.fileValueProperties.head
              Some(resourceClassInfo.fileValueProperties.head -> Vector(CreateValueV1WithComment(converted)))

            case None => throw BadRequestException(s"File required but none submitted")
          }

        } else {
          if (convertedFile.nonEmpty) {
            throw BadRequestException(
              s"File params are given but resource class $resourceClassIri does not allow any representation"
            )
          } else {
            None
          }
        }
    } yield fileValues
  }

  /**
   * Generates SPARQL to create the values for a resource.
   *
   * @param projectIri                       the IRI of the project.
   * @param resourceIri                      the IRI of the resource to be created.
   * @param resourceClassIri                 the IRI of the resource class.
   * @param defaultPropertyAccessPermissions the default object access permissions of each property attached to the resource class.
   * @param values                           the values to be created for resource.
   * @param fileValues                       the file values to be created with the resource.
   * @param clientResourceIDsToResourceIris  a map of client resource IDs (which may appear in standoff link tags
   *                                         in values passed to this method) to the IRIs that will be used for
   *                                         those resources.
   * @param userProfile                      the profile of the user making the request.
   * @param apiRequestID                     the the ID of the API request.
   * @return a [[GenerateSparqlToCreateMultipleValuesResponseV1]] returns response of generation of SPARQL for multiple values.
   */
  def generateSparqlForValuesOfNewResource(
    projectIri: IRI,
    resourceIri: IRI,
    resourceClassIri: IRI,
    defaultPropertyAccessPermissions: Map[IRI, String],
    values: Map[IRI, Seq[CreateValueV1WithComment]],
    fileValues: Option[(IRI, Vector[CreateValueV1WithComment])],
    clientResourceIDsToResourceIris: Map[String, IRI],
    creationDate: Instant,
    userProfile: UserADM,
    apiRequestID: UUID
  ): Task[GenerateSparqlToCreateMultipleValuesResponseV1] =
    for {
      // Ask the values responder for the SPARQL statements that are needed to create the values.
      generateSparqlForValuesRequest <- ZIO.attempt(
                                          GenerateSparqlToCreateMultipleValuesRequestV1(
                                            projectIri = projectIri,
                                            resourceIri = resourceIri,
                                            resourceClassIri = resourceClassIri,
                                            defaultPropertyAccessPermissions = defaultPropertyAccessPermissions,
                                            values = values ++ fileValues,
                                            clientResourceIDsToResourceIris = clientResourceIDsToResourceIris,
                                            creationDate = creationDate,
                                            userProfile = userProfile,
                                            apiRequestID = apiRequestID
                                          )
                                        )

      generateSparqlForValuesResponse <-
        messageRelay.ask[GenerateSparqlToCreateMultipleValuesResponseV1](generateSparqlForValuesRequest)
    } yield generateSparqlForValuesResponse

  /**
   * Generates SPARQL to create multiple resources in a single update operation.
   *
   * @param resourcesToCreate Collection of the resources to be created .
   * @param projectIri        IRI of the project .
   * @param namedGraph        the named graph the resources belongs to.
   * @param creatorIri        the creator of the resources to be created.
   * @return a [String] returns a Sparql query for creating the resources and their values .
   */
  def generateSparqlForNewResources(
    resourcesToCreate: Seq[SparqlTemplateResourceToCreate],
    projectIri: IRI,
    namedGraph: IRI,
    creatorIri: IRI
  ): String =
    // Generate SPARQL for creating the resources, and include the SPARQL for creating the values of every resource.
    org.knora.webapi.messages.twirl.queries.sparql.v1.txt
      .createNewResources(
        dataNamedGraph = namedGraph,
        resourcesToCreate = resourcesToCreate,
        projectIri = projectIri,
        creatorIri = creatorIri
      )
      .toString()

  /**
   * Verifies the created resource and its values.
   *
   * @param resourceIri                     IRI of the created resource .
   * @param creatorIri                      the creator of the resources to be created.
   * @param createNewResourceSparql         Sparql query to create the resource .
   * @param generateSparqlForValuesResponse Sparql statement for creation of values of resource.
   * @param projectADM                      the project in which the resource was created.
   * @param userProfile                     the profile of the user making the request.
   * @return a [[ResourceCreateResponseV1]] containing information about the created resource .
   */
  def verifyResourceCreated(
    resourceIri: IRI,
    creatorIri: IRI,
    createNewResourceSparql: String,
    generateSparqlForValuesResponse: GenerateSparqlToCreateMultipleValuesResponseV1,
    projectADM: ProjectADM,
    userProfile: UserADM
  ): Task[ResourceCreateResponseV1] =
    // Verify that the resource was created.
    for {
      createdResourcesSparql <- ZIO.attempt(
                                  org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                                    .getCreatedResource(
                                      resourceIri = resourceIri
                                    )
                                    .toString()
                                )

      createdResourceResponse <- triplestoreService.sparqlHttpSelect(createdResourcesSparql)

      _ = if (createdResourceResponse.results.bindings.isEmpty) {
            logger.error(
              s"Attempted a SPARQL update to create a new resource, but it inserted no rows:\n\n$createNewResourceSparql"
            )
            throw UpdateNotPerformedException(
              s"Resource $resourceIri was not created. Please report this as a possible bug."
            )
          }

      // Verify that all the requested values were created.
      verifyCreateValuesRequest = VerifyMultipleValueCreationRequestV1(
                                    resourceIri = resourceIri,
                                    unverifiedValues = generateSparqlForValuesResponse.unverifiedValues,
                                    userProfile = userProfile
                                  )

      verifyMultipleValueCreationResponse <-
        messageRelay.ask[VerifyMultipleValueCreationResponseV1](verifyCreateValuesRequest)

      // Convert CreateValueResponseV1 objects to ResourceCreateValueResponseV1 objects.
      resourceCreateValueResponses: Map[IRI, Seq[ResourceCreateValueResponseV1]] =
        verifyMultipleValueCreationResponse.verifiedValues.map {
          case (propIri: IRI, values: Seq[CreateValueResponseV1]) =>
            (
              propIri,
              values.map { valueResponse: CreateValueResponseV1 =>
                valueUtilV1.convertCreateValueResponseV1ToResourceCreateValueResponseV1(
                  creatorIri = creatorIri,
                  propertyIri = propIri,
                  resourceIri = resourceIri,
                  valueResponse = valueResponse
                )
              }
            )
        }

      apiResponse: ResourceCreateResponseV1 = ResourceCreateResponseV1(
                                                results = resourceCreateValueResponses,
                                                res_id = resourceIri,
                                                projectADM = projectADM
                                              )
    } yield apiResponse

  /**
   * Does pre-update checks, creates a resource, and verifies that it was created.
   *
   * @param resourceClassIri     the IRI of the resource class.
   * @param projectADM           the project in which the resource should be created.
   * @param label                the `rdfs:label` of the resource to be created.
   * @param resourceIri          the IRI of the resource to be created.
   * @param values               the values to be attached to the resource.
   * @param file                 a file that has been uploaded to Sipi's temporary storage and should be attached to the resource.
   * @param creatorIri           the creator of the resource to be created.
   * @param namedGraph           the named graph the resource belongs to.
   *
   * @param requestingUser       the user making the request.
   * @param apiRequestID         the request ID used for locking the resource.
   * @return a [[ResourceCreateResponseV1]] containing information about the created resource.
   */
  def createResourceAndCheck(
    resourceClassIri: IRI,
    projectADM: ProjectADM,
    label: String,
    resourceIri: IRI,
    values: Map[IRI, Seq[CreateValueV1WithComment]],
    file: Option[FileValueV1],
    creatorIri: IRI,
    namedGraph: IRI,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[ResourceCreateResponseV1] = {
    val fileValueContent: Option[FileValueContentV2] = file.map(_.toFileValueContentV2)

    val updateFuture = for {
      // Get ontology information about the resource class and its properties.
      resourceClassEntityInfoResponse <- messageRelay
                                           .ask[EntityInfoGetResponseV1](
                                             EntityInfoGetRequestV1(
                                               resourceClassIris = Set(resourceClassIri),
                                               propertyIris = Set.empty[IRI],
                                               userProfile = requestingUser
                                             )
                                           )

      resourceClassInfo = resourceClassEntityInfoResponse.resourceClassInfoMap(resourceClassIri)

      propertyEntityInfoResponse <- messageRelay
                                      .ask[EntityInfoGetResponseV1](
                                        EntityInfoGetRequestV1(
                                          resourceClassIris = Set.empty[IRI],
                                          propertyIris = resourceClassInfo.knoraResourceCardinalities.keySet,
                                          userProfile = requestingUser
                                        )
                                      )

      propertyInfoMap = propertyEntityInfoResponse.propertyInfoMap

      // Get the default object access permissions of the resource class and its properties.

      defaultResourceClassAccessPermissionsResponse <-
        messageRelay
          .ask[DefaultObjectAccessPermissionsStringResponseADM](
            DefaultObjectAccessPermissionsStringForResourceClassGetADM(
              projectIri = projectADM.id,
              resourceClassIri = resourceClassIri,
              targetUser = requestingUser,
              requestingUser = KnoraSystemInstances.Users.SystemUser
            )
          )

      defaultResourceClassAccessPermissions = defaultResourceClassAccessPermissionsResponse.permissionLiteral

      defaultPropertyAccessPermissionsFutures: Iterable[Task[(IRI, String)]] =
        propertyEntityInfoResponse.propertyInfoMap.keys.map { propertyIri =>
          for {
            defaultObjectAccessPermissions <- messageRelay
                                                .ask[DefaultObjectAccessPermissionsStringResponseADM](
                                                  DefaultObjectAccessPermissionsStringForPropertyGetADM(
                                                    projectIri = projectADM.id,
                                                    resourceClassIri = resourceClassIri,
                                                    propertyIri = propertyIri,
                                                    targetUser = requestingUser,
                                                    requestingUser = KnoraSystemInstances.Users.SystemUser
                                                  )
                                                )
          } yield (propertyIri, defaultObjectAccessPermissions.permissionLiteral)
        }

      defaultPropertyAccessPermissionsIterable <- ZIO.collectAll(defaultPropertyAccessPermissionsFutures)
      defaultPropertyAccessPermissions          = defaultPropertyAccessPermissionsIterable.toMap

      fileValues <- checkResource(
                      resourceClassIri = resourceClassIri,
                      resourceClassInfo = resourceClassInfo,
                      propertyInfoMap = propertyInfoMap,
                      values = values,
                      convertedFile = file,
                      userProfile = requestingUser
                    )

      // Everything looks OK, so we can create the resource and its values.

      // Make a timestamp for the resource and its values.
      creationDate: Instant = Instant.now

      generateSparqlForValuesResponse <-
        generateSparqlForValuesOfNewResource(
          projectIri = projectADM.id,
          resourceIri = resourceIri,
          resourceClassIri = resourceClassIri,
          defaultPropertyAccessPermissions = defaultPropertyAccessPermissions,
          values = values,
          fileValues = fileValues,
          clientResourceIDsToResourceIris = Map.empty[String, IRI],
          creationDate = creationDate,
          userProfile = requestingUser,
          apiRequestID = apiRequestID
        )

      resourcesToCreate: Seq[SparqlTemplateResourceToCreate] = Seq(
                                                                 SparqlTemplateResourceToCreate(
                                                                   resourceIri = resourceIri,
                                                                   permissions = defaultResourceClassAccessPermissions,
                                                                   sparqlForValues =
                                                                     generateSparqlForValuesResponse.insertSparql,
                                                                   resourceClassIri = resourceClassIri,
                                                                   resourceLabel = label,
                                                                   resourceCreationDate = creationDate
                                                                 )
                                                               )

      createNewResourceSparql = generateSparqlForNewResources(
                                  resourcesToCreate = resourcesToCreate,
                                  projectIri = projectADM.id,
                                  namedGraph = namedGraph,
                                  creatorIri = creatorIri
                                )

      // Do the update.
      _ <- triplestoreService.sparqlHttpUpdate(createNewResourceSparql)

      apiResponse <- verifyResourceCreated(
                       resourceIri = resourceIri,
                       creatorIri = creatorIri,
                       createNewResourceSparql = createNewResourceSparql,
                       generateSparqlForValuesResponse = generateSparqlForValuesResponse,
                       projectADM = projectADM,
                       userProfile = requestingUser
                     )
    } yield apiResponse

    resourceUtilV2.doSipiPostUpdate(updateFuture, fileValueContent.toSeq, requestingUser)
  }

  /**
   * Creates a new resource and attaches the given values to it.
   *
   * @param resourceClassIri     the resource type of the resource to be created.
   * @param values               the values to be attached to the resource.
   * @param file                 a file that has been uploaded to Sipi's temporary storage and should be attached to the resource.
   * @param projectIri           the project the resource belongs to.
   *
   * @param userProfile          the user that is creating the resource
   * @param apiRequestID         the ID of this API request.
   * @return a [[ResourceCreateResponseV1]] informing the client about the new resource.
   */
  private def createNewResource(
    resourceClassIri: IRI,
    label: String,
    values: Map[IRI, Seq[CreateValueV1WithComment]],
    file: Option[FileValueV1] = None,
    projectIri: IRI,
    userProfile: UserADM,
    apiRequestID: UUID
  ): Task[ResourceCreateResponseV1] =
    for {

      // Get user's IRI and don't allow anonymous users to create resources.
      userIri <- ZIO.attempt {
                   if (userProfile.isAnonymousUser) {
                     throw ForbiddenException("Anonymous users aren't allowed to create resources")
                   } else {
                     userProfile.id
                   }
                 }

      _ = if (resourceClassIri == OntologyConstants.KnoraBase.Resource) {
            throw BadRequestException(
              s"Instances of knora-base:Resource cannot be created, only instances of subclasses"
            )
          }

      // Get project info
      projectResponse <-
        messageRelay
          .ask[ProjectGetResponseADM](
            ProjectGetRequestADM(identifier =
              IriIdentifier
                .fromString(projectIri)
                .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
            )
          )

      // Ensure that the project isn't the system project or the shared ontologies project.

      resourceProjectIri: IRI = projectResponse.project.id

      _ =
        if (
          resourceProjectIri == OntologyConstants.KnoraAdmin.SystemProject || resourceProjectIri == OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject
        ) {
          throw BadRequestException(s"Resources cannot be created in project $resourceProjectIri")
        }

      // Ensure that the resource class isn't from a non-shared ontology in another project.

      resourceClassOntologyIri: SmartIri = resourceClassIri.toSmartIri.getOntologyFromEntity
      readOntologyMetadataV2 <- messageRelay.ask[ReadOntologyMetadataV2](
                                  OntologyMetadataGetByIriRequestV2(
                                    Set(resourceClassOntologyIri),
                                    userProfile
                                  )
                                )
      ontologyMetadata: OntologyMetadataV2 =
        readOntologyMetadataV2.ontologies.headOption
          .getOrElse(throw BadRequestException(s"Ontology $resourceClassOntologyIri not found"))
      ontologyProjectIri: IRI =
        ontologyMetadata.projectIri
          .getOrElse(throw InconsistentRepositoryDataException(s"Ontology $resourceClassOntologyIri has no project"))
          .toString

      _ =
        if (
          resourceProjectIri != ontologyProjectIri && !(ontologyMetadata.ontologyIri.isKnoraBuiltInDefinitionIri || ontologyMetadata.ontologyIri.isKnoraSharedDefinitionIri)
        ) {
          throw BadRequestException(
            s"Cannot create a resource in project $resourceProjectIri with resource class $resourceClassIri, which is defined in a non-shared ontology in another project"
          )
        }

      namedGraph       = StringFormatter.getGeneralInstance.projectDataNamedGraphV2(projectResponse.project)
      resourceIri: IRI = stringFormatter.makeRandomResourceIri(projectResponse.project.shortcode)

      // Check user's PermissionProfile (part of UserADM) to see if the user has the permission to
      // create a new resource in the given project.
      _ =
        if (
          !userProfile.permissions.hasPermissionFor(ResourceCreateOperation(resourceClassIri), resourceProjectIri, None)
        ) {
          throw ForbiddenException(
            s"User $userIri does not have permissions to create a resource in project $resourceProjectIri"
          )
        }

      result <- IriLocker.runWithIriLock(
                  apiRequestID,
                  resourceIri,
                  createResourceAndCheck(
                    resourceClassIri = resourceClassIri,
                    projectADM = projectResponse.project,
                    label = label,
                    resourceIri = resourceIri,
                    values = values,
                    file = file,
                    creatorIri = userIri,
                    namedGraph = namedGraph,
                    requestingUser = userProfile,
                    apiRequestID = apiRequestID
                  )
                )
    } yield result

  /**
   * Marks a resource as deleted.
   *
   * @param resourceDeleteRequest a [[ResourceDeleteRequestV1]].
   * @return a [[ResourceDeleteResponseV1]].
   */
  private def deleteResourceV1(resourceDeleteRequest: ResourceDeleteRequestV1): Task[ResourceDeleteResponseV1] = {

    def makeTaskFuture(userIri: IRI): Task[ResourceDeleteResponseV1] =
      for {
        // Check that the user has permission to delete the resource.
        t <- getResourceInfoV1(
               resourceIri = resourceDeleteRequest.resourceIri,
               userProfile = resourceDeleteRequest.userADM,
               queryOntology = false
             )
        permissionCode = t._1
        resourceInfo   = t._2

        _ = if (
              !PermissionUtilADM.impliesPermissionCodeV1(
                userHasPermissionCode = permissionCode,
                userNeedsPermission = OntologyConstants.KnoraBase.DeletePermission
              )
            ) {
              throw ForbiddenException(
                s"User $userIri does not have permission to mark resource ${resourceDeleteRequest.resourceIri} as deleted"
              )
            }

        projectInfoResponse <- messageRelay
                                 .ask[ProjectInfoResponseV1](
                                   ProjectInfoByIRIGetRequestV1(
                                     iri = resourceInfo.project_id,
                                     userProfileV1 = None
                                   )
                                 )

        // Make a timestamp to indicate when the resource was marked as deleted.
        currentTime: String = Instant.now.toString

        // Create update sparql string
        sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                         .deleteResource(
                           dataNamedGraph = StringFormatter.getGeneralInstance
                             .projectDataNamedGraphV1(projectInfoResponse.project_info),
                           resourceIri = resourceDeleteRequest.resourceIri,
                           maybeDeleteComment = resourceDeleteRequest.deleteComment,
                           currentTime = currentTime,
                           requestingUser = resourceDeleteRequest.userADM.id
                         )
                         .toString()

        // Do the update.
        _ <- triplestoreService.sparqlHttpUpdate(sparqlUpdate)

        // Check whether the update succeeded.
        sparqlQuery = org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                        .checkResourceDeletion(
                          resourceIri = resourceDeleteRequest.resourceIri
                        )
                        .toString()
        sparqlSelectResponse <- triplestoreService.sparqlHttpSelect(sparqlQuery)
        rows                  = sparqlSelectResponse.results.bindings

        _ =
          if (
            rows.isEmpty || !ValuesValidator.optionStringToBoolean(rows.head.rowMap.get("isDeleted"), fallback = false)
          ) {
            throw UpdateNotPerformedException(
              s"Resource ${resourceDeleteRequest.resourceIri} was not marked as deleted. Please report this as a possible bug."
            )
          }
      } yield ResourceDeleteResponseV1(id = resourceDeleteRequest.resourceIri)

    for {
      // Don't allow anonymous users to delete resources.
      userIri <- ZIO.attempt {
                   if (resourceDeleteRequest.userADM.isAnonymousUser) {
                     throw ForbiddenException("Anonymous users aren't allowed to mark resources as deleted")
                   } else {
                     resourceDeleteRequest.userADM.id
                   }
                 }

      // Do the remaining pre-update checks and the update while holding an update lock on the resource.
      taskResult <- IriLocker.runWithIriLock(
                      resourceDeleteRequest.apiRequestID,
                      resourceDeleteRequest.resourceIri,
                      makeTaskFuture(userIri)
                    )
    } yield taskResult
  }

  /**
   * Checks whether a resource belongs to a certain OWL class or to a subclass of that class.
   *
   * @param resourceIri          the IRI of the resource to be checked.
   * @param owlClass             the IRI of the OWL class to compare the resource's class to.
   *
   * @param userProfile          the profile of the user making the request.
   * @return a [[ResourceCheckClassResponseV1]].
   */
  private def checkResourceClass(
    resourceIri: IRI,
    owlClass: IRI,
    userProfile: UserADM
  ): Task[ResourceCheckClassResponseV1] =
    for {
      // Check that the user has permission to view the resource.
      t <- getResourceInfoV1(
             resourceIri = resourceIri,
             userProfile = userProfile,
             queryOntology = false
           )
      permissionCode = t._1
      resourceInfo   = t._2
      _ = if (
            !PermissionUtilADM.impliesPermissionCodeV1(
              userHasPermissionCode = permissionCode,
              userNeedsPermission = OntologyConstants.KnoraBase.RestrictedViewPermission
            )
          ) {
            throw ForbiddenException(s"User ${userProfile.id} does not have permission to view resource $resourceIri")
          }

      checkSubClassRequest = CheckSubClassRequestV1(
                               subClassIri = resourceInfo.restype_id,
                               superClassIri = owlClass,
                               userProfile = userProfile
                             )

      subClassResponse <- messageRelay.ask[CheckSubClassResponseV1](checkSubClassRequest)

    } yield ResourceCheckClassResponseV1(isInClass = subClassResponse.isSubClass)

  /**
   * Changes a resource's label.
   *
   * @param resourceIri  the IRI of the resource.
   * @param label        the new label.
   * @param apiRequestID the the ID of the API request.
   * @param userProfile  the profile of the user making the request.
   * @return a [[ChangeResourceLabelResponseV1]].
   */
  private def changeResourceLabelV1(
    resourceIri: IRI,
    label: String,
    apiRequestID: UUID,
    userProfile: UserADM
  ): Task[ChangeResourceLabelResponseV1] = {

    def makeTaskFuture(userIri: IRI): Task[ChangeResourceLabelResponseV1] =
      for {
        // get the resource's permissions
        t <- getResourceInfoV1(
               resourceIri = resourceIri,
               userProfile = userProfile,
               queryOntology = false
             )
        permissionCode = t._1
        resourceInfo   = t._2

        // check if the given user may change its label
        _ = if (
              !PermissionUtilADM.impliesPermissionCodeV1(
                userHasPermissionCode = permissionCode,
                userNeedsPermission = OntologyConstants.KnoraBase.ModifyPermission
              )
            ) {
              throw ForbiddenException(
                s"User $userIri does not have permission to change the label of resource $resourceIri"
              )
            }

        projectInfoResponse <- messageRelay
                                 .ask[ProjectInfoResponseV1](
                                   ProjectInfoByIRIGetRequestV1(
                                     iri = resourceInfo.project_id,
                                     userProfileV1 = None
                                   )
                                 )

        // get the named graph the resource is contained in by the resource's project
        namedGraph = StringFormatter.getGeneralInstance.projectDataNamedGraphV1(projectInfoResponse.project_info)

        // Make a timestamp to indicate when the resource was updated.
        currentTime: String = Instant.now.toString

        // the user has sufficient permissions to change the resource's label
        sparqlUpdate = org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                         .changeResourceLabel(
                           dataNamedGraph = namedGraph,
                           resourceIri = resourceIri,
                           label = label,
                           currentTime = currentTime
                         )
                         .toString()

        // Do the update.
        _ <- triplestoreService.sparqlHttpUpdate(sparqlUpdate)

        // Check whether the update succeeded.
        sparqlQuery = org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                        .checkResourceLabelChange(
                          resourceIri = resourceIri,
                          label = label
                        )
                        .toString()

        sparqlSelectResponse <- triplestoreService.sparqlHttpSelect(sparqlQuery)
        rows                  = sparqlSelectResponse.results.bindings

        // we expect exactly one row to be returned if the label was updated correctly in the data.
        _ = if (rows.length != 1) {
              throw UpdateNotPerformedException(
                s"The label of the resource $resourceIri was not updated correctly. Please report this as a possible bug."
              )
            }

      } yield ChangeResourceLabelResponseV1(res_id = resourceIri, label = label)

    for {
      // Don't allow anonymous users to change a resource's label.
      userIri <- ZIO.attempt {
                   if (userProfile.isAnonymousUser) {
                     throw ForbiddenException("Anonymous users aren't allowed to change a resource's label")
                   } else {
                     userProfile.id
                   }
                 }

      // Do the remaining pre-update checks and the update while holding an update lock on the resource.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID,
                      resourceIri,
                      makeTaskFuture(userIri)
                    )
    } yield taskResult

  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Helper methods.

  /**
   * Returns a [[ResourceInfoV1]] describing a resource.
   *
   * @param resourceIri          the IRI of the resource to be queried.
   *
   * @param userProfile          the user that is making the request.
   * @param queryOntology        if `true`, the ontology will be queried for information about the resource type, and the [[ResourceInfoV1]]
   *                             will include `restype_label`, `restype_description`, and `restype_iconsrc`. Otherwise, those member variables
   *                             will be empty.
   * @return a tuple (permission, [[ResourceInfoV1]]) describing the resource.
   */
  private def getResourceInfoV1(
    resourceIri: IRI,
    userProfile: UserADM,
    queryOntology: Boolean
  ): Task[(Option[Int], ResourceInfoV1)] =
    for {
      sparqlQuery <- ZIO.attempt(
                       org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                         .getResourceInfo(
                           resourceIri = resourceIri
                         )
                         .toString()
                     )
      resInfoResponse    <- triplestoreService.sparqlHttpSelect(sparqlQuery)
      resInfoResponseRows = resInfoResponse.results.bindings

      resInfo <- makeResourceInfoV1(
                   resourceIri = resourceIri,
                   resInfoResponseRows = resInfoResponseRows,
                   userProfile = userProfile,
                   queryOntology = queryOntology
                 )
    } yield resInfo

  /**
   * Queries the properties for the given resource.
   *
   * @param resourceIri          the IRI of the given resource.
   *
   * @param userProfile          the profile of the user making the request.
   * @return a [[PropertiesGetResponseV1]] representing the properties of the given resource.
   */
  private def getPropertiesV1(
    resourceIri: IRI,
    userProfile: UserADM
  ): Task[PropertiesGetResponseV1] =
    for {
      // get resource class of the specified resource
      resclassSparqlQuery <- ZIO.attempt(
                               org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                                 .getResourceClass(
                                   resourceIri = resourceIri
                                 )
                                 .toString()
                             )

      resclassQueryResponse <- triplestoreService.sparqlHttpSelect(resclassSparqlQuery)
      resclass = resclassQueryResponse.results.bindings.headOption
                   .getOrElse(throw InconsistentRepositoryDataException(s"No resource class given for $resourceIri"))

      properties <- getResourceProperties(
                      resourceIri = resourceIri,
                      maybeResourceTypeIri = Some(resclass.rowMap("resourceClass")),
                      userProfile = userProfile
                    )

      propertiesGetV1: Seq[PropertyGetV1] = properties.map { prop =>
                                              convertPropertyV1toPropertyGetV1(prop)
                                            }

    } yield PropertiesGetResponseV1(PropsGetV1(propertiesGetV1))

  /**
   * Queries the properties that have values for a given resource, and returns a [[Seq]] of [[PropertyV1]] objects representing
   * those properties and their values.
   *
   * @param resourceIri          the IRI of the resource to be queried.
   * @param maybeResourceTypeIri an optional IRI representing the resource's class. If provided, an additional query will be done
   *                             to get ontology-based information, such as labels and cardinalities, which will be included in
   *                             the returned [[PropertyV1]] objects.
   *
   * @param userProfile          the profile of the user making the request.
   * @return a [[Seq]] of [[PropertyV1]] objects representing the properties that have values for the resource.
   */
  private def getResourceProperties(
    resourceIri: IRI,
    maybeResourceTypeIri: Option[IRI],
    userProfile: UserADM
  ): Task[Seq[PropertyV1]] = {
    case class PropertyClassCardinalityInfos(
      propertyInfoMap: Map[IRI, PropertyInfoV1],
      resourceEntityInfoMap: Map[IRI, ClassInfoV1],
      propsAndCardinalities: Map[IRI, KnoraCardinalityInfo]
    )
    for {

      groupedPropsByType <- getGroupedProperties(resourceIri)

      infos <- maybeResourceTypeIri match {
                 case Some(resourceTypeIri) =>
                   val propertyEntityIris: Set[IRI] =
                     groupedPropsByType.groupedOrdinaryValueProperties.groupedProperties.keySet ++ groupedPropsByType.groupedLinkProperties.groupedProperties.keySet
                   val resourceEntityIris: Set[IRI] = Set(resourceTypeIri)

                   for {
                     entityInfoResponse <- messageRelay
                                             .ask[EntityInfoGetResponseV1](
                                               EntityInfoGetRequestV1(
                                                 resourceClassIris = resourceEntityIris,
                                                 propertyIris = propertyEntityIris,
                                                 userProfile = userProfile
                                               )
                                             )
                     resourceEntityInfoMap: Map[IRI, ClassInfoV1] = entityInfoResponse.resourceClassInfoMap
                     propertyInfoMap: Map[IRI, PropertyInfoV1]    = entityInfoResponse.propertyInfoMap

                     resourceTypeEntityInfo = resourceEntityInfoMap(resourceTypeIri)

                     // all properties and their cardinalities for the queried resource's type, except the ones that point to LinkValue objects
                     propsAndCardinalities: Map[IRI, KnoraCardinalityInfo] =
                       resourceTypeEntityInfo.knoraResourceCardinalities.filterNot { case (propertyIri, _) =>
                         resourceTypeEntityInfo.linkValueProperties(propertyIri)
                       }
                   } yield PropertyClassCardinalityInfos(propertyInfoMap, resourceEntityInfoMap, propsAndCardinalities)

                 case None =>
                   ZIO.attempt(
                     PropertyClassCardinalityInfos(
                       Map.empty[IRI, PropertyInfoV1],
                       Map.empty[IRI, ClassInfoV1],
                       Map.empty[IRI, KnoraCardinalityInfo]
                     )
                   )
               }

      projectShortcode = resourceIri.toSmartIri.getProjectCode
                           .getOrElse(throw InconsistentRepositoryDataException(s"Invalid resource IRI: $resourceIri"))

      queryResult <- queryResults2PropertyV1s(
                       containingResourceIri = resourceIri,
                       projectShortcode = projectShortcode,
                       groupedPropertiesByType = groupedPropsByType,
                       propertyInfoMap = infos.propertyInfoMap,
                       resourceEntityInfoMap = infos.resourceEntityInfoMap,
                       propsAndCardinalities = infos.propsAndCardinalities,
                       userProfile = userProfile
                     )
    } yield queryResult
  }

  /**
   * Converts a SPARQL query result into a [[ResourceInfoV1]]. Expects the query result to contain columns called `p` (predicate),
   * `o` (object), `objPred` (file value predicate, if `o` is a file value), and `objObj` (file value object).
   *
   * @param resourceIri          the IRI of the resource.
   * @param resInfoResponseRows  the SPARQL query result.
   *
   * @param userProfile          the user that is making the request.
   * @param queryOntology        if `true`, the ontology will be queried for information about the resource type, and the [[ResourceInfoV1]]
   *                             will include `restype_label`, `restype_description`, and `restype_iconsrc`. Otherwise, those member variables
   *                             will be empty.
   * @return a tuple (permission, [[ResourceInfoV1]]) describing the resource.
   */
  private def makeResourceInfoV1(
    resourceIri: IRI,
    resInfoResponseRows: Seq[VariableResultsRow],
    userProfile: UserADM,
    queryOntology: Boolean
  ): Task[(Option[Int], ResourceInfoV1)] = {
    val userProfileV1 = userProfile.asUserProfileV1

    if (resInfoResponseRows.isEmpty) {
      ZIO.fail(NotFoundException(s"Resource $resourceIri was not found (it may have been deleted)."))
    } else {
      for {

        // Extract the permission-relevant assertions from the query results.
        permissionRelevantAssertions <-
          ZIO.attempt(
            PermissionUtilADM.filterPermissionRelevantAssertions(
              resInfoResponseRows.map(row => (row.rowMap("prop"), row.rowMap("obj")))
            )
          )

        maybeResourceProjectStatement: Option[(IRI, IRI)] =
          permissionRelevantAssertions.find { case (subject, _) =>
            subject == OntologyConstants.KnoraBase.AttachedToProject
          }

        resourceProject =
          maybeResourceProjectStatement
            .getOrElse(
              throw InconsistentRepositoryDataException(s"Resource $resourceIri has no knora-base:attachedToProject")
            )
            ._2
        projectShortcode: String =
          resourceIri.toSmartIri.getProjectCode
            .getOrElse(throw InconsistentRepositoryDataException(s"Invalid resource IRI $resourceIri"))

        // Get the rows describing file values from the query results, grouped by file value IRI.
        fileValueGroupedRows: Seq[(IRI, Seq[VariableResultsRow])] =
          resInfoResponseRows
            .filter(row => ValuesValidator.optionStringToBoolean(row.rowMap.get("isFileValue"), fallback = false))
            .groupBy(row => row.rowMap("obj"))
            .toVector

        // Convert the file value rows to ValueProps objects, and filter out the ones that the user doesn't have permission to see.
        valuePropsForFileValues: Seq[(IRI, ValueProps)] = fileValueGroupedRows.map {
                                                            case (fileValueIri, fileValueRows) =>
                                                              (
                                                                fileValueIri,
                                                                valueUtilV1.createValueProps(
                                                                  fileValueIri,
                                                                  fileValueRows
                                                                )
                                                              )
                                                          }.filter { case (fileValueIri, fileValueProps) =>
                                                            val permissionCode =
                                                              PermissionUtilADM.getUserPermissionWithValuePropsV1(
                                                                valueIri = fileValueIri,
                                                                valueProps = fileValueProps,
                                                                entityProject = Some(resourceProject),
                                                                userProfile = userProfileV1
                                                              )
                                                            PermissionUtilADM.impliesPermissionCodeV1(
                                                              userHasPermissionCode = permissionCode,
                                                              userNeedsPermission =
                                                                OntologyConstants.KnoraBase.RestrictedViewPermission
                                                            )
                                                          }

        // Convert the ValueProps objects into FileValueV1 objects
        fileValuesWithFuture: Seq[Task[FileValueV1]] = valuePropsForFileValues.map {
                                                         case (fileValueIri, fileValueProps) =>
                                                           for {
                                                             valueV1 <-
                                                               valueUtilV1.makeValueV1(
                                                                 valueProps = fileValueProps,
                                                                 projectShortcode = projectShortcode,
                                                                 userProfile = userProfile
                                                               )

                                                           } yield valueV1 match {
                                                             case fileValueV1: FileValueV1 => fileValueV1
                                                             case otherValueV1 =>
                                                               throw InconsistentRepositoryDataException(
                                                                 s"Value $fileValueIri is not a knora-base:FileValue, it is an instance of ${otherValueV1.valueTypeIri}"
                                                               )
                                                           }
                                                       }

        fileValues <- ZIO.collectAll(fileValuesWithFuture)

        // Generate a IIIF preview URL from the full-size image.

        fullSizeImageFileValues: Seq[StillImageFileValueV1] = fileValues.collect {
                                                                case fileValue: StillImageFileValueV1 => fileValue
                                                              }

        preview: Option[LocationV1] =
          fullSizeImageFileValues.headOption.map { fullSizeImageFileValue: StillImageFileValueV1 =>
            valueUtilV1.fileValueV12LocationV1(fullSizeImageFileValueToPreview(fullSizeImageFileValue))
          }

        // Convert the file values into LocationV1 objects as required by Knora API v1.
        locations: Seq[LocationV1] = preview.toVector ++ fileValues.flatMap { fileValueV1 =>
                                       createMultipleImageResolutions(fileValueV1).map(oneResolution =>
                                         valueUtilV1.fileValueV12LocationV1(oneResolution)
                                       )
                                     }

        // Get the user's permission on the resource.
        userPermission = PermissionUtilADM.getUserPermissionFromAssertionsV1(
                           entityIri = resourceIri,
                           assertions = permissionRelevantAssertions,
                           userProfile = userProfileV1
                         )

        // group the SPARQL results by the predicate "prop" and map each row to a Seq of objects "obj", etc. (getting rid of VariableResultsRow).
        groupedByPredicateToWrap: Map[IRI, Seq[Map[String, String]]] =
          resInfoResponseRows
            .groupBy(row => row.rowMap("prop"))
            .map { case (predicate: IRI, rows: Seq[VariableResultsRow]) =>
              (predicate, rows.map(_.rowMap - "prop"))
            }

        groupedByPredicate = new ErrorHandlingMap(
                               groupedByPredicateToWrap,
                               { key: IRI =>
                                 s"Resource $resourceIri has no $key"
                               }
                             )

        // Query the ontology about the resource's OWL class.
        t <-
          if (queryOntology) {
            val resTypeIri = groupedByPredicate(OntologyConstants.Rdf.Type).head("obj")

            for {
              entityInfoResponse <- messageRelay
                                      .ask[EntityInfoGetResponseV1](
                                        EntityInfoGetRequestV1(
                                          resourceClassIris = Set(resTypeIri),
                                          userProfile = userProfile
                                        )
                                      )
              entityInfo = entityInfoResponse.resourceClassInfoMap(resTypeIri)
              label = entityInfo.getPredicateObject(
                        predicateIri = OntologyConstants.Rdfs.Label,
                        preferredLangs = Some(userProfile.lang, appConfig.fallbackLanguage)
                      )
              description = entityInfo.getPredicateObject(
                              predicateIri = OntologyConstants.Rdfs.Comment,
                              preferredLangs = Some(userProfile.lang, appConfig.fallbackLanguage)
                            )
              iconsrc = entityInfo.getPredicateObject(OntologyConstants.KnoraBase.ResourceIcon) match {
                          case Some(resClassIcon) =>
                            Some(valueUtilV1.makeResourceClassIconURL(resTypeIri, resClassIcon))
                          case _ => None
                        }
            } yield (label, description, iconsrc)
          } else {
            ZIO.attempt(None, None, None)
          }
        restype_label       = t._1
        restype_description = t._2
        restype_iconsrc     = t._3

        resourceInfo = ResourceInfoV1(
                         restype_id = groupedByPredicate(OntologyConstants.Rdf.Type).head("obj"),
                         firstproperty = Some(groupedByPredicate(OntologyConstants.Rdfs.Label).head("obj")),
                         preview = preview, // The first element of the list, or None if the list is empty
                         locations = if (locations.nonEmpty) Some(locations) else None,
                         locdata = locations.lastOption,
                         person_id = groupedByPredicate(OntologyConstants.KnoraBase.AttachedToUser).head("obj"),
                         project_id = groupedByPredicate(OntologyConstants.KnoraBase.AttachedToProject).head("obj"),
                         project_shortcode = projectShortcode,
                         restype_label = restype_label,
                         restype_name = Some(groupedByPredicate(OntologyConstants.Rdf.Type).head("obj")),
                         restype_description = restype_description,
                         restype_iconsrc = restype_iconsrc,
                         resclass_has_location = locations.nonEmpty
                       )
      } yield (userPermission, resourceInfo)
    }
  }

  /**
   * Queries the properties that have values for a given resource, and partitions the results into two categories: (1) results for properties that point
   * to ordinary Knora values ([[GroupedPropertiesByType.groupedOrdinaryValueProperties]]), (2) properties that point to link value objects (reifications of links to resources),
   * and (3) properties that point to other resources ([[GroupedPropertiesByType.groupedLinkProperties]]).
   * Then groups the results in each category first by property, then by property object, and finally by Knora object predicate, each level represented by a case class defined in [[GroupedProps]].
   *
   * @param resourceIri the IRI of the resource to be queried.
   * @return a [[GroupedPropertiesByType]] containing properties that point to ordinary value properties, link value properties, and link properties.
   */
  private def getGroupedProperties(resourceIri: IRI): Task[GroupedPropertiesByType] =
    for {
      sparqlQuery <- ZIO.attempt(
                       org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                         .getResourcePropertiesAndValues(
                           resourceIri = resourceIri
                         )
                         .toString()
                     )
      resPropsResponse <- triplestoreService.sparqlHttpSelect(sparqlQuery)

      // Partition the property result rows into rows with value properties and rows with link properties.
      (rowsWithLinks: Seq[VariableResultsRow], rowsWithValues: Seq[VariableResultsRow]) =
        resPropsResponse.results.bindings
          .partition(_.rowMap.get("isLinkProp").exists(_.toBoolean))

      // Partition the rows with values into rows with ordinary values and rows with link values (reifications).
      (rowsWithLinkValues: Seq[VariableResultsRow], rowsWithOrdinaryValues: Seq[VariableResultsRow]) =
        rowsWithValues
          .partition(_.rowMap.get("isLinkValueProp").exists(_.toBoolean))

      result = valueUtilV1.createGroupedPropsByType(
                 rowsWithOrdinaryValues = rowsWithOrdinaryValues,
                 rowsWithLinkValues = rowsWithLinkValues,
                 rowsWithLinks = rowsWithLinks
               )
    } yield result

  /**
   * Converts grouped property query results returned by the `getGroupedProperties` method to a [[Seq]] of [[PropertyV1]] objects, optionally
   * using ontology-based data if provided.
   *
   * @param groupedPropertiesByType The [[GroupedPropertiesByType]] returned by `getGroupedProperties` containing the resuls of the SPARQL query.
   * @param propertyInfoMap         a [[Map]] of entity IRIs to [[PropertyInfoV1]] objects. If this [[Map]] is not empty, it will be used to include
   *                                ontology-based information in the returned [[PropertyV1]] objects.
   * @param resourceEntityInfoMap   a [[Map]] of entity IRIs to [[ClassInfoV1]] objects. If this [[Map]] is not empty, it will be used to include
   *                                ontology-based information for linking properties in the returned [[PropertyV1]] objects.
   * @param propsAndCardinalities   a [[Map]] of property IRIs to their cardinalities in the class of the queried resource. If this [[Map]] is not
   *                                empty, it will be used to include cardinalities in the returned [[PropertyV1]] objects.
   * @param userProfile             the profile of the user making the request.
   * @return a [[Seq]] of [[PropertyV1]] objects.
   */
  private def queryResults2PropertyV1s(
    containingResourceIri: IRI,
    projectShortcode: String,
    groupedPropertiesByType: GroupedPropertiesByType,
    propertyInfoMap: Map[IRI, PropertyInfoV1],
    resourceEntityInfoMap: Map[IRI, ClassInfoV1],
    propsAndCardinalities: Map[IRI, KnoraCardinalityInfo],
    userProfile: UserADM
  ): Task[Seq[PropertyV1]] = {
    val userProfileV1 = userProfile.asUserProfileV1

    /**
     * Constructs a [[PropertyV1]].
     *
     * @param propertyIri         the IRI of the property.
     * @param propertyCardinality an optional cardinality that the queried resource's class assigns to the property.
     * @param propertyEntityInfo  an optional [[PropertyInfoV1]] describing the property.
     * @param valueObjects        a list of [[ValueObjectV1]] instances representing the `knora-base:Value` objects associated with the property in the queried resource.
     * @return a [[PropertyV1]].
     */
    def makePropertyV1(
      propertyIri: IRI,
      propertyCardinality: Option[KnoraCardinalityInfo],
      propertyEntityInfo: Option[PropertyInfoV1],
      valueObjects: Seq[ValueObjectV1]
    ): PropertyV1 =
      PropertyV1(
        pid = propertyIri,
        valuetype_id = propertyEntityInfo.flatMap { row =>
          if (row.isLinkProp) {
            // it is a linking property
            Some(OntologyConstants.KnoraBase.LinkValue)
          } else {
            row.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint)
          }
        },
        guiorder = propertyCardinality.flatMap(_.guiOrder),
        guielement = propertyEntityInfo.flatMap(
          _.getPredicateObject(SalsahGui.GuiElementProp).map(guiElementIri =>
            SalsahGuiConversions.iri2SalsahGuiElement(guiElementIri)
          )
        ),
        label = propertyEntityInfo.flatMap(
          _.getPredicateObject(
            predicateIri = OntologyConstants.Rdfs.Label,
            preferredLangs = Some(userProfile.lang, appConfig.fallbackLanguage)
          )
        ),
        occurrence = propertyCardinality.map(_.cardinality.toString),
        attributes = propertyEntityInfo match {
          case Some(entityInfo) =>
            if (entityInfo.isLinkProp) {
              (entityInfo.getPredicateStringObjectsWithoutLang(SalsahGui.GuiAttribute) +
                valueUtilV1.makeAttributeRestype(
                  entityInfo
                    .getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint)
                    .getOrElse(
                      throw InconsistentRepositoryDataException(
                        s"Property $propertyIri has no knora-base:objectClassConstraint"
                      )
                    )
                )).mkString(";")
            } else {
              entityInfo.getPredicateStringObjectsWithoutLang(SalsahGui.GuiAttribute).mkString(";")
            }
          case None => ""
        },
        value_rights = valueObjects.map(_.valuePermission),
        value_restype = valueObjects.map {
          _.valueV1 match {
            case link: LinkV1 => link.valueResourceClassLabel
            case _            => None
          }
        },
        value_iconsrcs = valueObjects.map {
          _.valueV1 match {
            case link: LinkV1 => link.valueResourceClassIcon
            case _            => None
          }
        },
        value_firstprops = valueObjects.map {
          _.valueV1 match {
            case link: LinkV1 => link.valueLabel
            case _            => None
          }
        },
        values = valueObjects.map(_.valueV1),
        value_ids = valueObjects.map(_.valueObjectIri),
        comments = valueObjects.map(_.comment)
      )

    // Make a PropertyV1 for each value property that has data.
    val valuePropertiesWithDataWithFuture: Iterable[Task[Option[PropertyV1]]] =
      groupedPropertiesByType.groupedOrdinaryValueProperties.groupedProperties.map {
        case (propertyIri: IRI, valueObject: ValueObjects) =>
          val valueObjectsV1WithFuture: Iterable[Task[ValueObjectV1]] = valueObject.valueObjects.map {
            case (valObjIri: IRI, valueProps: ValueProps) =>
              // Make sure the value object has an rdf:type.
              valueProps.literalData.getOrElse(
                OntologyConstants.Rdf.Type,
                throw InconsistentRepositoryDataException(s"$valObjIri has no rdf:type")
              )

              for {
                // Convert the SPARQL query results to a ValueV1.
                valueV1 <- valueUtilV1.makeValueV1(valueProps, projectShortcode, userProfile)

                valPermission = PermissionUtilADM.getUserPermissionWithValuePropsV1(
                                  valueIri = valObjIri,
                                  valueProps = valueProps,
                                  entityProject =
                                    None, // We don't need to specify this here, because it's in valueProps
                                  userProfile = userProfileV1
                                )

                predicates = valueProps.literalData

              } yield ValueObjectV1(
                valueObjectIri = valObjIri,
                valueV1 = valueV1,
                valuePermission = valPermission,
                comment = predicates.get(OntologyConstants.KnoraBase.ValueHasComment).map(_.literals.head),
                order = predicates.get(OntologyConstants.KnoraBase.ValueHasOrder) match {
                  // this should not be necessary as an order should always be given (also if there is only one value)
                  case Some(ValueLiterals(literals)) => literals.head.toInt
                  case _                             => 0 // order statement is missing, set it to zero
                }
              )
          }

          for {
            valueObjectsV1 <- ZIO.collectAll(valueObjectsV1WithFuture)

            valueObjectsV1Sorted =
              valueObjectsV1.toVector.sortBy(
                _.order
              ) // sort the values by their order given in the triplestore [[OntologyConstants.KnoraBase.ValueHasOrder]]

            // get all the values the user has at least viewing permissions on
            valueObjectListFiltered = valueObjectsV1Sorted.filter(_.valuePermission.nonEmpty)

            // Get the ontology information about the property.
            propertyEntityInfo = propertyInfoMap.get(propertyIri)

            // Make a PropertyV1 for the property.
            propertyV1 = makePropertyV1(
                           propertyIri = propertyIri,
                           propertyCardinality = propsAndCardinalities.get(propertyIri),
                           propertyEntityInfo = propertyEntityInfo,
                           valueObjects = valueObjectListFiltered
                         )
          } yield
          // If the property has a value that the user isn't allowed to see, and its cardinality
          // is ExactlyOne or ZeroOrOne, don't return any information about the property.
          propsAndCardinalities.get(propertyIri) match {
            case Some(cardinalityInfo)
                if (cardinalityInfo.cardinality == ExactlyOne || cardinalityInfo.cardinality == ZeroOrOne) && valueObjectsV1Sorted.nonEmpty && valueObjectListFiltered.isEmpty =>
              None
            case _ => Some(propertyV1)
          }
      }

    for {
      valuePropertiesWithDataWithOption <- ZIO.collectAll(
                                             valuePropertiesWithDataWithFuture
                                           )

      valuePropertiesWithData = valuePropertiesWithDataWithOption.toVector.flatten

      // Make a PropertyV1 for each link property with data. We have to treat links as a special case, because we
      // need information about the target resource.
      linkPropertiesWithDataWithFuture: Iterable[Task[Option[PropertyV1]]] =
        groupedPropertiesByType.groupedLinkProperties.groupedProperties.map {
          case (propertyIri: IRI, targetResource: ValueObjects) =>
            val valueObjectsV1WithFuture: Vector[Task[ValueObjectV1]] = targetResource.valueObjects.map {
              case (targetResourceIri: IRI, valueProps: ValueProps) =>
                val predicates = valueProps.literalData

                // Get the IRI of the resource class of the referenced resource.
                val referencedResType = predicates(OntologyConstants.Rdf.Type).literals.head

                // Get info about that resource class, if available.
                // Use resource entity infos to do so.
                val (maybeResourceClassLabel: Option[String], maybeResourceClassIcon: Option[String]) =
                  resourceEntityInfoMap.get(referencedResType) match {
                    case Some(referencedResTypeEntityInfo) =>
                      val labelOption: Option[String] = referencedResTypeEntityInfo.getPredicateObject(
                        predicateIri = OntologyConstants.Rdfs.Label,
                        preferredLangs = Some(userProfile.lang, appConfig.fallbackLanguage)
                      )
                      val resIconOption: Option[String] =
                        referencedResTypeEntityInfo.getPredicateObject(OntologyConstants.KnoraBase.ResourceIcon)

                      (labelOption, resIconOption)

                    case None => (None, None)
                  }

                val valueResourceClassOption = predicates(OntologyConstants.Rdf.Type).literals.headOption
                // build the correct path to the icon
                val maybeValueResourceClassIcon = valueResourceClassOption match {
                  case Some(resClass) if maybeResourceClassIcon.nonEmpty =>
                    Some(
                      valueUtilV1.makeResourceClassIconURL(resClass, maybeResourceClassIcon.get)
                    )
                  case _ => None
                }

                val valueV1 = LinkV1(
                  targetResourceIri = targetResourceIri,
                  valueLabel = predicates.get(OntologyConstants.Rdfs.Label).map(_.literals.head),
                  valueResourceClass = valueResourceClassOption,
                  valueResourceClassLabel = maybeResourceClassLabel,
                  valueResourceClassIcon = maybeValueResourceClassIcon
                )

                // A direct link between resources has a corresponding LinkValue reification. We use its IRI as the
                // value object IRI, since links don't have IRIs of their own.

                // Convert the link property IRI to a link value property IRI.
                val linkValuePropertyIri = stringFormatter.linkPropertyIriToLinkValuePropertyIri(propertyIri)

                // Get the details of the link value that's pointed to by that link value property, and that has the target resource as its rdf:object.
                val (linkValueIri, linkValueProps) =
                  groupedPropertiesByType.groupedLinkValueProperties.groupedProperties
                    .getOrElse(
                      linkValuePropertyIri,
                      throw InconsistentRepositoryDataException(
                        s"Resource $containingResourceIri has link property $propertyIri but does not have a corresponding link value property"
                      )
                    )
                    .valueObjects
                    .find { case (someLinkValueIri, someLinkValueProps) =>
                      someLinkValueProps.literalData
                        .getOrElse(
                          OntologyConstants.Rdf.Object,
                          throw InconsistentRepositoryDataException(s"Link value $someLinkValueIri has no rdf:object")
                        )
                        .literals
                        .head == targetResourceIri
                    }
                    .getOrElse(
                      throw InconsistentRepositoryDataException(
                        s"Link property $propertyIri of resource $containingResourceIri points to resource $targetResourceIri, but there is no corresponding link value with the target resource as its rdf:object"
                      )
                    )

                val linkValueOrder = linkValueProps.literalData.get(OntologyConstants.KnoraBase.ValueHasOrder) match {
                  // this should not be necessary as an order should always be given (also if there is only one value)
                  case Some(ValueLiterals(literals)) => literals.head.toInt
                  case _                             => 0 // order statement is missing, set it to zero
                }

                for {
                  apiValueV1ForLinkValue <- valueUtilV1.makeValueV1(linkValueProps, projectShortcode, userProfile)

                  _ = apiValueV1ForLinkValue match {
                        case linkValueV1: LinkValueV1 => linkValueV1
                        case _ =>
                          throw InconsistentRepositoryDataException(
                            s"Expected $linkValueIri to be a knora-base:LinkValue, but its type is ${apiValueV1ForLinkValue.valueTypeIri}"
                          )
                      }

                  // Check the permissions on the LinkValue.
                  linkValuePermission = PermissionUtilADM.getUserPermissionWithValuePropsV1(
                                          valueIri = linkValueIri,
                                          valueProps = linkValueProps,
                                          entityProject =
                                            None, // We don't need to specify this here, because it's in linkValueProps
                                          userProfile = userProfileV1
                                        )

                  // We only allow the user to see information about the link if they have at least view permission on both the link value
                  // and on the target resource.

                  targetResourcePermission = PermissionUtilADM.getUserPermissionWithValuePropsV1(
                                               valueIri = targetResourceIri,
                                               valueProps = valueProps,
                                               entityProject =
                                                 None, // We don't need to specify this here, because it's in valueProps
                                               userProfile = userProfileV1
                                             )

                  linkPermission = (targetResourcePermission, linkValuePermission) match {
                                     case (Some(targetResourcePermissionCode), Some(linkValuePermissionCode)) =>
                                       Some(scala.math.min(targetResourcePermissionCode, linkValuePermissionCode))
                                     case _ => None
                                   }

                } yield ValueObjectV1(
                  valueObjectIri = linkValueIri,
                  valueV1 = valueV1,
                  valuePermission = linkPermission,
                  order = linkValueOrder,
                  comment = linkValueProps.literalData
                    .get(OntologyConstants.KnoraBase.ValueHasComment)
                    .map(_.literals.head) // get comment from LinkValue
                )
            }.toVector

            for {
              valueObjectsV1 <- ZIO.collectAll(valueObjectsV1WithFuture)

              // get all the values the user has at least viewing permissions on
              valueObjectListFiltered = valueObjectsV1.filter(_.valuePermission.nonEmpty)

              // Get the ontology information about the property, if available.
              propertyEntityInfo = propertyInfoMap.get(propertyIri)

              // Make a PropertyV1 for the property.
              propertyV1 = makePropertyV1(
                             propertyIri = propertyIri,
                             propertyCardinality = propsAndCardinalities.get(propertyIri),
                             propertyEntityInfo = propertyEntityInfo,
                             valueObjects = valueObjectListFiltered
                           )

              // If the property has a value that the user isn't allowed to see, and its cardinality
              // is ExactlyOne or ZeroOrOne, don't return any information about the property.
            } yield propsAndCardinalities.get(propertyIri) match {
              case Some(cardinalityInfo)
                  if (cardinalityInfo.cardinality == ExactlyOne || cardinalityInfo.cardinality == ZeroOrOne) && valueObjectsV1.nonEmpty && valueObjectListFiltered.isEmpty =>
                None
              case _ => Some(propertyV1)
            }
        }

      linkPropertiesWithDataWithOption <- ZIO.collectAll(linkPropertiesWithDataWithFuture)

      linkPropertiesWithData: Vector[PropertyV1] = linkPropertiesWithDataWithOption.toVector.flatten

    } yield valuePropertiesWithData ++ linkPropertiesWithData
  }

  private def convertPropertyV1toPropertyGetV1(propertyV1: PropertyV1): PropertyGetV1 = {

    val valueObjects: Seq[PropertyGetValueV1] =
      propertyV1.value_ids.lazyZip(propertyV1.values).lazyZip(propertyV1.comments).map {
        case (id: IRI, value: ApiValueV1, comment: Option[String]) =>
          PropertyGetValueV1(
            id = id,
            value = value,
            textval = value.toString,
            comment = comment
          ) // TODO: person_id and lastmod are not handled yet. Probably these are never used by the GUI.
      }

    // TODO: try to unify this with ValueUtilV1's convertCreateValueResponseV1ToResourceCreateValueResponseV1
    PropertyGetV1(
      pid = propertyV1.pid,
      label = propertyV1.label,
      valuetype_id = propertyV1.valuetype_id,
      valuetype = propertyV1.valuetype_id match {
        // derive valuetype from valuetype_id
        case Some(OntologyConstants.KnoraBase.IntValue)     => Some("ival")
        case Some(OntologyConstants.KnoraBase.DecimalValue) => Some("dval")
        case Some(OntologyConstants.KnoraBase.DateValue)    => Some("dateval")
        case Some(_: IRI)                                   => Some("textval")
        case None                                           => None
      },
      guielement = propertyV1.guielement,
      attributes = propertyV1.attributes,
      is_annotation = propertyV1.is_annotation,
      values = valueObjects
    )
  }

  /**
   * Given a [[FileValueV1]], checks whether it represents a JPEG 2000 image. If so, returns a sequence of [[StillImageFileValueV1]]
   * objects representing different possible resolutions of the same image. Otherwise, returns a sequence containing the
   * same [[FileValueV1]] unchanged.
   *
   * @param fileValueV1 the file's metadata.
   * @return a sequence of [[FileValueV1]] objects.
   */
  private def createMultipleImageResolutions(fileValueV1: FileValueV1): Seq[FileValueV1] = {
    def scaleImageSize(dimension: Int, power: Int): Int =
      (dimension.toDouble / math.pow(2, power)).round.toInt

    fileValueV1 match {
      case stillImageFileValueV1: StillImageFileValueV1 =>
        // For JPEG 2000 images, create six LocationV1 objects representing different available resolutions.
        if (stillImageFileValueV1.internalMimeType == "image/jp2") {
          // Create descriptions of 6 different possible resolutions of the image.
          (5 to 0 by -1).map {
            // order them from smallest to largest
            powerOf2 =>
              stillImageFileValueV1.copy(
                dimX = scaleImageSize(stillImageFileValueV1.dimX, powerOf2),
                dimY = scaleImageSize(stillImageFileValueV1.dimY, powerOf2)
              )
          }
        } else {
          // For other MIME types, just leave the image as is.
          Vector(stillImageFileValueV1)
        }

      case otherFileValueV1 => Vector(otherFileValueV1)
    }
  }

  /**
   * Converts a full-size still image file value to a preview image.
   *
   * @param fullSizeImageFileValue the full-size image.
   * @return a corresponding preview image.
   */
  private def fullSizeImageFileValueToPreview(fullSizeImageFileValue: StillImageFileValueV1): StillImageFileValueV1 = {
    val proportion    = fullSizeImageFileValue.dimY.toDouble / 128.0
    val previewHeight = 128
    val previewWidth  = (fullSizeImageFileValue.dimX.toDouble / proportion).round.toInt

    fullSizeImageFileValue.copy(
      dimX = previewWidth,
      dimY = previewHeight
    )
  }
}
object ResourcesResponderV1Live {
  val layer: URLayer[
    ValueUtilV1
      with ResourceUtilV2
      with StandoffTagUtilV2
      with StringFormatter
      with TriplestoreService
      with MessageRelay
      with AppConfig,
    ResourcesResponderV1Live
  ] = ZLayer.fromZIO {
    for {
      config  <- ZIO.service[AppConfig]
      mr      <- ZIO.service[MessageRelay]
      ts      <- ZIO.service[TriplestoreService]
      sf      <- ZIO.service[StringFormatter]
      su      <- ZIO.service[StandoffTagUtilV2]
      ru      <- ZIO.service[ResourceUtilV2]
      vu      <- ZIO.service[ValueUtilV1]
      handler <- mr.subscribe(ResourcesResponderV1Live(config, mr, ts, su, vu, ru, sf))
    } yield handler
  }
}
