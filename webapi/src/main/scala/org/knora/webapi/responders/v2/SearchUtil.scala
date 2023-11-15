/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.knora.webapi.IRI
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

object SearchUtil {
  def constructSearchByLabelWithResourceClass(
    searchTerm: String,
    limitToResourceClass: IRI,
    limit: Int = 0,
    offset: Int = 0
  ) =
    Construct(
      s"""|PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |CONSTRUCT {
          |    ?resource rdfs:label ?label ;
          |        a knora-base:Resource ;
          |        knora-base:isMainResource true ;
          |        knora-base:isDeleted false ;
          |        a ?resourceType ;
          |        knora-base:attachedToUser ?resourceCreator ;
          |        knora-base:hasPermissions ?resourcePermissions ;
          |        knora-base:attachedToProject ?resourceProject  ;
          |        knora-base:creationDate ?creationDate ;
          |        knora-base:lastModificationDate ?lastModificationDate ;
          |        knora-base:hasValue ?valueObject ;
          |        ?resourceValueProperty ?valueObject .
          |    ?valueObject ?valueObjectProperty ?valueObjectValue .
          |} WHERE {
          |    {
          |        SELECT DISTINCT ?resource ?label
          |        WHERE {
          |            ?resource <http://jena.apache.org/text#query> (rdfs:label "$searchTerm") ;
          |                a ?resourceClass ;
          |                rdfs:label ?label .
          |            ?resourceClass rdfs:subClassOf* <$limitToResourceClass> .
          |            FILTER NOT EXISTS { ?resource knora-base:isDeleted true . }
          |        }
          |        ORDER BY ?resource
          |        LIMIT $limit
          |        OFFSET $offset
          |    }
          |
          |    ?resource a ?resourceType ;
          |        knora-base:attachedToUser ?resourceCreator ;
          |        knora-base:hasPermissions ?resourcePermissions ;
          |        knora-base:attachedToProject ?resourceProject ;
          |        knora-base:creationDate ?creationDate ;
          |        rdfs:label ?label .
          |    OPTIONAL { ?resource knora-base:lastModificationDate ?lastModificationDate . }
          |    OPTIONAL {
          |        ?resource ?resourceValueProperty ?valueObject .
          |        ?resourceValueProperty rdfs:subPropertyOf* knora-base:hasValue .
          |        ?valueObject a ?valueObjectType ;
          |            ?valueObjectProperty ?valueObjectValue .
          |        ?valueObjectType rdfs:subClassOf* knora-base:Value .
          |        FILTER(?valueObjectType != knora-base:LinkValue)
          |        FILTER NOT EXISTS { ?valueObject knora-base:isDeleted true . }
          |        FILTER NOT EXISTS { ?valueObjectValue a knora-base:StandoffTag . }
          |    }
          |}
          |""".stripMargin
    )

}
