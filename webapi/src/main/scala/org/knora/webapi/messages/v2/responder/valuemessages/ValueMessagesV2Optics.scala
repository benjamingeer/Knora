/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import monocle.*
import monocle.macros.*

import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.LicenseIdentifier
import org.knora.webapi.slice.admin.domain.model.LicenseUri

object ValueMessagesV2Optics {

  object FileValueV2Optics {

    val copyrightHolderOption: Lens[FileValueV2, Option[CopyrightHolder]] =
      GenLens[FileValueV2](_.copyrightHolder)

    val licenseIdentifierOption: Lens[FileValueV2, Option[LicenseIdentifier]] =
      GenLens[FileValueV2](_.licenseIdentifier)

    val licenseUriOption: Lens[FileValueV2, Option[LicenseUri]] = GenLens[FileValueV2](_.licenseUri)

  }

  object FileValueContentV2Optics {

    val fileValueV2: Lens[FileValueContentV2, FileValueV2] =
      Lens[FileValueContentV2, FileValueV2](_.fileValue)(fv => {
        case vc: MovingImageFileValueContentV2        => vc.copy(fileValue = fv)
        case vc: StillImageFileValueContentV2         => vc.copy(fileValue = fv)
        case vc: AudioFileValueContentV2              => vc.copy(fileValue = fv)
        case vc: DocumentFileValueContentV2           => vc.copy(fileValue = fv)
        case vc: StillImageExternalFileValueContentV2 => vc.copy(fileValue = fv)
        case vc: ArchiveFileValueContentV2            => vc.copy(fileValue = fv)
        case vc: TextFileValueContentV2               => vc.copy(fileValue = fv)
      })
    val copyrightHolderOption: Lens[FileValueContentV2, Option[CopyrightHolder]] =
      fileValueV2.andThen(FileValueV2Optics.copyrightHolderOption)
    val licenseIdentifierOption: Lens[FileValueContentV2, Option[LicenseIdentifier]] =
      fileValueV2.andThen(FileValueV2Optics.licenseIdentifierOption)
    val licenseUriOption: Lens[FileValueContentV2, Option[LicenseUri]] =
      fileValueV2.andThen(FileValueV2Optics.licenseUriOption)
  }
}
