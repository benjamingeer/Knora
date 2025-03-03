/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.errors

import com.typesafe.scalalogging.Logger
import org.apache.commons.lang3.SerializationException
import org.apache.commons.lang3.SerializationUtils
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.slice.admin.domain.service.KnoraUserService.Errors.UserServiceError

/*

    How to use and extend these exceptions
    ======================================

    Two kinds of exceptions
    -----------------------

    1. Exceptions extending RequestRejectedException mean that the problem is the client's fault. They are just
       reported to the client.

    2. Exceptions extending InternalServerException mean that the problem is not the client's fault. They are
       reported to the client and are also logged (e.g. to the console). When thrown in an actor, they are also
       escalated to the actor's supervisor.

    Error-handling tools
    --------------------

    In an actor, have the receive method handle each different incoming message type by calling some private method that
    returns a Future. The receive method can then pass this object to ActorUtil.future2Message, which takes care of
    reporting exceptions as described above.

    If you extract data from a Map, and there's any chance some required data might be missing, wrap your Map in
    an ErrorHandlingMap, which takes some of the work out of checking for missing values and throwing helpful
    exceptions.

    Adding new exception classes
    ----------------------------

    Only the leaf nodes in the tree of exception classes can be case classes, because case classes can't be subclassed.

    All our exceptions should extend the trait KnoraException. This declares them Serializable, and ensures that they
    also extend Exception.

    The constructor of every exception should, at minimum, take a message: String argument, and pass it
    up the class hierarchy to the constructor of Exception. It will then appear in logs and in the string returned by
    the exception's toString method.

    Your exception's constructor can also take a nested exception argument representing the original cause of the
    error, which should also be passed up the class hierarchy to the constructor of Exception. Then you'll get nice
    chained stack traces in your logs. However, you need to ensure that the nested exception is serializable, otherwise
    your Actor won't be able to report it. Use ExceptionUtil.logAndWrapIfNotSerializable for this. See
    TriplestoreConnectionException for an example.

 */

/**
 * A trait implemented by all Knora exceptions, which must be serializable and extend [[java.lang.Exception]].
 */
trait KnoraException extends Serializable

/**
 * An abstract base class for exceptions indicating that something about a request made it impossible to fulfil (e.g.
 * it was malformed or referred to nonexistent data).
 *
 * @param msg   a description of the error.
 * @param cause the cause of the error.
 */
abstract class RequestRejectedException(msg: String, cause: Throwable = null)
    extends Exception(msg, cause)
    with KnoraException

object RequestRejectedException {
  // So we can match instances of RequestRejectedException, even though it's an abstract class
  def unapply(e: RequestRejectedException): Option[RequestRejectedException] = Option(e)
}

/**
 * An exception indicating that the request parameters did not make sense.
 *
 * @param message a description of the error.
 */
final case class BadRequestException(message: String) extends RequestRejectedException(message)
object BadRequestException {
  def apply(e: Throwable): BadRequestException        = BadRequestException(e.getMessage)
  def apply(e: UserServiceError): BadRequestException = BadRequestException(e.message)
  def invalidQueryParamValue(key: String): BadRequestException =
    BadRequestException(s"Invalid value for query parameter '$key'")
  def missingQueryParamValue(key: String): BadRequestException =
    BadRequestException(s"Missing query parameter '$key'")

  implicit val codec: JsonCodec[BadRequestException] = DeriveJsonCodec.gen[BadRequestException]
}

/**
 * An exception indicating that a user has provided bad credentials.
 *
 * @param message a description of the error.
 */
case class BadCredentialsException(message: String) extends RequestRejectedException(message)

object BadCredentialsException {
  implicit val codec: JsonCodec[BadCredentialsException] = DeriveJsonCodec.gen[BadCredentialsException]
}

/**
 * An exception indicating that a user has made a request for which the user lacks the necessary permission.
 *
 * @param message a description of the error.
 */
