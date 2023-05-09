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
import io.kotest.matchers.shouldBe
import java.nio.file.Paths

class EventFolderKtTest : StringSpec() {
    val path = Paths.get("eventA")
    val descriptionPath = Paths.get("index.md")

    init {
        "toEvent" {
            val eventFolder = EventFolder(path, emptyList(), descriptionPath, emptyList())
            val event = eventFolder.toEvent()

            event.type shouldBe "eventA"
            event.descriptionPath shouldBe descriptionPath
        }
    }
}
