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

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.factcast.schema.registry.cli.domain.Event
import org.factcast.schema.registry.cli.validation.NO_DESCRIPTION
import org.factcast.schema.registry.cli.validation.NO_EVENT_VERSIONS
import java.nio.file.Path

data class EventFolder(
    override val path: Path,

    @field:NotEmpty(message = NO_EVENT_VERSIONS)
    @field:Valid
    val versionFolders: List<EventVersionFolder>,

    @field:NotNull(message = NO_DESCRIPTION)
    val description: Path?,

    @field:Valid
    val transformationFolders: List<TransformationFolder>
) : Folder {
    override fun getChildren(): List<Folder> = versionFolders
}

/**
 * This is an unsafe call. It assumes that some properties are non-null that were marked as nullable.
 * Should be only called if the EventFolder was validated beforehand.
 */
fun EventFolder.toEvent(): Event = Event(
    path.fileName.toString(),
    description!!,
    versionFolders.map(EventVersionFolder::toEventVersion),
    transformationFolders.map(TransformationFolder::toTransformation)
)
