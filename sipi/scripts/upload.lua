-- Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
--
-- This file is part of Knora.
--
-- Knora is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published
-- by the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- Knora is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU Affero General Public License for more details.
--
-- You should have received a copy of the GNU Affero General Public
-- License along with Knora.  If not, see <http://www.gnu.org/licenses/>.

--
-- Upload route for binary files (currently only images) to be used with Knora.
--

require "file_info"
require "send_response"
require "jwt"
require "clean_temp_dir"
require "util"

--------------------------------------------------------------------------
-- Calculate the SHA256 checksum of a file using the operating system tool
--------------------------------------------------------------------------
function file_checksum(path)
    local handle = io.popen("/usr/bin/sha256sum " .. path)
    local checksum_orig = handle:read("*a")
    handle:close()
    return string.match(checksum_orig, "%w*")
end
--------------------------------------------------------------------------


-- Buffer the response (helps with error handling).
local success, error_msg
success, error_msg = server.setBuffer()
if not success then
    send_error(500, "server.setBuffer() failed: " .. error_msg)
    return
end

-- Check for a valid JSON Web Token from Knora.
local token = get_knora_token()
if token == nil then
    return
end

-- Check that the temp folder is created
local tmpFolder = config.imgroot .. '/tmp/'
local exists
success, exists = server.fs.exists(tmpFolder)
if not success then -- tests server.fs.exists
    -- fs.exist was not run successful. This does not mean, that the tmp folder is not there.
    send_error(500, "server.fs.exists() failed: " .. exists)
    return
end
if not exists then -- checks the response of server.fs.exists
    -- tmp folder does not exist
    server.log("temp folder missing: " .. tmpFolder, server.loglevel.LOG_ERR)
    success, error_msg = server.fs.mkdir(tmpFolder, 511)
    if not success then
        send_error(500, "server.fs.mkdir() failed: " .. error_msg)
        return
    end
end

-- A table of data about each file that was uploaded.
local file_upload_data = {}

