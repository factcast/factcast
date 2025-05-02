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
package org.factcast.schema.registry.cli.registry.index

import com.fasterxml.jackson.annotation.JsonIgnore

interface Transformation {
    val id: String
    val ns: String
    val type: String
    val from: Int
    val to: Int
}

data class FileBasedTransformation(
    override val id: String,
    override val ns: String,
    override val type: String,
    override val from: Int,
    override val to: Int,
    val hash: String
) : Transformation

data class SyntheticTransformation(
    @get:JsonIgnore
    val syntheticId: String,
    override val ns: String,
    override val type: String,
    override val from: Int,
    override val to: Int
) : Transformation {
    override val id: String
        get() = "synthetic/$syntheticId"
}
