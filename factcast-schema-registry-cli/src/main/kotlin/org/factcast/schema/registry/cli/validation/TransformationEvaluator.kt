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
import org.factcast.store.pgsql.registry.transformation.SingleTransformation
import org.factcast.store.pgsql.registry.transformation.TransformationKey
import org.factcast.store.pgsql.registry.transformation.chains.TransformationChain
import org.factcast.store.pgsql.registry.transformation.chains.Transformer
import javax.inject.Singleton

@Singleton
class TransformationEvaluator(
    private val transformer: Transformer,
    private val fs: FileSystemService
) {
    fun evaluate(ns: Namespace, event: Event, transformation: Transformation, data: JsonNode): JsonNode {
        val key = TransformationKey.of(ns.name, event.type)
        val singleTransformation =
            SingleTransformation.of(
                key,
                transformation.from,
                transformation.to,
                fs.readToString(transformation.transformationPath.toFile())
            )
        val chain = TransformationChain.of(
            key,
            listOf(singleTransformation),
            "no-real-meaning"
        )

        return transformer.transform(chain, data)
    }


}
