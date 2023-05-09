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

fun formatErrors(errors: List<ProjectError>): List<String> = errors.map {
    when (it) {
        is ProjectError.NoEvents -> "No events found for namespace ${it.namespacePath.fileName}"
        is ProjectError.NoEventVersions ->
            "No versions found for event ${it.eventVersionsPath}"
        is ProjectError.NoExamples -> "No examples found for event ${it.path.fileName} at ${it.path}"
        is ProjectError.NoNamespaces -> "No namespaces found at ${it.projectPath}"
        is ProjectError.NoSuchFile -> "Corrupted JSON at ${it.filePath}"
        is ProjectError.CorruptedSchema -> "Broken schema at ${it.schemaPath}"
        is ProjectError.NoSchema -> "No schema found for ${it.path}"
        is ProjectError.WrongVersionFormat ->
            "Version ${it.version} is no number at ${it.path}"
        is ProjectError.NoDescription ->
            "No description found for ${it.descriptionPath}"
        is ProjectError.ValidationError ->
            """
Example ${it.examplePath} failed validation:
${it.result.joinToString("\n") { result ->
                "- ${result.asJson().get("instance").get("pointer").asText()}: ${result.message}"
            }}
                """.trimIndent()
        is ProjectError.NoUpcastForVersion ->
            "No upcast for ${it.type} from version ${it.fromVersion} to ${it.toVersion}"
        is ProjectError.NoDowncastForVersion ->
            """No downcast for ${it.type} from version ${it.fromVersion} to ${it.toVersion} (implicit noop failed)
${it.result.joinToString("\n") { result ->
                "- ${result.message}"
            }}
            """.trimIndent()
        is ProjectError.TransformationValidationError ->
            """Error while applying transformation for ${it.type} converting version ${it.fromVersion} to ${it.toVersion}:
${it.result.joinToString("\n") { result ->
                "- ${result.message}"
            }}

            """.trimIndent()
        is ProjectError.MissingVersionForTransformation ->
            "Version ${it.fromVersion} or ${it.toVersion} does not exist for ${it.transformationPath}"
        is ProjectError.TransformationError -> """Exception during transformation of ${it.type} from ${it.fromVersion} to ${it.toVersion}:
${it.exception.message}
        """.trimIndent()
    }
}
