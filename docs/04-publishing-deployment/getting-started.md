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

# Getting Started with Knora

Running Knora locally or on a server requires [Docker](https://www.docker.com), which
can be freely downloaded. Please follow the instructions for installing
[Docker Desktop](https://www.docker.com/products/docker-desktop).

Additional software:

- [Apple Xcode](https://itunes.apple.com/us/app/xcode/id497799835)
- git
- expect
- sbt
- java 11

These can be easily installed on macOS using [Homebrew](https://brew.sh):

```bash
$ brew install git
$ brew install expect
$ brew install sbt
```

To install Adoptopenjdk Java 11 with [Homebrew](https://brew.sh):

```bash
$ brew tap AdoptOpenJDK/openjdk
$ brew cask install AdoptOpenJDK/openjdk/adoptopenjdk11
```

To pin the version of Java, please add this environment variable to you startup script (bashrc, etc.):

```
export JAVA_HOME=`/usr/libexec/java_home -v 11`
```

## Choosing a Triplestore

Knora requires a standards-compliant
[RDF](https://www.w3.org/TR/rdf11-primer/) triplestore. A number of
triplestore implementations are available, including [free
software](http://www.gnu.org/philosophy/free-sw.en.html) as well as
proprietary options.

Knora is designed to work with any standards-compliant
triplestore. It is primarily tested with
[Apache Jena Fuseki](https://jena.apache.org), an open source triplestore.

Built-in support and configuration for a high-performance, proprietary
triplestore [Ontotext GraphDB](http://ontotext.com/products/graphdb/) is
provided but unmaintained (GraphDB must be licensed separately by the user).
Other triplestores are planned.

## Running the Knora-Stack

Use `git` to clone the Knora repository from [Github](https://github.com/dasch-swiss/knora-api).

The following environment variables are **optional**:

- `KNORA_DB_HOME`: sets the path to the folder where the triplestore will store
the database files
- `KNORA_DB_IMPORT`: sets the path to the import directory accessible from
inside the docker image

```bash
$ export KNORA_DB_IMPORT=/path/to/some/folder
$ export KNORA_DB_HOME=/path/to/some/other_folder
```

Then from inside the cloned `Knora-API` repository folder, run:

```bash
$ make stack-up
```

## Creating Repositories and Loading Test Data

To create a test repository called `knora-test` and load test data, run:

```
$ make init-db-test
```

The scripts called by `make` can be found under `webapi/scripts`. You can
create your own scripts based on these scripts, to create new
repositories and optionally to load existing Knora-compliant RDF data
into them.

If you need to reload the test data, you need to stop and **delete** the
running Apache Fuseki instance. **Make sure you don't delete important data.**
To stop the instance and delete the repository, run the following command:

```
$ make stack-down-delete-volumes
```

after which you can start the stack again with `make stack-up`, recreate
the repository and load the data with `make init-db-test`.
