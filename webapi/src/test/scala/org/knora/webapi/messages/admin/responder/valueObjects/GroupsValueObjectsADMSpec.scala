/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.UnitSpec
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsErrorMessagesADM.{
  GROUP_DESCRIPTION_INVALID_ERROR,
  GROUP_DESCRIPTION_MISSING_ERROR,
  GROUP_IRI_INVALID_ERROR,
  GROUP_IRI_MISSING_ERROR,
  GROUP_NAME_INVALID_ERROR,
  GROUP_NAME_MISSING_ERROR
}
import org.knora.webapi.messages.admin.responder.listsmessages.ListsErrorMessagesADM._
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import zio.prelude.Validation

/**
 * This spec is used to test the [[GroupsValueObjectsADM]] value objects creation.
 */
class GroupsValueObjectsADMSpec extends UnitSpec(ValueObjectsADMSpec.config) {
  "GroupIRI value object" when {
    val validGroupIri = "http://rdfh.ch/groups/0803/qBCJAdzZSCqC_2snW5Q7Nw"

    "created using empty value" should {
      "throw BadRequestException" in {
        GroupIRI.create("").merge should equal(BadRequestException(GROUP_IRI_MISSING_ERROR))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        GroupIRI.create("not a group IRI").merge should equal(BadRequestException(GROUP_IRI_INVALID_ERROR))
      }
    }
    "created using valid value" should {
      "not throw BadRequestException " in {
        GroupIRI.create(validGroupIri).merge should not equal BadRequestException(GROUP_IRI_INVALID_ERROR)
      }
      "return value passed to value object" in {
        GroupIRI.create(validGroupIri).toOption.get.value should equal(validGroupIri)
      }
    }
  }

  "GroupName value object" when {
    val validGroupName = "Valid group name"

    "created using empty value" should {
      "throw BadRequestException" in {
        GroupName.create("").merge should equal(BadRequestException(GROUP_NAME_MISSING_ERROR))
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        GroupName.create("Invalid group name\r").merge should equal(BadRequestException(GROUP_NAME_INVALID_ERROR))
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        GroupName.create(validGroupName).merge should not equal BadRequestException(GROUP_NAME_INVALID_ERROR)
      }
      "return value passed to value object" in {
        GroupName.create(validGroupName).toOption.get.value should equal(validGroupName)
      }
    }
  }

  "GroupDescription value object" when {
    val validDescription = Seq(StringLiteralV2(value = "Valid description", language = Some("en")))
    val invalidDescription = Seq(StringLiteralV2(value = "Invalid description \r", language = Some("en")))

    "created using empty value" should {
      "throw BadRequestException" in {
        GroupDescription.make(Seq.empty) should equal(
          Validation.fail(BadRequestException(GROUP_DESCRIPTION_MISSING_ERROR))
        )
      }
    }
    "created using invalid value" should {
      "throw BadRequestException" in {
        GroupDescription.make(invalidDescription) should equal(
          Validation.fail(BadRequestException(GROUP_DESCRIPTION_INVALID_ERROR))
        )
      }
    }
    "created using valid value" should {
      "not throw BadRequestExceptions" in {
        GroupDescription.make(validDescription).toOption.get.value should not equal
          BadRequestException(GROUP_DESCRIPTION_INVALID_ERROR)
      }
      "return value passed to value object" in {
        GroupDescription.make(validDescription).toOption.get.value should equal(validDescription)
      }
    }
  }

  "GroupStatus value object" when {
    "created using valid value" should {
      "return value passed to value object" in {
        GroupStatus.make(true).toOption.get.value should equal(true)
        GroupStatus.make(false).toOption.get.value should equal(false)
      }
    }
  }

  "GroupSelfJoin value object" when {
    "created using valid value" should {
      "return value passed to value object" in {
        GroupSelfJoin.make(false).toOption.get.value should equal(false)
        GroupSelfJoin.make(true).toOption.get.value should equal(true)
      }
    }
  }
}
