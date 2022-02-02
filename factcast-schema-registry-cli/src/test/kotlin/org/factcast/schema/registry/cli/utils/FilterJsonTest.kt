package org.factcast.schema.registry.cli.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.karumi.kotlinsnapshot.matchWithSnapshot
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class FilterJsonTest : StringSpec() {

    val objectMapper = ObjectMapper()

    init {
        "filters fields" {

            val inputJson = """
                {
                    "foo" : {
                      "title" : "i am unwanted 1",
                      "type": "string",
                      "example": "foo"
                    },
                    "nested" : {
                        "type": "object",
                        "description": "bar",
                        "properties": {
                          "foo": {
                            "type": "string",
                            "title" : "i am unwanted 2"                            
                          }
                        }
                    },
                    "type": {
                       "title" : "i am unwanted 3",
                       "type": "string",
                       "example": "foo"
                    }
                }
            """.trimIndent()
            val unfiltered = objectMapper.readTree(inputJson)

            val filtered = filterJson(unfiltered, setOf("title", "example", "description"))

            filtered.toPrettyString().matchWithSnapshot("filter-complex")
            filtered.findParents("title")?.size shouldBe 0
            filtered.findParents("example")?.size shouldBe 0
            filtered.findParents("foo")?.size shouldBe 2
            filtered.findParents("nested")?.size shouldBe 1
            filtered.findParents("type")?.size shouldBe 4
        }

        "filters oneOf" {

            val inputJson = """
                {
                     "oneOf" : [ {
                        "title" : "TransactionType(deal) / MetaData(meta)",
                        "required" : [ "transportAuctionAllocationId", "meta" ]
                      } ]
                }
            """.trimIndent()
            val unfiltered = objectMapper.readTree(inputJson)

            val filtered = filterJson(unfiltered, setOf("title"))

            filtered.toPrettyString().matchWithSnapshot("filter-oneof")
            filtered.findParents("title")?.size shouldBe 0
            filtered.findParents("required")?.size shouldBe 1
        }


        "does not remove a property called 'title'" {

            val inputJson = """
                  {
                    "title": {
                      "title": "i am unwanted",
                      "type": "string"
                    }
                }
            """.trimIndent()
            val unfiltered = objectMapper.readTree(inputJson)

            val filtered = filterJson(unfiltered, setOf("title"))

            filtered.toPrettyString().matchWithSnapshot("filter-should-not-filter-title-property")
        }

        "no content change if title is missing" {
            // arrange
            val inputJson = """
                {
                    "foo" : {
                      "type": "string"
                    }
                }
            """.trimIndent()

            val unfiltered = objectMapper.readTree(inputJson)
            unfiltered shouldBe filterJson(unfiltered, setOf("title"))
        }
    }
}
