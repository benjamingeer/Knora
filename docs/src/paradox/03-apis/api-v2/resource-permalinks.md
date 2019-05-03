<!---
Copyright © 2015-2019 the contributors (see Contributors.md).

This file is part of Knora.

Knora is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Knora is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
-->

# Resource Permalinks

@@toc

Knora provides a permanent, citable URL for each resource. These
URLs use [Archival Resource Key (ARK) Identifiers](http://n2t.net/e/ark_ids.html),
and are designed to stay the same even if the resource itself is moved
from one Knora repository to another.

## Obtaining ARK URLs

A resource is always returned with two ARK URLs: one that will always refer
to the latest version of the resource (`knora-api:arkUrl`), and one that refers
specifically to the version being returned (`knora-api:versionArkUrl`).
For example:

```jsonld
{
  "@id" : "http://rdfh.ch/0803/2a6221216701",
  "@type" : "incunabula:book",
  "knora-api:arkUrl" : {
    "@type" : "xsd:anyURI",
    "@value" : "http://ark.dasch.swiss/ark:/72163/1/0803/2a6221216701W"
  },
  "knora-api:versionArkUrl" : {
    "@type" : "xsd:anyURI",
    "@value" : "http://ark.dasch.swiss/ark:/72163/1/0803/2a6221216701W.20180528T155203897Z"
  },
  "knora-api:attachedToProject" : {
    "@id" : "http://rdfh.ch/projects/0803"
  },
  "knora-api:attachedToUser" : {
    "@id" : "http://rdfh.ch/users/91e19f1e01"
  },
  "knora-api:creationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "2016-03-02T15:05:21Z"
  },
  "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser",
  "rdfs:label" : "Reise ins Heilige Land",
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "incunabula" : "http://0.0.0.0:3333/ontology/0803/incunabula/v2#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

For more information on getting past versions of resources, see
@ref:[Get a Full Representation of a Version of a Resource by IRI](reading-and-searching-resources.md#get-a-full-representation-of-a-version-of-a-resource-by-iri)
and
@ref:[Get the Version History of a Resource](reading-and-searching-resources.md#get-the-version-history-of-a-resource).

## Resolving Knora ARK URLs

A Knora ARK URL is intended to be resolved by the [Knora ARK resolver](https://github.com/dhlab-basel/ark-resolver).

## Knora ARK URL Format

The format of a Knora ARK URL is as follows:

```
http://HOST/ark:/NAAN/VERSION/PROJECT/RESOURCE_UUID[.TIMESTAMP]
```

For details, see @ref:[Archival Resource Key (ARK) Identifiers](../../05-internals/design/api-v2/ark.md).

For example, given the Knora resource IRI `http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ`,
and using the DaSCH's ARK resolver hostname and NAAN, the corresponding
ARK URL without a timestamp is:

```
http://ark.dasch.swiss/ark:/72163/1/0001/0C=0L1kORryKzJAJxxRyRQY
```

The same ARK URL with an optional timestamp is:

```
http://ark.dasch.swiss/ark:/72163/1/0001/0C=0L1kORryKzJAJxxRyRQY.20180604T085622513Z
```

Without a timestamp, a Knora ARK URL refers to the latest version of the
resource at the time when the URL is resolved.