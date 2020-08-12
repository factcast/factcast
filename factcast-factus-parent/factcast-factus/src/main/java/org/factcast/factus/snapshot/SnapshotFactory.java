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
package org.factcast.factus.snapshot;

import java.util.Set;

import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.serializer.SnapshotSerializer.DefaultSnapshotSerializer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SnapshotFactory {
    private static final org.factcast.factus.serializer.SnapshotSerializer defaultSerializer = new DefaultSnapshotSerializer();

    @NonNull
    private final Set<org.factcast.factus.serializer.SnapshotSerializer> registeredSerializers;

    public SnapshotFactory(
            @NonNull Set<org.factcast.factus.serializer.SnapshotSerializer> registeredSerializers) {
        this.registeredSerializers = registeredSerializers;
    }

    public org.factcast.factus.serializer.SnapshotSerializer retrieveSerializer(
            @NonNull Class<?> aClass) {
        SerializeUsing classAnnotation = aClass.getAnnotation(SerializeUsing.class);
        if (classAnnotation == null) {
            return defaultSerializer;
        } else {
            Class<? extends SnapshotSerializer> ser = classAnnotation.value();
            return registeredSerializers.stream()
                    .filter(ser::isInstance)
                    .findFirst()
                    .orElseGet(() -> {
                        log.error(
                                "Unregistered serializer requested: {}. Falling back to default. ",
                                ser);
                        return defaultSerializer;
                    });
        }
    }
}