case class ForbiddenException(message: String) extends RequestRejectedException(message)

object ForbiddenException {
  implicit val codec: JsonCodec[ForbiddenException] = DeriveJsonCodec.gen[ForbiddenException]
}

/**
 * An exception indicating that the requested data was not found.
 *
 * @param message a description of the error.
 */
case class NotFoundException(message: String) extends RequestRejectedException(message)
object NotFoundException {
  val notFound: NotFoundException = NotFoundException("The requested data was not found")

  implicit val codec: JsonCodec[NotFoundException] = DeriveJsonCodec.gen[NotFoundException]
}

/**
 * An exception indicating that a requested update is not allowed because it would create a duplicate value.
 *
 * @param message a description of the error.
 */
case class DuplicateValueException(message: String = "Duplicate values are not permitted")
    extends RequestRejectedException(message)

object DuplicateValueException {
  implicit val codec: JsonCodec[DuplicateValueException] = DeriveJsonCodec.gen[DuplicateValueException]
}

/**
 * An exception indicating that a requested update is not allowed because it would violate an ontology constraint,
 * e.g. an `knora-base:objectClassConstraint` or an OWL cardinality restriction.
 *
 * @param message a description of the error.
 */
case class OntologyConstraintException(message: String) extends RequestRejectedException(message)
object OntologyConstraintException {
  implicit val codec: JsonCodec[OntologyConstraintException] = DeriveJsonCodec.gen[OntologyConstraintException]
}

/**
 * An exception indicating that a requested update is not allowed because another user has edited the
 * data that was to be updated.
 *
 * @param message a description of the error.
 */
case class EditConflictException(message: String) extends RequestRejectedException(message)

/**
 * An exception indicating that the submitted standoff is not valid.
 *
 * @param message a description of the error.
 */
case class InvalidStandoffException(message: String) extends RequestRejectedException(message)

/**
 * An exception indicating that an error occurred when converting standoff markup to or from another format.
 *
 * @param message a description of the error.
 */
case class StandoffConversionException(message: String) extends RequestRejectedException(message)

/**
 * An exception indicating that the Gravsearch query submitted to the API v2 search route was invalid.
 *
 * @param message a description of the error.
 */
case class GravsearchException(message: String) extends RequestRejectedException(message)
object GravsearchException {
  implicit val codec: JsonCodec[GravsearchException] = DeriveJsonCodec.gen[GravsearchException]
}

/**
 * An exception indication that the JSON-LD submitted to the API v2 was invalid.
 *
 * @param msg   a description of the error.
 * @param cause the cause for the error
 */
case class InvalidJsonLDException(msg: String, cause: Throwable = null) extends RequestRejectedException(msg, cause)

/**
 * An exception indication that the RDF submitted to the API v2 was invalid.
 *
 * @param msg   a description of the error.
 * @param cause the cause for the error
 */
case class InvalidRdfException(msg: String, cause: Throwable = null) extends RequestRejectedException(msg, cause)

/**
 * An exception indication that the validation of one or more values submitted to the API v2 failed.
 *
 * @param msg   a description of the error.
 */
case class ValidationException(msg: String) extends RequestRejectedException(msg)
object ValidationException {
  implicit val codec: JsonCodec[ValidationException] = DeriveJsonCodec.gen[ValidationException]
}

/**
 * An abstract class for exceptions indicating that something went wrong and it's not the client's fault.
 *
 * @param message a description of the error.
 * @param cause   the original exception representing the cause of the error, if any.
 */
abstract class InternalServerException(message: String, cause: Option[Throwable] = None)
    extends Exception(message, cause.orNull)
    with KnoraException

object InternalServerException {
  // So we can match instances of InternalServerException, even though it's an abstract class
  def unapply(e: InternalServerException): Option[InternalServerException] = Option(e)
}

/**
 * An exception indicating that during authentication something unexpected happened.
 *
 * @param message a description of the error.
 */
