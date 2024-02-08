package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.prefix
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.RIO
import zio.ZIO
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.TestDataFactory.User.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.UserRepo
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object UserRepoLiveSpec extends ZIOSpecDefault {

  // Accessor functions of the UserRepo service which are tested
  def save(user: KnoraUser): ZIO[UserRepo, Throwable, KnoraUser]         = ZIO.serviceWithZIO[UserRepo](_.save(user))
  def findById(id: UserIri): ZIO[UserRepo, Throwable, Option[KnoraUser]] = ZIO.serviceWithZIO[UserRepo](_.findById(id))
  def findAll(): ZIO[UserRepo, Throwable, List[KnoraUser]]               = ZIO.serviceWithZIO[UserRepo](_.findAll())

  // Test data
  val unknownUserIri: UserIri = UserIri.unsafeFrom("http://rdfh.ch/users/doesNotExist")

  private def createUserQuery(u: KnoraUser): Update = {
    val triples = Rdf
      .iri(u.id.value)
      .has(RDF.TYPE, Vocabulary.KnoraAdmin.User)
      .andHas(Vocabulary.KnoraAdmin.username, Rdf.literalOf(u.username.value))
      .andHas(Vocabulary.KnoraAdmin.email, Rdf.literalOf(u.email.value))
      .andHas(Vocabulary.KnoraAdmin.givenName, Rdf.literalOf(u.givenName.value))
      .andHas(Vocabulary.KnoraAdmin.familyName, Rdf.literalOf(u.familyName.value))
      .andHas(Vocabulary.KnoraAdmin.preferredLanguage, Rdf.literalOf(u.preferredLanguage.value))
      .andHas(Vocabulary.KnoraAdmin.status, Rdf.literalOf(u.status.value))
      .andHas(Vocabulary.KnoraAdmin.password, Rdf.literalOf(u.passwordHash.value))
      .andHas(Vocabulary.KnoraAdmin.isInSystemAdminGroup, Rdf.literalOf(u.isInSystemAdminGroup.value))
    u.projects.foreach(prjIri => triples.andHas(Vocabulary.KnoraAdmin.isInProject, Rdf.iri(prjIri.value)))
    u.groups.foreach(grpIri => triples.andHas(Vocabulary.KnoraAdmin.isInGroup, Rdf.iri(grpIri.value)))
    Update(
      Queries
        .INSERT_DATA(triples)
        .into(Rdf.iri(adminDataNamedGraph.value))
        .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS), prefix(XSD.NS))
    )
  }

  def storeUsersInTripleStore(users: KnoraUser*): RIO[TriplestoreService, Unit] =
    ZIO.foreach(users)(user => ZIO.serviceWithZIO[TriplestoreService](_.query(createUserQuery(user)))).unit

  val spec: Spec[Any, Any] = suite("UserRepoLiveSpec")(
    test("findById given an  existing user should return that user") {
      for {
        _    <- storeUsersInTripleStore(testUser)
        user <- findById(testUser.id)
      } yield assertTrue(user.contains(testUser))
    },
    test("findAll given existing users should return all user") {
      for {
        _    <- storeUsersInTripleStore(testUser, testUser2)
        user <- findAll()
      } yield assertTrue(user.sortBy(_.id.value) == List(testUser, testUser2).sortBy(_.id.value))
    },
    test("findById given a non existing user should return None") {
      for {
        _    <- storeUsersInTripleStore(testUser, testUser2)
        user <- findById(unknownUserIri)
      } yield assertTrue(user.isEmpty)
    },
    test("should create and find user") {
      for {
        savedUser <- save(testUser)
        foundUser <- findById(savedUser.id).someOrFail(new Exception("User not found"))
      } yield assertTrue(savedUser == testUser, foundUser == testUser)
    },
    test("should update an existing user find user with new username") {
      for {
        _           <- save(testUser)                                                     // create the user
        _           <- save(testUser.copy(username = Username.unsafeFrom("newUsername"))) // update the username
        updatedUser <- findById(testUser.id).someOrFail(new Exception("User not found"))
      } yield assertTrue(updatedUser.username == Username.unsafeFrom("newUsername"))
    }
  )
    .provide(UserRepoLive.layer, TriplestoreServiceInMemory.emptyLayer, StringFormatter.test)
}
