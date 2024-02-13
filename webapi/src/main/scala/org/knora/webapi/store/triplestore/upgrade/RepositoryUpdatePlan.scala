/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade

import com.typesafe.scalalogging.Logger

import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.store.triplestore.upgrade.plugins.*

/**
 * The plan for updating a repository to work with the current version of Knora.
 */
object RepositoryUpdatePlan {

  /**
   * Constructs list of all repository update plugins in chronological order.
   */
  def makePluginsForVersions(log: Logger): Seq[PluginForKnoraBaseVersion] =
    Seq(
      PluginForKnoraBaseVersion(
        versionNumber = 1,
        plugin = new UpgradePluginPR1307(),
        prBasedVersionString = Some("PR 1307")
      ),
      PluginForKnoraBaseVersion(
        versionNumber = 2,
        plugin = new UpgradePluginPR1322(),
        prBasedVersionString = Some("PR 1322")
      ),
      PluginForKnoraBaseVersion(
        versionNumber = 3,
        plugin = new UpgradePluginPR1367(),
        prBasedVersionString = Some("PR 1367")
      ),
      PluginForKnoraBaseVersion(
        versionNumber = 4,
        plugin = new UpgradePluginPR1372(),
        prBasedVersionString = Some("PR 1372")
      ),
      PluginForKnoraBaseVersion(
        versionNumber = 5,
        plugin = new MigrateOnlyBuiltInGraphs,
        prBasedVersionString = Some("PR 1440")
      ),
      PluginForKnoraBaseVersion(versionNumber = 6, plugin = new MigrateOnlyBuiltInGraphs), // PR 1206
      PluginForKnoraBaseVersion(versionNumber = 7, plugin = new MigrateOnlyBuiltInGraphs), // PR 1403
      PluginForKnoraBaseVersion(versionNumber = 8, plugin = new UpgradePluginPR1615()),
      PluginForKnoraBaseVersion(versionNumber = 9, plugin = new UpgradePluginPR1746(log)),
      PluginForKnoraBaseVersion(versionNumber = 10, plugin = new MigrateOnlyBuiltInGraphs), // PR 1808
      PluginForKnoraBaseVersion(versionNumber = 11, plugin = new MigrateOnlyBuiltInGraphs), // PR 1813
      PluginForKnoraBaseVersion(versionNumber = 12, plugin = new MigrateOnlyBuiltInGraphs), // PR 1891
      PluginForKnoraBaseVersion(versionNumber = 13, plugin = new UpgradePluginPR1921(log)),
      PluginForKnoraBaseVersion(versionNumber = 14, plugin = new MigrateOnlyBuiltInGraphs), // PR 1992
      PluginForKnoraBaseVersion(versionNumber = 20, plugin = new UpgradePluginPR2018(log)),
      PluginForKnoraBaseVersion(versionNumber = 21, plugin = new UpgradePluginPR2079(log)),
      PluginForKnoraBaseVersion(versionNumber = 22, plugin = new UpgradePluginPR2081(log)),
      PluginForKnoraBaseVersion(versionNumber = 23, plugin = new UpgradePluginPR2094(log)),
      PluginForKnoraBaseVersion(versionNumber = 24, plugin = new MigrateOnlyBuiltInGraphs), // PR 2076
      PluginForKnoraBaseVersion(versionNumber = 25, plugin = new MigrateOnlyBuiltInGraphs), // PR 2268
      PluginForKnoraBaseVersion(versionNumber = 26, plugin = new MigrateOnlyBuiltInGraphs), // PR 3003
      PluginForKnoraBaseVersion(versionNumber = 27, plugin = new MigrateOnlyBuiltInGraphs), // PR 3026
      PluginForKnoraBaseVersion(versionNumber = 28, plugin = new MigrateOnlyBuiltInGraphs)  // PR 3038
      // KEEP IT ON THE BOTTOM
      // From "versionNumber = 6" don't use prBasedVersionString!
    )

  /**
   * The built-in named graphs that are always updated when there is a new version of knora-base.
   */
  val builtInNamedGraphs: Set[RdfDataObject] = Set(
    RdfDataObject(
      path = "knora-ontologies/knora-admin.ttl",
      name = "http://www.knora.org/ontology/knora-admin"
    ),
    RdfDataObject(
      path = "knora-ontologies/knora-base.ttl",
      name = "http://www.knora.org/ontology/knora-base"
    ),
    RdfDataObject(
      path = "knora-ontologies/salsah-gui.ttl",
      name = "http://www.knora.org/ontology/salsah-gui"
    ),
    RdfDataObject(
      path = "knora-ontologies/standoff-onto.ttl",
      name = "http://www.knora.org/ontology/standoff"
    ),
    RdfDataObject(
      path = "knora-ontologies/standoff-data.ttl",
      name = "http://www.knora.org/data/standoff"
    )
  )

  /**
   * Represents an update plugin with its knora-base version number and version string.
   *
   * @param versionNumber        the knora-base version number that the plugin's transformation produces.
   * @param plugin               the plugin.
   * @param prBasedVersionString the plugin's PR-based version string (not used for new plugins).
   */
  case class PluginForKnoraBaseVersion(
    versionNumber: Int,
    plugin: UpgradePlugin,
    prBasedVersionString: Option[String] = None
  ) {
    lazy val versionString: String =
      prBasedVersionString match {
        case Some(str) => str
        case None      => s"knora-base v$versionNumber"
      }
  }
}
