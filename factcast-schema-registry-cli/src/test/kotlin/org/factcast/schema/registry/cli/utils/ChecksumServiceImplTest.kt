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
package org.factcast.schema.registry.cli.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
import org.factcast.schema.registry.cli.fs.FileSystemService
import java.nio.file.Paths

class ChecksumServiceImplTest : StringSpec() {
    private val fs = mockk<FileSystemService>()
    val uut = ChecksumServiceImpl(fs, ObjectMapper())

    init {
        "createMd5Hash from path" {
            val dummyPath = Paths.get(".")
            val content = "foo".toByteArray()

            every { fs.readToBytes(dummyPath) } returns content

            uut.createMd5Hash(dummyPath) shouldBe "acbd18db4cc2f85cedef654fccc4a4d8"

            verifyAll { fs.readToBytes(dummyPath) }
        }

        "createMd5Hash from Json Node" {
            val dummyJson = JsonNodeFactory.instance.objectNode()
            uut.createMd5Hash(dummyJson) shouldBe "99914b932bd37a50b983c5e7c90ae93b"
        }
    }
}
