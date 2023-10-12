/*
 * Copyright © 2017-2020 factcast.org
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
import org.factcast.schema.registry.cli.domain.Event
import org.factcast.schema.registry.cli.domain.Namespace
import org.factcast.schema.registry.cli.domain.Transformation
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.store.registry.transformation.SingleTransformation
import org.factcast.store.registry.transformation.TransformationKey
import org.factcast.store.registry.transformation.chains.TransformationChain
import org.factcast.store.registry.transformation.chains.Transformer
import jakarta.inject.Singleton

@Singleton
class TransformationEvaluator(
    private val transformer: Transformer,
    private val fs: FileSystemService
) {
    /**
     * will return null if the transformation should not be considered by schema reg cli
     */
    fun evaluate(ns: Namespace, event: Event, transformation: Transformation, data: JsonNode): JsonNode? {
        val transformationAsString = fs.readToString(transformation.transformationPath.toFile())

        if (transformationAsString.startsWith("/*CLI IGNORE*/")) {
            return null
        }

        val key = TransformationKey.of(ns.name, event.type)
        val singleTransformation =
            SingleTransformation.of(
                key,
                transformation.from,
                transformation.to,
                transformationAsString
            )
        val chain = TransformationChain.of(
            key,
            listOf(singleTransformation),
            "no-real-meaning"
        )

        return transformer.transform(chain, data)
    }


}
