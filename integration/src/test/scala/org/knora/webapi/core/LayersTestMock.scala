/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko
import org.apache.pekko.actor.ActorSystem
import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfig.AppConfigurations
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2Live
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.*
import org.knora.webapi.responders.v2.*
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.OntologyCacheHelpers
import org.knora.webapi.responders.v2.ontology.OntologyTriplestoreHelpers
import org.knora.webapi.routing.*
import org.knora.webapi.slice.admin.AdminModule
import org.knora.webapi.slice.admin.api.*
import org.knora.webapi.slice.admin.api.AdminApiModule
import org.knora.webapi.slice.admin.api.service.GroupRestService
import org.knora.webapi.slice.admin.api.service.PermissionRestService
import org.knora.webapi.slice.admin.api.service.ProjectRestService
import org.knora.webapi.slice.admin.api.service.UserRestService
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.admin.domain.service.ProjectExportStorageService
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.InfrastructureModule
import org.knora.webapi.slice.infrastructure.api.ManagementEndpoints
import org.knora.webapi.slice.infrastructure.api.ManagementRoutes
import org.knora.webapi.slice.lists.api.ListsApiModule
import org.knora.webapi.slice.lists.domain.ListsService
import org.knora.webapi.slice.ontology.api.OntologyApiModule
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.ontology.api.service.RestCardinalityServiceLive
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.domain.service.OntologyService
import org.knora.webapi.slice.ontology.domain.service.OntologyServiceLive
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheLive
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.resourceinfo.ResourceInfoLayers
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoEndpoints
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resources.api.ResourcesApiModule
import org.knora.webapi.slice.resources.repo.service.ResourcesRepo
import org.knora.webapi.slice.resources.repo.service.ResourcesRepoLive
import org.knora.webapi.slice.search.api.SearchApiRoutes
import org.knora.webapi.slice.search.api.SearchEndpoints
import org.knora.webapi.slice.security.ScopeResolver
import org.knora.webapi.slice.security.SecurityModule
import org.knora.webapi.slice.security.api.AuthenticationApiModule
import org.knora.webapi.slice.security.api.AuthenticationApiRoutes
import org.knora.webapi.slice.shacl.ShaclModule
import org.knora.webapi.slice.shacl.api.ShaclApiModule
import org.knora.webapi.store.iiif.IIIFRequestMessageHandler
import org.knora.webapi.store.iiif.IIIFRequestMessageHandlerLive
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.impl.SipiServiceMock
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testservices.TestClientService
import org.knora.webapi.testservices.TestDspIngestClient

object LayersTestMock { self =>

  /**
   * The `Environment`s that we require for the tests to run - with or without Sipi
   */
  type Environment = LayersLive.DspEnvironmentLive & FusekiTestContainer & MessageRelayActorRef & TestClientService &
    TestDspIngestClient

  /**
   * Provides a layer for integration tests which depend on Fuseki as Testcontainers.
   * Sipi/IIIFService will be mocked with the [[SipiServiceMock]]
   *
   * @param system An optional [[pekko.actor.ActorSystem]] for use with Akka's [[pekko.testkit.TestKit]]
   * @return a [[ULayer]] with the [[DefaultTestEnvironmentWithoutSipi]]
   */
  def layer(
    system: Option[ActorSystem] = None,
  ): ULayer[self.Environment] = {
    // Due to bug in Scala 2 compiler invoking methods with by-name parameters in provide/provideSome method does not work
    // assign the layer to a temp val and use it in the ZLayer.make
    val temp = system.map(ActorSystemTest.layer).getOrElse(PekkoActorSystem.layer)
    ZLayer.make[self.Environment](
      temp,
      TestContainerLayers.fusekiOnly,
      SipiServiceMock.layer,
      /// common
      AdminApiModule.layer,
      AdminModule.layer,
      ApiComplexV2JsonLdRequestParser.layer,
      ApiRoutes.layer,
      ApiV2Endpoints.layer,
      AssetPermissionsResponder.layer,
      AuthenticationApiModule.layer,
      AuthorizationRestService.layer,
      BaseEndpoints.layer,
      CardinalityHandler.layer,
      CardinalityService.layer,
      ConstructResponseUtilV2.layer,
      DspIngestClientLive.layer,
      HandlerMapper.layer,
      HttpServer.layer,
      IIIFRequestMessageHandlerLive.layer,
      InfrastructureModule.layer,
      IriConverter.layer,
      IriService.layer,
      KnoraResponseRenderer.layer,
      ListsApiModule.layer,
      ListsResponder.layer,
      ListsService.layer,
      ManagementEndpoints.layer,
      ManagementRoutes.layer,
      MessageRelayActorRef.layer,
      MessageRelayLive.layer,
      OntologyApiModule.layer,
      OntologyCacheHelpers.layer,
      OntologyCacheLive.layer,
      OntologyRepoLive.layer,
      OntologyResponderV2.layer,
      OntologyServiceLive.layer,
      OntologyTriplestoreHelpers.layer,
      PermissionUtilADMLive.layer,
      PermissionsResponder.layer,
      PredicateObjectMapper.layer,
      PredicateRepositoryLive.layer,
      ProjectExportServiceLive.layer,
      ProjectExportStorageServiceLive.layer,
      ProjectImportService.layer,
      RepositoryUpdater.layer,
      ResourceInfoLayers.live,
      ResourceUtilV2Live.layer,
      ResourcesApiModule.layer,
      ResourcesRepoLive.layer,
      ResourcesResponderV2.layer,
      RestCardinalityServiceLive.layer,
      SearchApiRoutes.layer,
      SearchEndpoints.layer,
      SearchResponderV2Module.layer,
      SecurityModule.layer,
      ShaclApiModule.layer,
      ShaclModule.layer,
      StandoffResponderV2.layer,
      StandoffTagUtilV2Live.layer,
      State.layer,
      StringFormatter.live,
      TapirToPekkoInterpreter.layer,
      TestClientService.layer,
      TestDspIngestClient.layer,
      TriplestoreServiceLive.layer,
      ValuesResponderV2.layer,
    )
  }
}
