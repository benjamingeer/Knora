package org.knora.webapi

import scala.Predef._

/** This object was generated by sbt-buildinfo. */
case object BuildInfo2 {
    /** The value is "webapi". */
    val name: String = "webapi"
    /** The value is "9.1.0-45-g8b5fcf2-SNAPSHOT". */
    val version: String = "9.1.0-45-g8b5fcf2-SNAPSHOT"
    /** The value is "2.12.8". */
    val scalaVersion: String = "2.12.8"
    /** The value is "1.2.8". */
    val sbtVersion: String = "1.2.8"
    /** The value is "10.1.7". */
    val akkaHttp: String = "10.1.7"
    override val toString: String = {
        "name: %s, version: %s, scalaVersion: %s, sbtVersion: %s, akkaHttp: %s".format(
            name, version, scalaVersion, sbtVersion, akkaHttp
        )
    }
}