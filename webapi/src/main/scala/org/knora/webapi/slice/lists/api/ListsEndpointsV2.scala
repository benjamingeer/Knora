/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.lists.api

import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.tapir.*
import zio.*
import zio.ZLayer

import dsp.valueobjects.UuidUtil
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages
import org.knora.webapi.messages.admin.responder.listsmessages.ListADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListChildNodeADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListRootNodeInfoADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.listsmessages.ListGetResponseV2
import org.knora.webapi.messages.v2.responder.listsmessages.NodeGetResponseV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.admin.api.Codecs.TapirCodec
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.common.api.ApiV2
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions

final case class ListsEndpointsV2(private val base: BaseEndpoints) {
  val listIri =
    path[ListIri](TapirCodec.listIri)
      .name("listIri")
      .description("The iri to a list.")
      .example(ListIri.unsafeFrom("http://rdfh.ch/lists/0001/" + UuidUtil.makeRandomBase64EncodedUuid))

  val getV2Lists = base.withUserEndpoint.get
    .in("v2" / "lists" / listIri)
    .in(ApiV2.Inputs.formatOptions)
    .out(header[MediaType](HeaderNames.ContentType))
    .out(stringBody.example(Examples.listGetResponseV2.format(FormatOptions.default, Examples.appConfig)))
    .description("Returns a list (a graph with all list nodes).")

  val getV2Node = base.withUserEndpoint.get
    .in("v2" / "node" / listIri)
    .in(ApiV2.Inputs.formatOptions)
    .out(header[MediaType](HeaderNames.ContentType))
    .out(stringBody.example(Examples.nodeGetResponseV2.format(FormatOptions.default, Examples.appConfig)))
    .description("Returns a list node.")

  val endpoints: Seq[AnyEndpoint] = Seq(
    getV2Lists,
    getV2Node,
  ).map(_.endpoint.tag("V2 Lists"))
}

object ListsEndpointsV2 {
  val layer = ZLayer.derive[ListsEndpointsV2]
}

private object Examples {

  val appConfig: AppConfig = UnsafeZioRun.runOrThrow(for {
    config <- AppConfig.parseConfig
    _       = StringFormatter.init(config)
  } yield config)(Runtime.default)

  val listGetResponseV2: ListGetResponseV2 = ListGetResponseV2(
    list = ListADM(
      listinfo = ListRootNodeInfoADM(
        id = "http://rdfh.ch/lists/0001/treeList",
        projectIri = "http://rdfh.ch/projects/0001",
        name = Some("Tree list root"),
        labels = StringLiteralSequenceV2(
          Vector(
            StringLiteralV2.from(value = "Tree list root", language = Some("en")),
            StringLiteralV2.from(value = "Listenwurzel", language = Some("de")),
          ),
        ),
        comments =
          StringLiteralSequenceV2(Vector(StringLiteralV2.from(value = "Anything Tree List", language = Some("en")))),
      ).sorted,
      children = Seq(
        ListChildNodeADM(
          id = "http://rdfh.ch/lists/0001/treeList01",
          name = Some("Tree list node 01"),
          labels =
            StringLiteralSequenceV2(Vector(StringLiteralV2.from(value = "Tree list node 01", language = Some("en")))),
          comments = StringLiteralSequenceV2.empty,
          children = Seq.empty[ListChildNodeADM],
          position = 0,
          hasRootNode = "http://rdfh.ch/lists/0001/treeList",
        ),
        ListChildNodeADM(
          id = "http://rdfh.ch/lists/0001/treeList02",
          name = Some("Tree list node 02"),
          labels = StringLiteralSequenceV2(
            Vector(
              StringLiteralV2.from(value = "Baumlistenknoten 02", language = Some("de")),
              StringLiteralV2.from(value = "Tree list node 02", language = Some("en")),
            ),
          ),
          comments = StringLiteralSequenceV2.empty,
          children = Seq.empty[ListChildNodeADM],
          position = 1,
          hasRootNode = "http://rdfh.ch/lists/0001/treeList",
        ),
        ListChildNodeADM(
          id = "http://rdfh.ch/lists/0001/treeList03",
          name = Some("Tree list node 03"),
          labels =
            StringLiteralSequenceV2(Vector(StringLiteralV2.from(value = "Tree list node 03", language = Some("en")))),
          comments = StringLiteralSequenceV2.empty,
          children = Seq(
            ListChildNodeADM(
              id = "http://rdfh.ch/lists/0001/treeList10",
              name = Some("Tree list node 10"),
              labels = StringLiteralSequenceV2(
                Vector(StringLiteralV2.from(value = "Tree list node 10", language = Some("en"))),
              ),
              comments = StringLiteralSequenceV2.empty,
              children = Seq.empty[ListChildNodeADM],
              position = 0,
              hasRootNode = "http://rdfh.ch/lists/0001/treeList",
            ),
            ListChildNodeADM(
              id = "http://rdfh.ch/lists/0001/treeList11",
              name = Some("Tree list node 11"),
              labels = StringLiteralSequenceV2(
                Vector(StringLiteralV2.from(value = "Tree list node 11", language = Some("en"))),
              ),
              comments = StringLiteralSequenceV2.empty,
              children = Seq.empty[ListChildNodeADM],
              position = 1,
              hasRootNode = "http://rdfh.ch/lists/0001/treeList",
            ),
          ),
          position = 2,
          hasRootNode = "http://rdfh.ch/lists/0001/treeList",
        ),
      ),
    ),
    "de",
    "en",
  )

  val nodeGetResponseV2: NodeGetResponseV2 = NodeGetResponseV2(
    node = listsmessages.ListChildNodeInfoADM(
      name = Some("Tree list node 11"),
      id = "http://rdfh.ch/lists/0001/treeList11",
      labels = StringLiteralSequenceV2(
        stringLiterals = Vector(
          StringLiteralV2.from(
            value = "Tree list node 11",
            language = Some("en"),
          ),
        ),
      ),
      position = 1,
      hasRootNode = "http://rdfh.ch/lists/0001/treeList",
      comments = StringLiteralSequenceV2.empty,
    ),
    userLang = "de",
    fallbackLang = "en",
  )
}
