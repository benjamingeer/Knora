/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.v2.responder.searchmessages

import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.rdf.{JsonLDDocument, JsonLDInt, JsonLDObject, JsonLDString}
import org.knora.webapi.messages.util.search.ConstructQuery
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.{OntologyConstants, SmartIri}
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.{ApiV2Schema, IRI, SchemaOption}

/**
 * An abstract trait for messages that can be sent to `SearchResponderV2`.
 */
sealed trait SearchResponderRequestV2 extends KnoraRequestV2 {

  def requestingUser: UserADM
}

/**
 * Requests the amount of results (resources count) of a given fulltext search. A successful response will be a [[ResourceCountV2]].
 *
 * @param searchValue          the values to search for.
 * @param limitToProject       limit search to given project.
 * @param limitToResourceClass limit search to given resource class.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 */
case class FullTextSearchCountRequestV2(
  searchValue: String,
  limitToProject: Option[IRI],
  limitToResourceClass: Option[SmartIri],
  limitToStandoffClass: Option[SmartIri],
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM
) extends SearchResponderRequestV2

/**
 * Requests a fulltext search. A successful response will be a [[org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2]].
 *
 * @param searchValue          the values to search for.
 * @param offset               the offset to be used for paging.
 * @param limitToProject       limit search to given project.
 * @param limitToResourceClass limit search to given resource class.
 * @param returnFiles          if true, return any file value value attached to each matching resource.
 * @param targetSchema         the target API schema.
 * @param schemaOptions        the schema options submitted with the request.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 */
case class FulltextSearchRequestV2(
  searchValue: String,
  offset: Int,
  limitToProject: Option[IRI],
  limitToResourceClass: Option[SmartIri],
  limitToStandoffClass: Option[SmartIri],
  returnFiles: Boolean,
  targetSchema: ApiV2Schema,
  schemaOptions: Set[SchemaOption],
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM
) extends SearchResponderRequestV2

/**
 * Requests the amount of results (resources count) of a given Gravsearch query. A successful response will be a [[ResourceCountV2]].
 *
 * @param constructQuery       a Sparql construct query provided by the client.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 */
case class GravsearchCountRequestV2(
  constructQuery: ConstructQuery,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM
) extends SearchResponderRequestV2

/**
 * Performs a Gravsearch query. A successful response will be a [[org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2]].
 *
 * @param constructQuery       a Sparql construct query provided by the client.
 * @param targetSchema         the target API schema.
 * @param schemaOptions        the schema options submitted with the request.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 */
case class GravsearchRequestV2(
  constructQuery: ConstructQuery,
  targetSchema: ApiV2Schema,
  schemaOptions: Set[SchemaOption] = Set.empty[SchemaOption],
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM
) extends SearchResponderRequestV2

/**
 * Requests a search of resources by their label. A successful response will be a [[ResourceCountV2]].
 *
 * @param searchValue          the values to search for.
 * @param limitToProject       limit search to given project.
 * @param limitToResourceClass limit search to given resource class.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 */
case class SearchResourceByLabelCountRequestV2(
  searchValue: String,
  limitToProject: Option[IRI],
  limitToResourceClass: Option[SmartIri],
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM
) extends SearchResponderRequestV2

/**
 * Requests a search of resources by their label. A successful response will be a [[org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2]].
 *
 * @param searchValue          the values to search for.
 * @param offset               the offset to be used for paging.
 * @param limitToProject       limit search to given project.
 * @param limitToResourceClass limit search to given resource class.
 * @param targetSchema         the schema of the response.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 */
case class SearchResourceByLabelRequestV2(
  searchValue: String,
  offset: Int,
  limitToProject: Option[IRI],
  limitToResourceClass: Option[SmartIri],
  targetSchema: ApiV2Schema,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM
) extends SearchResponderRequestV2

/**
 * Represents the number of resources found by a search query.
 */
case class ResourceCountV2(numberOfResources: Int) extends KnoraJsonLDResponseV2 {
  override def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    settings: KnoraSettingsImpl,
    schemaOptions: Set[SchemaOption]
  ): JsonLDDocument =
    JsonLDDocument(
      body = JsonLDObject(
        Map(
          OntologyConstants.SchemaOrg.NumberOfItems -> JsonLDInt(numberOfResources)
        )
      ),
      context = JsonLDObject(
        Map(
          "schema" -> JsonLDString(OntologyConstants.SchemaOrg.SchemaOrgPrefixExpansion)
        )
      )
    )
}

/**
 * Requests resources of the specified class from the specified project.
 *
 * @param projectIri           the IRI of the project.
 * @param resourceClass        the IRI of the resource class, in the complex schema.
 * @param orderByProperty      the IRI of the property that the resources are to be ordered by, in the complex schema.
 * @param page                 the page number of the results page to be returned.
 * @param targetSchema         the schema of the response.
 * @param schemaOptions        the schema options submitted with the request.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 */
case class SearchResourcesByProjectAndClassRequestV2(
  projectIri: SmartIri,
  resourceClass: SmartIri,
  orderByProperty: Option[SmartIri],
  page: Int,
  targetSchema: ApiV2Schema,
  schemaOptions: Set[SchemaOption],
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM
) extends SearchResponderRequestV2
