/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.repo.impl

import zio._
import zio.stm.TMap

import java.util.UUID

import dsp.project.api.ProjectRepo
import dsp.project.domain.Project
import dsp.valueobjects.Project._
import dsp.valueobjects.ProjectId

/**
 * Project repo test implementation. Mocks the project repo for tests.
 *
 * @param projects                   a map of project UUIDs to projects (UUID -> Project).
 * @param lookupTableShortCodeToUuid a map of shortcodes to projects (shortCode -> UUID).
 */
final case class ProjectRepoMock(
  projects: TMap[UUID, Project],
  lookupTableShortCodeToUuid: TMap[ShortCode, UUID]
) extends ProjectRepo {

  /**
   * @inheritDoc
   */
  override def storeProject(project: Project): UIO[ProjectId] =
    (for {
      _ <- projects.put(project.id.uuid, project)
      _ <- lookupTableShortCodeToUuid.put(project.id.shortCode, project.id.uuid)
    } yield project.id).commit.tap(id => ZIO.logInfo(s"Stored project: ${id.uuid}"))

  /**
   * @inheritDoc
   */
  override def getProjects(): UIO[List[Project]] =
    projects.values.commit.tap(projectList => ZIO.logInfo(s"Looked up all projects, found ${projectList.size}"))

  /**
   * @inheritDoc
   */
  override def getProjectById(id: ProjectId): IO[Option[Nothing], Project] =
    projects
      .get(id.uuid)
      .commit
      .some
      .tapBoth(
        _ => ZIO.logInfo(s"Could not find project with UUID '${id.uuid}'"),
        _ => ZIO.logInfo(s"Looked up project by UUID '${id.uuid}'")
      )

  /**
   * @inheritDoc
   */
  override def getProjectByShortCode(shortCode: ShortCode): IO[Option[Nothing], Project] =
    (for {
      uuid    <- lookupTableShortCodeToUuid.get(shortCode).some
      project <- projects.get(uuid).some
    } yield project).commit.tapBoth(
      _ => ZIO.logInfo(s"Couldn't find project with shortCode '${shortCode}'"),
      _ => ZIO.logInfo(s"Looked up project by shortCode '${shortCode}'")
    )

  /**
   * @inheritDoc
   */
  override def checkIfShortCodeIsAvailable(shortCode: ShortCode): IO[Option[Nothing], Unit] =
    (for {
      exists <- lookupTableShortCodeToUuid.contains(shortCode).commit
      _ <- if (exists) ZIO.fail(None) // project shortcode does exist
           else ZIO.succeed(()) // project shortcode does not exist
    } yield ()).tapBoth(
      _ => ZIO.logInfo(s"Checked for project with shortCode '$shortCode', project not found."),
      uuid => ZIO.logInfo(s"Checked for project with shortCode '$shortCode', found project with UUID '$uuid'.")
    )

  /**
   * @inheritDoc
   */
  override def deleteProject(id: ProjectId): IO[Option[Nothing], ProjectId] =
    (for {
      _ <- projects.get(id.uuid).some
      _ <- projects.delete(id.uuid)                        // removes the values (Project) for the key (UUID)
      _ <- lookupTableShortCodeToUuid.delete(id.shortCode) // remove the project also from the lookup table
    } yield id).commit.tapBoth(
      _ => ZIO.logDebug(s"Did not delete project '${id.uuid}' because it was not in the repository"),
      _ => ZIO.logDebug(s"Deleted project: ${id.uuid}")
    )

  /**
   * Additional method for the test implementation of ProjectRepo.
   * Adds a variable amount of Projects to the Repo
   *
   * @param pp a various length parameter of Project value objects that should be added to the repo as initialization
   */
  def initializeRepo(pp: Project*) = for {
    ids <- ZIO.collectAll(pp.map(storeProject(_)))
  } yield ids

}

object ProjectRepoMock {
  val layer: ZLayer[Any, Nothing, ProjectRepoMock] =
    ZLayer {
      for {
        projects <- TMap.empty[UUID, Project].commit
        lookUp   <- TMap.empty[ShortCode, UUID].commit
      } yield ProjectRepoMock(projects, lookUp)
    }.tap(_ => ZIO.logInfo(">>> In-memory project repository initialized <<<"))
}
