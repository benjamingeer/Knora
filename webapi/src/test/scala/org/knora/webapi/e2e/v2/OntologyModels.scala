/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of the DaSCH Service Platform.
 *
 *  The DaSCH Service Platform  is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU Affero General Public
 *  License as published by the Free Software Foundation, either version 3
 *  of the License, or (at your option) any later version.
 *
 *  The DaSCH Service Platform is distributed in the hope that it will be
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with the DaSCH Service Platform.  If not, see
 *  <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.e2e.v2

import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM

import java.time.Instant
import scala.annotation.tailrec

final case class LangString(language: String, value: String)

sealed abstract case class CreateClassRequest private (value: String)
object CreateClassRequest {
  def make(
    ontologyName: String,
    lastModificationDate: Instant,
    className: String,
    label: LangString,
    comment: LangString
  ): CreateClassRequest = {
    val LocalHost_Ontology = "http://0.0.0.0:3333/ontology"
    val ontologyId         = LocalHost_Ontology + s"/0001/$ontologyName/v2"

    val value = s"""{
                   |  "@id" : "$ontologyId",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : {
                   |    "@type" : "xsd:dateTimeStamp",
                   |    "@value" : "$lastModificationDate"
                   |  },
                   |  "@graph" : [ {
                   |    "@id" : "$ontologyName:$className",
                   |    "@type" : "owl:Class",
                   |    "rdfs:label" : {
                   |      "@language" : "${label.language}",
                   |      "@value" : "${label.value}"
                   |    },
                   |    "rdfs:comment" : {
                   |      "@language" : "${comment.language}",
                   |      "@value" : "${comment.value}"
                   |    },
                   |    "rdfs:subClassOf" : [
                   |      {
                   |        "@id": "knora-api:Resource"
                   |      }
                   |    ]
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "$ontologyName" : "$ontologyId#"
                   |  }
                   |}""".stripMargin
    new CreateClassRequest(value) {}
  }
}

sealed trait PropertyValueType {
  val value: String
}
object PropertyValueType {
  case object TextValue extends PropertyValueType {
    val value = "knora-api:TextValue"
  }
  case object IntValue extends PropertyValueType {
    val value = "knora-api:IntValue"
  }
}

sealed abstract case class CreatePropertyRequest private (value: String)
object CreatePropertyRequest {
  def make(
    ontologyName: String,
    lastModificationDate: Instant,
    propertyName: String,
    subjectClassName: String,
    propertyType: PropertyValueType,
    label: LangString,
    comment: LangString
  ): CreatePropertyRequest = {
    val LocalHost_Ontology = "http://0.0.0.0:3333/ontology"
    val ontologyId         = LocalHost_Ontology + s"/0001/$ontologyName/v2"
    val value              = s"""{
                   |  "@id" : "$ontologyId",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : {
                   |    "@type" : "xsd:dateTimeStamp",
                   |    "@value" : "$lastModificationDate"
                   |  },
                   |  "@graph" : [ {
                   |      "@id" : "$ontologyName:$propertyName",
                   |      "@type" : "owl:ObjectProperty",
                   |      "knora-api:subjectType" : {
                   |        "@id" : "$ontologyName:$subjectClassName"
                   |      },
                   |      "knora-api:objectType" : {
                   |        "@id" : "${propertyType.value}"
                   |      },
                   |      "rdfs:comment" : {
                   |        "@language" : "${comment.language}",
                   |        "@value" : "${comment.value}"
                   |      },
                   |      "rdfs:label" : {
                   |        "@language" : "${label.language}",
                   |        "@value" : "${label.value}"
                   |      },
                   |      "rdfs:subPropertyOf" : {
                   |        "@id" : "knora-api:hasValue"
                   |      }
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "$ontologyName" : "$ontologyId#"
                   |  }
                   |}""".stripMargin
    new CreatePropertyRequest(value) {}
  }
}

sealed trait CardinalityRestriction {
  val cardinality: String
  val value: Int
}
object CardinalityRestriction {
  case object MaxCardinalityOne extends CardinalityRestriction {
    val cardinality = "owl:maxCardinality"
    val value       = 1
  }
}

final case class Property(ontology: String, property: String)
final case class Restriction(restriction: CardinalityRestriction, onProperty: Property) {
  def stringify(): String =
    s"""
       | {
       |    "@type": "owl:Restriction",
       |    "${restriction.cardinality}" : ${restriction.value},
       |    "owl:onProperty" : {
       |      "@id" : "${onProperty.ontology}:${onProperty.property}"
       |    }
       | }
       |""".stripMargin
}

sealed abstract case class AddCardinalitiesRequest private (value: String)
object AddCardinalitiesRequest {

  @tailrec
  private def stringifyRestrictions(restrictions: List[Restriction], acc: String = ""): String =
    restrictions match {
      case Nil       => acc
      case r :: Nil  => acc + r.stringify()
      case r :: rest => stringifyRestrictions(restrictions = rest, acc + r.stringify() + ", ")
    }

  def make(
    ontologyName: String,
    lastModificationDate: Instant,
    className: String,
    restrictions: List[Restriction]
  ): AddCardinalitiesRequest = {
    val LocalHost_Ontology         = "http://0.0.0.0:3333/ontology"
    val ontologyId                 = LocalHost_Ontology + s"/0001/$ontologyName/v2"
    val restrictionsString: String = stringifyRestrictions(restrictions)
    val value                      = s"""
                   |{
                   |  "@id" : "$ontologyId",
                   |  "@type" : "owl:Ontology",
                   |  "knora-api:lastModificationDate" : {
                   |    "@type" : "xsd:dateTimeStamp",
                   |    "@value" : "$lastModificationDate"
                   |  },
                   |  "@graph" : [ {
                   |    "@id" : "$ontologyName:$className",
                   |    "@type" : "owl:Class",
                   |    "rdfs:subClassOf" : [
                   |      $restrictionsString
                   |    ]
                   |  } ],
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "owl" : "http://www.w3.org/2002/07/owl#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "$ontologyName" : "$ontologyId#"
                   |  }
                   |}
            """.stripMargin
    new AddCardinalitiesRequest(value) {}
  }
}
