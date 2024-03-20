/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko
import zio.*

import org.knora.webapi.config.AppConfig.AppConfigurations
import org.knora.webapi.config.AppConfig.AppConfigurationsTest
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.search.QueryTraverser
import org.knora.webapi.messages.util.search.gravsearch.prequery.InferenceOptimizationService
import org.knora.webapi.messages.util.search.gravsearch.transformers.ConstructTransformer
import org.knora.webapi.messages.util.search.gravsearch.transformers.OntologyInferencer
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionRunner
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2Live
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.*
import org.knora.webapi.responders.v2.*
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.CardinalityHandlerLive
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.responders.v2.ontology.OntologyHelpersLive
import org.knora.webapi.routing.*
import org.knora.webapi.slice.admin.AdminModule
import org.knora.webapi.slice.admin.api.*
import org.knora.webapi.slice.admin.api.AdminApiModule
import org.knora.webapi.slice.admin.api.service.PermissionsRestService
import org.knora.webapi.slice.admin.api.service.UsersRestService
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.infrastructure.api.ManagementEndpoints
import org.knora.webapi.slice.infrastructure.api.ManagementRoutes
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.ontology.api.service.RestCardinalityServiceLive
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheLive
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.resourceinfo.ResourceInfoLayers
import org.knora.webapi.slice.resourceinfo.api.service.RestResourceInfoService
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.search.api.SearchApiRoutes
import org.knora.webapi.slice.search.api.SearchEndpoints
import org.knora.webapi.store.cache.CacheService
import org.knora.webapi.store.cache.CacheServiceRequestMessageHandler
import org.knora.webapi.store.cache.CacheServiceRequestMessageHandlerLive
import org.knora.webapi.store.iiif.IIIFRequestMessageHandler
import org.knora.webapi.store.iiif.IIIFRequestMessageHandlerLive
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.impl.SipiServiceLive
import org.knora.webapi.store.iiif.impl.SipiServiceMock
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
  type DefaultTestEnvironmentWithoutSipi = LayersLive.DspEnvironmentLive with FusekiTestContainer with TestClientService
  type DefaultTestEnvironmentWithSipi = DefaultTestEnvironmentWithoutSipi
    with SipiTestContainer
    with DspIngestTestContainer
    with SharedVolumes.Images

  type CommonR0 = pekko.actor.ActorSystem
    with AppConfigurationsTest
    with JwtService
    with SipiService
    with StringFormatter
  type CommonR =
    ApiRoutes
      with AdminApiEndpoints
      with ApiV2Endpoints
      with AppRouter
      with AssetPermissionsResponder
      with Authenticator
      with AuthorizationRestService
      with CacheServiceRequestMessageHandler
      with CardinalityHandler
      with CardinalityService
      with ConstructResponseUtilV2
      with ConstructTransformer
      with DspIngestClient
      with GravsearchTypeInspectionRunner
      with GroupsResponderADM
      with HttpServer
      with IIIFRequestMessageHandler
      with InferenceOptimizationService
      with IriConverter
      with IriService
      with KnoraUserService
      with ListsResponder
      with ListsResponderV2
      with MessageRelay
      with OntologyCache
      with OntologyHelpers
      with OntologyInferencer
      with OntologyRepo
      with OntologyResponderV2
      with PermissionUtilADM
      with PermissionsResponderADM
      with PermissionsRestService
      with PredicateObjectMapper
      with ProjectExportService
      with ProjectExportStorageService
      with ProjectImportService
      with ProjectService
      with ProjectsResponderADM
      with QueryTraverser
      with RepositoryUpdater
      with ResourceUtilV2
      with ResourcesResponderV2
      with RestCardinalityService
      with RestResourceInfoService
      with SearchApiRoutes
      with SearchResponderV2
      with StandoffResponderV2
      with StandoffTagUtilV2
      with State
      with TestClientService
      with TriplestoreService
      with UserService
      with UsersResponder
      with UsersRestService
      with ValuesResponderV2

  private val commonLayersForAllIntegrationTests =
    ZLayer.makeSome[CommonR0, CommonR](
      AdminModule.layer,
      AdminApiModule.layer,
      ApiRoutes.layer,
      ApiV2Endpoints.layer,
      AppRouter.layer,
      AssetPermissionsResponder.layer,
      AuthenticatorLive.layer,
      AuthorizationRestServiceLive.layer,
      BaseEndpoints.layer,
      CacheService.layer,
      CacheServiceRequestMessageHandlerLive.layer,
      CardinalityHandlerLive.layer,
      CardinalityService.layer,
      ConstructResponseUtilV2Live.layer,
      ConstructTransformer.layer,
      DspIngestClientLive.layer,
      GravsearchTypeInspectionRunner.layer,
      GroupsResponderADMLive.layer,
      HandlerMapper.layer,
      HttpServer.layer,
      IIIFRequestMessageHandlerLive.layer,
      InferenceOptimizationService.layer,
      IriConverter.layer,
      IriService.layer,
      KnoraResponseRenderer.layer,
      KnoraUserToUserConverter.layer,
      ListsResponder.layer,
      ListsResponderV2.layer,
      ManagementRoutes.layer,
      MessageRelayLive.layer,
      OntologyCacheLive.layer,
      OntologyHelpersLive.layer,
      OntologyInferencer.layer,
      OntologyRepoLive.layer,
      OntologyResponderV2Live.layer,
      PermissionUtilADMLive.layer,
      PermissionsResponderADMLive.layer,
      PredicateObjectMapper.layer,
      PredicateRepositoryLive.layer,
      ProjectExportServiceLive.layer,
      ProjectExportStorageServiceLive.layer,
      ProjectImportServiceLive.layer,
      ProjectsResponderADMLive.layer,
      QueryTraverser.layer,
      RepositoryUpdater.layer,
      ResourceInfoLayers.live,
      ResourceUtilV2Live.layer,
      ResourcesResponderV2Live.layer,
      RestCardinalityServiceLive.layer,
      SearchApiRoutes.layer,
      SearchEndpoints.layer,
      SearchResponderV2Live.layer,
      StandoffResponderV2Live.layer,
      StandoffTagUtilV2Live.layer,
      State.layer,
      TapirToPekkoInterpreter.layer,
      TestClientService.layer,
      TriplestoreServiceLive.layer,
      UserService.layer,
      UsersResponder.layer,
      ValuesResponderV2Live.layer,
      ManagementEndpoints.layer,
    )

  private val fusekiAndSipiTestcontainers =
    ZLayer.make[
      AppConfigurations
        with DspIngestTestContainer
        with FusekiTestContainer
        with JwtService
        with SharedVolumes.Images
        with SipiService
        with SipiTestContainer
        with StringFormatter,
    ](
      AppConfigForTestContainers.testcontainers,
      DspIngestClientLive.layer,
      DspIngestTestContainer.layer,
      FusekiTestContainer.layer,
      SipiTestContainer.layer,
      SipiServiceLive.layer,
      JwtServiceLive.layer,
      SharedVolumes.Images.layer,
      StringFormatter.test,
    )

  private val fusekiTestcontainers =
    ZLayer.make[FusekiTestContainer with AppConfigurations with JwtService with SipiService with StringFormatter](
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      FusekiTestContainer.layer,
      SipiServiceMock.layer,
      JwtServiceLive.layer,
      StringFormatter.test,
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
