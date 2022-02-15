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
package org.factcast.schema.registry.cli.validation

import com.github.fge.jsonschema.core.report.ProcessingReport
import java.nio.file.Path

const val NO_DESCRIPTION = "{NoDescription}"
const val NO_EVENT_VERSIONS = "{NoEventVersions}"
const val NO_SCHEMA = "{NoSchema}"
const val NO_EXAMPLES = "{NoExamples}"
const val NO_EVENTS = "{NoEvents}"
const val NO_NAMESPACES = "{NoNamespaces}"
const val NO_TRANSFORMATION_FILE = "{NoTransformationFile}"
const val TRANSFORMATION_VERSION_INVALID = "{TransformationVersionInvalid}"
const val VERSION_INVALID = "{VersionInvalid}"

sealed class ProjectError {
    class NoDescription(val descriptionPath: Path) : ProjectError()
    class NoEvents(val namespacePath: Path) : ProjectError()
    class NoEventVersions(val eventVersionsPath: Path) : ProjectError()
    class NoSchema(val path: Path) : ProjectError()
    class NoExamples(val path: Path) : ProjectError()
    class NoNamespaces(val projectPath: Path) : ProjectError()
    class NoSuchFile(val filePath: Path) : ProjectError()
    class CorruptedSchema(val schemaPath: Path) : ProjectError()
    class ValidationError(val examplePath: Path, val result: ProcessingReport) : ProjectError()
    class WrongVersionFormat(val version: String, val path: Path) : ProjectError()
    class NoUpcastForVersion(val fromVersion: Int, val toVersion: Int, val type: String) : ProjectError()
    class TransformationValidationError(val type: String, val fromVersion: Int, val toVersion: Int, val result: ProcessingReport) : ProjectError()
    class TransformationError(val type: String, val fromVersion: Int, val toVersion: Int, val exception: Throwable) : ProjectError()
    class NoDowncastForVersion(val fromVersion: Int, val toVersion: Int, val type: String, val result: ProcessingReport) : ProjectError()
    class MissingVersionForTransformation(val fromVersion: Int, val toVersion: Int, val transformationPath: Path) : ProjectError()
}
