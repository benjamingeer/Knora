/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin

import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.AdminDomainModule
import org.knora.webapi.slice.admin.repo.AdminRepoModule
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService

object AdminModule {

  type Dependencies =
    AppConfig & IriService & OntologyRepo & PredicateObjectMapper & TriplestoreService

  type Provided = AdminDomainModule.Provided

  val layer: ZLayer[Dependencies, Nothing, Provided] = AdminRepoModule.layer >>> AdminDomainModule.layer
}
