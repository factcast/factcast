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
import org.factcast.schema.registry.cli.domain.Transformation
import org.factcast.schema.registry.cli.domain.Version
import javax.inject.Singleton

@Singleton
class MissingTransformationCalculatorImpl : MissingTransformationCalculator {
    override fun calculateDowncastTransformations(event: Event): List<MissingTransformation> {
        val sortedByVersion = event.versions
            .sortedByDescending { it.version }

        return calculateMissingTransformations(sortedByVersion, event.transformations)
    }

    override fun calculateUpcastTransformations(event: Event): List<MissingTransformation> {
        val sortedByVersion = event.versions
            .sortedBy { it.version }

        return calculateMissingTransformations(sortedByVersion, event.transformations)
    }

    private fun calculateMissingTransformations(
        sortedByVersion: List<Version>,
        transformations: List<Transformation>
    ): List<Pair<Version, Version>> {
        val neededDowncastTransformations = sortedByVersion.mapIndexedNotNull { index, version ->
            if (sortedByVersion.last() == version) {
                null
            } else {
                Pair(version, sortedByVersion[index.plus(1)])
            }
        }

        return neededDowncastTransformations.filter { (fromVersion, toVersion) ->
            transformations.none {
                it.from == fromVersion.version && it.to == toVersion.version
            }
        }
    }
}
