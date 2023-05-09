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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

fun filterJson(input: JsonNode, removedSchemaProps: Set<String>): JsonNode {
    val tree = input.deepCopy<JsonNode>()

    removedSchemaProps.forEach { property ->
        tree.filter(property)
    }

    return tree
}

fun JsonNode.filter(name: String) {
    when {
        isObject -> {
            if ((has("type") && get("type").isTextual) ||
                (has("required") && get("required").isArray)
            ) {
                (this as ObjectNode).remove(name)
            }

            forEach { node ->
                node.filter(name)
            }
        }
        isArray -> forEach { node -> node.filter(name) }
    }
}
