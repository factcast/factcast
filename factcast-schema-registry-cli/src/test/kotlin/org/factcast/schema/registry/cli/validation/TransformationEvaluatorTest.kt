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
package org.factcast.schema.registry.cli.validation

import com.fasterxml.jackson.databind.JsonNode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.factcast.schema.registry.cli.domain.Event
import org.factcast.schema.registry.cli.domain.Namespace
import org.factcast.schema.registry.cli.domain.Transformation
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.store.registry.transformation.chains.TransformationChain
import org.factcast.store.registry.transformation.chains.Transformer
import java.io.File

class TransformationEvaluatorTest : StringSpec() {

    val dummyData = mockk<JsonNode>()
    val resultData = mockk<JsonNode>()

    val transformer = mockk<Transformer>(relaxed = true)
    val fs = mockk<FileSystemService>(relaxed = true)
    val ns = mockk<Namespace>()
    val event = mockk<Event>()
    val transformation = mockk<Transformation>(relaxed = true)
    val dummyFile = mockk<File>()
    val dummyTransformation = "function(e) {return  e}"
    val skippedDummyTransformation = "/*CLI IGNORE*/ function(e) {return  e}"

    val uut = TransformationEvaluator(transformer, fs)

    init {
        "evaluate" {
            val chainSlot = slot<TransformationChain>()

            every { ns.name } returns "ns"
            every { event.type } returns "type"
            every { transformation.from } returns 1
            every { transformation.to } returns 2
            every { transformation.transformationPath.toFile() } returns dummyFile

            every {
                fs.readToString(transformation.transformationPath.toFile())
            } returns dummyTransformation

            every { transformer.transform(capture(chainSlot), eq(dummyData)) } returns resultData

            val result = uut.evaluate(ns, event, transformation, dummyData)

            result shouldBe resultData

            chainSlot.captured.run {
                fromVersion() shouldBe 1
                toVersion() shouldBe 2
                id() shouldBe "no-real-meaning"
                key().ns() shouldBe "ns"
                key().type() shouldBe "type"
            }

            verify {
                fs.readToString(transformation.transformationPath.toFile())
                transformer.transform(any(), eq(dummyData))
            }
        }

        "skipped" {
            val chainSlot = slot<TransformationChain>()

            every { ns.name } returns "ns"
            every { event.type } returns "type"
            every { transformation.from } returns 1
            every { transformation.to } returns 2
            every { transformation.transformationPath.toFile() } returns dummyFile

            every {
                fs.readToString(transformation.transformationPath.toFile())
            } returns skippedDummyTransformation

            every { transformer.transform(capture(chainSlot), eq(dummyData)) } returns resultData

            uut.evaluate(ns, event, transformation, dummyData).shouldBeNull()

            verify {
                fs.readToString(transformation.transformationPath.toFile())
                transformer.transform(any(), eq(dummyData))
            }
        }
    }
}
