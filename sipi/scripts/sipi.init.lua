-- * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

require "get_knora_session"

-------------------------------------------------------------------------------
-- This function is being called from sipi before the file is served.
-- DSP-API is called to ask for the user's permissions on the file
-- Parameters:
--    prefix: This is the prefix that is given on the IIIF url
--    identifier: the identifier for the image
--    cookie: The cookie that may be present
--
-- Returns:
--    permission:
--       'allow' : the view is allowed with the given IIIF parameters
--       'restrict:watermark=<path-to-watermark>' : Add a watermark
--       'restrict:size=<iiif-size-string>' : reduce size/resolution
--       'deny' : no access!
--    filepath: server-path where the master file is located
-------------------------------------------------------------------------------
function pre_flight(prefix, identifier, cookie)
    server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - pre_flight called in sipi.init.lua", server.loglevel.LOG_DEBUG)

    if config.prefix_as_path then
        filepath = config.imgroot .. '/' .. prefix .. '/' .. identifier
    else
        filepath = config.imgroot .. '/' .. identifier
    end

    server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - pre_flight - filepath: " .. filepath, server.loglevel.LOG_DEBUG)

    if prefix == "thumbs" then
        -- always allow thumbnails
        return 'allow', filepath
    end

    if prefix == "tmp" then
        -- always allow access to tmp folder
        return 'allow', filepath
    end

    dsp_cookie_header = nil

    if cookie ~= '' then

        -- tries to extract the DSP session name and id from the cookie:
        -- gets the digits between "sid=" and the closing ";" (only given in case of several key value pairs)
        -- returns nil if it cannot find it
        session = get_session_id(cookie)

        if session == nil or session["name"] == nil or session["id"] == nil then
            -- no session could be extracted
            server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - cookie key is invalid: " .. cookie, server.loglevel.LOG_ERR)
        else
            dsp_cookie_header = { Cookie = session["name"] .. "=" .. session["id"] }
            server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - pre_flight - dsp_cookie_header: " ..
                dsp_cookie_header["Cookie"], server.loglevel.LOG_DEBUG)
        end
    end

    --
    -- Allows to set SIPI_WEBAPI_HOSTNAME environment variable and use its value.
    --
    local webapi_hostname = os.getenv("SIPI_WEBAPI_HOSTNAME")
    if webapi_hostname == nil then
        webapi_hostname = config.knora_path
    end
    server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - pre_flight - webapi_hostname: " .. webapi_hostname,
        server.loglevel.LOG_DEBUG)

    --
    -- Allows to set SIPI_WEBAPI_PORT environment variable and use its value.
    --
    local webapi_port = os.getenv("SIPI_WEBAPI_PORT")
    if webapi_port == nil then
        webapi_port = config.knora_port
    end
    server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - pre_flight - webapi_port: " .. webapi_port, server.loglevel.LOG_DEBUG)

    api_url = 'http://' .. webapi_hostname .. ':' .. webapi_port .. '/admin/files/' .. prefix .. '/' .. identifier

    server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - pre_flight - api_url: " .. api_url, server.loglevel.LOG_DEBUG)

    success, result = server.http("GET", api_url, dsp_cookie_header, 5000)

    -- check HTTP request was successful
    if not success then
        server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - pre_flight - Server.http() failed: " .. result,
            server.loglevel.LOG_ERR)
        return 'deny'
    end

    if result.status_code ~= 200 then
        server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - pre_flight - DSP-API returned HTTP status code " ..
            result.status_code, server.loglevel.LOG_ERR)
        server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - result body: " .. result.body, server.loglevel.LOG_ERR)
        return 'deny'
    end

    server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - pre_flight - response body: " .. tostring(result.body),
        server.loglevel.LOG_DEBUG)

    success, response_json = server.json_to_table(result.body)
    if not success then
        server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - Server.http() failed: " .. response_json, server.loglevel.LOG_ERR)
        return 'deny'
    end

    server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - pre_flight - permission code: " .. response_json.permissionCode,
        server.loglevel.LOG_DEBUG)

    if response_json.permissionCode == 0 then
        -- no view permission on file
        return 'deny'
    elseif response_json.permissionCode == 1 then
        -- restricted view permission on file
        -- either watermark or size (depends on project, should be returned with permission code by Sipi responder)
        -- currently, only size is used

        local restrictedViewSize

        if response_json.restrictedViewSettings ~= nil then
            server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - pre_flight - restricted view settings - watermark: " ..
                tostring(response_json.restrictedViewSettings.watermark), server.loglevel.LOG_DEBUG)

            if response_json.restrictedViewSettings.size ~= nil then
                server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - pre_flight - restricted view settings - size: " ..
                    tostring(response_json.restrictedViewSettings.size), server.loglevel.LOG_DEBUG)
                restrictedViewSize = response_json.restrictedViewSettings.size
            else
                server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - pre_flight - using default restricted view size",
                    server.loglevel.LOG_DEBUG)
                restrictedViewSize = config.thumb_size
            end
        else
            server.log(os.date("!%Y-%m-%dT%H:%M:%S") .. " - pre_flight - using default restricted view size",
                server.loglevel.LOG_DEBUG)
            restrictedViewSize = config.thumb_size
        end

        return {
            type = 'restrict',
            size = restrictedViewSize
        }, filepath
    elseif response_json.permissionCode >= 2 then
        -- full view permissions on file
        return 'allow', filepath
    else
        -- invalid permission code
        return 'deny'
    end
end

-------------------------------------------------------------------------------
