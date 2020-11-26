package org.factcast.schema.registry.cli.json

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec


class TitleFilterServiceImplTest : StringSpec() {

    val uut = TitleFilterServiceImpl()
    val objectMapper = ObjectMapper()

    init {
        "filters Title" {

            val inputJson = """
                {
                    "foo" : "bar",
                    "title" : "i am unwanted",
                    "nested" : {
                        "foo" : "bar",
                        "title" : "i am unwanted"
                    }
                }
            """.trimIndent()
            val unfiltered = objectMapper.readTree(inputJson)

            val filtered = uut.filter(unfiltered)

            filtered?.findParents("title")?.size shouldBe 0
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
            unfiltered shouldBe uut.filter(unfiltered)
        }
    }
}
