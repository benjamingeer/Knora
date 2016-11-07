package org.knora.webapi.messages.v1.store.triplestoremessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.util.ErrorHandlingMap
import org.knora.webapi.{InconsistentTriplestoreDataException, TriplestoreResponseException}
import spray.json.{DefaultJsonProtocol, NullOptions, RootJsonFormat}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

sealed trait TriplestoreRequest

/**
  * Simple message for initial actor functionality.
  */
case class HelloTriplestore(txt: String) extends TriplestoreRequest

/**
  * Simple message for checking the connection to the triplestore.
  */
case object CheckConnection extends TriplestoreRequest

/**
  * Represents a SPARQL SELECT query to be sent to the triplestore.
  *
  * @param sparql       the SPARQL string.
  * @param useInference if `true`, ask the triplestore to use inference in the query, if possible. If the triplestore
  *                     is being accessed over HTTP, this is likely to mean setting a parameter in the HTTP request.
  *                     Note that with GraphDB, setting this to `false` is not sufficient to completely disable inference,
  *                     because it does not disable the `owl:sameAs` optimisation. To completely disable inference
  *                     for a query in GraphDB, you must include `FROM <http://www.ontotext.com/explicit>`
  *                     in the SPARQL.
  */
case class SparqlSelectRequest(sparql: String, useInference: Boolean = false) extends TriplestoreRequest

/**
  * Represents a response to a SPARQL SELECT query, containing a parsed representation of the response (JSON, etc.)
  * returned by the triplestore
  *
  * @param head    the header of the response, containing the variable names.
  * @param results the body of the response, containing rows of query results.
  */
case class SparqlSelectResponse(head: SparqlSelectResponseHeader, results: SparqlSelectResponseBody) {

    /**
      * Returns the contents of the first row of results.
      *
      * @return a [[Map]] representing the contents of the first row of results.
      */
    @throws[InconsistentTriplestoreDataException]("if the query returned no results.")
    def getFirstRow: VariableResultsRow = {
        if (results.bindings.isEmpty) {
            throw TriplestoreResponseException(s"A SPARQL query unexpectedly returned an empty result")
        }

        results.bindings.head
    }
}

/**
  * Represents the header of a JSON response to a SPARQL SELECT query.
  *
  * @param vars the names of the variables that were used in the SPARQL SELECT statement.
  */
case class SparqlSelectResponseHeader(vars: Seq[String])

/**
  * Represents the body of a JSON response to a SPARQL SELECT query.
  *
  * @param bindings the bindings of values to the variables used in the SPARQL SELECT statement.
  *                 Empty rows are not allowed.
  */
case class SparqlSelectResponseBody(bindings: Seq[VariableResultsRow]) {
    require(bindings.forall(_.rowMap.nonEmpty), "Empty rows are not allowed in a SparqlSelectResponseBody")
}


/**
  * Represents a row of results in a JSON response to a SPARQL SELECT query.
  *
  * @param rowMap a map of variable names to values in the row. An empty string is not allowed as a variable
  *               name or value.
  */
case class VariableResultsRow(rowMap: ErrorHandlingMap[String, String]) {
    require(rowMap.forall {
        case (key, value) => key.nonEmpty && value.nonEmpty
    }, "An empty string is not allowed as a variable name or value in a VariableResultsRow")
}

/*
 * Transaction management for SPARQL updates over HTTP is currently commented out because of
 * issue <https://github.com/dhlab-basel/Knora/issues/85>.
 */

/*

/**
  * Starts a new SPARQL update transaction.
  */
case class BeginUpdateTransaction() extends TriplestoreRequest

/**
  * Indicates that the specified transaction was begun.
  * @param transactionID the transaction ID.
  */
case class UpdateTransactionBegun(transactionID: UUID)

/**
  * Commits a SPARQL update transaction.
  * @param transactionID the transaction ID.
  */