-- Process the uploaded files.
for file_index, file_params in pairs(server.uploads) do
    --
    -- Check that the file's MIME type is supported.
    --
    local mime_info
    success, mime_info = server.file_mimetype(file_index)
    if not success then
        send_error(500, "server.file_mimetype() failed: " .. tostring(mime_info))
        return
    end
    local mime_type = mime_info["mimetype"]
    if mime_type == nil then
        send_error(400, "Could not determine MIME type of uploaded file")
        return
    end

    --
    -- get some more MIME type related information
    --
    local original_filename = file_params["origname"]
    local file_info = get_file_info(original_filename, mime_type)
    if file_info == nil then
        send_error(400, "Unsupported MIME type: " .. tostring(mime_type))
        return
    end

    -- Make a random filename for the temporary file.
    local uuid62
    success, uuid62 = server.uuid62()
    if not success then
        send_error(500, "server.uuid62() failed: " .. uuid62)
        return
    end


    -- Construct response data about the file that was uploaded.
    local media_type = file_info["media_type"]

    -- Add a subdirectory path if necessary.
    local tmp_storage_filename
    if media_type == IMAGE then
        tmp_storage_filename = uuid62 .. ".jp2"
    else
        tmp_storage_filename = uuid62 .. "." .. file_info["extension"]
    end
    local hashed_tmp_storage_filename
    success, hashed_tmp_storage_filename = helper.filename_hash(tmp_storage_filename)
    if not success then
        send_error(500, "helper.filename_hash() failed: " .. tostring(hashed_tmp_storage_filename))
        return
    end

    -- filename for sidecar file
    local tmp_storage_sidecar = uuid62 .. ".info"
    local hashed_tmp_storage_sidecar
    success, hashed_tmp_storage_sidecar = helper.filename_hash(tmp_storage_sidecar)
    if not success then
        send_error(500, "helper.filename_hash() failed: " .. tostring(hashed_tmp_storage_sidecar))
        return
    end

    -- filename for original file copy
    local tmp_storage_original = uuid62 .. "." .. file_info["extension"] .. ".orig"
    local hashed_tmp_storage_original
    success, hashed_tmp_storage_original = helper.filename_hash(tmp_storage_original)
    if not success then
        send_error(500, "helper.filename_hash() failed: " .. tostring(hashed_tmp_storage_original))
        return
    end

    local tmp_storage_file_path = config.imgroot .. '/tmp/' .. hashed_tmp_storage_filename
    local tmp_storage_sidecar_path = config.imgroot .. '/tmp/' .. hashed_tmp_storage_sidecar
    local tmp_storage_original_path = config.imgroot .. '/tmp/' .. hashed_tmp_storage_original

    -- Create a IIIF base URL for the converted file.
    local tmp_storage_url = get_external_protocol() .. "://" .. get_external_hostname() .. ":" .. get_external_port() .. '/tmp/' .. tmp_storage_filename

    -- Copy original file also to tmp
    success, error_msg = server.copyTmpfile(file_index, tmp_storage_original_path)
    if not success then
        send_error(500, "server.copyTmpfile() failed for " .. tostring(tmp_storage_original_path) .. ": " .. tostring(error_msg))
        return
    end

    -- Is this an image file?
    if media_type == IMAGE then
        --
        -- Yes. Create a new Lua image object. This reads the image into an
        -- internal in-memory representation independent of the original image format.
        --
        local uploaded_image
        success, uploaded_image = SipiImage.new(file_index, {original = original_filename, hash = "sha256"})
        if not success then
            send_error(500, "SipiImage.new() failed: " .. tostring(uploaded_image))
            return
        end

        -- Check that the file extension is correct for the file's MIME type.
        local check
        success, check = uploaded_image:mimetype_consistency(mime_type, original_filename)
        if not success then
            send_error(500, "upload.lua: uploaded_image:mimetype_consistency() failed: " .. check)
            return
        end
        if not check then
            send_error(400, MIMETYPES_INCONSISTENCY)
            return
        end

        -- Convert the image to JPEG 2000 format.
        success, error_msg = uploaded_image:write(tmp_storage_file_path)
        if not success then
            send_error(500, "uploaded_image:write() failed for " .. tostring(tmp_storage_file_path) .. ": " .. tostring(error_msg))
            return
        end
        server.log("upload.lua: wrote image file to " .. tmp_storage_file_path, server.loglevel.LOG_DEBUG)
    else
        -- It's not an image file. Just move it to its temporary storage location.
        success, error_msg = server.copyTmpfile(file_index, tmp_storage_file_path)
        if not success then
            send_error(500, "server.copyTmpfile() failed for " .. tostring(tmp_storage_file_path) .. ": " .. tostring(error_msg))
            return
        end
        server.log("upload.lua: wrote non-image file to " .. tmp_storage_file_path, server.loglevel.LOG_DEBUG)
    end

    --
    -- Calculate checksum of original file
    --
    local checksum_original = file_checksum(tmp_storage_original_path)

    --
    -- Calculate checksum of derivative file
    --
    local checksum_derivative = file_checksum(tmp_storage_file_path)

    --
    -- prepare and write sidecar file
    --
    local sidecar_data = {
        originalFilename = original_filename,
        checksumOriginal = checksum_original,
        originalInternalFilename = hashed_tmp_storage_original,
        internalFilename = tmp_storage_filename,
        checksumDerivative = checksum_derivative
    }
    local success, jsonstr = server.table_to_json(sidecar_data)
    if not success then
        send_error(500, "Couldn't create json string!")
        return
    end
    sidecar = io.open(tmp_storage_sidecar_path, "w")
    sidecar:write(jsonstr)
    sidecar:close()

    local this_file_upload_data = {
        internalFilename = tmp_storage_filename,
        originalFilename = original_filename,
        temporaryUrl = tmp_storage_url,
        fileType = media_type,
        sidecarFile = tmp_storage_sidecar,
        checksumOriginal = checksum_orig,
        checksumDerivative = checksum_derivative
    }
    file_upload_data[file_index] = this_file_upload_data
end

-- Clean up old temporary files.
clean_temp_dir()
-- Return the file upload data in the response.
local response = {}
response["uploadedFiles"] = file_upload_data
send_success(response)
