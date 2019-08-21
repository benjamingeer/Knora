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

# Generating Client API Code

The following route returns a Zip file containing generated client API
code for the specified target:

```
HTTP GET to http://host/clientapi/TARGET
```

Currently the only supported `TARGET` is `typescript`. For documentation
on defining client APIs, see
@ref:[Client API Code Generation Framework](../design/client-api/index.md).

To check whether the generated TypeScript code compiles, without actually
integrating it into `knora-api-js-lib`, use:

```
HTTP GET to http://host/clientapi/typescript?mock=true
```

This adds mock TypeScript library dependencies.
