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
package org.factcast.factus;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpecCoordinates;
import org.factcast.factus.serializer.EventSerializer;

import lombok.NonNull;
import lombok.val;

/**
 * EventPojos are expected to carry
 * a @{@link org.factcast.core.spec.Specification} annotation.
 */
public interface EventPojo {

    default Fact toFact(@NonNull EventSerializer ser) {
        return toFact(ser, UUID.randomUUID());
    }

    default Fact toFact(@NonNull EventSerializer ser, UUID factId) {
        val spec = FactSpecCoordinates.from(getClass());

        val b = Fact.builder();
        b.id(factId);

        b.ns(spec.ns());
        b.type(spec.type());
        int version = spec.version();
        if (version > 0) // 0 is not allowed on publishing
        {
            b.version(version);
        }

        aggregateIds().forEach(b::aggId);

        additionalFactHeaders().forEach((key, value) -> {
            if (key == null) {
                throw new IllegalArgumentException(
                        "Keys of additional fact headers must not be null ('" + key + "':'" + value
                                + "')");
            }
            b.meta(key, value);
        });

        return b.build(ser.serialize(this));
    }

    @NonNull
    default Map<String, String> additionalFactHeaders() {
        return Collections.emptyMap();
    }

    @NonNull
    Set<UUID> aggregateIds();

}
