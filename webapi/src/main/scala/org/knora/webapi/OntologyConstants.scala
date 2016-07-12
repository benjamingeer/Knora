/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi

/**
  * Contains string constants for IRIs from ontologies used by the application.
  */
object OntologyConstants {

    object Rdf {
        val Type = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        val Subject = "http://www.w3.org/1999/02/22-rdf-syntax-ns#subject"
        val Predicate = "http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate"
        val Object = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object"
    }

    object Rdfs {
        val Label = "http://www.w3.org/2000/01/rdf-schema#label"
        val Comment = "http://www.w3.org/2000/01/rdf-schema#comment"
        val SubclassOf = "http://www.w3.org/2000/01/rdf-schema#subClassOf"
    }

    object Owl {
        val Restriction = "http://www.w3.org/2002/07/owl#Restriction"

        val OnProperty = "http://www.w3.org/2002/07/owl#onProperty"

        val Cardinality = "http://www.w3.org/2002/07/owl#cardinality"
        val MinCardinality = "http://www.w3.org/2002/07/owl#minCardinality"
        val MaxCardinality = "http://www.w3.org/2002/07/owl#maxCardinality"


        /**
          * Cardinality IRIs expressed as OWL restrictions, which specify the properties that resources of
          * a particular type can have.
          */
        val cardinalityOWLRestrictions = Set(
            Cardinality,
            MinCardinality,
            MaxCardinality
        )
    }

    object KnoraBase {
        val Resource = "http://www.knora.org/ontology/knora-base#Resource"

        val ObjectClassConstraint = "http://www.knora.org/ontology/knora-base#objectClassConstraint"

        val HasLinkTo = "http://www.knora.org/ontology/knora-base#hasLinkTo"
        val IsRegionOf = "http://www.knora.org/ontology/knora-base#isRegionOf"

        val ValueHasString = "http://www.knora.org/ontology/knora-base#valueHasString"
        val ValueHasInteger = "http://www.knora.org/ontology/knora-base#valueHasInteger"
        val ValueHasDecimal = "http://www.knora.org/ontology/knora-base#valueHasDecimal"
        val ValueHasStandoff = "http://www.knora.org/ontology/knora-base#valueHasStandoff"
        val ValueHasResPtr = "http://www.knora.org/ontology/knora-base#valueHasResPtr"
        val ValueHasStartJDC = "http://www.knora.org/ontology/knora-base#valueHasStartJDC"
        val ValueHasEndJDC = "http://www.knora.org/ontology/knora-base#valueHasEndJDC"
        val ValueHasCalendar = "http://www.knora.org/ontology/knora-base#valueHasCalendar"
        val ValueHasStartPrecision = "http://www.knora.org/ontology/knora-base#valueHasStartPrecision"
        val ValueHasEndPrecision = "http://www.knora.org/ontology/knora-base#valueHasEndPrecision"
        val ValueHasBoolean = "http://www.knora.org/ontology/knora-base#valueHasBoolean"
        val ValueHasUri = "http://www.knora.org/ontology/knora-base#valueHasUri"
        val ValueHasColor = "http://www.knora.org/ontology/knora-base#valueHasColor"
        val ValueHasGeometry = "http://www.knora.org/ontology/knora-base#valueHasGeometry"
        val ValueHasListNode = "http://www.knora.org/ontology/knora-base#valueHasListNode"
        val ValueHasIntervalStart = "http://www.knora.org/ontology/knora-base#valueHasIntervalStart"
        val ValueHasIntervalEnd = "http://www.knora.org/ontology/knora-base#valueHasIntervalEnd"
        val ValueHasOrder = "http://www.knora.org/ontology/knora-base#valueHasOrder"
        val ValueHasRefCount = "http://www.knora.org/ontology/knora-base#valueHasRefCount"
        val ValueHasComment = "http://www.knora.org/ontology/knora-base#valueHasComment"
        val ValueHasGeonameCode = "http://www.knora.org/ontology/knora-base#valueHasGeonameCode"

        val PreviousValue = "http://www.knora.org/ontology/knora-base#previousValue"