case class AuthenticationException(
  message: String = "Error during authentication. Please report this as a possible bug.",
  cause: Option[Throwable] = None,
) extends InternalServerException(message)

object AuthenticationException {
  def apply(message: String, e: Throwable, log: Logger): AuthenticationException =
    AuthenticationException(message, Some(ExceptionUtil.logAndWrapIfNotSerializable(e, log)))
}

/**
 * Indicates that data could not be converted from one format to another. This exception should not be thrown when
 * validating user input, but rather when processing input that has already been validated, or data that has been
 * loaded from the triplestore.
 *
 * @param message a description of the error.
 */
case class DataConversionException(message: String) extends InternalServerException(message)

/**
 * An exception indicating that during file upload there was an error.
 *
 * @param message a description of the error.
 */
case class FileUploadException(message: String = "Error during file upload. Please report this as a possible bug.")
    extends InternalServerException(message)

/**
 * An exception indicating that a requested update was not performed, although it was expected to succeed.
 * This probably indicates a bug.
 *
 * @param message a description of the error.
 */
case class UpdateNotPerformedException(
  message: String = "A requested update was not performed. Please report this as a possible bug.",
) extends InternalServerException(message)

/**
 * An exception indicating that an unsupported value was passed.
 * This probably indicates a bug.
 *
 * @param message a description of the error.
 */
case class UnsupportedValueException(
  message: String = "An unsupported value was given. Please report this as a possible bug.",
) extends InternalServerException(message)

/**
 * Indicates an internal server error in standoff-related processing.
 *
 * @param message a description of the error.
 * @param cause   the original exception representing the cause of the error, if any.
 */
case class StandoffInternalException(message: String, cause: Option[Throwable] = None)
    extends InternalServerException(message, cause)

object StandoffInternalException {
  def apply(message: String, e: Throwable, log: Logger): StandoffInternalException =
    StandoffInternalException(message, Some(ExceptionUtil.logAndWrapIfNotSerializable(e, log)))
}

/**
 * Indicates that something happened that should be impossible.
 *
 * @param message a description of the error.
 * @param cause   the original exception representing the cause of the error, if any.
 */
case class AssertionException(message: String, cause: Option[Throwable] = None)
    extends InternalServerException(message, cause)

object AssertionException {
  def apply(message: String, e: Throwable, log: Logger): AssertionException =
    AssertionException(message, Some(ExceptionUtil.logAndWrapIfNotSerializable(e, log)))
}

/**
 * Indicates an inconsistency in repository data.
 *
 * @param message a description of the error.
 */
case class InconsistentRepositoryDataException(message: String, cause: Option[Throwable] = None)
    extends InternalServerException(message, cause)

object InconsistentRepositoryDataException {
  def apply(message: String, e: Throwable, log: Logger): InconsistentRepositoryDataException =
    InconsistentRepositoryDataException(message, Some(ExceptionUtil.logAndWrapIfNotSerializable(e, log)))
}

case class MissingLastModificationDateOntologyException(message: String, cause: Option[Throwable] = None)
    extends InternalServerException(message, cause)

/**
 * Indicates that the API server generated invalid JSON in an API response.
 *
 * @param message a description of the error.
 * @param cause   the original exception representing the cause of the error, if any.
 */
case class InvalidApiJsonException(message: String, cause: Option[Throwable] = None)
    extends InternalServerException(message, cause)

object InvalidApiJsonException {
  def apply(message: String, e: Throwable, log: Logger): InvalidApiJsonException =
    InvalidApiJsonException(message, Some(ExceptionUtil.logAndWrapIfNotSerializable(e, log)))
}

/**
 * Indicates that during caching with [[org.knora.webapi.store.cache.api.CacheService]] something went wrong.
 *
 * @param message a description of the error.
 */
abstract class CacheServiceException(message: String) extends InternalServerException(message)

/**
 * Indicates that an application lock could not be acquired.
 *
 * @param message a description of the error.
 */
