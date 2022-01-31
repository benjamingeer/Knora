/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import akka.actor.Props
import org.knora.webapi.feature.{FeatureFactoryConfig, KnoraSettingsFeatureFactoryConfig}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, SmartIriLiteralV2, StringLiteralV2}
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.ontologymessages.{
  PredicateInfoV2,
  PropertyInfoContentV2,
  ReadOntologyV2,
  ReadPropertyInfoV2
}
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.store.triplestore.http.HttpTriplestoreConnector
import org.knora.webapi.util.cache.CacheUtil
import org.knora.webapi.{IntegrationSpec, InternalSchema, TestContainerFuseki}

import java.time.Instant
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
 * This spec is used to test [[org.knora.webapi.responders.v2.ontology.Cache]].
 */
class CacheSpec extends IntegrationSpec(TestContainerFuseki.PortConfig) {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  val additionalTestData = List(
    RdfDataObject(
      path = "test_data/ontologies/books-onto.ttl",
      name = "http://www.knora.org/ontology/0001/books"
    ),
    RdfDataObject(
      path = "test_data/all_data/books-data.ttl",
      name = "http://www.knora.org/data/0001/books"
    )
  )

  val defaultFeatureFactoryConfig: FeatureFactoryConfig = new KnoraSettingsFeatureFactoryConfig(settings)

  // start fuseki http connector actor
  private val fusekiActor = system.actorOf(
    Props(new HttpTriplestoreConnector()).withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
    name = "httpTriplestoreConnector"
  )

  override def beforeAll(): Unit = {
    CacheUtil.createCaches(settings.caches)
    waitForReadyTriplestore(fusekiActor)
    loadTestData(fusekiActor, additionalTestData)
  }

  "The basic functionality of the ontology cache" should {

    "successfully load all ontologies" in {
      val ontologiesFromCacheFuture: Future[Map[SmartIri, ReadOntologyV2]] = for {
        _ <- Cache.loadOntologies(
          settings,
          fusekiActor,
          defaultFeatureFactoryConfig,
          KnoraSystemInstances.Users.SystemUser
        )
        cacheData: Cache.OntologyCacheData <- Cache.getCacheData
        ontologies: Map[SmartIri, ReadOntologyV2] = cacheData.ontologies
      } yield ontologies

      ontologiesFromCacheFuture map { res: Map[SmartIri, ReadOntologyV2] =>
        res.size should equal(13)
      }
    }
  }

