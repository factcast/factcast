/*
 * Copyright © 2017-2023 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.schema.registry.cli.project.structure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Paths

class EventVersionFolderTest : StringSpec() {
    val path = Paths.get("1")
    val dummyPath = Paths.get(".")
    val examplePath = Paths.get("example.json")

    init {
        "toEventVersion" {
            val versionFolder = EventVersionFolder(path, dummyPath, dummyPath, listOf(examplePath))
            val eventVersion = versionFolder.toEventVersion()

            eventVersion.version shouldBe 1
            eventVersion.descriptionPath shouldBe dummyPath
            eventVersion.schemaPath shouldBe dummyPath
            eventVersion.examples shouldHaveSize 1
            eventVersion.examples[0].name shouldBe "example.json"
            eventVersion.examples[0].exampleFilePath shouldBe examplePath
        }
    }
}
