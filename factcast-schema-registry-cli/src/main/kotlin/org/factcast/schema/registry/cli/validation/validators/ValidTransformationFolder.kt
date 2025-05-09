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
package org.factcast.schema.registry.cli.validation.validators

import jakarta.validation.Constraint
import jakarta.validation.Payload
import org.factcast.schema.registry.cli.validation.TRANSFORMATION_VERSION_INVALID
import org.factcast.schema.registry.cli.validation.validators.impl.ValidTransformationFolderValidator
import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ValidTransformationFolderValidator::class])
annotation class ValidTransformationFolder(
    val message: String = TRANSFORMATION_VERSION_INVALID,
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)
