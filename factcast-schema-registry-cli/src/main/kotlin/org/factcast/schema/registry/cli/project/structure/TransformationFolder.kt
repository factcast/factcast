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

import jakarta.validation.constraints.NotNull
import org.factcast.schema.registry.cli.domain.Transformation
import org.factcast.schema.registry.cli.validation.NO_TRANSFORMATION_FILE
import org.factcast.schema.registry.cli.validation.validators.ValidTransformationFolder
import java.nio.file.Path

data class TransformationFolder(
    @field:ValidTransformationFolder
    override val path: Path,

    @field:NotNull(message = NO_TRANSFORMATION_FILE)
    val transformation: Path?
) : Folder {
    override fun getChildren(): List<Folder> = emptyList()
}

/**
 * This is an unsafe call. It assumes that some properties are non-null that were marked as nullable.
 * Should be only called if the TransformationFolder was validated beforehand.
 */
fun TransformationFolder.toTransformation(): Transformation {
    val (from, to) = path.fileName.toString().split("-").map(String::toInt)

    return Transformation(from, to, transformation!!)
}
