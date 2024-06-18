/*
 * Copyright © 2017-2020 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.schema.registry.cli.project.structure

import io.micronaut.core.annotation.Introspected
import java.nio.file.Path
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.factcast.schema.registry.cli.domain.Example
import org.factcast.schema.registry.cli.domain.Version
import org.factcast.schema.registry.cli.validation.NO_DESCRIPTION
import org.factcast.schema.registry.cli.validation.NO_EXAMPLES
import org.factcast.schema.registry.cli.validation.NO_SCHEMA
import org.factcast.schema.registry.cli.validation.validators.ValidVersionFolder

@Introspected
data class EventVersionFolder(
    @field:ValidVersionFolder
    override val path: Path,

    @field:NotNull(message = NO_SCHEMA)
    val schema: Path?,

    @field:NotNull(message = NO_DESCRIPTION)
    val description: Path?,

    @field:NotEmpty(message = NO_EXAMPLES)
    val examples: List<Path>
) : Folder {
    override fun getChildren(): List<Folder> = emptyList()
}

/**
 * This is an unsafe call. It assumes that some properties are non-null that were marked as nullable.
 * Should be only called if the EventVersionFolder was validated beforehand.
 */
fun EventVersionFolder.toEventVersion() =
    Version(
        path.fileName.toString().toInt(),
        schema!!,
        description!!,
        examples.map { example ->
            Example(example.fileName.toString(), example)
        })
