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
package org.factcast.schema.registry.cli.validation.validators.impl

import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Paths
import javax.validation.ConstraintValidatorContext

class ValidTransformationFolderValidatorTest : StringSpec() {
    val uut = ValidTransformationFolderValidator()

    val ctx = mockk<ConstraintValidatorContext>()

    init {
        "isValid" {
            every { ctx.defaultConstraintMessageTemplate } returns "foo"

            table(
                headers("path", "validity"),
                row(Paths.get("1-2"), true),
                row(Paths.get("1-v2"), false),
                row(Paths.get("v1-2"), false),
                row(Paths.get("1"), false),
                row(Paths.get("1-2-3"), false)
            ).forAll { path, valid ->
                uut.isValid(path, ctx) shouldBe valid
            }
        }
    }
}
