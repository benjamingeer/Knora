/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko
import zio.*

import org.knora.sipi.SipiServiceTestDelegator
import org.knora.sipi.WhichSipiService
import org.knora.webapi.config.AppConfig.AppConfigurations
import org.knora.webapi.config.AppConfig.AppConfigurationsTest
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.config.JwtConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.search.QueryTraverser
import org.knora.webapi.messages.util.search.gravsearch.prequery.InferenceOptimizationService
import org.knora.webapi.messages.util.search.gravsearch.transformers.OntologyInferencer
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionRunner
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2Live
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.*
import org.knora.webapi.responders.v2.*
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.OntologyCacheHelpersLive
import org.knora.webapi.responders.v2.ontology.OntologyTriplestoreHelpers
import org.knora.webapi.responders.v2.ontology.OntologyTriplestoreHelpersLive
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
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.infrastructure.JwtServiceLive
import org.knora.webapi.slice.infrastructure.api.ManagementEndpoints
import org.knora.webapi.slice.infrastructure.api.ManagementRoutes
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.ontology.api.service.RestCardinalityServiceLive
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.domain.service.OntologyServiceLive
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheLive
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.resourceinfo.ResourceInfoLayers
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resources.repo.service.ResourcesRepo
import org.knora.webapi.slice.resources.repo.service.ResourcesRepoLive
import org.knora.webapi.slice.search.api.SearchApiRoutes
import org.knora.webapi.slice.search.api.SearchEndpoints
import org.knora.webapi.store.iiif.IIIFRequestMessageHandler
import org.knora.webapi.store.iiif.IIIFRequestMessageHandlerLive
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.testcontainers.DspIngestTestContainer
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testcontainers.SharedVolumes
import org.knora.webapi.testcontainers.SipiTestContainer
import org.knora.webapi.testservices.TestClientService

object LayersTest {

  /**
   * The `Environment`s that we require for the tests to run - with or without Sipi
   */
  type DefaultTestEnvironmentWithoutSipi =
    LayersLive.DspEnvironmentLive & FusekiTestContainer & TestClientService

  type DefaultTestEnvironmentWithSipi =
    DefaultTestEnvironmentWithoutSipi & SipiTestContainer & DspIngestTestContainer & SharedVolumes.Images

  type CommonR0 =
    pekko.actor.ActorSystem & AppConfigurationsTest & JwtConfig & WhichSipiService

  type CommonR =
    // format: off
    AdminApiEndpoints &
    AdminModule.Provided &
    ApiRoutes &
    ApiV2Endpoints &
    AppRouter &
    AssetPermissionsResponder &
    Authenticator &
    AuthorizationRestService &
    CardinalityHandler &
    ConstructResponseUtilV2 &
    DspIngestClient &
    GravsearchTypeInspectionRunner &
    GroupRestService &
    HttpServer &
    IIIFRequestMessageHandler &
    InferenceOptimizationService &
    InvalidTokenCache &
    IriConverter &
    JwtService &
    ListsResponder &
    ListsResponderV2 &
    MessageRelay &
    OntologyCache &
    OntologyCacheHelpers &
    OntologyInferencer &
    OntologyRepo &
    OntologyResponderV2 &
    OntologyTriplestoreHelpers &
    PermissionRestService &
    PermissionUtilADM &
    PermissionsResponder &
    ProjectExportService &
    ProjectExportStorageService &
    ProjectImportService &
    ProjectRestService &
    RepositoryUpdater &
    ResourceUtilV2 &
    ResourcesRepo &
    ResourcesResponderV2 &
    RestCardinalityService &
    SearchApiRoutes &
    SearchResponderV2Module.Provided &
    SipiService &
    StandoffResponderV2 &
    StandoffTagUtilV2 &
    State &
    StringFormatter &
    TestClientService &
    TriplestoreService &
    UserRestService &
    ValuesResponderV2
  // format: on