case class CommitUpdateTransaction(transactionID: UUID) extends TriplestoreRequest

/**
  * Indicates that the specified transaction was committed.
  * @param transactionID the transaction ID.
  */
case class UpdateTransactionCommitted(transactionID: UUID)

/**
  * Rolls back an uncommitted SPARQL update transaction.
  * @param transactionID the transaction ID.
  */
case class RollbackUpdateTransaction(transactionID: UUID) extends TriplestoreRequest

/**
  * Indicates that the specified transaction was rolled back.
  * @param transactionID the transaction ID.
  */
case class UpdateTransactionRolledBack(transactionID: UUID)

/**
  * Represents a SPARQL Update operation to be entered as part of an update transaction.
  * @param transactionID the transaction ID.
  * @param sparql the SPARQL string.
  */
case class SparqlUpdateRequest(transactionID: UUID, sparql: String) extends TriplestoreRequest

/**
  * Indicates that the requested SPARQL Update was entered into the transaction..
  * @param transactionID the transaction ID.
  */
case class SparqlUpdateResponse(transactionID: UUID)

*/

/**
  * Represents a SPARQL Update operation to be performed.
  *
  * @param sparql the SPARQL string.
  */
case class SparqlUpdateRequest(sparql: String) extends TriplestoreRequest

/**
  * Indicates that the requested SPARQL Update was executed and returned no errors..
  */
case class SparqlUpdateResponse()

/**
  * Message for resetting the contents of the triplestore and loading a fresh set of data. The data needs to be
  * stored in an accessible path and supplied via the [[RdfDataObject]].
  *
  * @param rdfDataObjects contains a list of [[RdfDataObject]].
  */
case class ResetTriplestoreContent(rdfDataObjects: Seq[RdfDataObject]) extends TriplestoreRequest

/**
  * Sent as a response to [[ResetTriplestoreContent]] if the request was processed successfully.
  */
case class ResetTriplestoreContentACK()

/**
  * Message for removing all content from the triple store.
  */
case class DropAllTriplestoreContent() extends TriplestoreRequest

/**
  * Sent as a response to [[DropAllTriplestoreContent]] if the request was processed successfully.
  */
case class DropAllTriplestoreContentACK()

/**
  * Inserts data into the triplestore.
  *
  * @param rdfDataObjects contains a list of [[RdfDataObject]].
  */
case class InsertTriplestoreContent(rdfDataObjects: Seq[RdfDataObject]) extends TriplestoreRequest

/**
  * Sent as a response to [[InsertTriplestoreContent]] if the request was processed successfully.
  */
case class InsertTriplestoreContentACK()

/**
  * Initialize the triplestore. This will initiate the (re)creation of the repository and adding data to it.
  *
  * @param rdfDataObject contains a list of [[RdfDataObject]].
  */
case class InitTriplestore(rdfDataObject: RdfDataObject) extends TriplestoreRequest

/**
  * Initialization ((re)creation of repository and loading of data) is finished successfully.
  */
case class InitTriplestoreACK()

/**
  * Ask triplestore if it has finished initialization
  */
case class Initialized() extends TriplestoreRequest

/**
  * Response indicating whether the triplestore has finished initialization and is ready for processing messages
  *
  * @param initFinished indicates if actor initialization has finished
  */
case class InitializedResponse(initFinished: Boolean)


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Contains the path to the 'ttl' file and the name of the named graph it should be loaded in.
  *
  * @param path to the 'ttl' file
  * @param name of the named graph the data will be load into.
  */
case class RdfDataObject(path: String, name: String)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about resources and their properties.
  */
trait TriplestoreJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    implicit val rdfDataObjectFormat: RootJsonFormat[RdfDataObject] = jsonFormat2(RdfDataObject)
    implicit val resetTriplestoreContentFormat: RootJsonFormat[ResetTriplestoreContent] = jsonFormat1(ResetTriplestoreContent)

}