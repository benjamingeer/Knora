/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.rdf.jenaimpl

import org.knora.webapi.feature.FeatureToggle
import org.knora.webapi.feature.ToggleStateOn
import org.knora.webapi.util.rdf.JsonLDUtilSpec

/**
 * Tests [[org.knora.webapi.messages.util.rdf.JsonLDUtil]] using the Jena RDF API.
 */
class JenaJsonLDUtilSpec extends JsonLDUtilSpec(FeatureToggle("jena-rdf-library", ToggleStateOn(1)))
