/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.contributors

import java.nio.file.{Files, Path}

import org.knora.webapi.CoreSpec
import org.knora.webapi.util.FileUtil

/**
  * Tests [[GenerateContributorsFile]].
  */
class GenerateContributorsFileSpec extends CoreSpec() {
  "The GenerateContributorsFile utility" should {
    "generate a contributors file" ignore { // GitHub returns an HTTP 403 (Forbidden) error when this is run on Travis without a GitHub API key.
      val tmpContributorsFile: Path = Files.createTempFile("TestContributors", ".md")
      tmpContributorsFile.toFile.deleteOnExit()
      GenerateContributorsFile.main(Array("-o", s"${tmpContributorsFile.toAbsolutePath}"))
      val fileContents = FileUtil.readTextFile(tmpContributorsFile)
      fileContents.contains("Benjamin Geer") should ===(true)
    }
  }
}