        val HasFileValue = "http://www.knora.org/ontology/knora-base#hasFileValue"
        val HasStillImageFileValue = "http://www.knora.org/ontology/knora-base#hasStillImageFileValue"
        val HasMovingImageFileValue = "http://www.knora.org/ontology/knora-base#hasMovingImageFileValue"
        val HasAudioFileValue = "http://www.knora.org/ontology/knora-base#hasAudioFileValue"
        val HasDDDFileValue = "http://www.knora.org/ontology/knora-base#hasDDDFileValue"
        val HasTextFileValue = "http://www.knora.org/ontology/knora-base#hasTextFileValue"
        val HasDocumentFileValue = "http://www.knora.org/ontology/knora-base#hasDocumentFileValue"

        val IsPreview = "http://www.knora.org/ontology/knora-base#isPreview"
        val ResourceIcon = "http://www.knora.org/ontology/knora-base#resourceIcon"
        val PreferredLanguage = "http://www.knora.org/ontology/knora-base#preferredLanguage"
        val Username = "http://www.knora.org/ontology/knora-base#userid"
        val Email = "http://www.knora.org/ontology/knora-base#email"
        val Password = "http://www.knora.org/ontology/knora-base#password"

        val InternalMimeType = "http://www.knora.org/ontology/knora-base#internalMimeType"
        val InternalFilename = "http://www.knora.org/ontology/knora-base#internalFilename"
        val OriginalFilename = "http://www.knora.org/ontology/knora-base#originalFilename"
        val DimX = "http://www.knora.org/ontology/knora-base#dimX"
        val DimY = "http://www.knora.org/ontology/knora-base#dimY"
        val QualityLevel = "http://www.knora.org/ontology/knora-base#qualityLevel"

        val TextValue = "http://www.knora.org/ontology/knora-base#TextValue"
        val IntValue = "http://www.knora.org/ontology/knora-base#IntValue"
        val BooleanValue = "http://www.knora.org/ontology/knora-base#BooleanValue"
        val UriValue = "http://www.knora.org/ontology/knora-base#UriValue"
        val DecimalValue = "http://www.knora.org/ontology/knora-base#DecimalValue"
        val DateValue = "http://www.knora.org/ontology/knora-base#DateValue"
        val ColorValue = "http://www.knora.org/ontology/knora-base#ColorValue"
        val GeomValue = "http://www.knora.org/ontology/knora-base#GeomValue"
        val ListValue = "http://www.knora.org/ontology/knora-base#ListValue"
        val IntervalValue = "http://www.knora.org/ontology/knora-base#IntervalValue"
        val StillImageFileValue = "http://www.knora.org/ontology/knora-base#StillImageFileValue"
        val MovingImageFileValue = "http://www.knora.org/ontology/knora-base#MovingImageFileValue"
        val FileValue = "http://www.knora.org/ontology/knora-base#FileValue"
        val LinkValue = "http://www.knora.org/ontology/knora-base#LinkValue"
        val GeonameValue = "http://www.knora.org/ontology/knora-base#GeonameValue"

        val KnoraAdminPrefix = "http://www.knora.org/ontology/knora-base#"
        val IsInGroup = "http://www.knora.org/ontology/knora-base#isInGroup"
        val IsInProject = "http://www.knora.org/ontology/knora-base#isInProject"
        val IsDeleted = "http://www.knora.org/ontology/knora-base#isDeleted"

        val UnknownUser = "http://www.knora.org/ontology/knora-base#UnknownUser"
        val KnownUser = "http://www.knora.org/ontology/knora-base#KnownUser"
        val ProjectMember = "http://www.knora.org/ontology/knora-base#ProjectMember"
        val Owner = "http://www.knora.org/ontology/knora-base#Owner"

        val HasRestrictedViewPermission = "http://www.knora.org/ontology/knora-base#hasRestrictedViewPermission"
        val HasViewPermission = "http://www.knora.org/ontology/knora-base#hasViewPermission"
        val HasModifyPermission = "http://www.knora.org/ontology/knora-base#hasModifyPermission"
        val HasDeletePermission = "http://www.knora.org/ontology/knora-base#hasDeletePermission"
        val HasChangeRightsPermission = "http://www.knora.org/ontology/knora-base#hasChangeRightsPermission"
        val HasMaxPermission = HasChangeRightsPermission

