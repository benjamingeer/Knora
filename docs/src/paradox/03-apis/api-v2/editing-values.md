<!---
Copyright © 2015-2018 the contributors (see Contributors.md).

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

# Editing Values

@@toc

## Creating a Value

To create a value in an existing resource, use this route:

```
HTTP POST to http://host/v2/values
```

The body of the request is a JSON-LD document in the
@ref:[complex API schema](introduction.md#api-schema), specifying the resource's IRI and type,
the resource property, and the content of the value. The representation of the value
is the same as when it is returned in a `GET` request, except that its IRI is not
given. For example, to create an integer value:

```jsonld
{
  "@id" : "http://rdfh.ch/0001/a-thing",
  "@type" : "anything:Thing",
  "anything:hasInteger" : {
    "@type" : "knora-api:IntValue",
    "knora-api:intValueAsInt" : 4
  },
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

Each value can have a comment, given in `knora-api:valueHasComment`. For example:

```jsonld
{
  "@id" : "http://rdfh.ch/0001/a-thing",
  "@type" : "anything:Thing",
  "anything:hasInteger" : {
    "@type" : "knora-api:IntValue",
    "knora-api:intValueAsInt" : 4,
    "knora-api:valueHasComment" : "This is a comment."
  },
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

Permissions for the new value can be given by adding `knora-api:hasPermissions`. For example:

```jsonld
{
  "@id" : "http://rdfh.ch/0001/a-thing",
  "@type" : "anything:Thing",
  "anything:hasInteger" : {
    "@type" : "knora-api:IntValue",
    "knora-api:intValueAsInt" : 4,
    "knora-api:hasPermissions" : "CR knora-base:Creator|V http://rdfh.ch/groups/0001/thing-searcher"
  },
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

The format of the object of `knora-api:hasPermissions` is described in
@ref:[Permissions](../../02-knora-ontologies/knora-base.md#permissions).

If permissions are not given, configurable default permissions are used
(see @ref:[Default Object Access Permissions](../../05-internals/design/administration.md#default-object-access-permissions)).

To create a value, the user must have **modify permission** on the containing resource.

The response is a JSON-LD document containing only `@id` and `@type`, returning the IRI
and type of the value that was created.

### Creating a Link Between Resources

To create a link, you must create a `knora-api:LinkValue`, which represents metadata about the
link. The property that connects the resource to the `LinkValue` is a link value property, whose
name is constructed by adding `Value` to the name of the link property
(see @ref:[Links Between Resources](../../02-knora-ontologies/knora-base.md#links-between-resources)).
The triple representing the direct link between the resources is created automatically. For
example, if the link property that should connect the resources is `anything:hasOtherThing`,
we can create a link like this:

```jsonld
{
  "@id" : "http://rdfh.ch/0001/a-thing",
  "@type" : "anything:Thing",
  "anything:hasOtherThingValue" : {
    "@type" : "knora-api:LinkValue",
    "knora-api:linkValueHasTargetIri" : {
      "@id" : "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A"
    }
  },
  "@context" : {
    "xsd" : "http://www.w3.org/2001/XMLSchema#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

As with ordinary values, permissions on links can be specified by adding `knora-api:hasPermissions`.

### Creating a Text Value Without Standoff Markup

Use the predicate `knora-api:valueAsString` of `knora-api:TextValue`:

```jsonld
{
  "@id" : "http://rdfh.ch/0001/a-thing",
  "@type" : "anything:Thing",
  "anything:hasText" : {
    "@type" : "knora-api:TextValue",
    "knora-api:valueAsString" : "This is a text without markup."
  },
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

### Creating a Text Value with Standoff Markup

Currently, the only way to create a text value with standoff markup is to submit it in XML format
using an @ref:[XML-to-standoff mapping](xml-to-standoff-mapping.md). For example, suppose we use
the standard mapping, `http://rdfh.ch/standoff/mappings/StandardMapping`. We can then make an XML
document like this:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<text>
   This text links to another <a class="salsah-link" href="http://rdfh.ch/0001/another-thing">resource</a>.
</text>
```

This document can then be embedded in a JSON-LD request, using the predicate `knora-api:textValueAsXml`:

```jsonld
{
  "@id" : "http://rdfh.ch/0001/a-thing",
  "@type" : "anything:Thing",
  "anything:hasText" : {
    "@type" : "knora-api:TextValue",
    "knora-api:textValueAsXml" : "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text>\n   This text links to another <a class=\"salsah-link\" href=\"http://rdfh.ch/0001/another-thing\">resource</a>.\n</text>",
    "knora-api:textValueHasMapping" : {
      "@id": "http://rdfh.ch/standoff/mappings/StandardMapping"
    }
  },
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

Note that quotation marks and line breaks in the XML must be escaped, and that the IRI of the mapping must be
provided.

## Updating a Value

To update a value, use this route:

```
HTTP PUT to http://host/v2/values
```

Updating a value means creating a new version of an existing value. The new version
will have a different IRI. The request is the same as for creating a value, except that
the `@id` of the current value version is given. For example, to update an integer value:

```jsonld
{
  "@id" : "http://rdfh.ch/0001/a-thing",
  "@type" : "anything:Thing",
  "anything:hasInteger" : {
    "@id" : "http://rdfh.ch/0001/a-thing/values/vp96riPIRnmQcbMhgpv_Rg",
    "@type" : "knora-api:IntValue",
    "knora-api:intValueAsInt" : 5
  },
  "@context" : {
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

The value can be given a comment by using `knora-api:valueHasComment`. To change only
the comment of a value, you can resubmit the existing value with the updated comment.

Permissions can be specified by adding `knora-api:hasPermissions`. Otherwise, the new
version has the same permissions as the previous one. To change the permissions
on a value, the user must have **change rights permission** on the value.

To update a link, the user must have **modify permission** on the containing resource as
well as on the value.

The response is a JSON-LD document containing only `@id` and `@type`, returning the IRI
and type of the new value version.

If you submit an outdated value ID in a request to update a value, the response will be
an HTTP 404 (Not Found) error.

## Deleting a Value

Knora does not normally delete values; instead, it marks them as deleted, which means
that they do not appear in normal query results.

To mark a value as deleted, use this route:

```
HTTP DELETE to http://host/v2/values/RESOURCE_IRI/PROPERTY_IRI/VALUE_IRI[?deleteComment=DELETE_COMMENT]
```

The resource IRI, property IRI, and value IRI must be URL-encoded. If the value
is a link value, the property must be a link value property.

The optional URL parameter `deleteComment` specifies a comment to be attached to the
value, explaining why it has been marked as deleted. The comment must also be URL-encoded.
