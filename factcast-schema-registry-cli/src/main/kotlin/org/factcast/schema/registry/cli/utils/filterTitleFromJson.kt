package org.factcast.schema.registry.cli.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

fun filterTitleFrom(input: JsonNode): JsonNode {
    val tree = input.deepCopy<JsonNode>()

    tree.findParents("title")
            ?.map { it as ObjectNode }
            ?.forEach { it.remove("title") }

    return tree
}
