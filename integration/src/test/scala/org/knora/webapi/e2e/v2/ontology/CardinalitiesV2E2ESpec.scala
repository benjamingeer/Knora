/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2.ontology

import org.apache.pekko
import zio.*

import dsp.errors.BadRequestException
import org.knora.webapi.E2ESpec
import org.knora.webapi.RdfMediaTypes
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService

import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.model.StatusCodes
import pekko.http.scaladsl.model.headers.BasicHttpCredentials

class CardinalitiesV2E2ESpec extends E2ESpec {

  private def createProject(shortname: String, shortcode: String) = UnsafeZioRun.runOrThrow(
    ZIO
      .serviceWithZIO[KnoraProjectService] {
        _.createProject(
          ProjectCreateRequest(
            shortname = Shortname.unsafeFrom(shortname),
            shortcode = Shortcode.unsafeFrom(shortcode),
            description = List(Description.unsafeFrom("test project", Some("en"))),
          ),
        )
      }
      .map(_.id),
  )

  private def createOntology(projectIri: ProjectIri) = {
    val payload =
      s"""|{
          |  "knora-api:ontologyName" : "inherit",
          |  "knora-api:attachedToProject" : {
          |    "@id" : "$projectIri"
          |  },
          |  "rdfs:label" : "inheritance ontology",
          |  "@context" : {
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
          |  }
          |}
          |""".stripMargin
    val request = Post(
      s"$baseApiUrl/v2/ontologies",
      HttpEntity(RdfMediaTypes.`application/ld+json`, payload),
    ) ~> addCredentials(rootCredentials)
    val response = singleAwaitingRequest(request)
    assert(response.status == StatusCodes.OK, responseToString(response))
    val lastModificationDate = getLastModificationDate(response)
    val ontologyIri =
      JsonLDUtil
        .parseJsonLD(responseToString(response))
        .body
        .getRequiredString(JsonLDKeywords.ID)
        .fold(msg => throw BadRequestException(msg), identity)
    (ontologyIri, lastModificationDate)
  }

  private def createClass(
    ontologyIri: String,
    ontologyName: String,
    className: String,
    superClass: Option[String],
    lastModificationDate: String,
  ) = {
    val sp = superClass match {
      case None        => "knora-api:Resource"
      case Some(value) => s"$ontologyName:$value"
    }
    val payload =
      f"""|{
          |  "@id" : "$ontologyIri",
          |  "@type" : "owl:Ontology",
          |  "knora-api:lastModificationDate" : {
          |    "@type" : "xsd:dateTimeStamp",
          |    "@value" : "$lastModificationDate"
          |  },
          |  "@graph" : [
          |    {
          |      "@id" : "$ontologyName:$className",
          |      "@type" : "owl:Class",
          |      "rdfs:label" : {
          |        "@language" : "en",
          |        "@value" : "$className"
          |      },
          |      "rdfs:subClassOf" : {
          |        "@id" : "$sp"
          |      }
          |    }
          |  ],
          |  "@context" : {
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "$ontologyName" : "$ontologyIri#",
          |    "owl" : "http://www.w3.org/2002/07/owl#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
          |  }
          |}
          |""".stripMargin
    val request = Post(
      s"$baseApiUrl/v2/ontologies/classes",
      HttpEntity(RdfMediaTypes.`application/ld+json`, payload),
    ) ~> addCredentials(rootCredentials)
    val response = singleAwaitingRequest(request)
    assert(response.status == StatusCodes.OK, responseToString(response))
    getLastModificationDate(response)
  }

  private def createProperty(
    ontologyIri: String,
    ontologyName: String,
    propertyName: String,
    lastModificationDate: String,
  ) = {
    val payload =
      s"""|{
          |  "@id" : "$ontologyIri",
          |  "@type" : "owl:Ontology",
          |  "knora-api:lastModificationDate" : {
          |    "@type" : "xsd:dateTimeStamp",
          |    "@value" : "$lastModificationDate"
          |  },
          |  "@graph" : [
          |    {
          |      "@id" : "$ontologyName:$propertyName",
          |      "@type" : "owl:ObjectProperty",
          |      "knora-api:objectType" : {
          |        "@id" : "knora-api:IntValue"
          |      },
          |      "rdfs:label" : {
          |        "@language" : "en",
          |        "@value" : "property $propertyName"
          |      },
          |      "rdfs:subPropertyOf" : {
          |        "@id" : "knora-api:hasValue"
          |      }
          |    }
          |  ],
          |  "@context" : {
          |    "$ontologyName" : "$ontologyIri#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
          |    "owl" : "http://www.w3.org/2002/07/owl#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
          |  }
          |}
          |""".stripMargin
    val request = Post(
      s"$baseApiUrl/v2/ontologies/properties",
      HttpEntity(RdfMediaTypes.`application/ld+json`, payload),
    ) ~> addCredentials(rootCredentials)
    val response = singleAwaitingRequest(request)
    assert(response.status == StatusCodes.OK, responseToString(response))
    getLastModificationDate(response)
  }

