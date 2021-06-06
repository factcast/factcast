package org.factcast.schema.registry.cli.validation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
import jdk.nashorn.api.scripting.ScriptObjectMirror
import java.nio.file.Paths
import org.factcast.schema.registry.cli.js.JsFunctionExecutorImpl

class TransformationEvaluatorTest : StringSpec() {

    val dummyData = mockk<JsonNode>()
    val resultData = mockk<JsonNode>()

    val dummyPath = Paths.get(".")
    val om = mockk<ObjectMapper>(relaxed = true)
    val executor = mockk<JsFunctionExecutorImpl>(relaxed = true)

    val scriptObjectMirror = mockk<ScriptObjectMirror>()

    val uut = TransformationEvaluator(executor, om)

    init {
        "evaluate" {
            val resultMap = mapOf("foo" to "baz")
            val inputMap = mapOf("foo" to "bar")

            every {
                executor.execute("transform", dummyPath, inputMap)
            } returns resultMap

            every { om.treeToValue(dummyData, any<Class<Any>>()) } returns inputMap
            every { om.valueToTree<JsonNode>(resultMap) } returns resultData

            val result = uut.evaluate(dummyPath, dummyData)

            result shouldBe resultData

            verifyAll {
                executor.execute("transform", dummyPath, any<Map<String, Any>>())
                om.treeToValue(dummyData, any<Class<Any>>())
                om.valueToTree<JsonNode>(resultMap)
            }
        }

        "checkMapFixNoNewArray" {
            val inputMap = mapOf(
                    "normalValue" to "asdf",
                    "intValue" to 1,
                    "nestedMap" to mapOf("a" to "b"),
                    "existingArray" to listOf(1,2,3)
            )
            // no changes
            val expectedResultMap = mapOf(
                    "normalValue" to "asdf",
                    "intValue" to 1,
                    "nestedMap" to mapOf("a" to "b"),
                    "existingArray" to listOf(1,2,3)
            )

            val fixedMap = uut.fixArrayTransformations(inputMap)

            fixedMap shouldBe expectedResultMap
        }

        "checkMapFixNewArrayTurnsToList" {
            val inputMap = mapOf(
                    "normalValue" to "asdf",
                    "intValue" to 1,
                    "nestedMap" to mapOf("a" to "b"),
                    "newArray" to scriptObjectMirror
            )

            every { scriptObjectMirror.isArray } returns true
            every { scriptObjectMirror.to(any<Class<List<*>>>()) } returns listOf(1,2,3)

            val expectedResultMap = mapOf(
                    "normalValue" to "asdf",
                    "intValue" to 1,
                    "nestedMap" to mapOf("a" to "b"),
                    "newArray" to listOf(1,2,3)
            )

            val fixedMap = uut.fixArrayTransformations(inputMap)

            fixedMap shouldBe expectedResultMap
        }
    }
}
