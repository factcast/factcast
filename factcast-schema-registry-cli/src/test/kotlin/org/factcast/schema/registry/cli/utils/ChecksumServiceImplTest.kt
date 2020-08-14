package org.factcast.schema.registry.cli.utils

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
import java.nio.file.Paths
import org.factcast.schema.registry.cli.fs.FileSystemService

class ChecksumServiceImplTest : StringSpec() {
    private val fs = mockk<FileSystemService>()
    val uut = ChecksumServiceImpl(fs)

    init {
        "createMd5Hash" {
            val dummyPath = Paths.get(".")
            val content = "foo".toByteArray()

            every { fs.readToBytes(dummyPath) } returns content

            uut.createMd5Hash(dummyPath) shouldBe "acbd18db4cc2f85cedef654fccc4a4d8"

            verifyAll { fs.readToBytes(dummyPath) }
        }
    }
}
