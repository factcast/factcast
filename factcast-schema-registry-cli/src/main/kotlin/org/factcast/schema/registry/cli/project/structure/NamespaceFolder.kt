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
import javax.validation.Valid
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull
import org.factcast.schema.registry.cli.domain.Namespace
import org.factcast.schema.registry.cli.validation.NO_DESCRIPTION
import org.factcast.schema.registry.cli.validation.NO_EVENTS

@Introspected
data class NamespaceFolder(
    override val path: Path,

    @field:NotEmpty(message = NO_EVENTS)
    @field:Valid
    val eventFolders: List<EventFolder>,

    @field:NotNull(message = NO_DESCRIPTION)
    val description: Path?
) : Folder {
    override fun getChildren(): List<Folder> = eventFolders
}

/**
 * This is an unsafe call. It assumes that some properties are non-null that were marked as nullable.
 * Should be only called if the NamespaceFolder was validated beforehand.
 */
fun NamespaceFolder.toNamespace() =
    Namespace(path.fileName.toString(), description!!, eventFolders.map(EventFolder::toEvent))
