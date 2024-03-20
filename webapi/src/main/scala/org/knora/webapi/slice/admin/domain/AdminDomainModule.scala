/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain

import zio.ZLayer

import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.repo.AdminRepoModule
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.store.cache.CacheService
import org.knora.webapi.store.triplestore.api.TriplestoreService

object AdminDomainModule {

  type Dependencies =
    AdminRepoModule.Provided & CacheService & IriService & OntologyRepo & PasswordService & PredicateObjectMapper & TriplestoreService

  type Provided = KnoraGroupService & KnoraProjectService & KnoraUserService & MaintenanceService & ProjectService

  val layer = ZLayer.makeSome[Dependencies, Provided](
    KnoraGroupService.layer,
    KnoraProjectService.layer,
    KnoraUserService.layer,
    MaintenanceService.layer,
    ProjectService.layer,
  )
}
