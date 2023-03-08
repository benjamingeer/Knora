/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v1.responder.listmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

import org.knora.webapi.IRI
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.ResponderRequest.KnoraRequestV1
import org.knora.webapi.messages.v1.responder.KnoraResponseV1
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
 * An abstract trait for messages that can be sent to `HierarchicalListsResponderV1`.
 */
sealed trait ListsResponderRequestV1 extends KnoraRequestV1 with RelayedMessage

/**
 * Requests a list. A successful response will be a [[HListGetResponseV1]].
 *
 * @param iri         the IRI of the list.
 * @param userProfile the profile of the user making the request.
 */
case class HListGetRequestV1(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV1

/**
 * Requests a selection (flat list). A successful response will be a [[SelectionGetResponseV1]].
 *
 * @param iri         the IRI of the list.
 * @param userProfile the profile of the user making the request.
 */
case class SelectionGetRequestV1(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV1

/**
 * Requests the path from the root node of a list to a particular node. A successful response will be
 * a [[NodePathGetResponseV1]].
 *
 * @param iri         the IRI of the node.
 * @param userProfile the profile of the user making the request.
 */
case class NodePathGetRequestV1(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV1

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Responses

/**
 * An abstract class extended by `HListGetResponseV1` and `SelectionGetResponseV1`.
 */
sealed abstract class ListGetResponseV1 extends KnoraResponseV1

/**
 * Provides a hierarchical list representing a "hlist" in the old SALSAH.
 *
 * @param hlist the list requested.
 */
case class HListGetResponseV1(hlist: Seq[ListNodeV1]) extends ListGetResponseV1 with ListV1JsonProtocol {
  def toJsValue: JsValue = hlistGetResponseV1Format.write(this)
}

/**
 * Provides a hierarchical list representing a "selection" in the old SALSAH.
 *
 * @param selection the list requested.
 */
case class SelectionGetResponseV1(selection: Seq[ListNodeV1]) extends ListGetResponseV1 with ListV1JsonProtocol {
  def toJsValue: JsValue = selectionGetResponseV1Format.write(this)
}

/**
 * Responds to a [[NodePathGetRequestV1]] by providing the path to a particular hierarchical list node.
 *
 * @param nodelist a list of the nodes composing the path from the list's root node up to and including the specified node.
 */
case class NodePathGetResponseV1(nodelist: Seq[NodePathElementV1]) extends ListGetResponseV1 with ListV1JsonProtocol {
  def toJsValue: JsValue = nodePathGetResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
 * Represents a hierarchical list node in Knora API v1 format.
 *
 * @param id       the IRI of the list node.
 * @param name     the name of the list node.
 * @param label    the label of the list node.
 * @param children the list node's child nodes.
 * @param level    the depth of the node in the tree.
 * @param position the position of the node among its siblings.
 */
case class ListNodeV1(
  id: IRI,
  name: Option[String],
  label: Option[String],
  children: Seq[ListNodeV1],
  level: Int,
  position: Int
)

/**
 * Represents a node on a hierarchical list path.
 *
 * @param id    the IRI of the list node.
 * @param name  the name of the list node.
 * @param label the label of the list node.
 */
case class NodePathElementV1(id: IRI, name: Option[String], label: Option[String])

/**
 * An enumeration whose values correspond to the types of hierarchical list objects that [[org.knora.webapi.responders.v1.ListsResponderV1]] actor can
 * produce: "hlists" | "selections".
 */
object PathType extends Enumeration {
  val HList, Selection = Value
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
 * A spray-json protocol for generating Knora API v1 JSON providing data about lists.
 */
trait ListV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

  implicit object HierarchicalListV1JsonFormat extends JsonFormat[ListNodeV1] {

    /**
     * Recursively converts a [[ListNodeV1]] to a [[JsValue]].
     *
     * @param tree a [[ListNodeV1]].
     * @return a [[JsValue]].
     */
    def write(tree: ListNodeV1): JsValue = {
      // Does the node have children?
      val childrenOption = if (tree.children.nonEmpty) {
        // Yes: recursively convert them to JSON.
        Some("children" -> JsArray(tree.children.map(write).toVector))
      } else {
        // No: don't include a "children" array in the output.
        None
      }

      val fields = Map(
        "id"    -> tree.id.toJson,
        "name"  -> tree.name.toJson,
        "label" -> tree.label.toJson,
        "level" -> tree.level.toJson
      ) ++ childrenOption

      JsObject(fields)
    }

    /**
     * Not implemented.
     */
    def read(value: JsValue): ListNodeV1 = ???
  }

  implicit val hlistGetResponseV1Format: RootJsonFormat[HListGetResponseV1] = jsonFormat(HListGetResponseV1, "hlist")
  implicit val selectionGetResponseV1Format: RootJsonFormat[SelectionGetResponseV1] =
    jsonFormat(SelectionGetResponseV1, "selection")
  implicit val nodePathElementV1Format: JsonFormat[NodePathElementV1] =
    jsonFormat(NodePathElementV1, "id", "name", "label")
  implicit val nodePathGetResponseV1Format: RootJsonFormat[NodePathGetResponseV1] =
    jsonFormat(NodePathGetResponseV1, "nodelist")
}
