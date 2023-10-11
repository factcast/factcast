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
package org.factcast.schema.registry.cli.utils

import arrow.core.Either
import com.github.fge.jsonschema.core.exceptions.ProcessingException
import com.github.fge.jsonschema.main.JsonSchema
import com.github.fge.jsonschema.main.JsonSchemaFactory
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.validation.ProjectError
import java.nio.file.Path
import jakarta.inject.Singleton

@Singleton
class SchemaServiceImpl(
    private val fileSystemService: FileSystemService,
    private val jsonSchemaFactory: JsonSchemaFactory
) : SchemaService {
    override fun loadSchema(path: Path): Either<ProjectError, JsonSchema> {
        val jsonNode = fileSystemService.readToJsonNode(path) ?: return Either.Left(ProjectError.NoSuchFile(path))

        return try {
            Either.Right(jsonSchemaFactory.getJsonSchema(jsonNode))
        } catch (e: ProcessingException) {
            Either.Left(ProjectError.CorruptedSchema(path))
        }
    }
}
