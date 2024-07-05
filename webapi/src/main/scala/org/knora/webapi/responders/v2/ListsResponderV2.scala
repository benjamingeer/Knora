/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import zio.*

import org.knora.webapi.IRI
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.admin.responder.listsmessages.ChildNodeInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListGetResponseADM
import org.knora.webapi.messages.v2.responder.listsmessages.*
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.domain.model.User

final case class ListsResponderV2(private val appConfig: AppConfig, private val listsResponder: ListsResponder) {

  /**
   * Gets a list from the triplestore.
   *
   * @param listIri        the Iri of the list's root node.
   * @param requestingUser the user making the request.
   * @return a [[ListGetResponseV2]].
   */
  def getList(listIri: IRI, requestingUser: User): Task[ListGetResponseV2] =
    listsResponder
      .listGetRequestADM(listIri)
      .mapAttempt(_.asInstanceOf[ListGetResponseADM])
      .map(resp => ListGetResponseV2(resp.list, requestingUser.lang, appConfig.fallbackLanguage))

  /**
   * Gets a single list node from the triplestore.
   *
   * @param nodeIri              the Iri of the list node.
   *
   * @param requestingUser       the user making the request.
   * @return a  [[NodeGetResponseV2]].
   */
  def getNode(nodeIri: IRI, requestingUser: User): Task[NodeGetResponseV2] =
    listsResponder
      .listNodeInfoGetRequestADM(nodeIri)
      .flatMap {
        case ChildNodeInfoGetResponseADM(node) => ZIO.succeed(node)
        case _                                 => ZIO.die(new IllegalStateException(s"No child node found $nodeIri"))
      }
      .map(NodeGetResponseV2(_, requestingUser.lang, appConfig.fallbackLanguage))
}

object ListsResponderV2 {
  val layer = ZLayer.derive[ListsResponderV2]
}
