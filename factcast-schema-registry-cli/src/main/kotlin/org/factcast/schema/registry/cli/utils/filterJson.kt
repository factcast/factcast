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
            if ((has("type") && get("type").isTextual)
                || (has("required") && get("required").isArray)
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