<!---
Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)

This file is part of DSP — DaSCH Service Platform.

DSP is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

DSP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with DSP. If not, see <http://www.gnu.org/licenses/>.
-->

# Data Formats in DSP-API

As explained in [What Is DSP and DSP-API (previous Knora)?](what-is-knora.md), the DSP stores data
in a small number of formats that are suitable for long-term preservation while
facilitating data reuse.

The following is a non-exhaustive list of data formats and how their content
can be stored and managed by DSP-API:

| Original Format                              | Format in DSP                                                                                                              |
|----------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| Text (XML, LaTeX, Microsoft Word, etc.)      | [Knora resources](../03-apis/api-v2/editing-resources.md) (RDF) containing [Standoff/RDF](standoff-rdf.md)            |
| Tabular data, including relational databases | [Knora resources](../03-apis/api-v2/editing-resources.md)                                                                  |
| Data in tree or graph structures             | [Knora resources](../03-apis/api-v2/editing-resources.md)                                                                  |
| Images (JPEG, PNG, etc.)                     | JPEG 2000 files stored by [Sipi](https://github.com/dhlab-basel/Sipi)                                                        |
| Audio and video files                        | Audio and video files stored by [Sipi](https://github.com/dhlab-basel/Sipi) (in archival formats to be determined)           |
| PDF                                          | Can be stored by Sipi, but data reuse is improved by extracting the text for storage as [Standoff/RDF](standoff-rdf.md) |
