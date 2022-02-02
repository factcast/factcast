package org.factcast.schema.registry.cli.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

fun filterJson(input: JsonNode, removedSchemaProps: Set<String>): JsonNode {
    val tree = input.deepCopy<JsonNode>()

    removedSchemaProps.forEach { property ->
        tree.findParents(property)
            ?.map { it as ObjectNode }
            ?.forEach { it.remove(property) }
    }

    return tree
}
