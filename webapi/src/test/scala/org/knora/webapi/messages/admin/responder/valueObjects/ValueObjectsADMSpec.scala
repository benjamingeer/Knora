/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.valueObjects

import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.{CreateUserApiRequestADM, UserCreatePayloadADM}
import org.knora.webapi.{IRI, UnitSpec}
import org.scalatest.enablers.Messaging.messagingNatureOfThrowable

object ValueObjectsADMSpec {
  val config: Config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * This spec is used to test the creation of value objects of the [[ValueObject]] trait.
 */
class ValueObjectsADMSpec extends UnitSpec(ValueObjectsADMSpec.config) {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  /**
   * Convenience method returning the UserCreatePayloadADM from the [[CreateUserApiRequestADM]] object
   *
   * @param createUserApiRequestADM the [[CreateUserApiRequestADM]] object
   * @return                        a [[UserCreatePayloadADM]]
   */
  private def createUserCreatePayloadADM(createUserApiRequestADM: CreateUserApiRequestADM): UserCreatePayloadADM =
    UserCreatePayloadADM.create(
      id = stringFormatter
        .validateOptionalUserIri(createUserApiRequestADM.id, throw BadRequestException(s"Invalid user IRI")),
      username = Username.create(createUserApiRequestADM.username).fold(error => throw error, value => value),
      email = Email.create(createUserApiRequestADM.email).fold(error => throw error, value => value),
      givenName = GivenName.create(createUserApiRequestADM.givenName).fold(error => throw error, value => value),
      familyName = FamilyName.create(createUserApiRequestADM.familyName).fold(error => throw error, value => value),
      password = Password.create(createUserApiRequestADM.password).fold(error => throw error, value => value),
      status = Status.make(createUserApiRequestADM.status).fold(error => throw error.head, value => value),
      lang = LanguageCode.create(createUserApiRequestADM.lang).fold(error => throw error, value => value),
      systemAdmin = SystemAdmin.create(createUserApiRequestADM.systemAdmin).fold(error => throw error, value => value)
    )

  /**
   * Convenience method returning the [[CreateUserApiRequestADM]] object
   *
   * @param id          the optional IRI of the user to be created (unique).
   * @param username    the username of the user to be created (unique).
   * @param email       the email of the user to be created (unique).
   * @param givenName   the given name of the user to be created.
   * @param familyName  the family name of the user to be created
   * @param password    the password of the user to be created.
   * @param status      the status of the user to be created (active = true, inactive = false).
   * @param lang        the default language of the user to be created.
   * @param systemAdmin the system admin membership.
   * @return            a [[UserCreatePayloadADM]]
   */
  private def createUserApiRequestADM(
    id: Option[IRI] = None,
    username: String = "donald.duck",
    email: String = "donald.duck@example.com",
    givenName: String = "Donald",
    familyName: String = "Duck",
    password: String = "test",
    status: Boolean = true,
    lang: String = "en",
    systemAdmin: Boolean = false
  ): CreateUserApiRequestADM =
    CreateUserApiRequestADM(
      id = id,
      username = username,
      email = email,
      givenName = givenName,
      familyName = familyName,
      password = password,
      status = status,
      lang = lang,
      systemAdmin = systemAdmin
    )

