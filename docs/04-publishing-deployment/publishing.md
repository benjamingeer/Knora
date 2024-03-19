<!---
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Publishing

DSP is published as a set of [Docker](https://www.docker.com) images under the
[DaSCH Dockerhub Organization](https://hub.docker.com/u/daschswiss).

The following Docker images are published:

- [DSP-API](https://hub.docker.com/r/daschswiss/knora-api)
- [Sipi](https://hub.docker.com/r/daschswiss/knora-sipi) (includes DSP's specific Sipi scripts)
- [DSP-APP](https://hub.docker.com/r/daschswiss/dsp-app)

DSP's Docker images are published automatically through Github CI each time a
pull-request is merged into the `main` branch.

Each image is tagged with a version number, which is derived by
using the result of `git describe`. The describe version is built from the
`last tag + number of commits since tag + short hash`, e.g., `8.0.0-7-ga7827e9`.

The images can be published locally by running:

```bash
make docker-build
```

or to Dockerhub:

```bash
make docker-publish
```
