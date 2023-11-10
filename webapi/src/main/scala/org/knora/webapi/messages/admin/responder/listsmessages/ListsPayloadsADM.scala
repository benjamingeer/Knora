/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.listsmessages

import dsp.valueobjects.Iri.*
import dsp.valueobjects.List.*

/**
 * List root node and child node creation payloads
 */
sealed trait ListNodeCreatePayloadADM
//    TODO-mpro:
//     1. lack of consistency between parentNodeIri and hasRootNode in change payload - should be renamed to parentNodeIri
//     2. Rethink other field names if they are descriptive enough, e.g. id should be renamed to customIri or something similar
object ListNodeCreatePayloadADM {
  final case class ListRootNodeCreatePayloadADM(
    id: Option[ListIri] = None,
    projectIri: ProjectIri,
    name: Option[ListName] = None,
    labels: Labels,
    comments: Comments
  ) extends ListNodeCreatePayloadADM
  final case class ListChildNodeCreatePayloadADM(
    id: Option[ListIri] = None,
    parentNodeIri: ListIri,
    projectIri: ProjectIri,
    name: Option[ListName] = None,
    position: Option[Position] = None,
    labels: Labels,
    comments: Option[Comments] = None
  ) extends ListNodeCreatePayloadADM
}

/**
 * List node update payload
 */
final case class ListNodeChangePayloadADM(
//  TODO-mpro: listIri can be probably removed here or maybe from the route??
  listIri: ListIri,
  projectIri: ProjectIri,
  hasRootNode: Option[ListIri] = None,
  position: Option[Position] = None,
  name: Option[ListName] = None,
  labels: Option[Labels] = None,
  comments: Option[Comments] = None
)

/**
 * Node Name update payload
 */
final case class NodeNameChangePayloadADM(
  name: ListName
)

/**
 * Node Labels update payload
 */
final case class NodeLabelsChangePayloadADM(
  labels: Labels
)

/**
 * Node Comments update payload
 */
final case class NodeCommentsChangePayloadADM(
  comments: Comments
)
