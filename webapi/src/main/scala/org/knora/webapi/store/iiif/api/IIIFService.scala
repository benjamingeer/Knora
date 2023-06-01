/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.api

import zio._
import zio.macros.accessible
import zio.nio.file.Path

import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages.DeleteTemporaryFileRequest
import org.knora.webapi.messages.store.sipimessages.GetFileMetadataRequest
import org.knora.webapi.messages.store.sipimessages.GetFileMetadataResponse
import org.knora.webapi.messages.store.sipimessages.IIIFServiceStatusResponse
import org.knora.webapi.messages.store.sipimessages.MoveTemporaryFileToPermanentStorageRequest
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileRequest
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileResponse
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.slice.admin.domain.service.Asset

@accessible
trait IIIFService {

  /**
   * Asks Sipi for metadata about a file, served from the 'knora.json' route.
   *
   * @param getFileMetadataRequest the request.
   * @return a [[GetFileMetadataResponse]] containing the requested metadata.
   */
  def getFileMetadata(getFileMetadataRequest: GetFileMetadataRequest): Task[GetFileMetadataResponse]

  /**
   * Asks Sipi to move a file from temporary storage to permanent storage.
   *
   * @param moveTemporaryFileToPermanentStorageRequestV2 the request.
   * @return a [[SuccessResponseV2]].
   */
  def moveTemporaryFileToPermanentStorage(
    moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest
  ): Task[SuccessResponseV2]

  /**
   * Asks Sipi to delete a temporary file.
   *
   * @param deleteTemporaryFileRequestV2 the request.
   * @return a [[SuccessResponseV2]].
   */
  def deleteTemporaryFile(deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest): Task[SuccessResponseV2]

  /**
   * Asks Sipi for a text file used internally by Knora.
   *
   * @param textFileRequest the request message.
   */
  def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): Task[SipiGetTextFileResponse]

  /**
   * Tries to access the IIIF Service.
   */
  def getStatus(): Task[IIIFServiceStatusResponse]

  /**
   * Downloads an asset from Sipi.
   * @param asset The asset to download.
   * @param targetDir The target directory in which the asset should be stored.
   * @param user The user who is downloading the asset.
   * @return The path to the downloaded asset. If the asset could not be downloaded, [[None]] is returned.
   */
  def downloadAsset(asset: Asset, targetDir: Path, user: UserADM): Task[Option[Path]]
}