  private def addRequiredCardinalityToClass(
    ontologyIri: String,
    ontologyName: String,
    className: String,
    propertyName: String,
    lastModificationDate: String,
  ) = {
    val payload =
      s"""|{
          |  "@id" : "$ontologyIri",
          |  "@type" : "owl:Ontology",
          |  "knora-api:lastModificationDate" : {
          |    "@type" : "xsd:dateTimeStamp",
          |    "@value" : "$lastModificationDate"
          |  },
          |  "@graph" : [ 
          |    {
          |      "@id" : "$ontologyName:$className",
          |      "@type" : "owl:Class",
          |      "rdfs:subClassOf" : {
          |        "@type": "owl:Restriction",
          |        "owl:cardinality": 1,
          |        "owl:onProperty": {
          |          "@id" : "$ontologyName:$propertyName"
          |        }
          |      }
          |    }
          |  ],
          |  "@context" : {
          |    "$ontologyName" : "$ontologyIri#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "owl" : "http://www.w3.org/2002/07/owl#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
          |  }
          |}
          |""".stripMargin
    val request = Post(
      s"$baseApiUrl/v2/ontologies/cardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, payload),
    ) ~> addCredentials(rootCredentials)
    val response = singleAwaitingRequest(request)
    assert(response.status == StatusCodes.OK, responseToString(response))
    getLastModificationDate(response)
  }

  private def createValue(
    projectIri: ProjectIri,
    ontologyIri: String,
    ontologyName: String,
    className: String,
    propertyNames: List[String],
  ) = {
    val propDefinitions =
      propertyNames
        .map(prop => s"""|  "$ontologyName:$prop" : {
                         |    "@type" : "knora-api:IntValue",
                         |    "knora-api:intValueAsInt" : 42
                         |  },""".stripMargin)
        .mkString("\n")
    val payload =
      s"""|{
          |  "@type" : "$ontologyName:$className",
          |  "rdfs:label": "Instance of $className",
          |  "knora-api:attachedToProject" : {
          |    "@id" : "$projectIri"
          |  },
          |$propDefinitions
          |  "@context" : {
          |    "$ontologyName" : "$ontologyIri#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "owl" : "http://www.w3.org/2002/07/owl#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
          |  }
          |}
          |""".stripMargin
    val request = Post(
      s"$baseApiUrl/v2/resources",
      HttpEntity(RdfMediaTypes.`application/ld+json`, payload),
    ) ~> addCredentials(rootCredentials)
    val response = singleAwaitingRequest(request)
    (response.status, responseToString(response))
  }

  private def getLastModificationDate(response: HttpResponse): String =
    JsonLDUtil
      .parseJsonLD(responseToString(response))
      .body
      .getRequiredObject(OntologyConstants.KnoraApiV2Complex.LastModificationDate)
      .fold(e => throw BadRequestException(e), identity)
      .getRequiredString(JsonLDKeywords.VALUE)
      .fold(msg => throw BadRequestException(msg), identity)

