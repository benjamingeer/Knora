/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.rdf

import org.eclipse.rdf4j.model.Namespace
import org.eclipse.rdf4j.model.impl.SimpleNamespace
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion
import org.knora.webapi.messages.OntologyConstants.KnoraBase.KnoraBasePrefixExpansion
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph

object Vocabulary {
  object KnoraAdmin {
    val NS: Namespace = new SimpleNamespace("knora-admin", KnoraAdminPrefixExpansion)

    // resource class IRIs
    val User: Iri         = Rdf.iri(KnoraAdminPrefixExpansion, "User")
    val UserGroup: Iri    = Rdf.iri(KnoraAdminPrefixExpansion, "UserGroup")
    val KnoraProject: Iri = Rdf.iri(KnoraAdminPrefixExpansion, "knoraProject")

    // property IRIs
    val username: Iri              = Rdf.iri(KnoraAdminPrefixExpansion, "username")
    val email: Iri                 = Rdf.iri(KnoraAdminPrefixExpansion, "email")
    val givenName: Iri             = Rdf.iri(KnoraAdminPrefixExpansion, "givenName")
    val familyName: Iri            = Rdf.iri(KnoraAdminPrefixExpansion, "familyName")
    val status: Iri                = Rdf.iri(KnoraAdminPrefixExpansion, "status")
    val preferredLanguage: Iri     = Rdf.iri(KnoraAdminPrefixExpansion, "preferredLanguage")
    val password: Iri              = Rdf.iri(KnoraAdminPrefixExpansion, "password")
    val isInProject: Iri           = Rdf.iri(KnoraAdminPrefixExpansion, "isInProject")
    val isInGroup: Iri             = Rdf.iri(KnoraAdminPrefixExpansion, "isInGroup")
    val isInSystemAdminGroup: Iri  = Rdf.iri(KnoraAdminPrefixExpansion, "isInSystemAdminGroup")
    val isInProjectAdminGroup: Iri = Rdf.iri(KnoraAdminPrefixExpansion, "isInProjectAdminGroup")
    val hasSelfJoinEnabled: Iri    = Rdf.iri(KnoraAdminPrefixExpansion, "hasSelfJoinEnabled")

    // user group properties
    val belongsToProject: Iri  = Rdf.iri(KnoraAdminPrefixExpansion, "belongsToProject")
    val groupName: Iri         = Rdf.iri(KnoraAdminPrefixExpansion, "groupName")
    val groupDescriptions: Iri = Rdf.iri(KnoraAdminPrefixExpansion, "groupDescriptions")

    // project properties
    val projectRestrictedViewSize: Iri      = Rdf.iri(KnoraAdminPrefixExpansion, "projectRestrictedViewSize")
    val projectRestrictedViewWatermark: Iri = Rdf.iri(KnoraAdminPrefixExpansion, "projectRestrictedViewWatermark")
  }

  object KnoraBase {
    val NS: Namespace = new SimpleNamespace("knora-base", KnoraBasePrefixExpansion)

    // property IRIs
    val attachedToProject: Iri = Rdf.iri(KnoraBasePrefixExpansion, "attachedToProject")
  }

  object NamedGraphs {
    val knoraAdminIri: Iri = Rdf.iri(adminDataNamedGraph.value)
  }
}
