/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.responders.v2

import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.sipimessages.{DeleteTemporaryFileRequestV2, GetImageMetadataRequestV2, GetImageMetadataResponseV2, MoveTemporaryFileToPermanentStorageRequestV2}
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil.{handleUnexpectedMessage, try2Message}

import scala.util.{Success, Try}

/**
  * Imitates [[MockSipiResponderV2]], with hard-coded responses.
  */
class MockSipiResponderV2 extends Responder {
    override def receive: Receive = {
        case getFileMetadataRequestV2: GetImageMetadataRequestV2 => try2Message(sender(), getFileMetadataV2(getFileMetadataRequestV2), log)
        case moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequestV2 => try2Message(sender(), moveTemporaryFileToPermanentStorageV2(moveTemporaryFileToPermanentStorageRequestV2), log)
        case deleteTemporaryFileRequestV2: DeleteTemporaryFileRequestV2 => try2Message(sender(), deleteTemporaryFileV2(deleteTemporaryFileRequestV2), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    private def getFileMetadataV2(getFileMetadataRequestV2: GetImageMetadataRequestV2): Try[GetImageMetadataResponseV2] =
        Success {
            GetImageMetadataResponseV2(
                originalFilename = "test2.tiff",
                originalMimeType = "image/tiff",
                width = 512,
                height = 256
            )
        }

    private def moveTemporaryFileToPermanentStorageV2(moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequestV2): Try[SuccessResponseV2] =
        Success(SuccessResponseV2("Moved file to permanent storage"))

    private def deleteTemporaryFileV2(deleteTemporaryFileRequestV2: DeleteTemporaryFileRequestV2): Try[SuccessResponseV2] =
        Success(SuccessResponseV2("Deleted temporary file."))
}
