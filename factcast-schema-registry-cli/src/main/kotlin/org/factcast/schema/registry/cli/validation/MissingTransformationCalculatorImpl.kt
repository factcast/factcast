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

import org.factcast.schema.registry.cli.domain.Event
import javax.inject.Singleton

@Singleton
class MissingTransformationCalculatorImpl : MissingTransformationCalculator {
    override fun calculateDowncastTransformations(event: Event): List<MissingTransformation> {
        return event.versions
            .filter { version ->
                version.version != 1 && !event.transformations.any {
                    it.from == version.version && it.to == (version.version.minus(
                        1
                    ))
                }
            }
            .map { version ->
                val toVersion = event.versions.find { it.version == version.version.minus(1) }!!
                Pair(version, toVersion)
            }
    }

    override fun calculateUpcastTransformations(event: Event): List<MissingTransformation> {
        val maxVersion = event.versions.maxBy { it.version }!!.version
        return event.versions
            .filter { version ->
                version.version != maxVersion && !event.transformations.any {
                    it.from == version.version && it.to == (version.version.plus(
                        1
                    ))
                }
            }
            .map { version ->
                val toVersion = event.versions.find { it.version == version.version.plus(1) }!!
                Pair(version, toVersion)
            }
    }
}