  private val rootCredentials = BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)

  "Ontologies endpoint" should {
    "be able to create resource instances with all properties, when adding cardinalities on super class first" in {

      val projectIri = createProject("test1", "4441")

      val (ontologyIri, lmd)   = createOntology(projectIri)
      var lastModificationDate = lmd

      val superClassName = "SuperClass"
      val ontologyName   = "inherit"
      lastModificationDate = createClass(
        ontologyIri = ontologyIri,
        ontologyName = ontologyName,
        className = superClassName,
        superClass = None,
        lastModificationDate = lastModificationDate,
      )

      val subClassName = "SubClass"
      lastModificationDate = createClass(
        ontologyIri = ontologyIri,
        ontologyName = ontologyName,
        className = subClassName,
        superClass = Some(superClassName),
        lastModificationDate = lastModificationDate,
      )

      val superClassProperty1 = "superClassProperty1"
      val superClassProperty2 = "superClassProperty2"
      val subClassProperty1   = "subClassProperty1"
      val subClassProperty2   = "subClassProperty2"
      for (prop <- List(superClassProperty1, superClassProperty2, subClassProperty1, subClassProperty2)) {
        lastModificationDate = createProperty(
          ontologyIri = ontologyIri,
          ontologyName = ontologyName,
          propertyName = prop,
          lastModificationDate = lastModificationDate,
        )
      }

      // first adding the the cardinalities to the *super*, then to the *sub* class
      val clsAndProps = List(
        (superClassName, superClassProperty1),
        (superClassName, superClassProperty2),
        (subClassName, subClassProperty1),
        (subClassName, subClassProperty2),
      )
      for ((cls, prop) <- clsAndProps) {
        lastModificationDate = addRequiredCardinalityToClass(
          ontologyIri = ontologyIri,
          ontologyName = ontologyName,
          className = cls,
          propertyName = prop,
          lastModificationDate = lastModificationDate,
        )
      }

      val (superStatus, superResponse) = createValue(
        projectIri = projectIri,
        ontologyIri = ontologyIri,
        ontologyName = ontologyName,
        className = superClassName,
        propertyNames = List(superClassProperty1, superClassProperty2),
      )
      assert(superStatus == StatusCodes.OK, superResponse)

      val (subStatus, subResponse) = createValue(
        projectIri = projectIri,
        ontologyIri = ontologyIri,
        ontologyName = ontologyName,
        className = subClassName,
        propertyNames = List(superClassProperty1, superClassProperty2, subClassProperty1, subClassProperty2),
      )
      assert(subStatus == StatusCodes.OK, subResponse)
    }

    "be able to create resource instances with all properties, when adding cardinalities on sub class first" in {

      val projectIri = createProject("test2", "4442")

      val (ontologyIri, lmd)   = createOntology(projectIri)
      var lastModificationDate = lmd

      val superClassName = "SuperClass"
      val ontologyName   = "inherit"
      lastModificationDate = createClass(
        ontologyIri = ontologyIri,
        ontologyName = ontologyName,
        className = superClassName,
        superClass = None,
        lastModificationDate = lastModificationDate,
      )

      val subClassName = "SubClass"
      lastModificationDate = createClass(
        ontologyIri = ontologyIri,
        ontologyName = ontologyName,
        className = subClassName,
        superClass = Some(superClassName),
        lastModificationDate = lastModificationDate,
      )

      val superClassProperty1 = "superClassProperty1"
      val superClassProperty2 = "superClassProperty2"
      val subClassProperty1   = "subClassProperty1"
      val subClassProperty2   = "subClassProperty2"
      for (prop <- List(superClassProperty1, superClassProperty2, subClassProperty1, subClassProperty2)) {
        lastModificationDate = createProperty(
          ontologyIri = ontologyIri,
          ontologyName = ontologyName,
          propertyName = prop,
          lastModificationDate = lastModificationDate,
        )
      }

      // first adding the the cardinalities to the *sub*, then to the *super* class
      val clsAndProps = List(
        (subClassName, subClassProperty1),
        (subClassName, subClassProperty2),
        (superClassName, superClassProperty1),
        (superClassName, superClassProperty2),
      )
      for ((cls, prop) <- clsAndProps) {
        lastModificationDate = addRequiredCardinalityToClass(
          ontologyIri = ontologyIri,
          ontologyName = ontologyName,
          className = cls,
          propertyName = prop,
          lastModificationDate = lastModificationDate,
        )
      }

      val (superStatus, superResponse) = createValue(
        projectIri = projectIri,
        ontologyIri = ontologyIri,
        ontologyName = ontologyName,
        className = superClassName,
        propertyNames = List(superClassProperty1, superClassProperty2),
      )
      assert(superStatus == StatusCodes.OK, superResponse)

      val (subStatus, subResponse) = createValue(
        projectIri = projectIri,
        ontologyIri = ontologyIri,
        ontologyName = ontologyName,
        className = subClassName,
        propertyNames = List(superClassProperty1, superClassProperty2, subClassProperty1, subClassProperty2),
      )
      assert(subStatus == StatusCodes.OK, subResponse)
    }

    "not be able to create subclass instances with missing required properties defined on superclass, when adding cardinalities on super class first" in {

      val projectIri = createProject("test3", "4443")

      val (ontologyIri, lmd)   = createOntology(projectIri)
      var lastModificationDate = lmd

      val superClassName = "SuperClass"
      val ontologyName   = "inherit"
      lastModificationDate = createClass(
        ontologyIri = ontologyIri,
        ontologyName = ontologyName,
        className = superClassName,
        superClass = None,
        lastModificationDate = lastModificationDate,
      )

      val subClassName = "SubClass"
      lastModificationDate = createClass(
        ontologyIri = ontologyIri,
        ontologyName = ontologyName,
        className = subClassName,
        superClass = Some(superClassName),
        lastModificationDate = lastModificationDate,
      )

      val superClassProperty1 = "superClassProperty1"
      val superClassProperty2 = "superClassProperty2"
      val subClassProperty1   = "subClassProperty1"
      val subClassProperty2   = "subClassProperty2"
      for (prop <- List(superClassProperty1, superClassProperty2, subClassProperty1, subClassProperty2)) {
        lastModificationDate = createProperty(
          ontologyIri = ontologyIri,
          ontologyName = ontologyName,
          propertyName = prop,
          lastModificationDate = lastModificationDate,
        )
      }

      // first adding the cardinalities to the *super*, then to the *sub* class
      val clsAndProps = List(
        (superClassName, superClassProperty1),
        (superClassName, superClassProperty2),
        (subClassName, subClassProperty1),
        (subClassName, subClassProperty2),
      )
      for ((cls, prop) <- clsAndProps) {
        lastModificationDate = addRequiredCardinalityToClass(
          ontologyIri = ontologyIri,
          ontologyName = ontologyName,
          className = cls,
          propertyName = prop,
          lastModificationDate = lastModificationDate,
        )
      }

      val (status, response) = createValue(
        projectIri = projectIri,
        ontologyIri = ontologyIri,
        ontologyName = ontologyName,
        className = subClassName,
        // missing mandatory props defined on super class
        propertyNames = List(subClassProperty1, subClassProperty2),
      )
      assert(status == StatusCodes.BadRequest, response)
    }

    "not be able to create subclass instances with missing properties defined on superclass, when adding cardinalities on sub class first" in {

      val projectIri = createProject("test4", "4444")

      val (ontologyIri, lmd)   = createOntology(projectIri)
      var lastModificationDate = lmd

      val superClassName = "SuperClass"
      val ontologyName   = "inherit"
      lastModificationDate = createClass(
        ontologyIri = ontologyIri,
        ontologyName = ontologyName,
        className = superClassName,
        superClass = None,
        lastModificationDate = lastModificationDate,
      )

      val subClassName = "SubClass"
      lastModificationDate = createClass(
        ontologyIri = ontologyIri,
        ontologyName = ontologyName,
        className = subClassName,
        superClass = Some(superClassName),
        lastModificationDate = lastModificationDate,
      )

      val superClassProperty1 = "superClassProperty1"
      val superClassProperty2 = "superClassProperty2"
      val subClassProperty1   = "subClassProperty1"
      val subClassProperty2   = "subClassProperty2"
      for (prop <- List(superClassProperty1, superClassProperty2, subClassProperty1, subClassProperty2)) {
        lastModificationDate = createProperty(
          ontologyIri = ontologyIri,
          ontologyName = ontologyName,
          propertyName = prop,
          lastModificationDate = lastModificationDate,
        )
      }

      // first adding the the cardinalities to the *sub*, then to the *super* class
      val clsAndProps = List(
        (subClassName, subClassProperty1),
        (subClassName, subClassProperty2),
        (superClassName, superClassProperty1),
        (superClassName, superClassProperty2),
      )
      for ((cls, prop) <- clsAndProps) {
        lastModificationDate = addRequiredCardinalityToClass(
          ontologyIri = ontologyIri,
          ontologyName = ontologyName,
          className = cls,
          propertyName = prop,
          lastModificationDate = lastModificationDate,
        )
      }

      val (status, response) = createValue(
        projectIri = projectIri,
        ontologyIri = ontologyIri,
        ontologyName = ontologyName,
        className = subClassName,
        // missing mandatory props defined on super class
        propertyNames = List(subClassProperty1, subClassProperty2),
      )
      assert(status == StatusCodes.BadRequest, response)
    }

  }
}
