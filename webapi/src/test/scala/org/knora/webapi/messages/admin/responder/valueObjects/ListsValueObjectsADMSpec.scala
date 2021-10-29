/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.admin.responder.listsmessages.ListsErrorMessagesADM.{
  COMMENT_INVALID_ERROR,
  COMMENT_MISSING_ERROR,
  INVALID_POSITION,
  LABEL_INVALID_ERROR,
  LABEL_MISSING_ERROR,
  LIST_NAME_INVALID_ERROR,
  LIST_NAME_MISSING_ERROR,
  LIST_NODE_IRI_INVALID_ERROR,
  LIST_NODE_IRI_MISSING_ERROR,
  PROJECT_IRI_INVALID_ERROR,
  PROJECT_IRI_MISSING_ERROR
}
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.UnitSpec

/**
 * This spec is used to test the creation of value objects of the [[ListsValueObjectsADM]].
 */
class ListsValueObjectsADMSpec extends UnitSpec(ValueObjectsADMSpec.config) {

  "ListIRI value object" when {
    val validListIri = "http://rdfh.ch/lists/0803/qBCJAdzZSCqC_2snW5Q7Nw"

    "created using empty value" should {
      "throw BadRequestException" in {
        ListIRI.create("") should equal(Left(BadRequestException(LIST_NODE_IRI_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        ListIRI.create("not a list IRI") should equal(Left(BadRequestException(LIST_NODE_IRI_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "return value object that value equals to the value used to its creation" in {
        ListIRI.create(validListIri) should not equal Left(BadRequestException(LIST_NODE_IRI_INVALID_ERROR))
      }
    }
  }

  "ProjectIRI value object" when {
//    TODO: check string formatter project iri validation because passing just "http://rdfh.ch/projects/@@@@@@" works
    val validProjectIri = "http://rdfh.ch/projects/0001"

    "created using empty value" should {
      "throw BadRequestException" in {
        ProjectIRI.create("") should equal(Left(BadRequestException(PROJECT_IRI_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        ProjectIRI.create("not a project IRI") should equal(Left(BadRequestException(PROJECT_IRI_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        ProjectIRI.create(validProjectIri) should not equal Left(BadRequestException(PROJECT_IRI_INVALID_ERROR))
      }
    }
  }

  "ListName value object" when {
    val validListName = "It's valid list name example"

    "created using empty value" should {
      "throw BadRequestException" in {
        ListName.create("") should equal(Left(BadRequestException(LIST_NAME_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
//        TODO: should this: "\"It's invalid list name example\"" pass? Same for comments and labels
        ListName.create("\r") should equal(Left(BadRequestException(LIST_NAME_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        ListName.create(validListName) should not equal Left(BadRequestException(LIST_NAME_INVALID_ERROR))
      }
    }
  }

  "Position value object" when {
    val validPosition = 0

    "created using invalid value" should {
      "throw BadRequestException" in {
        Position.create(-2) should equal(Left(BadRequestException(INVALID_POSITION)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Position.create(validPosition) should not equal Left(BadRequestException(INVALID_POSITION))
      }
    }
  }

  "Labels value object" when {
    val validLabels = Seq(StringLiteralV2(value = "New Label", language = Some("en")))
    val invalidLabels = Seq(StringLiteralV2(value = "\r", language = Some("en")))

    "created using empty value" should {
      "throw BadRequestException" in {
        Labels.create(Seq.empty) should equal(Left(BadRequestException(LABEL_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        Labels.create(invalidLabels) should equal(Left(BadRequestException(LABEL_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Labels.create(validLabels) should not equal Left(BadRequestException(LABEL_INVALID_ERROR))
      }
    }
  }

  "Comments value object" when {
    val validComments = Seq(StringLiteralV2(value = "New Comment", language = Some("en")))
    val invalidComments = Seq(StringLiteralV2(value = "\r", language = Some("en")))

    "created using empty value" should {
      "throw BadRequestException" in {
        Comments.create(Seq.empty) should equal(Left(BadRequestException(COMMENT_MISSING_ERROR)))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        Comments.create(invalidComments) should equal(Left(BadRequestException(COMMENT_INVALID_ERROR)))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        Comments.create(validComments) should not equal Left(BadRequestException(COMMENT_INVALID_ERROR))
      }
    }
  }
}
