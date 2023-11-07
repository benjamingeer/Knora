/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.repo

import zio.Ref
import zio.Task
import zio.ULayer
import zio.ZLayer

import dsp.valueobjects.RestrictedViewSize
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortcodeIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortnameIdentifier
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.common.repo.AbstractInMemoryCrudRepository
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

final case class KnoraProjectRepoInMemory(projects: Ref[List[KnoraProject]])
    extends AbstractInMemoryCrudRepository[KnoraProject, InternalIri](projects, _.id)
    with KnoraProjectRepo {

  override def findById(id: ProjectIdentifierADM): Task[Option[KnoraProject]] = projects.get.map(
    _.find(id match {
      case ShortcodeIdentifier(shortcode) => _.shortcode == shortcode
      case ShortnameIdentifier(shortname) => _.shortname == shortname
      case IriIdentifier(iri)             => _.id.value == iri.value
    })
  )

  override def setProjectRestrictedViewSize(project: KnoraProject, size: RestrictedViewSize): Task[Unit] = ???
}

object KnoraProjectRepoInMemory {
  val layer: ULayer[KnoraProjectRepoInMemory] =
    ZLayer.fromZIO(Ref.make(List.empty[KnoraProject]).map(KnoraProjectRepoInMemory(_)))
}
