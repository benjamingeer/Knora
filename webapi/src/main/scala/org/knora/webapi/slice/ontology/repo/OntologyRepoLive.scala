package org.knora.webapi.slice.ontology.repo
import zio.Task
import zio.ZLayer

import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

final case class OntologyRepoLive(private val converter: IriConverter, private val ontologyCache: OntologyCache)
    extends OntologyRepo {

  override def findOntologyBy(iri: InternalIri): Task[Option[ReadOntologyV2]] =
    converter.asSmartIri(iri).flatMap(findOntologyBy)

  private def findOntologyBy(ontologyIri: SmartIri): Task[Option[ReadOntologyV2]] =
    ontologyCache.get.map(_.ontologies.get(ontologyIri))

  override def findClassBy(iri: InternalIri): Task[Option[ReadClassInfoV2]] = for {
    ontologyIri <- converter.getOntologyIriFromClassIri(iri)
    ontology    <- findOntologyBy(ontologyIri)
    classIri    <- converter.asSmartIri(iri)
  } yield ontology.flatMap(_.classes.get(classIri))
}

object OntologyRepo {
  val layer: ZLayer[IriConverter with OntologyCache, Nothing, OntologyRepoLive] =
    ZLayer.fromFunction(OntologyRepoLive.apply _)
}