  "Updating the ontology cache," when {

    val CACHE_NOT_AVAILABLE_ERROR = "Cache not available"

    "removing a property from an ontology," should {

      "remove the property from the cache." in {
        val iri: SmartIri = stringFormatter.toSmartIri(additionalTestData.head.name)
        val hasTitlePropertyIri = stringFormatter.toSmartIri(s"${additionalTestData.head.name}#hasTitle")

        val previousCacheDataFuture = Cache.getCacheData
        val previousCacheData = Await.result(previousCacheDataFuture, 2 seconds)

        val previousBooksMaybe = previousCacheData.ontologies.get(iri)
        previousBooksMaybe match {
          case Some(previousBooks) =>
            // copy books-onto but remove :hasTitle property
            val newBooks = previousBooks.copy(
              ontologyMetadata = previousBooks.ontologyMetadata.copy(
                lastModificationDate = Some(Instant.now())
              ),
              properties = previousBooks.properties.view.filterKeys(_ != hasTitlePropertyIri).toMap
            )

            // store new ontology to cache
            val newCacheData = previousCacheData.copy(
              ontologies = previousCacheData.ontologies + (iri -> newBooks)
            )
            Cache.storeCacheData(newCacheData)

            // read back the cache
            val newCachedCacheDataFuture = for {
              cacheData <- Cache.getCacheData
            } yield cacheData
            val newCachedCacheData = Await.result(newCachedCacheDataFuture, 2 seconds)

            // ensure that the cache updated correctly
            val newCachedBooksMaybe = newCachedCacheData.ontologies.get(iri)
            newCachedBooksMaybe match {
              case Some(newCachedBooks) =>
                // check length
                assert(newCachedBooks.properties.size != previousBooks.properties.size)
                assert(newCachedBooks.properties.size == newBooks.properties.size)

                // check actual property
                previousBooks.properties should contain key hasTitlePropertyIri
                newCachedBooks.properties should not contain key(hasTitlePropertyIri)

              case None => fail(message = CACHE_NOT_AVAILABLE_ERROR)
            }

          case None => fail(message = CACHE_NOT_AVAILABLE_ERROR)
        }
      }
    }

    "adding a property to an ontology," should {

      "add a value property to the cache." in {

        val iri: SmartIri = stringFormatter.toSmartIri(additionalTestData.head.name)
        val hasDescriptionPropertyIri = stringFormatter.toSmartIri(s"${additionalTestData.head.name}#hasDescription")

        val previousCacheDataFuture = Cache.getCacheData
        val previousCacheData = Await.result(previousCacheDataFuture, 2 seconds)

        val previousBooksMaybe = previousCacheData.ontologies.get(iri)
        previousBooksMaybe match {
          case Some(previousBooks) =>
            // copy books-onto but add :hasDescription property
            val descriptionProp = ReadPropertyInfoV2(
              entityInfoContent = PropertyInfoContentV2(
                propertyIri = hasDescriptionPropertyIri,
                predicates = Map(
                  stringFormatter.toSmartIri(OntologyConstants.Rdf.Type) -> PredicateInfoV2(
                    predicateIri = stringFormatter.toSmartIri(OntologyConstants.Rdf.Type),
                    Seq(SmartIriLiteralV2(stringFormatter.toSmartIri(OntologyConstants.Owl.ObjectProperty)))
                  ),
                  stringFormatter.toSmartIri(OntologyConstants.Rdfs.Label) -> PredicateInfoV2(
                    predicateIri = stringFormatter.toSmartIri(OntologyConstants.Rdfs.Label),
                    Seq(
                      StringLiteralV2("A Description", language = Some("en")),
                      StringLiteralV2("Eine Beschreibung", language = Some("de"))
                    )
                  ),
                  stringFormatter.toSmartIri(OntologyConstants.KnoraBase.SubjectClassConstraint) -> PredicateInfoV2(
                    predicateIri = stringFormatter.toSmartIri(OntologyConstants.KnoraBase.SubjectClassConstraint),
                    Seq(SmartIriLiteralV2(iri))
                  ),
                  stringFormatter.toSmartIri(OntologyConstants.KnoraBase.ObjectClassConstraint) -> PredicateInfoV2(
                    predicateIri = stringFormatter.toSmartIri(OntologyConstants.KnoraBase.ObjectClassConstraint),
                    Seq(SmartIriLiteralV2(stringFormatter.toSmartIri(OntologyConstants.KnoraBase.TextValue)))
                  ),
                  stringFormatter.toSmartIri(OntologyConstants.SalsahGui.GuiElementClass) -> PredicateInfoV2(
                    predicateIri = stringFormatter.toSmartIri(OntologyConstants.SalsahGui.GuiElementClass),
                    Seq(SmartIriLiteralV2(stringFormatter.toSmartIri(OntologyConstants.SalsahGui.SimpleText)))
                  ),
                  stringFormatter.toSmartIri(OntologyConstants.SalsahGui.GuiAttribute) -> PredicateInfoV2(
                    predicateIri = stringFormatter.toSmartIri(OntologyConstants.SalsahGui.GuiAttribute),
                    Seq(
                      StringLiteralV2("size=80"),
                      StringLiteralV2("maxlength=255")
                    )
                  )
                ),
                subPropertyOf = Set(stringFormatter.toSmartIri(OntologyConstants.KnoraBase.HasValue)),
                ontologySchema = InternalSchema
              ),
              isResourceProp = true,
              isEditable = true
            )
            val newProps = previousBooks.properties + (hasDescriptionPropertyIri -> descriptionProp)
            val newBooks = previousBooks.copy(
              ontologyMetadata = previousBooks.ontologyMetadata.copy(
                lastModificationDate = Some(Instant.now())
              ),
              properties = newProps
            )

            // store new ontology to cache
            val newCacheData = previousCacheData.copy(
              ontologies = previousCacheData.ontologies + (iri -> newBooks)
            )
            Cache.storeCacheData(newCacheData)

            // read back the cache
            val newCachedCacheDataFuture = for {
              cacheData <- Cache.getCacheData
            } yield cacheData
            val newCachedCacheData = Await.result(newCachedCacheDataFuture, 2 seconds)

            // ensure that the cache updated correctly
            val newCachedBooksMaybe = newCachedCacheData.ontologies.get(iri)
            newCachedBooksMaybe match {
              case Some(newCachedBooks) =>
                // check length
                assert(newCachedBooks.properties.size != previousBooks.properties.size)
                assert(newCachedBooks.properties.size == newBooks.properties.size)

                // check actual property
                previousBooks.properties should not contain key(hasDescriptionPropertyIri)
                newCachedBooks.properties should contain key hasDescriptionPropertyIri

              case None => fail(message = CACHE_NOT_AVAILABLE_ERROR)
            }

          case None => fail(message = CACHE_NOT_AVAILABLE_ERROR)
        }
      }

      "add a link property and a link value property to the cache." in {

        val ontologyIri = stringFormatter.toSmartIri("http://www.knora.org/ontology/0001/books")
        val hasPagePropertyIri = stringFormatter.toSmartIri("http://www.knora.org/ontology/0001/books#hasPage")
        val pagePropertyIri = stringFormatter.toSmartIri("http://www.knora.org/ontology/0001/books#Page")
        val hasPageValuePropertyIri =
          stringFormatter.toSmartIri("http://www.knora.org/ontology/0001/books#hasPageValue")
        val bookIri = stringFormatter.toSmartIri("http://rdfh.ch/0001/book-instance-01")

        val previousCacheData = Await.result(Cache.getCacheData, 2 seconds)
        previousCacheData.ontologies.get(ontologyIri) match {
          case Some(previousBooks) =>
            // copy books-ontology but add link from book to page
            val linkPropertyInfoContent = PropertyInfoContentV2(
              propertyIri = hasPagePropertyIri,
              predicates = Map(
                stringFormatter.toSmartIri(OntologyConstants.Rdf.Type) -> PredicateInfoV2(
                  predicateIri = stringFormatter.toSmartIri(OntologyConstants.Rdf.Type),
                  Seq(SmartIriLiteralV2(stringFormatter.toSmartIri(OntologyConstants.Owl.ObjectProperty)))
                ),
                stringFormatter.toSmartIri(OntologyConstants.Rdfs.Label) -> PredicateInfoV2(
                  predicateIri = stringFormatter.toSmartIri(OntologyConstants.Rdfs.Label),
                  Seq(
                    StringLiteralV2("Seite im Buch", language = Some("de")),
                    StringLiteralV2("Page in the book", language = Some("en"))
                  )
                ),
                stringFormatter.toSmartIri(OntologyConstants.KnoraBase.SubjectClassConstraint) -> PredicateInfoV2(
                  predicateIri = stringFormatter.toSmartIri(OntologyConstants.KnoraBase.SubjectClassConstraint),
                  Seq(SmartIriLiteralV2(bookIri))
                ),
                stringFormatter.toSmartIri(OntologyConstants.KnoraBase.ObjectClassConstraint) -> PredicateInfoV2(
                  predicateIri = stringFormatter.toSmartIri(OntologyConstants.KnoraBase.ObjectClassConstraint),
                  Seq(SmartIriLiteralV2(pagePropertyIri))
                ),
                stringFormatter.toSmartIri(OntologyConstants.SalsahGui.GuiElementClass) -> PredicateInfoV2(
                  predicateIri = stringFormatter.toSmartIri(OntologyConstants.SalsahGui.GuiElementClass),
                  Seq(SmartIriLiteralV2(stringFormatter.toSmartIri(OntologyConstants.SalsahGui.Searchbox)))
                )
              ),
              subPropertyOf = Set(stringFormatter.toSmartIri(OntologyConstants.KnoraBase.HasLinkTo)),
              ontologySchema = InternalSchema
            )
            val hasPageProperties = ReadPropertyInfoV2(
              entityInfoContent = linkPropertyInfoContent,
              isResourceProp = true,
              isEditable = true,
              isLinkProp = true
            )
            val hasPageValueProperties = ReadPropertyInfoV2(
              entityInfoContent = OntologyHelpers.linkPropertyDefToLinkValuePropertyDef(linkPropertyInfoContent),
              isResourceProp = true,
              isEditable = true,
              isLinkValueProp = true
            )
            val newProps = previousBooks.properties +
              (hasPagePropertyIri -> hasPageProperties) +
              (hasPageValuePropertyIri -> hasPageValueProperties)
            val newBooks = previousBooks.copy(
              ontologyMetadata = previousBooks.ontologyMetadata.copy(
                lastModificationDate = Some(Instant.now())
              ),
              properties = newProps
            )

            // store new ontology to cache
            val newCacheData = previousCacheData.copy(
              ontologies = previousCacheData.ontologies + (ontologyIri -> newBooks)
            )
            Cache.storeCacheData(newCacheData)

            // read back the cache
            val newCachedCacheData = Await.result(Cache.getCacheData, 2 seconds)

            // ensure that the cache updated correctly
            newCachedCacheData.ontologies.get(ontologyIri) match {
              case Some(newCachedBooks) =>
                // check length
                assert(newCachedBooks.properties.size != previousBooks.properties.size)
                assert(newCachedBooks.properties.size == newBooks.properties.size)

                // check actual property
                previousBooks.properties should not contain key(hasPagePropertyIri)
                previousBooks.properties should not contain key(hasPageValuePropertyIri)
                newCachedBooks.properties should contain key (hasPagePropertyIri)
                newCachedBooks.properties should contain key (hasPageValuePropertyIri)

                // check isEditable == true
                val newHasPageValuePropertyMaybe = newCachedBooks.properties.get(hasPageValuePropertyIri)
                newHasPageValuePropertyMaybe should not equal (None)
                newHasPageValuePropertyMaybe match {
                  case Some(newHasPageValueProperty) =>
                    assert(newHasPageValueProperty.isEditable)
                    assert(newHasPageValueProperty.isLinkValueProp)

                  case None => fail(message = CACHE_NOT_AVAILABLE_ERROR)
                }

                newCachedBooks should equal(newBooks)

              case None => fail(message = CACHE_NOT_AVAILABLE_ERROR)
            }

          case None => fail(message = CACHE_NOT_AVAILABLE_ERROR)
        }
      }
    }
  }
}
