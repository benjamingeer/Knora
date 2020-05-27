package org.knora.webapi.store.triplestore.upgrade

import org.knora.webapi.store.triplestore.upgrade.plugins.{NoopPlugin, UpgradePluginPR1307, UpgradePluginPR1322, UpgradePluginPR1367, UpgradePluginPR1372, UpgradePluginPR1615}

/**
 * The plan for updating a repository to work with the current version of Knora.
 */
object RepositoryUpdatePlan {
    /**
     * A list of all repository update plugins in chronological order.
     */
    val pluginsForVersions: Seq[PluginForKnoraBaseVersion] = Seq(
        PluginForKnoraBaseVersion(versionNumber = 1, plugin = new UpgradePluginPR1307, prBasedVersionString = Some("PR 1307")),
        PluginForKnoraBaseVersion(versionNumber = 2, plugin = new UpgradePluginPR1322, prBasedVersionString = Some("PR 1322")),
        PluginForKnoraBaseVersion(versionNumber = 3, plugin = new UpgradePluginPR1367, prBasedVersionString = Some("PR 1367")),
        PluginForKnoraBaseVersion(versionNumber = 4, plugin = new UpgradePluginPR1372, prBasedVersionString = Some("PR 1372")),
        PluginForKnoraBaseVersion(versionNumber = 5, plugin = new NoopPlugin, prBasedVersionString = Some("PR 1440")),
        PluginForKnoraBaseVersion(versionNumber = 6, plugin = new NoopPlugin), // PR 1206
        PluginForKnoraBaseVersion(versionNumber = 7, plugin = new NoopPlugin), // PR 1403
        PluginForKnoraBaseVersion(versionNumber = 8, plugin = new UpgradePluginPR1615)
    )

    /**
     * The built-in named graphs that are always updated when there is a new version of knora-base.
     */
    val builtInNamedGraphs: Set[BuiltInNamedGraph] = Set(
        BuiltInNamedGraph(
            filename = "knora-ontologies/knora-admin.ttl",
            iri = "http://www.knora.org/ontology/knora-admin"
        ),
        BuiltInNamedGraph(
            filename = "knora-ontologies/knora-base.ttl",
            iri = "http://www.knora.org/ontology/knora-base"
        ),
        BuiltInNamedGraph(
            filename = "knora-ontologies/salsah-gui.ttl",
            iri = "http://www.knora.org/ontology/salsah-gui"
        ),
        BuiltInNamedGraph(
            filename = "knora-ontologies/standoff-onto.ttl",
            iri = "http://www.knora.org/ontology/standoff"
        ),
        BuiltInNamedGraph(
            filename = "knora-ontologies/standoff-data.ttl",
            iri = "http://www.knora.org/data/standoff"
        )
    )

    /**
     * Represents an update plugin with its knora-base version number and version string.
     *
     * @param versionNumber        the knora-base version number that the plugin's transformation produces.
     * @param plugin               the plugin.
     * @param prBasedVersionString the plugin's PR-based version string (not used for new plugins).
     */
    case class PluginForKnoraBaseVersion(versionNumber: Int, plugin: UpgradePlugin, prBasedVersionString: Option[String] = None) {
        lazy val versionString: String = {
            prBasedVersionString match {
                case Some(str) => str
                case None => s"knora-base v$versionNumber"
            }
        }
    }

    /**
     * Represents a Knora built-in named graph.
     *
     * @param filename the filename containing the named graph.
     * @param iri      the IRI of the named graph.
     */
    case class BuiltInNamedGraph(filename: String, iri: String)
}
