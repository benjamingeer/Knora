/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.repo.rdf

import org.eclipse.rdf4j.model.Namespace
import org.eclipse.rdf4j.model.impl.SimpleNamespace
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.iri

import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion
import org.knora.webapi.messages.OntologyConstants.KnoraBase.KnoraBasePrefixExpansion
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.AdminConstants.permissionsDataNamedGraph

object Vocabulary {
  object KnoraAdmin {
    private val ka    = KnoraAdminPrefixExpansion
    val NS: Namespace = new SimpleNamespace("knora-admin", KnoraAdminPrefixExpansion)

    // resource class IRIs
    val User: Iri         = Rdf.iri(ka, "User")
    val UserGroup: Iri    = Rdf.iri(ka, "UserGroup")
    val KnoraProject: Iri = Rdf.iri(ka, "knoraProject")

    // known instances
    val SystemProject: Iri = Rdf.iri(ka, "SystemProject")
    val KnownUser: Iri     = Rdf.iri(ka, "KnownUser")

    // property IRIs
    val username: Iri              = Rdf.iri(ka, "username")
    val email: Iri                 = Rdf.iri(ka, "email")
    val givenName: Iri             = Rdf.iri(ka, "givenName")
    val familyName: Iri            = Rdf.iri(ka, "familyName")
    val status: Iri                = Rdf.iri(ka, "status")
    val preferredLanguage: Iri     = Rdf.iri(ka, "preferredLanguage")
    val password: Iri              = Rdf.iri(ka, "password")
    val isInProject: Iri           = Rdf.iri(ka, "isInProject")
    val isInGroup: Iri             = Rdf.iri(ka, "isInGroup")
    val isInSystemAdminGroup: Iri  = Rdf.iri(ka, "isInSystemAdminGroup")
    val isInProjectAdminGroup: Iri = Rdf.iri(ka, "isInProjectAdminGroup")
    val hasSelfJoinEnabled: Iri    = Rdf.iri(ka, "hasSelfJoinEnabled")

    // user group properties
    val belongsToProject: Iri  = Rdf.iri(ka, "belongsToProject")
    val groupName: Iri         = Rdf.iri(ka, "groupName")
    val groupDescriptions: Iri = Rdf.iri(ka, "groupDescriptions")

    // project properties
    val projectRestrictedViewSize: Iri      = Rdf.iri(ka, "projectRestrictedViewSize")
    val projectRestrictedViewWatermark: Iri = Rdf.iri(ka, "projectRestrictedViewWatermark")
    val projectDescription: Iri             = Rdf.iri(ka, "projectDescription")
    val projectKeyword: Iri                 = Rdf.iri(ka, "projectKeyword")
    val projectLogo: Iri                    = Rdf.iri(ka, "projectLogo")
    val projectLongname: Iri                = Rdf.iri(ka, "projectLongname")
    val projectShortcode: Iri               = Rdf.iri(ka, "projectShortcode")
    val projectShortname: Iri               = Rdf.iri(ka, "projectShortname")

    // permission properties
    val AdministrativePermission: Iri      = Rdf.iri(ka, "AdministrativePermission")
    val DefaultObjectAccessPermission: Iri = Rdf.iri(ka, "DefaultObjectAccessPermission")
    val forProject: Iri                    = Rdf.iri(ka, "forProject")
    val forGroup: Iri                      = Rdf.iri(ka, "forGroup")
    val forProperty: Iri                   = Rdf.iri(ka, "forProperty")
    val forResourceClass: Iri              = Rdf.iri(ka, "forResourceClass")
  }

  object KnoraBase {
    private val kb = KnoraBasePrefixExpansion

    val NS: Namespace = new SimpleNamespace("knora-base", kb)

    val linkValue: Iri = iri(kb + "LinkValue")

    val isDeleted: Iri         = iri(kb + "isDeleted")
    val attachedToUser: Iri    = iri(kb + "attachedToUser")
    val attachedToProject: Iri = iri(kb + "attachedToProject")
    val hasPermissions: Iri    = iri(kb + "hasPermissions")
    val creationDate: Iri      = iri(kb + "creationDate")

