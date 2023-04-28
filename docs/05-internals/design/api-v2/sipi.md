<!---
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# DSP-API and Sipi

## Configuration

The DSP-API specific configuration and scripts for Sipi are in the
`sipi` subdirectory of the DSP-API source tree. See the `README.md` for
instructions on how to start Sipi with DSP-API.

## Lua Scripts

DSP-API v2 uses custom Lua scripts to control Sipi. These scripts can be
found in `sipi/scripts` in the DSP-API source tree.

Each of these scripts expects a [JSON Web Token](https://jwt.io/) in the
URL parameter `token`. In all cases, the token must be signed by DSP-API,
it must have an expiration date and not have expired, its issuer must equal 
the hostname and port of the API, and its audience must include `Sipi`. 
The other contents of the expected tokens are described below.

### upload.lua

The `upload.lua` script is available at Sipi's `upload` route. It processes one
or more file uploads submitted to Sipi. It converts uploaded images to JPEG 2000
format, and stores them in Sipi's `tmp` directory. The usage of this script is described in
[Upload Files to Sipi](../../../03-endpoints/api-v2/editing-values.md#upload-files-to-sipi).

### upload_without_processing.lua

The `upload_without_processing.lua` script is available at Sipi's `upload_without_processing` route. 
It receives files submitted to Sipi but does not process them. 
Instead, it stores them as is in Sipi's `tmp` directory.

### store.lua

The `store.lua` script is available at Sipi's `store` route. It moves a file
from temporary to permanent storage. It expects an HTTP `POST` request containing
`application/x-www-form-urlencoded` data with the parameters `prefix` (the
project shortcode) and `filename` (the internal Sipi-generated filename of the file
to be moved).

The JWT sent to this script must contain the key `knora-data`, whose value
must be a JSON object containing:

- `permission`: must be `StoreFile`
- `prefix`: the project shortcode submitted in the form data
- `filename`: the filename submitted in the form data

### delete_temp_file.lua

The `delete_temp_file.lua` script is available at Sipi's `delete_temp_file` route.
It is used only if DSP-API rejects a file value update request. It expects an
HTTP `DELETE` request, with a filename as the last component of the URL.

The JWT sent to this script must contain the key `knora-data`, whose value
must be a JSON object containing:

- `permission`: must be `DeleteTempFile`
- `filename`: must be the same as the filename submitted in the URL

### clean_temp_dir.lua
The `clean_temp_dir.lua` script is available at Sipi's `clean_temp_dir` route.
When called, it deletes old temporary files from `tmp` and (recursively) from any subdirectories. 
The maximum allowed age of temporary files can be set in Sipi's configuration file, 
using the parameter `max_temp_file_age`, which takes a value in seconds.

The `clean_temp_dir` route requires basic authentication.

## SipiConnector

In DSP-API, the `org.knora.webapi.iiif.SipiConnector` handles all communication
with Sipi. It blocks while processing each request, to ensure that the number of
concurrent requests to Sipi is not greater than
`akka.actor.deployment./storeManager/iiifManager/sipiConnector.nr-of-instances`.
If it encounters an error, it returns `SipiException`.

## The Image File Upload Workflow

1. The client uploads an image file to the `upload` route, which runs
  `upload.lua`. The image is converted to JPEG 2000 and stored in Sipi's `tmp`
  directory. In the response, the client receives the JPEG 2000's unique,
  randomly generated filename.
2. The client submits a JSON-LD request to a DSP-API route (`/v2/values` or `/v2/resources`)
   to create or change a file value. The request includes Sipi's internal filename.
3. During parsing of this JSON-LD request, a `StillImageFileValueContentV2`
   is constructed to represent the file value. During the construction of this
   object, a `GetFileMetadataRequestV2` is sent to `SipiConnector`, which
   uses Sipi's built-in `knora.json` route to get the rest of the file's
   metadata.
4. A responder (`ResourcesResponderV2` or `ValuesResponderV2`) validates
   the request and updates the triplestore. (If it is `ResourcesResponderV2`,
   it asks `ValuesResponderV2` to generate SPARQL for the values.)
5. The responder that did the update calls `ValueUtilV2.doSipiPostUpdate`.
   If the triplestore update was successful, this method sends
   `MoveTemporaryFileToPermanentStorageRequestV2` to `SipiConnector`, which
   makes a request to Sipi's `store` route. Otherwise, the same method sends
   `DeleteTemporaryFileRequestV2` to `SipiConnector`, which makes a request
   to Sipi's `delete_temp_file` route.

If the request to DSP-API cannot be parsed, the temporary file is not deleted
immediately, but it will be deleted during the processing of a subsequent
request by Sipi's `upload` route.

If Sipi's `store` route fails, DSP-API returns the `SipiException` to the client.
In this case, manual intervention may be necessary to restore consistency
between DSP-API and Sipi.

If Sipi's `delete_temp_file` route fails, the error is not returned to the client,
because there is already a DSP-API error that needs to be returned to the client.
In this case, the Sipi error is simply logged.
