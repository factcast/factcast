package org.factcast.schema.registry.cli.registry.impl

import com.google.common.annotations.VisibleForTesting
import org.factcast.schema.registry.cli.registry.index.FileBasedTransformation
import org.factcast.schema.registry.cli.registry.index.Index
import org.factcast.schema.registry.cli.registry.index.Schema
import org.factcast.schema.registry.cli.registry.templates.IndexFileCalculator2
import org.factcast.schema.registry.cli.utils.ChecksumService
import org.factcast.schema.registry.cli.validation.MissingTransformationCalculator
import java.io.File
import java.nio.file.Path

class IndexFileCalculator2Impl(
        private val checksumService: ChecksumService,
        private val missingTransformationCalculator: MissingTransformationCalculator) : IndexFileCalculator2 {
    override fun calculateIndex(contentBase: Path): Index {
        val defaultJsonSchemaFileName = "schema.json"
        val defaultTransformationScriptName = "transform.js"

        val schemas = contentBase.toFile()
                .walkBottomUp()
                .filter { it.name == defaultJsonSchemaFileName }
                .map { toSchema(it.toPath()) }
                .toList()

        val transformations = contentBase.toFile()
                .walkBottomUp()
                .filter { it.name == defaultTransformationScriptName }
                .map { toFileBasedTransformation(it.toPath()) }
                .toList()

        return Index(schemas, transformations)
    }

    @VisibleForTesting
    fun toSchema(jsonSchema: Path): Schema {
        val (fileName, version, type, ns) = splitPath(jsonSchema).subList(0, 4)
        val id = "$ns/$type/$version/$fileName"

        return Schema(id, ns, type, version.toInt(), checksumService.createMd5Hash(jsonSchema)
        )
    }

    @VisibleForTesting
    fun toFileBasedTransformation(transformationScript: Path): FileBasedTransformation {
        val (fileName, versions, type, ns) = splitPath(transformationScript).subList(0, 4)
        val id = "$ns/$type/$versions/$fileName"

        val (from, to) = versions.split("-")

        return FileBasedTransformation(id, ns, type, from.toInt(), to.toInt(), checksumService.createMd5Hash(transformationScript))
    }

    private fun splitPath(transformationScript: Path): List<String> {
        return transformationScript.toString()
                .split(File.separator)  // be platform independent
                .reversed()
    }
}