case class ApplicationLockException(message: String) extends InternalServerException(message)

/**
 * Indicates that an error occurred in transaction management.
 */
case class TransactionManagementException(message: String) extends InternalServerException(message)

/**
 * Indicates that an Akka actor received an unexpected message.
 *
 * @param message a description of the error.
 */
case class UnexpectedMessageException(message: String) extends InternalServerException(message)

/**
 * Indicates that an error occurred in the application's cache.
 *
 * @param message a description of the error.
 */
case class ApplicationCacheException(message: String) extends InternalServerException(message)

/**
 * Indicates that an error occurred during the generation of SPARQL query code.
 *
 * @param message a description of the error.
 */
case class SparqlGenerationException(message: String) extends InternalServerException(message)

/**
 * Indicates that an error occurred during the generation of client API code.
 *
 * @param message a description of the error.
 */
case class ClientApiGenerationException(message: String) extends InternalServerException(message)

/**
 * A generic [[InternalServerException]] for wrapping any non-serializable exception in a serializable form.
 */
case class WrapperException(e: Throwable) extends InternalServerException(e.toString)

/**
 * Indicates that an error occurred when trying to write a file to the disk.
 *
 * @param message a description of the error.
 */
case class FileWriteException(message: String) extends InternalServerException(message)

/**
 * Indicates that a request attempted to use a feature that has not yet been implemented.
 *
 * @param message a description of the error.
 */
case class NotImplementedException(message: String) extends InternalServerException(message)

/**
 * An abstract base class for exceptions indicating that something about a configuration made it impossible to start.
 *
 * @param message a description of the error.
 */
abstract class ApplicationConfigurationException(message: String, cause: Option[Throwable] = None)
    extends Exception(message, cause.orNull)
    with KnoraException

object ApplicationConfigurationException {
  // So we can match instances of ApplicationConfigurationException, even though it's an abstract class
  def unapply(e: ApplicationConfigurationException): Option[ApplicationConfigurationException] = Option(e)
}

/**
 * Indicates that an unsupported triplestore was selected in the configuration.
 *
 * @param message a description of the error.
 */
case class UnsupportedTriplestoreException(message: String) extends ApplicationConfigurationException(message)

/**
 * Indicates that the HTTP configuration is incorrect.
 *
 * @param message a description of the error.
 */
case class HttpConfigurationException(message: String) extends ApplicationConfigurationException(message)

/**
 * Indicates that a test configuration is incorrect.
 *
 * @param message a description of the error.
 */
case class TestConfigurationException(message: String) extends ApplicationConfigurationException(message)

/**
 * Indicates that RDF processing failed.
 *
 * @param message a description of the error.
 * @param cause   the original exception representing the cause of the error, if any.
 */
case class RdfProcessingException(message: String, cause: Option[Throwable] = None)
    extends InternalServerException(message)

/**
 * Indicates that optimizing the Gravsearch query failed.
 *
 * @param message a description of the error.
 */
case class GravsearchOptimizationException(message: String) extends InternalServerException(message)

/**
 * Helper functions for error handling.
 */
object ExceptionUtil {

  /**
   * Checks whether an exception is serializable.
   *
   * @param e the exception to be checked.
   * @return `true` if the exception is serializable, otherwise `false`.
   */
  def isSerializable(e: Throwable): Boolean =
    try {
      SerializationUtils.serialize(e)
      true
    } catch {
      case _: SerializationException => false
    }

  /**
   * Checks whether an exception is serializable. If it is serializable, it is returned as-is. If not,
   * the exception is logged with its stack trace, and a string representation of it is returned in a
   * [[WrapperException]].
   *
   * @param e the exception to be checked.
   * @return the same exception, or a [[WrapperException]].
   */
  def logAndWrapIfNotSerializable(e: Throwable, log: Logger): Throwable =
    if (isSerializable(e)) {
      e
    } else {
      log.error(e.toString)
      WrapperException(e)
    }
}
