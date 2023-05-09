package org.factcast.schema.registry.cli.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
import org.factcast.schema.registry.cli.fs.FileSystemService
import java.nio.file.Paths

class ChecksumServiceImplTest : StringSpec() {
    private val fs = mockk<FileSystemService>()
    val uut = ChecksumServiceImpl(fs, ObjectMapper())

    init {
        "createMd5Hash from path" {
            val dummyPath = Paths.get(".")
            val content = "foo".toByteArray()

            every { fs.readToBytes(dummyPath) } returns content

            uut.createMd5Hash(dummyPath) shouldBe "acbd18db4cc2f85cedef654fccc4a4d8"

            verifyAll { fs.readToBytes(dummyPath) }
        }

        "createMd5Hash from Json Node" {
            val dummyJson = JsonNodeFactory.instance.objectNode()
            uut.createMd5Hash(dummyJson) shouldBe "99914b932bd37a50b983c5e7c90ae93b"
        }
    }
}
