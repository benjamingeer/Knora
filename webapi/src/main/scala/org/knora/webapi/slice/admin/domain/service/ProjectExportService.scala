/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.apache.jena.graph.Triple
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.system.StreamRDF
import org.apache.jena.riot.system.StreamRDFBase
import org.apache.jena.riot.system.StreamRDFWriter
import org.apache.jena.sparql.core.Quad
import zio.Scope
import zio.Task
import zio.URLayer
import zio.ZIO
import zio.ZLayer
import zio.macros.accessible
import zio.nio.file.Files
import zio.nio.file.Path

import java.io.OutputStream
import scala.collection.mutable

import org.knora.webapi.messages.twirl
import org.knora.webapi.messages.util.rdf.TriG
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.AdminConstants.permissionsDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.util.ZScopedJavaIoStreams

@accessible
trait ProjectExportService {
  def exportProject(project: KnoraProject): Task[Path]

  /**
   * Exports a project to a file.
   * The file format is TriG.
   * The data exported is:
   *  * the project metadata
   *  * the project's permission data
   *  * the triples of the project's ontologies
   *
   * @param project the project to be exported
   * @return the [[Path]] to the file to which the project was exported
   */
  def exportProjectTriples(project: KnoraProject): Task[Path]

  /**
   * Exports a project to a file.
   * The file format is TriG.
   * The data exported is:
   * * the project metadata
   * * the project's permission data
   * * the triples of the project's ontologies
   *
   * @param project the project to be exported
   * @param targetFile the file to which the project is to be exported
   * @return the [[Path]] to the file to which the project was exported
   */
  def exportProjectTriples(project: KnoraProject, targetFile: Path): Task[Path]
}

/**
 * Represents a named graph to be saved to a TriG file.
 *
 * @param graphIri the IRI of the named graph.
 * @param tempDir  the directory in which the file is to be saved.
 */
private case class NamedGraphTrigFile(graphIri: InternalIri, tempDir: Path) {
  lazy val dataFile: Path = {
    val filename = graphIri.value.replaceAll("[.:/]", "_") + ".trig"
    tempDir / filename
  }
}

private object TriGCombiner {

  def combineTrigFiles(inputFiles: Iterable[Path], outputFile: Path): Task[Path] = ZIO.scoped {
    for {
      outFile   <- ZScopedJavaIoStreams.fileBufferedOutputStream(outputFile)
      outWriter <- createPrefixDedupStreamRDF(outFile).map { it => it.start(); it }
      _ <- ZIO.foreachDiscard(inputFiles)(file =>
             // Combine the files and write the output to the given OutputStream
             for {
               is <- ZScopedJavaIoStreams.fileInputStream(file)
               _  <- ZIO.attemptBlocking(RDFParser.source(is).lang(Lang.TRIG).parse(outWriter))
             } yield ()
           )
    } yield outputFile
  }

  private def createPrefixDedupStreamRDF(os: OutputStream): ZIO[Scope, Throwable, StreamRDF] = {
    def acquire                    = ZIO.attempt(StreamRDFWriter.getWriterStream(os, Lang.TRIG))
    def release(writer: StreamRDF) = ZIO.attempt(writer.finish()).logError.ignore
    ZIO.acquireRelease(acquire)(release).map(dedupPrefixStream)
  }

  // Define a custom StreamRDF implementation to filter out duplicate @prefix directives
  private def dedupPrefixStream(writer: StreamRDF): StreamRDF = new StreamRDFBase {
    private val prefixes = mutable.Set[String]()
    override def prefix(prefix: String, iri: String): Unit =
      if (!prefixes.contains(prefix)) {
        writer.prefix(prefix, iri)
        prefixes.add(prefix)
      }
    override def triple(triple: Triple): Unit = writer.triple(triple)
    override def quad(quad: Quad): Unit       = writer.quad(quad)
    override def base(base: String): Unit     = writer.base(base)
    override def finish(): Unit               = writer.finish()
    override def start(): Unit                = writer.start()
  }
}

