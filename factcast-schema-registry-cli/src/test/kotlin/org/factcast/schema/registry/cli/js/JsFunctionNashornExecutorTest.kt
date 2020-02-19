package org.factcast.schema.registry.cli.js

import io.kotlintest.matchers.maps.shouldContain
import io.kotlintest.specs.StringSpec
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.factcast.schema.registry.cli.fs.FileSystemService
import java.nio.file.Paths

const val FN = """
function foo(data) {
    data.foo = "baz"
}    
"""

class JsFunctionNashornExecutorTest : StringSpec() {
    val fs = mockk<FileSystemService>()
    val dummyPath = Paths.get(".")
    val uut = JsFunctionNashornExecutor(fs)

    init {
        "execute with code" {
            val input = mutableMapOf("foo" to "bar")
            val result = uut.execute("foo", FN, input)

            result shouldContain ("foo" to "baz")
        }

        "execute with file path" {
            every { fs.readToString(dummyPath.toFile()) } returns FN

            val input = mutableMapOf("foo" to "bar")
            val result = uut.execute("foo", dummyPath, input)

            result shouldContain ("foo" to "baz")

            verify { fs.readToString(dummyPath.toFile()) }
            confirmVerified(fs)
        }
    }

}