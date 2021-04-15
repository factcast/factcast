package org.factcast.schema.registry.cli.registry.impl

import org.factcast.schema.registry.cli.registry.index.Index
import org.factcast.schema.registry.cli.registry.templates.IndexFileCalculator2
import org.factcast.schema.registry.cli.utils.ChecksumService
import org.factcast.schema.registry.cli.validation.MissingTransformationCalculator
import java.nio.file.Path

class IndexFileCalculator2Impl(checksumService: ChecksumService, missingTransformationCalculator: MissingTransformationCalculator) : IndexFileCalculator2 {
    override fun calculateIndex(contentBase: Path): Index {
        TODO("Not yet implemented")
    }

}