final case class ProjectExportServiceLive(
  private val projectService: ProjectADMService,
  private val triplestoreService: TriplestoreService
) extends ProjectExportService {

  override def exportProjectTriples(project: KnoraProject): Task[Path] =
    Files
      .createTempDirectory(Some(project.shortname), fileAttributes = Nil)
      .map(trigExportFile(project, _))
      .flatMap(exportProjectTriples(project, _))

  private def trigExportFile(project: KnoraProject, tempDir: Path) = tempDir / s"${project.shortname}.trig"

  override def exportProjectTriples(project: KnoraProject, targetFile: Path): Task[Path] = ZIO.scoped {
    for {
      tempDir         <- Files.createTempDirectoryScoped(Some(project.shortname), fileAttributes = Nil)
      ontologyAndData <- downloadOntologyAndData(project, tempDir)
      adminData       <- downloadProjectAdminData(project, tempDir)
      permissionData  <- downloadPermissionData(project, tempDir)
      resultFile      <- mergeDataToFile(ontologyAndData :+ adminData :+ permissionData, targetFile)
    } yield resultFile
  }

  private def downloadOntologyAndData(project: KnoraProject, tempDir: Path): Task[List[NamedGraphTrigFile]] = for {
    allGraphsTrigFile <-
      projectService.getNamedGraphsForProject(project).map(_.map(NamedGraphTrigFile(_, tempDir)))
    files <- ZIO.foreach(allGraphsTrigFile)(file =>
               triplestoreService.sparqlHttpGraphFile(file.graphIri, file.dataFile, TriG).as(file)
             )
  } yield files

  private def downloadProjectAdminData(project: KnoraProject, tempDir: Path): Task[NamedGraphTrigFile] = {
    val graphIri = adminDataNamedGraph
    val file     = NamedGraphTrigFile(graphIri, tempDir)
    for {
      query <- ZIO.attempt(twirl.queries.sparql.admin.txt.getProjectAdminData(project.id.value))
      _     <- triplestoreService.sparqlHttpConstructFile(query.toString(), graphIri, file.dataFile, TriG)
    } yield file
  }

  private def downloadPermissionData(project: KnoraProject, tempDir: Path) = {
    val graphIri = permissionsDataNamedGraph
    val file     = NamedGraphTrigFile(graphIri, tempDir)
    for {
      query <- ZIO.attempt(twirl.queries.sparql.admin.txt.getProjectPermissions(project.id.value))
      _     <- triplestoreService.sparqlHttpConstructFile(query.toString(), graphIri, file.dataFile, TriG)
    } yield file
  }

  private def mergeDataToFile(allData: Seq[NamedGraphTrigFile], targetFile: Path): Task[Path] =
    TriGCombiner.combineTrigFiles(allData.map(_.dataFile), targetFile)

  override def exportProject(project: KnoraProject): Task[Path] = ZIO.scoped {
    for {
      exportDir  <- Files.createTempDirectory(Some(s"export-${project.shortname}"), fileAttributes = Nil)
      collectDir <- Files.createTempDirectoryScoped(Some(project.shortname), fileAttributes = Nil)
      _          <- exportProjectTriples(project, trigExportFile(project, collectDir))
      _          <- exportProjectAssets(project, collectDir)
      zipped     <- ZipUtility.zipFolder(collectDir, exportDir)
    } yield zipped
  }

  private def exportProjectAssets(project: KnoraProject, tempDir: Path) = {
    val exportedAssetsDir = tempDir / "assets"
    for {
      _ <- Files.createDirectory(exportedAssetsDir)
    } yield exportedAssetsDir
  }
}

object ProjectExportServiceLive {
  val layer: URLayer[ProjectADMService with TriplestoreService, ProjectExportService] =
    ZLayer.fromFunction(ProjectExportServiceLive.apply _)
}
