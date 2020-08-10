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
import java.util.UUID;

import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.DefaultSnapshotSerializer;
import org.factcast.factus.serializer.SnapshotSerializer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
public class SnapshotFactory {
    private static final org.factcast.factus.serializer.SnapshotSerializer defaultSerializer = new DefaultSnapshotSerializer();

    @NonNull
    private final Set<org.factcast.factus.serializer.SnapshotSerializer> registeredSerializers;

    public SnapshotFactory(
            @NonNull Set<org.factcast.factus.serializer.SnapshotSerializer> registeredSerializers) {
        this.registeredSerializers = registeredSerializers;
    }

    // TODO should be just one?

    public <P extends SnapshotProjection> ProjectionSnapshot<P> createFrom(UUID factId, P p) {
        val type = getType(p);
        val ser = retrieveSerializer(type);
        byte[] bytes = ser.serialize(p);
        return new ProjectionSnapshot<P>(type, factId, bytes);
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

    public <A extends Aggregate> AggregateSnapshot<A> createFrom(UUID factId, A a) {
        Class<A> type = getType(a);
        val ser = retrieveSerializer(type);
        byte[] bytes = ser.serialize(a);
        return new AggregateSnapshot<A>(type, factId, bytes);
    }

    @SuppressWarnings("unchecked")
    private <A extends SnapshotProjection> Class<A> getType(A a) {
        // TODO remove proxy classes
        return (Class<A>) a.getClass();
    }

}
