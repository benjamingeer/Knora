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
        val KnoraBasePrefix = "knora-base:"
        val KnoraBasePrefixExpansion = "http://www.knora.org/ontology/knora-base#"

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

        val IsDeleted = "http://www.knora.org/ontology/knora-base#isDeleted"

        
        /* Standoff */
        val StandoffHasAttribute = "http://www.knora.org/ontology/knora-base#standoffHasAttribute"
        val StandoffHasStart = "http://www.knora.org/ontology/knora-base#standoffHasStart"
        val StandoffHasEnd = "http://www.knora.org/ontology/knora-base#standoffHasEnd"
        val StandoffHasHref = "http://www.knora.org/ontology/knora-base#standoffHasHref"
        val StandoffHasLink = "http://www.knora.org/ontology/knora-base#standoffHasLink"
        val HasStandoffLinkTo = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"
        val HasStandoffLinkToValue = "http://www.knora.org/ontology/knora-base#hasStandoffLinkToValue"

        /* Resource creator */
        val AttachedToUser = "http://www.knora.org/ontology/knora-base#attachedToUser"
        
        /* Resource's project */
        val AttachedToProject = "http://www.knora.org/ontology/knora-base#attachedToProject"

        /* User */
        val User = KnoraBasePrefixExpansion                   + "User"
        val Username = KnoraBasePrefixExpansion               + "userid"
        val Email = KnoraBasePrefixExpansion                  + "email"
        val Password = KnoraBasePrefixExpansion               + "password"
        val UsersActiveProject = KnoraBasePrefixExpansion     + "currentproject"
        val IsActiveUser = KnoraBasePrefixExpansion           + "isActiveUser"
        val PreferredLanguage = KnoraBasePrefixExpansion      + "preferredLanguage"
        val IsInProject = KnoraBasePrefixExpansion            + "isInProject"
        val IsInGroup = KnoraBasePrefixExpansion              + "isInGroup"
        val IsInSystemAdminGroup = KnoraBasePrefixExpansion   + "isInSystemAdminGroup"
        val IsInProjectAdminGroup = KnoraBasePrefixExpansion  + "isInProjectAdminGroup"

        /* Project */
        val KnoraProject = KnoraBasePrefixExpansion           + "knoraProject"
        val ProjectShortname = KnoraBasePrefixExpansion       + "projectShortname"
        val ProjectLongname = KnoraBasePrefixExpansion        + "projectLongname"
        val ProjectDescription = KnoraBasePrefixExpansion     + "projectDescription"
        val ProjectKeywords = KnoraBasePrefixExpansion        + "projectKeywords"
        val ProjectBasepath = KnoraBasePrefixExpansion        + "projectBasepath"
        val ProjectLogo = KnoraBasePrefixExpansion            + "projectLogo"
        val ProjectOntologyGraph = KnoraBasePrefixExpansion   + "projectOntologyGraph"
        val ProjectDataGraph = KnoraBasePrefixExpansion       + "projectDataGraph"
        val IsActiveProject = KnoraBasePrefixExpansion        + "isActiveProject"
        val HasSelfJoinEnabled = KnoraBasePrefixExpansion     + "hasSelfJoinEnabled"
        val HasProjectAdmin = KnoraBasePrefixExpansion        + "hasProjectAdmin"

        /* Group */
        val Group = KnoraBasePrefixExpansion                  + "UserGroup"
        val GroupName = KnoraBasePrefixExpansion              + "groupName"
        val GroupDescription = KnoraBasePrefixExpansion       + "groupDescription"
        val BelongsToProject = KnoraBasePrefixExpansion       + "belongsToProject"
        val IsActiveGroup = KnoraBasePrefixExpansion          + "isActiveGroup"

        /* Built-In Groups */
        val UnknownUser = KnoraBasePrefixExpansion            + "UnknownUser"
        val KnownUser = KnoraBasePrefixExpansion              + "KnownUser"
        val ProjectMember = KnoraBasePrefixExpansion          + "ProjectMember"
        val Owner = "http://www.knora.org/ontology/knora-base#Owner" // ToDo: remove as it is now called creator
        val Creator = KnoraBasePrefixExpansion                + "Creator"
        val SystemAdmin = KnoraBasePrefixExpansion            + "SystemAdmin"
        val ProjectAdmin = KnoraBasePrefixExpansion           + "ProjectAdmin"

        /* Institution */
        val Institution = KnoraBasePrefixExpansion + "Institution"

        /* Permissions */
        val HasPermissions = "http://www.knora.org/ontology/knora-base#hasPermissions"

        val PermissionListDelimiter = '|'
        val GroupListDelimiter = ','

        val RestrictedViewPermission = "RV"
        val ViewPermission = "V"
        val ModifyPermission = "M"
        val DeletePermission = "D"
        val ChangeRightsPermission = "CR"
        val MaxPermission = ChangeRightsPermission


        val ObjectAccessPermissionAbbreviations = Seq(
            RestrictedViewPermission,
            ViewPermission,
            ModifyPermission,
            DeletePermission,
            ChangeRightsPermission
        )

        val ProjectResourceCreateAllPermission = "ProjectResourceCreateAllPermission"
        val ProjectResourceCreateRestrictedPermission = "ProjectResourceCreateRestrictedPermission"
        val ProjectAdminAllPermission = "ProjectAdminAllPermission"
        val ProjectAdminGroupAllPermission = "ProjectAdminGroupAllPermission"
        val ProjectAdminGroupRestrictedPermission = "ProjectAdminGroupRestrictedPermission"
        val ProjectAdminRightsAllPermission = "ProjectAdminRightsAllPermission"
        val ProjectAdminOntologyAllPermission = "ProjectAdminOntologyAllPermission"

        val AdministrativePermissionAbbreviations = Seq(
            ProjectResourceCreateAllPermission,
            ProjectResourceCreateRestrictedPermission,
            ProjectAdminAllPermission,
            ProjectAdminGroupAllPermission,
            ProjectAdminGroupRestrictedPermission,
            ProjectAdminRightsAllPermission,
            ProjectAdminOntologyAllPermission
        )

        val DefaultRestrictedViewPermission = "DRV"
        val DefaultViewPermission = "DV"
        val DefaultModifyPermission = "DM"
        val DefaultDeletePermission = "DD"
        val DefaultChangeRightsPermission = "DCR"

        val DefaultObjectAccessPermissionAbbreviations = Seq(
            DefaultRestrictedViewPermission,
            DefaultViewPermission,
            DefaultModifyPermission,
            DefaultDeletePermission,
            DefaultChangeRightsPermission
        )

        val HasDefaultRestrictedViewPermission = "http://www.knora.org/ontology/knora-base#hasDefaultRestrictedViewPermission"
        val HasDefaultViewPermission = "http://www.knora.org/ontology/knora-base#hasDefaultViewPermission"
        val HasDefaultModifyPermission = "http://www.knora.org/ontology/knora-base#hasDefaultModifyPermission"
        val HasDefaultDeletePermission = "http://www.knora.org/ontology/knora-base#hasDefaultDeletePermission"
        val HasDefaultChangeRightsPermission = "http://www.knora.org/ontology/knora-base#hasDefaultChangeRightsPermission"

        val DefaultPermissionProperties = Set(
            HasDefaultRestrictedViewPermission,
            HasDefaultViewPermission,
            HasDefaultModifyPermission,
            HasDefaultDeletePermission,
            HasDefaultChangeRightsPermission
        )

        val AdministrativePermission = KnoraBasePrefixExpansion       + "AdministrativePermission"
        val DefaultObjectAccessPermission = KnoraBasePrefixExpansion  + "DefaultObjectAccessPermission"
        val ForProject = KnoraBasePrefixExpansion                     + "forProject"
        val ForGroup = KnoraBasePrefixExpansion                       + "forGroup"
        val ForResourceClass = KnoraBasePrefixExpansion               + "forResourceClass"
        val ForProperty = KnoraBasePrefixExpansion                    + "forProperty"

        val AllProjects = KnoraBasePrefixExpansion                    + "AllProjects"
        val AllGroups = KnoraBasePrefixExpansion                      + "AllGroups"
        val AllResourceClasses = KnoraBasePrefixExpansion             + "AllResourceClasses"
        val AllProperties = KnoraBasePrefixExpansion                  + "AllProperties"

        /**
          * The system user is the owner of objects that are created by the system, rather than directly by the user,
          * such as link values for standoff resource references.
          */
        val SystemUser = "http://www.knora.org/ontology/knora-base#SystemUser"

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
        val Name = "http://xmlns.com/foaf/0.1/name"
    }

}
