<!---
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Interaction Between Sipi and DSP-API

## General Remarks

DSP-API and Sipi (Simple Image Presentation Interface) are two
**complementary** software projects. Whereas DSP-API deals with data that
is written to and read from a triplestore (metadata and annotations),
Sipi takes care of storing, converting and serving image files as well
as other types of files such as audio, video, or documents (binary files
it just stores and serves).

DSP-API and Sipi stick to a clear division of responsibility regarding
files: DSP-API knows about the names of files that are attached to
resources as well as some metadata and is capable of creating the URLs
for the client to request them from Sipi, but the whole handling of
files (storing, naming, organization of the internal directory
structure, format conversions, and serving) is taken care of by Sipi.

## Adding Files to DSP

A file is first uploaded to Sipi, then its metadata is submitted to
DSP. The implementation of this procedure is described in
[DSP-API and Sipi](../05-internals/design/api-v2/sipi.md). Instructions
for the client are given in
[Creating File Values](../03-endpoints/api-v2/editing-values.md#creating-file-values)
(for DSP-API v2) and in
[Adding Resources with Image Files](../03-endpoints/api-v1/adding-resources.md#adding-resources-with-image-files)
(for API v1).

## Retrieving Files from Sipi

### File URLs in API v2

In DSP-API v2, image file URLs are provided in [IIIF](https://iiif.io/) format. In the simple
[ontology schema](../03-endpoints/api-v2/introduction.md#api-schema), a file value is simply
a IIIF URL that can be used to retrieve the file from Sipi. In the complex schema,
it is a `StillImageFileValue` with additional properties that the client can use to construct
different IIIF URLs, e.g. at different resolutions. See the `knora-api` ontology for details.

### File URLs in API v1

In API v1, for each file value, DSP-API creates several Sipi URLs for accessing the file at different
resolutions:

```
"resinfo": {
   "locations": [
      {
         "duration": ​0,
         "nx": ​95,
         "path": "http://sipiserver:port/knora/incunabula_0000000002.jpg/full/max/0/default.jpg",
         "ny": ​128,
         "fps": ​0,
         "format_name": "JPEG",
         "origname": "ad+s167_druck1=0001.tif",
         "protocol": "file"
      },
      {
         "duration": ​0,
          "nx": ​82,
          "path": "http://sipiserver:port/knora/incunabula_0000000002.jp2/full/82,110/0/default.jpg",
          "ny": ​110,
          "fps": ​0,
          "format_name": "JPEG2000",
          "origname": "ad+s167_druck1=0001.tif",
          "protocol": "file"
      },
      {
          "duration": ​0,
          "nx": ​163,
          "path": "http://sipiserver:port/knora/incunabula_0000000002.jp2/full/163,219/0/default.jpg",
          "ny": ​219,
          "fps": ​0,
          "format_name": "JPEG2000",
          "origname": "ad+s167_druck1=0001.tif",
          "protocol": "file"
      }
      ...
   ],
"restype_label": "Seite",
"resclass_has_location": true,
```

Each of these paths has to be handled by the browser by making a call to
Sipi, obtaining the binary representation in the desired quality.

## Authentication of Users with Sipi

File access is restricted to users who have the permission to view the resource that the file is attached to.
In order to check whether a user has the permission to view a resource, Sipi needs to know the user's identity.
The identity is provided by DSP-API in the form of a JWT token.
This jwt token can be provided to Sipi in the following ways:

1. _recommended_ - The `Authorization` header of the request as a `Bearer` type token.
2. _deprecated_ - The value for a `token` query parameter of the request. This is unsafe a the token is visible in the
   URL.
3. _deprecated_ - As a session cookie as set by the dsp-api. For the session cookie to be sent to Sipi, both the DSP-API
   and Sipi endpoints need to
   be under the same domain, e.g., `api.example.com` and `iiif.example.com`.