  private val commonLayersForAllIntegrationTests =
    ZLayer.makeSome[CommonR0, CommonR](
      AdminApiModule.layer,
      AdminModule.layer,
      ApiRoutes.layer,
      ApiV2Endpoints.layer,
      AppRouter.layer,
      AssetPermissionsResponder.layer,
      AuthenticatorLive.layer,
      AuthorizationRestService.layer,
      BaseEndpoints.layer,
      CardinalityHandler.layer,
      CardinalityService.layer,
      ConstructResponseUtilV2Live.layer,
      DspIngestClientLive.layer,
      HandlerMapper.layer,
      HttpServer.layer,
      IIIFRequestMessageHandlerLive.layer,
      InferenceOptimizationService.layer,
      InvalidTokenCache.layer,
      IriConverter.layer,
      IriService.layer,
      JwtServiceLive.layer,
      KnoraResponseRenderer.layer,
      ListsResponder.layer,
      ListsResponderV2.layer,
      ManagementEndpoints.layer,
      ManagementRoutes.layer,
      MessageRelayLive.layer,
      OntologyCacheLive.layer,
      OntologyCacheHelpersLive.layer,
      OntologyRepoLive.layer,
      OntologyResponderV2Live.layer,
      OntologyServiceLive.layer,
      OntologyTriplestoreHelpersLive.layer,
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
      ResourcesRepoLive.layer,
      ResourcesResponderV2.layer,
      RestCardinalityServiceLive.layer,
      SearchApiRoutes.layer,
      SearchEndpoints.layer,
      SearchResponderV2Module.layer,
      SipiServiceTestDelegator.layer,
      StandoffResponderV2.layer,
      StandoffTagUtilV2Live.layer,
      State.layer,
      StringFormatter.live,
      TapirToPekkoInterpreter.layer,
      TestClientService.layer,
      TriplestoreServiceLive.layer,
      ValuesResponderV2Live.layer,
    )

  private val fusekiAndSipiTestcontainers =
    ZLayer.make[
      AppConfigurations & DspIngestTestContainer & FusekiTestContainer & SharedVolumes.Images & SipiTestContainer &
        WhichSipiService,
    ](
      AppConfigForTestContainers.testcontainers,
      DspIngestTestContainer.layer,
      FusekiTestContainer.layer,
      SipiTestContainer.layer,
      SharedVolumes.Images.layer,
      WhichSipiService.live,
    )

  private val fusekiTestcontainers =
    ZLayer.make[FusekiTestContainer & AppConfigurations & WhichSipiService](
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      FusekiTestContainer.layer,
      WhichSipiService.mock,
    )

  /**
   * Provides a layer for integration tests which depend on Fuseki as Testcontainers.
   * Sipi/IIIFService will be mocked with the [[SipiServiceMock]]
   *
   * @param system An optional [[pekko.actor.ActorSystem]] for use with Akka's [[pekko.testkit.TestKit]]
   * @return a [[ULayer]] with the [[DefaultTestEnvironmentWithoutSipi]]
   */
  def integrationTestsWithFusekiTestcontainers(
    system: Option[pekko.actor.ActorSystem] = None,
  ): ULayer[DefaultTestEnvironmentWithoutSipi] = {
    // Due to bug in Scala 2 compiler invoking methods with by-name parameters in provide/provideSome method does not work
    // assign the layer to a temp val and use it in the ZLayer.make
    val temp = system.map(ActorSystemTest.layer).getOrElse(ActorSystem.layer)
    ZLayer.make[DefaultTestEnvironmentWithoutSipi](
      commonLayersForAllIntegrationTests,
      fusekiTestcontainers,
      temp,
    )
  }

  /**
   * Provides a layer for integration tests which depend on Fuseki and Sipi as Testcontainers.
   * @return a [[ULayer]] with the [[DefaultTestEnvironmentWithSipi]]
   */
  val integrationTestsWithSipiAndFusekiTestcontainers: ULayer[DefaultTestEnvironmentWithSipi] =
    ZLayer.make[DefaultTestEnvironmentWithSipi](
      commonLayersForAllIntegrationTests,
      fusekiAndSipiTestcontainers,
      ActorSystem.layer,
    )
}
