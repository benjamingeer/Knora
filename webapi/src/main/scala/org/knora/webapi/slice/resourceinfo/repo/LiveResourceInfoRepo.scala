package org.knora.webapi.slice.resourceinfo.repo

import org.knora.webapi.IRI
import org.knora.webapi.messages.twirl.queries.sparql.v2.txt.resourcesByCreationDate
import org.knora.webapi.messages.util.rdf.{SparqlSelectResult, VariableResultsRow}
import org.knora.webapi.slice.resourceinfo.repo
import org.knora.webapi.store.triplestore.api.TriplestoreService
import zio.{UIO, ZIO, ZLayer}

import java.time.Instant

final case class LiveResourceInfoRepo(ts: TriplestoreService) extends ResourceInfoRepo {

  override def findByProjectAndResourceClass(projectIri: IRI, resourceClass: IRI): UIO[List[ResourceInfo]] = {
    val query = resourcesByCreationDate(resourceClass, projectIri).toString
    ts.sparqlHttpSelect(query).map(toResourceInfoList)
  }

  private def toResourceInfoList(result: SparqlSelectResult): List[ResourceInfo] =
    result.results.bindings.map(toResourceInfo).toList

  private def toResourceInfo(row: VariableResultsRow): ResourceInfo = {
    val rowMap = row.rowMap
    repo.ResourceInfo(
      rowMap("resource"),
      Instant.parse(rowMap("creationDate")),
      rowMap.get("lastModificationDate").map(Instant.parse),
      rowMap("isDeleted").toBoolean
    )
  }
}

object LiveResourceInfoRepo {
  val layer: ZLayer[TriplestoreService, Nothing, LiveResourceInfoRepo] =
    ZLayer.fromZIO(ZIO.service[TriplestoreService].map(LiveResourceInfoRepo(_)))
}
