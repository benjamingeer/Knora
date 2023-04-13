/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v1.responder.searchmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import zio._

import dsp.errors.BadRequestException
import org.knora.webapi._
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.ResponderRequest.KnoraRequestV1
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.KnoraResponseV1

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
 * An abstract trait for messages that can be sent to `SearchResponderV1`.
 */
sealed trait SearchResponderRequestV1 extends KnoraRequestV1 with RelayedMessage {
  def searchValue: Any

  // will be implemented as String or a List of Strings
  def filterByRestype: Option[IRI]

  def filterByProject: Option[IRI]

  def startAt: Int

  def showNRows: Int

  def userProfile: UserADM
}

/**
 * Requests a fulltext search. A successful response will be a [[SearchGetResponseV1]].
 *
 * @param userProfile the profile of the user making the request.
 */
case class FulltextSearchGetRequestV1(
  searchValue: String,
  filterByRestype: Option[IRI] = None,
  filterByProject: Option[IRI] = None,
  startAt: Int,
  showNRows: Int,
  userProfile: UserADM
) extends SearchResponderRequestV1

/**
 * Requests an extended search. A successful response will be a [[SearchGetResponseV1]].
 *
 * @param userProfile the profile of the user making the request.
 */
case class ExtendedSearchGetRequestV1(
  filterByRestype: Option[IRI] = None,
  filterByProject: Option[IRI] = None,
  filterByOwner: Option[IRI] = None,
  propertyIri: Seq[IRI] = Nil, // parallel structure
  propertyValueType: Seq[IRI] = Nil,
  compareProps: Seq[SearchComparisonOperatorV1.Value] = Nil, // parallel structure
  searchValue: Seq[String] = Nil,                            // parallel structure
  startAt: Int,
  showNRows: Int,
  userProfile: UserADM
) extends SearchResponderRequestV1

/**
 * Represents a response to a user search query (both fulltext and extended search)
 *
 * @param subjects  list of [[SearchResultRowV1]] each representing on resource.
 * @param nhits     total number of hits.
 * @param paging    information for paging.
 * @param thumb_max maximal dimensions of preview representations.
 */
case class SearchGetResponseV1(
  subjects: Seq[SearchResultRowV1] = Nil,
  nhits: String,
  paging: Seq[SearchResultPage] = Nil,
  thumb_max: SearchPreviewDimensionsV1
) extends KnoraResponseV1 {
  def toJsValue = SearchV1JsonProtocol.searchResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
 * Enumeration representing the possible operators for comparing properties to a given value
 */
object SearchComparisonOperatorV1 extends Enumeration {
  val EXISTS        = Value(0, "EXISTS")
  val EQ            = Value(1, "EQ")
  val NOT_EQ        = Value(2, "!EQ")
  val GT            = Value(3, "GT")
  val GT_EQ         = Value(4, "GT_EQ")
  val LT            = Value(5, "LT")
  val LT_EQ         = Value(6, "LT_EQ")
  val MATCH         = Value(7, "MATCH")
  val MATCH_BOOLEAN = Value(8, "MATCH_BOOLEAN")
  val LIKE          = Value(9, "LIKE")
  val NOT_LIKE      = Value(10, "!LIKE")

  val valueMap: Map[String, SearchComparisonOperatorV1.Value] = values.map(v => (v.toString, v)).toMap

  def lookup(name: String): Task[SearchComparisonOperatorV1.Value] =
    ZIO.fromOption(valueMap.get(name)).orElseFail(BadRequestException(s"Invalid search comparison operator: $name"))
}

/**
 * The maximum X and Y dimensions of the preview representations in a list of search results.
 *
 * @param nx max width.
 * @param ny max height.
 */
case class SearchPreviewDimensionsV1(nx: Int, ny: Int)

/**
 * Represents one row (resource) in [[SearchGetResponseV1]]
 *
 * @param obj_id       IRI of the retrieved resource.
 * @param preview_path path to a preview representation.
 * @param iconsrc      icon representing the resource type.
 * @param icontitle    description of the resource type.
 * @param iconlabel    description of the resource type.
 * @param valuetype_id value type of the first property.
 * @param valuelabel   label of the first property.
 * @param value        (text) value of the first property.
 */
case class SearchResultRowV1(
  obj_id: IRI,
  preview_path: Option[String],
  iconsrc: Option[String],
  icontitle: Option[String],
  iconlabel: Option[String],
  valuetype_id: Seq[IRI],
  valuelabel: Seq[String],
  value: Seq[String] = Nil,
  preview_nx: Int,
  preview_ny: Int,
  rights: Option[Int] = None
)

/**
 * An element in a list of search result pages.
 *
 * @param current    true if this element represents the current page.
 * @param start_at   the index of the first search result on the page.
 * @param show_nrows the number of results on the page.
 */
case class SearchResultPage(current: Boolean, start_at: Int, show_nrows: Int)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
 * A spray-json protocol for generating Knora API v1 JSON providing data about representations of a resource.
 */
object SearchV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

  implicit val searchResultPageV1Format: JsonFormat[SearchResultPage] = jsonFormat3(SearchResultPage)
  implicit val searchPreviewDimensionsV1Format: JsonFormat[SearchPreviewDimensionsV1] = jsonFormat2(
    SearchPreviewDimensionsV1
  )
  implicit val searchResultRowV1Format: JsonFormat[SearchResultRowV1]      = jsonFormat11(SearchResultRowV1)
  implicit val searchResponseV1Format: RootJsonFormat[SearchGetResponseV1] = jsonFormat4(SearchGetResponseV1)
}
