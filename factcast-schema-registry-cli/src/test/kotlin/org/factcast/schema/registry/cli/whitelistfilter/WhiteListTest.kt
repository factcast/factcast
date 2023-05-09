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
package org.factcast.schema.registry.cli.whitelistfilter

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Paths

class WhiteListTest : StringSpec() {

    init {
        "positive match" {
            val projectPath = Paths.get("/parent")
            val whiteListConfig = listOf("/foo/**")
            val whiteList = WhiteList(projectPath, whiteListConfig)

            whiteList.matches(Paths.get("/parent/foo/bar")) shouldBe true
        }

        "no match" {
            val projectPath = Paths.get("/parent")
            val whiteListConfig = listOf("/foo/**")

            val whiteList = WhiteList(projectPath, whiteListConfig)

            whiteList.matches(Paths.get("/something/else/bar")) shouldBe false
        }
    }
}