        val HasDefaultRestrictedViewPermission = "http://www.knora.org/ontology/knora-base#hasDefaultRestrictedViewPermission"
        val HasDefaultViewPermission = "http://www.knora.org/ontology/knora-base#hasDefaultViewPermission"
        val HasDefaultModifyPermission = "http://www.knora.org/ontology/knora-base#hasDefaultModifyPermission"
        val HasDefaultDeletePermission = "http://www.knora.org/ontology/knora-base#hasDefaultDeletePermission"
        val HasDefaultChangeRightsPermission = "http://www.knora.org/ontology/knora-base#hasDefaultChangeRightsPermission"

        val defaultPermissionProperties = Set(
            HasDefaultRestrictedViewPermission,
            HasDefaultViewPermission,
            HasDefaultModifyPermission,
            HasDefaultDeletePermission,
            HasDefaultChangeRightsPermission
        )

        val StandoffHasAttribute = "http://www.knora.org/ontology/knora-base#standoffHasAttribute"
        val StandoffHasStart = "http://www.knora.org/ontology/knora-base#standoffHasStart"
        val StandoffHasEnd = "http://www.knora.org/ontology/knora-base#standoffHasEnd"
        val StandoffHasHref = "http://www.knora.org/ontology/knora-base#standoffHasHref"
        val StandoffHasLink = "http://www.knora.org/ontology/knora-base#standoffHasLink"
        val HasStandoffLinkTo = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"
        val HasStandoffLinkToValue = "http://www.knora.org/ontology/knora-base#hasStandoffLinkToValue"

        val AttachedToUser = "http://www.knora.org/ontology/knora-base#attachedToUser"
        val AttachedToProject = "http://www.knora.org/ontology/knora-base#attachedToProject"

        val UsersActiveProject = KnoraAdminPrefix + "currentproject"

        val ProjectShortname = KnoraAdminPrefix + "projectShortname"
        val ProjectLongname = KnoraAdminPrefix + "projectLongname"
        val ProjectDescription = KnoraAdminPrefix + "projectDescription"
        val ProjectKeyword = KnoraAdminPrefix + "projectKeyword"
        val ProjectBasepath = KnoraAdminPrefix + "projectBasepath"
        val ProjectLogo = KnoraAdminPrefix + "projectLogo"
    }

    object SalsahGui {
        val GuiAttribute = "http://www.knora.org/ontology/salsah-gui#guiAttribute"
        val GuiOrder = "http://www.knora.org/ontology/salsah-gui#guiOrder"
        val GuiElement = "http://www.knora.org/ontology/salsah-gui#guiElement"
        val SimpleText = "http://www.knora.org/ontology/salsah-gui#SimpleText"
        val Textarea = "http://www.knora.org/ontology/salsah-gui#Textarea"
        val Pulldown = "http://www.knora.org/ontology/salsah-gui#Pulldown"
        val Slider = "http://www.knora.org/ontology/salsah-gui#Slider"
        val Spinbox = "http://www.knora.org/ontology/salsah-gui#Spinbox"
        val Searchbox = "http://www.knora.org/ontology/salsah-gui#Searchbox"
        val Date = "http://www.knora.org/ontology/salsah-gui#Date"
        val Geometry = "http://www.knora.org/ontology/salsah-gui#Geometry"
        val Colorpicker = "http://www.knora.org/ontology/salsah-gui#Colorpicker"
        val List = "http://www.knora.org/ontology/salsah-gui#List"
        val Radio = "http://www.knora.org/ontology/salsah-gui#Radio"
        val Richtext = "http://www.knora.org/ontology/salsah-gui#Richtext"
        val Interval = "http://www.knora.org/ontology/salsah-gui#Interval"
        val Geonames = "http://www.knora.org/ontology/salsah-gui#Geonames"
        val Fileupload = "http://www.knora.org/ontology/salsah-gui#Fileupload"

        object attributeNames {
            val resourceClass = "restypeid"
            val assignmentOperator = "="
        }
    }

    object Foaf {
        val GivenName = "http://xmlns.com/foaf/0.1/givenName"
        val FamilyName = "http://xmlns.com/foaf/0.1/familyName"
    }

}