  "When the UserCreatePayloadADM case class is created it" should {
    "create a valid UserCreatePayloadADM" in {

      val request = createUserApiRequestADM()

      val userCreatePayloadADM = createUserCreatePayloadADM(request)

      userCreatePayloadADM.id should equal(request.id)
      userCreatePayloadADM.username.get.value should equal(request.username)
      userCreatePayloadADM.email.get.value should equal(request.email)
      userCreatePayloadADM.password.get.value should equal(request.password)
      userCreatePayloadADM.givenName.get.value should equal(request.givenName)
      userCreatePayloadADM.familyName.get.value should equal(request.familyName)
      userCreatePayloadADM.status.get.value should equal(request.status)
      userCreatePayloadADM.lang.get.value should equal(request.lang)
      userCreatePayloadADM.systemAdmin.get.value should equal(request.systemAdmin)

      val otherRequest = createUserApiRequestADM(
        id = Some("http://rdfh.ch/users/notdonald"),
        username = "not.donald.duck",
        email = "not.donald.duck@example.com",
        givenName = "NotDonald",
        familyName = "NotDuck",
        password = "notDonaldDuckTest",
        status = false,
        lang = "de",
        systemAdmin = true
      )

      val otherUserCreatePayloadADM = createUserCreatePayloadADM(otherRequest)

      otherUserCreatePayloadADM.id should equal(otherRequest.id)
      otherUserCreatePayloadADM.username.get.value should equal(otherRequest.username)
      otherUserCreatePayloadADM.email.get.value should equal(otherRequest.email)
      otherUserCreatePayloadADM.password.get.value should equal(otherRequest.password)
      otherUserCreatePayloadADM.givenName.get.value should equal(otherRequest.givenName)
      otherUserCreatePayloadADM.familyName.get.value should equal(otherRequest.familyName)
      otherUserCreatePayloadADM.status.get.value should equal(otherRequest.status)
      otherUserCreatePayloadADM.lang.get.value should equal(otherRequest.lang)
      otherUserCreatePayloadADM.systemAdmin.get.value should equal(otherRequest.systemAdmin)

      otherUserCreatePayloadADM.id should not equal request.id
      otherUserCreatePayloadADM.username.get.value should not equal request.username
      otherUserCreatePayloadADM.email.get.value should not equal request.email
      otherUserCreatePayloadADM.password.get.value should not equal request.password
      otherUserCreatePayloadADM.givenName.get.value should not equal request.givenName
      otherUserCreatePayloadADM.familyName.get.value should not equal request.familyName
      otherUserCreatePayloadADM.status.get.value should not equal request.status
      otherUserCreatePayloadADM.lang.get.value should not equal request.lang
      otherUserCreatePayloadADM.systemAdmin.get.value should not equal request.systemAdmin
    }

    "throw 'BadRequestException' if 'username' is missing" in {
      val request = createUserApiRequestADM(username = "")

      the[BadRequestException] thrownBy createUserCreatePayloadADM(request) should have message "Missing username"
    }

    "throw 'BadRequestException' if 'email' is missing" in {
      val request = createUserApiRequestADM(email = "")

      the[BadRequestException] thrownBy createUserCreatePayloadADM(request) should have message "Missing email"
    }

    "throw 'BadRequestException' if 'password' is missing" in {
      val request = createUserApiRequestADM(password = "")

      the[BadRequestException] thrownBy createUserCreatePayloadADM(request) should have message "Missing password"
    }

    "throw 'BadRequestException' if 'givenName' is missing" in {
      val request = createUserApiRequestADM(givenName = "")

      the[BadRequestException] thrownBy createUserCreatePayloadADM(request) should have message "Missing given name"
    }

    "throw 'BadRequestException' if 'familyName' is missing" in {
      val request = createUserApiRequestADM(familyName = "")

      the[BadRequestException] thrownBy createUserCreatePayloadADM(request) should have message "Missing family name"
    }

    "throw 'BadRequestException' if 'lang' is missing" in {
      val request = createUserApiRequestADM(lang = "")

      the[BadRequestException] thrownBy createUserCreatePayloadADM(request) should have message "Missing language code"
    }

    "throw 'BadRequestException' if the supplied 'id' is not a valid IRI" in {
      val request = createUserApiRequestADM(id = Some("invalid-iri"))

      the[BadRequestException] thrownBy createUserCreatePayloadADM(request) should have message "Invalid user IRI"

    }

    "throw 'BadRequestException' if 'username' is invalid" in {
      Set(
        createUserApiRequestADM(username = "don"), // too short
        createUserApiRequestADM(username = "asdfoiasdfasdnlasdkjflasdjfaskdjflaskdjfaddssdskdfjs"), // too long
        createUserApiRequestADM(username = "_donald"), // starts with _
        createUserApiRequestADM(username = ".donald"), // starts with .
        createUserApiRequestADM(username = "donald_"), // ends with _
        createUserApiRequestADM(username = "donald."), // ends with .
        createUserApiRequestADM(username = "donald__duck"), // contains multiple _
        createUserApiRequestADM(username = "donald..duck"), // contains multiple .
        createUserApiRequestADM(username = "donald#duck"), // contains not only alphanumeric characters
        createUserApiRequestADM(username = "dönälddück") // contains umlaut characters
      ).map(request =>
        the[BadRequestException] thrownBy createUserCreatePayloadADM(request) should have message "Invalid username"
      )
    }

    "throw 'BadRequestException' if 'email' is invalid" in {
      Set(
        createUserApiRequestADM(email = "don"), // does not contain @
        createUserApiRequestADM(email = "don@"), // ends with @
        createUserApiRequestADM(email = "@don") // starts with @
      ).map(request =>
        the[BadRequestException] thrownBy createUserCreatePayloadADM(request) should have message "Invalid email"
      )
    }

    "throw 'BadRequestException' if 'lang' is invalid" in {
      val request = createUserApiRequestADM(lang = "xy")

      the[BadRequestException] thrownBy createUserCreatePayloadADM(request) should have message "Invalid language code"
    }

  }

}
