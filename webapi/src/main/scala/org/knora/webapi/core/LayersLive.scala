/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko.actor.ActorSystem
import zio.*
import zio.ULayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfig.AppConfigurations
import org.knora.webapi.config.InstrumentationServerConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.search.QueryTraverser
import org.knora.webapi.messages.util.search.gravsearch.transformers.OntologyInferencer
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionRunner
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2Live
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.*
import org.knora.webapi.responders.admin.ListsResponder
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
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
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
import org.knora.webapi.slice.ontology.domain.service.OntologyServiceLive
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheLive
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.ontology.repo.service.PredicateRepositoryLive
import org.knora.webapi.slice.resourceinfo.ResourceInfoLayers
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resources.repo.service.ResourcesRepoLive
import org.knora.webapi.slice.search.api.SearchApiRoutes
import org.knora.webapi.slice.search.api.SearchEndpoints
import org.knora.webapi.slice.security.SecurityModule
import org.knora.webapi.slice.security.api.AuthenticationApiModule
import org.knora.webapi.slice.shacl.ShaclModule
import org.knora.webapi.slice.shacl.api.ShaclApiModule
import org.knora.webapi.slice.shacl.api.ShaclApiRoutes
import org.knora.webapi.store.iiif.IIIFRequestMessageHandler
import org.knora.webapi.store.iiif.IIIFRequestMessageHandlerLive
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.impl.SipiServiceLive
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater

object LayersLive {

  /**
   * The `Environment` that we require to exist at startup.
   */
  type DspEnvironmentLive =
    // format: off
    ActorSystem &
    AdminApiEndpoints &
    AdminModule.Provided &
    ApiComplexV2JsonLdRequestParser &
    ApiRoutes &
    ApiV2Endpoints &
    AppConfigurations &
    AssetPermissionsResponder &
    AuthorizationRestService &
    AuthenticationApiModule.Provided &
    CardinalityHandler &
    ConstructResponseUtilV2 &
    DefaultObjectAccessPermissionService &
    GroupRestService &
    HttpServer &
    IIIFRequestMessageHandler &
    InfrastructureModule.Provided &
    InstrumentationServerConfig &
    IriConverter &
    ListsApiModule.Provided &
    ListsResponder &
    ListsService &
    MessageRelay &
    OntologyCache &
    OntologyCacheHelpers &
    OntologyInferencer &
    OntologyApiModule.Provided &
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
    ResourcesResponderV2 &
    RestCardinalityService &
    SecurityModule.Provided &
    SearchApiRoutes &
    SearchResponderV2Module.Provided &
    ShaclApiModule.Provided &
    ShaclModule.Provided &
    SipiService &
    StandoffResponderV2 &
    StandoffTagUtilV2 &
    State &
    StringFormatter &
    TriplestoreService &
    UserRestService &
    ValuesResponderV2
    // format: on

  /**
   * All effect layers needed to provide the `Environment`
   */
  val dspLayersLive: ULayer[DspEnvironmentLive] =
    ZLayer.make[DspEnvironmentLive](
      AdminApiModule.layer,
      AdminModule.layer,
      ApiComplexV2JsonLdRequestParser.layer,
      ApiRoutes.layer,
      ApiV2Endpoints.layer,
      AppConfig.layer,
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
      PekkoActorSystem.layer,
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
      SecurityModule.layer,
      SearchApiRoutes.layer,
      SearchEndpoints.layer,
      SearchResponderV2Module.layer,
      ShaclApiModule.layer,
      ShaclModule.layer,
      SipiServiceLive.layer,
      StandoffResponderV2.layer,
      StandoffTagUtilV2Live.layer,
      State.layer,
      StringFormatter.live,
      TapirToPekkoInterpreter.layer,
      TriplestoreServiceLive.layer,
      ValuesResponderV2.layer,
      // ZLayer.Debug.mermaid,
    )
}
