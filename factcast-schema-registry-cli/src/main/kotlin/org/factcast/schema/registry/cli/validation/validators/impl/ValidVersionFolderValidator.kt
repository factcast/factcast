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
package org.factcast.schema.registry.cli.validation.validators.impl

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.validation.validator.constraints.ConstraintValidator
import org.factcast.schema.registry.cli.validation.validators.ValidVersionFolder
import java.nio.file.Path
import javax.inject.Singleton

@Singleton
class ValidVersionFolderValidator : ConstraintValidator<ValidVersionFolder, Path> {
    override fun isValid(
        value: Path?,
        annotationMetadata: AnnotationValue<ValidVersionFolder>,
        context: io.micronaut.validation.validator.constraints.ConstraintValidatorContext
    ) = value != null && try {
        value.fileName.toString().toInt()

        true
    } catch (e: NumberFormatException) {
        false
    }
}