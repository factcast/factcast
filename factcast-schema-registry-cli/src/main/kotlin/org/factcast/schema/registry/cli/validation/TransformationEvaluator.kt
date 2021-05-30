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
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Path
import javax.inject.Singleton
import jdk.nashorn.api.scripting.ScriptObjectMirror
import org.factcast.schema.registry.cli.js.JsFunctionExecutor

@Singleton
class TransformationEvaluator(
    private val jsFunctionExecutor: JsFunctionExecutor,
    private val om: ObjectMapper
) {
    fun evaluate(pathToTransformation: Path, data: JsonNode): JsonNode {
        val dataAsMap = om.treeToValue(data, Map::class.java)

        val result = jsFunctionExecutor.execute("transform", pathToTransformation, dataAsMap)

        // when the transform script added an array to an object, it has the type ScriptObjectMirror with isArray=true
        // but is internally a map of idx -> value
        // Jackson transforms this into an object with index as key, not an array. So find and fix this:
        val fixedResult = fixArrayTransformations(result)

        return om.valueToTree(fixedResult)
    }

    fun fixArrayTransformations(data: Map<*, *>): Map<*, *> {
        return data.mapValues {
            if (it.value is ScriptObjectMirror && (it.value as ScriptObjectMirror).isArray) {
                (it.value as ScriptObjectMirror).to(List::class.java)
            } else if (it.value is Map<*, *>) {
                fixArrayTransformations(it.value as Map<*, *>)
            } else {
                it.value
            }
        }
    }
}
