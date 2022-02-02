package org.factcast.schema.registry.cli.utils

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class FilterJsonTest : StringSpec() {

    val objectMapper = ObjectMapper()

    init {
        "filters fields" {

            val inputJson = """
                {
                    "foo" : "bar",
                    "title" : "i am unwanted",
                    "nested" : {
                        "foo" : "bar",
                        "title" : "i am unwanted",
                        "example": "foo"
                    }
                }
            """.trimIndent()
            val unfiltered = objectMapper.readTree(inputJson)

            val filtered = filterJson(unfiltered, setOf("title", "example"))

            filtered.findParents("title")?.size shouldBe 0
            filtered.findParents("example")?.size shouldBe 0
            filtered.findParents("foo")?.size shouldBe 2
        }

        "no content change if title is missing" {
            // arrange
            val inputJson = """
                {
                    "foo" : "bar",
                    "nested" : {
                        "foo" : "bar"
                    }
                }
            """.trimIndent()

            val unfiltered = objectMapper.readTree(inputJson)
            unfiltered shouldBe filterJson(unfiltered, setOf("title"))
        }
    }
}
