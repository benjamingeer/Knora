/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.instrumentation

import com.typesafe.scalalogging.Logger
import kamon.instrumentation.futures.scala.ScalaFutureInstrumentation.trace
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Success

/**
 * A set of methods used for measuring stuff that is happening.
 */
trait InstrumentationSupport {

  /**
   * For convenience. Returns the metrics logger based on the current
   * class name.
   */
  protected lazy val metricsLogger: Logger = getMetricsLoggerForClass

  /**
   * Measures the time the future needs to complete.
   *
   * Example:
   *
   * val f = tracedFuture {
   * Future {
   * work inside the future
   * }
   * }
   *
   * @param name   the name identifying the span.
   * @param future the future we want to instrument.
   */
  def tracedFuture[A](name: String)(future: => Future[A])(implicit ec: ExecutionContext): Future[A] = {

    /**
     * NOTE: The elapsed time of the span is saved somewhere by kamon, but
     * I have no idea how to get to it and this is why I'm calculating
     * it in the metricsLogger.info line. This is a quick and dirty hack to
     * have at least something.
     */
    val start = System.currentTimeMillis()
    trace(name)(future.andThen { case Success(_) =>
      metricsLogger.info(s"$name: {} ms", System.currentTimeMillis() - start)
    })
    // .andThen(case completed => logger.info(s"$name: " + (System.currentTimeMillis() - start) + "ms"))
  }

  //    def counter(name: String) = Kamon.metrics.counter(name)
  //    def minMaxCounter(name: String) = Kamon.metrics.minMaxCounter(name)
  //    def time[A](name: String)(thunk: => A) = Latency.measure(Kamon.metrics.histogram(name))(thunk)
  //    def traceFuture[A](name:String)(future: => Future[A]):Future[A] =
  //        Tracer.withContext(Kamon.tracer.newContext(name)) {
  //            future.andThen { case completed ⇒ Tracer.currentContext.finish() }(SameThreadExecutionContext)
  //        }

  /**
   * Based on the current class name, create a logger with the name in the
   * form 'M-ClassName', e.g., 'M-RedisManager'.
   * All loggers returned by this method can be configured in 'logback.xml',
   * i.e., turned on or off.
   */
  def getMetricsLoggerForClass: Logger = {
    val simpleClassName = this.getClass.getSimpleName
    Logger(LoggerFactory.getLogger(s"M-$simpleClassName"))
  }
}
