/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.ULayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfig.AppConfigurations
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
import org.knora.webapi.slice.admin.api.*
import org.knora.webapi.slice.admin.api.service.MaintenanceRestService
import org.knora.webapi.slice.admin.api.service.ProjectADMRestService
import org.knora.webapi.slice.admin.api.service.ProjectsADMRestServiceLive
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
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
import org.knora.webapi.slice.resourceinfo.api.service.RestResourceInfoServiceLive
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.cache.CacheServiceRequestMessageHandler
import org.knora.webapi.store.cache.CacheServiceRequestMessageHandlerLive
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFRequestMessageHandler
import org.knora.webapi.store.iiif.IIIFRequestMessageHandlerLive
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater

object LayersLive {

  /**
   * The `Environment` that we require to exist at startup.
   */
  type DspEnvironmentLive =
    ActorSystem & ApiRoutes & AppConfigurations & AppRouter & Authenticator & CacheService &
      CacheServiceRequestMessageHandler & CardinalityHandler & CardinalityService & ConstructResponseUtilV2 &
      ConstructTransformer & GravsearchTypeInspectionRunner & GroupsResponderADM & HttpServer &
      IIIFRequestMessageHandler & IIIFService & InferenceOptimizationService & IriService & IriConverter & JwtService &
      KnoraProjectRepo & ListsResponderADM & ListsResponderV2 & MessageRelay & OntologyCache & OntologyHelpers &
      OntologyRepo & OntologyResponderV2 & PermissionUtilADM & PermissionsResponderADM & PredicateObjectMapper &
      ProjectADMRestService & ProjectADMService & ProjectExportService & ProjectExportStorageService &
      ProjectImportService & ProjectsResponderADM & QueryTraverser & RepositoryUpdater & ResourceUtilV2 &
      ResourceUtilV2 & ResourcesResponderV2 & RestCardinalityService & RestPermissionService & RestResourceInfoService &
      SearchResponderV2 & SipiResponderADM & OntologyInferencer & StandoffResponderV2 & StandoffTagUtilV2 & State &
      StoresResponderADM & StringFormatter & TriplestoreService & UsersResponderADM & ValuesResponderV2

  /**
   * All effect layers needed to provide the `Environment`
   */
  val dspLayersLive: ULayer[DspEnvironmentLive] =
    ZLayer.make[DspEnvironmentLive](
      ActorSystem.layer,
      AdminApiRoutes.layer,
      ApiRoutes.layer,
      AppConfig.layer,
      AppRouter.layer,
      AuthenticatorLive.layer,
      BaseEndpoints.layer,
      CacheServiceInMemImpl.layer,
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
      IIIFServiceSipiImpl.layer,
      InferenceOptimizationService.layer,
      IriConverter.layer,
      IriService.layer,
      JwtServiceLive.layer,
      KnoraProjectRepoLive.layer,
      ListsResponderADMLive.layer,
      ListsResponderV2Live.layer,
      MaintenanceEndpoints.layer,
      MaintenanceEndpointsHandlers.layer,
      MaintenanceRestService.layer,
      MaintenanceServiceLive.layer,
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
      ProjectADMServiceLive.layer,
      ProjectExportServiceLive.layer,
      ProjectExportStorageServiceLive.layer,
      ProjectImportServiceLive.layer,
      ProjectsADMRestServiceLive.layer,
      ProjectsEndpoints.layer,
      ProjectsEndpointsHandler.layer,
      ProjectsResponderADMLive.layer,
      QueryTraverser.layer,
      RepositoryUpdater.layer,
      ResourceInfoLayers.live,
      ResourceUtilV2Live.layer,
      ResourcesResponderV2Live.layer,
      RestCardinalityServiceLive.layer,
      RestPermissionServiceLive.layer,
      RestResourceInfoServiceLive.layer,
      SearchResponderV2Live.layer,
      SipiResponderADMLive.layer,
      StandoffResponderV2Live.layer,
      StandoffTagUtilV2Live.layer,
      State.layer,
      StoresResponderADMLive.layer,
      StringFormatter.live,
      TapirToPekkoInterpreter.layer,
      TriplestoreServiceLive.layer,
      UsersResponderADMLive.layer,
      ValuesResponderV2Live.layer
    )
}