    val valueHasString: Iri    = iri(kb + "valueHasString")
    val valueHasUUID: Iri      = iri(kb + "valueHasUUID")
    val valueHasComment: Iri   = iri(kb + "valueHasComment")
    val valueHasOrder: Iri     = iri(kb + "valueHasOrder")
    val valueCreationDate: Iri = iri(kb + "valueCreationDate")

    val valueHasInteger: Iri               = iri(kb + "valueHasInteger")
    val valueHasBoolean: Iri               = iri(kb + "valueHasBoolean")
    val valueHasDecimal: Iri               = iri(kb + "valueHasDecimal")
    val valueHasUri: Iri                   = iri(kb + "valueHasUri")
    val valueHasStartJDN: Iri              = iri(kb + "valueHasStartJDN")
    val valueHasEndJDN: Iri                = iri(kb + "valueHasEndJDN")
    val valueHasStartPrecision: Iri        = iri(kb + "valueHasStartPrecision")
    val valueHasEndPrecision: Iri          = iri(kb + "valueHasEndPrecision")
    val valueHasCalendar: Iri              = iri(kb + "valueHasCalendar")
    val valueHasColor: Iri                 = iri(kb + "valueHasColor")
    val valueHasGeometry: Iri              = iri(kb + "valueHasGeometry")
    val valueHasListNode: Iri              = iri(kb + "valueHasListNode")
    val valueHasIntervalStart: Iri         = iri(kb + "valueHasIntervalStart")
    val valueHasIntervalEnd: Iri           = iri(kb + "valueHasIntervalEnd")
    val valueHasTimeStamp: Iri             = iri(kb + "valueHasTimeStamp")
    val valueHasGeonameCode: Iri           = iri(kb + "valueHasGeonameCode")
    val valueHasRefCount: Iri              = iri(kb + "valueHasRefCount")
    val valueHasLanguage: Iri              = iri(kb + "valueHasLanguage")
    val valueHasMapping: Iri               = iri(kb + "valueHasMapping")
    val valueHasMaxStandoffStartIndex: Iri = iri(kb + "valueHasMaxStandoffStartIndex")
    val valueHasStandoff: Iri              = iri(kb + "valueHasStandoff")
    val hasCopyrightHolder: Iri            = iri(kb + "hasCopyrightHolder")
    val hasAuthorship: Iri                 = iri(kb + "hasAuthorship")
    val hasLicense: Iri                    = iri(kb + "hasLicense")

    val internalFilename: Iri = iri(kb + "internalFilename")
    val internalMimeType: Iri = iri(kb + "internalMimeType")
    val originalFilename: Iri = iri(kb + "originalFilename")
    val originalMimeType: Iri = iri(kb + "originalMimeType")
    val dimX: Iri             = iri(kb + "dimX")
    val dimY: Iri             = iri(kb + "dimY")
    val externalUrl: Iri      = iri(kb + "externalUrl")
    val pageCount: Iri        = iri(kb + "pageCount")

    val standoffTagHasStartIndex: Iri    = iri(kb + "standoffTagHasStartIndex")
    val standoffTagHasEndIndex: Iri      = iri(kb + "standoffTagHasEndIndex")
    val standoffTagHasStartParent: Iri   = iri(kb + "standoffTagHasStartParent")
    val standoffTagHasEndParent: Iri     = iri(kb + "standoffTagHasEndParent")
    val standoffTagHasOriginalXMLID: Iri = iri(kb + "standoffTagHasOriginalXMLID")
    val standoffTagHasUUID: Iri          = iri(kb + "standoffTagHasUUID")
    val standoffTagHasStart: Iri         = iri(kb + "standoffTagHasStart")
    val standoffTagHasEnd: Iri           = iri(kb + "standoffTagHasEnd")

    val hasTextValueType: Iri = iri(kb + "hasTextValueType")

    val UnformattedText: Iri     = iri(kb + "UnformattedText")
    val FormattedText: Iri       = iri(kb + "FormattedText")
    val CustomFormattedText: Iri = iri(kb + "CustomFormattedText")
    val UndefinedTextType: Iri   = iri(kb + "UndefinedTextType")
  }

  object NamedGraphs {
    val dataAdmin: Iri       = Rdf.iri(adminDataNamedGraph.value)
    val dataPermissions: Iri = Rdf.iri(permissionsDataNamedGraph.value)
  }
}
