package org.knora.webapi.slice.resourceinfo.repo

import org.knora.webapi.IRI

import java.time.Instant

case class ResourceInfo(
  iri: IRI,
  creationDate: Instant,
  lastModificationDate: Instant,
  deleteDate: Option[Instant],
  isDeleted: Boolean
)
object ResourceInfo {
  def apply(iri: IRI, creationDate: Instant, lastModificationDate: Instant): ResourceInfo =
    ResourceInfo(iri, creationDate, lastModificationDate, deleteDate = None, isDeleted = false)
  def apply(iri: IRI, creationDate: Instant, lastModificationDate: Instant, deleteDate: Instant): ResourceInfo =
    ResourceInfo(iri, creationDate, lastModificationDate, Some(deleteDate), isDeleted = true)
}
