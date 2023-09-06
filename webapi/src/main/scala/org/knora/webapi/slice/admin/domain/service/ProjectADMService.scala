/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio._

import dsp.valueobjects.Project.Shortcode
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectKeywordsGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsKeywordsGetResponseADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

trait ProjectADMService {
  def findAll: Task[List[ProjectADM]]
  def findByProjectIdentifier(projectId: ProjectIdentifierADM): Task[Option[ProjectADM]]
  def findByShortname(shortname: String): Task[Option[ProjectADM]] =
    ProjectIdentifierADM.ShortnameIdentifier.fromString(shortname).fold(_ => ZIO.none, findByProjectIdentifier)
  def findAllProjectsKeywords: Task[ProjectsKeywordsGetResponseADM]
  def findProjectKeywordsBy(id: ProjectIdentifierADM): Task[Option[ProjectKeywordsGetResponseADM]]
  def getNamedGraphsForProject(project: KnoraProject): Task[List[InternalIri]]
}

object ProjectADMService {

  /**
   * Given the [[ProjectADM]] constructs the project's data named graph.
   *
   * @param project A [[ProjectADM]].
   * @return the [[InternalIri]] of the project's data named graph.
   */
  def projectDataNamedGraphV2(project: ProjectADM): InternalIri = {
    val shortcode = Shortcode
      .make(project.shortcode)
      .getOrElse(throw new IllegalArgumentException(s"Invalid project shortcode: ${project.shortcode}"))
    projectDataNamedGraphV2(shortcode, project.shortname)
  }

  /**
   * Given the [[KnoraProject]] constructs the project's data named graph.
   *
   * @param project A [[KnoraProject]].
   * @return the [[InternalIri]] of the project's data named graph.
   */
  def projectDataNamedGraphV2(project: KnoraProject): InternalIri =
    projectDataNamedGraphV2(project.shortcode, project.shortname)

  private def projectDataNamedGraphV2(shortcode: Shortcode, projectShortname: String) =
    InternalIri(s"${OntologyConstants.NamedGraphs.DataNamedGraphStart}/${shortcode.value}/$projectShortname")
}

final case class ProjectADMServiceLive(
  private val ontologyRepo: OntologyRepo,
  private val projectRepo: KnoraProjectRepo
) extends ProjectADMService {

  override def findAll: Task[List[ProjectADM]] = projectRepo.findAll().flatMap(ZIO.foreachPar(_)(toProjectADM))
  override def findByProjectIdentifier(projectId: ProjectIdentifierADM): Task[Option[ProjectADM]] =
    projectRepo.findById(projectId).flatMap(ZIO.foreach(_)(toProjectADM))

  private def toProjectADM(knoraProject: KnoraProject): Task[ProjectADM] =
    for {
      ontologyIris <- projectRepo.findOntologies(knoraProject)
    } yield ProjectADM(
      id = knoraProject.id.value,
      shortname = knoraProject.shortname,
      shortcode = knoraProject.shortcode.value,
      longname = knoraProject.longname,
      description = knoraProject.description,
      keywords = knoraProject.keywords,
      logo = knoraProject.logo,
      status = knoraProject.status,
      selfjoin = knoraProject.selfjoin,
      ontologies = ontologyIris.map(_.value)
    ).unescape

  override def findAllProjectsKeywords: Task[ProjectsKeywordsGetResponseADM] =
    for {
      projects <- projectRepo.findAll()
      keywords  = projects.flatMap(_.keywords).distinct.sorted
    } yield ProjectsKeywordsGetResponseADM(keywords)

  override def findProjectKeywordsBy(id: ProjectIdentifierADM): Task[Option[ProjectKeywordsGetResponseADM]] =
    for {
      projectMaybe <- projectRepo.findById(id)
      keywordsMaybe = projectMaybe.map(_.keywords)
      result        = keywordsMaybe.map(ProjectKeywordsGetResponseADM(_))
    } yield result

  override def getNamedGraphsForProject(project: KnoraProject): Task[List[InternalIri]] = {
    val projectGraph = ProjectADMService.projectDataNamedGraphV2(project)
    ontologyRepo
      .findByProject(project.id)
      .map(_.map(_.ontologyMetadata.ontologyIri.toInternalIri))
      .map(_ :+ projectGraph)
  }
}

object ProjectADMServiceLive {
  val layer: URLayer[OntologyRepo with KnoraProjectRepo, ProjectADMServiceLive] =
    ZLayer.fromFunction(ProjectADMServiceLive.apply _)
}
