package org.factcast.schema.registry.cli.registry.impl

import com.karumi.kotlinsnapshot.matchWithSnapshot
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.mockk.*
import org.factcast.schema.registry.cli.domain.*
import org.factcast.schema.registry.cli.fs.FileSystemService
import java.nio.file.Paths

class HugoTemplateServiceImplTest : StringSpec() {
    val fs = mockk<FileSystemService>()
    val dummyPath = Paths.get(".")

    val uut = HugoTemplateServiceImpl(fs)

    override fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
    }

    init {
        "loadHomeTemplate" {
            every { fs.readToString(any()) } returns "MyCompanySchemaRegistry"

            val project = Project(dummyPath, emptyList())

            uut.loadHomeTemplate(project).matchWithSnapshot("HomeTemplateData")

            verifyAll { fs.readToString(dummyPath.toFile()) }
        }

        "loadNamespaceTemplate" {
            every { fs.readToString(any()) } returns "Namespace Description"

            val namespace = Namespace("NamespaceA", dummyPath, emptyList())

            uut.loadNamespaceTemplate(namespace).matchWithSnapshot("NamespaceTemplateData")

            verifyAll { fs.readToString(dummyPath.toFile()) }
        }

        "loadEventTemplate" {
            val eventDescriptionPath = Paths.get("description.md")
            val version1 = Version(1, dummyPath, dummyPath, emptyList())
            val version2 = Version(2, dummyPath, dummyPath, emptyList())
            val event = Event("eventA", eventDescriptionPath, listOf(version1, version2), emptyList())

            val namespace = Namespace("NamespaceA", dummyPath, emptyList())

            every { fs.readToString(eventDescriptionPath.toFile()) } returns "EventDescription"
            every { fs.readToString(dummyPath.toFile()) } returns "VersionDescription"

            uut.loadEventTemplate(namespace, event).matchWithSnapshot("EventTemplateData")

            verify(exactly = 4) { fs.readToString(dummyPath.toFile()) }
            verify(exactly = 1) { fs.readToString(eventDescriptionPath.toFile()) }
            confirmVerified(fs)
        }

        "loadVersionTemplate" {
            val eventDescriptionPath = Paths.get("description.md")
            val example = Example("example1", dummyPath)
            val version1 = Version(1, dummyPath, dummyPath, listOf(example))
            val event = Event("eventA", eventDescriptionPath, listOf(version1), emptyList())
            val namespace = Namespace("NamespaceA", dummyPath, emptyList())

            every { fs.readToString(eventDescriptionPath.toFile()) } returns "EventDescription"
            every { fs.readToString(dummyPath.toFile()) } returns "VersionDescription"

            uut.loadVersionTemplate(namespace, event, version1).matchWithSnapshot("VersionTemplateData")

            verify(exactly = 3) { fs.readToString(dummyPath.toFile()) }
            confirmVerified(fs)
        }

        "loadTransformationsTemplate" {
            val transformation1 = Transformation(1, 2, dummyPath)
            val version1 = Version(1, dummyPath, dummyPath, emptyList())
            val event = Event("eventA", dummyPath, listOf(version1), listOf(transformation1))
            val namespace = Namespace("NamespaceA", dummyPath, emptyList())

            every { fs.readToString(dummyPath.toFile()) } returns "jscode"

            uut.loadTransformationsTemplate(namespace, event).matchWithSnapshot("TransformationsTemplateData")

            verify { fs.readToString(dummyPath.toFile()) }
            confirmVerified(fs)
        }
    }
}
