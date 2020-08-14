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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotCache;
import org.jetbrains.annotations.NotNull;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
abstract class AbstractSnapshotRepository {
    protected static final String KEY_DELIMITER = ":";

    protected final SnapshotCache snapshotCache;

    protected void putBlocking(@NonNull Snapshot snapshot) {
        snapshotCache.setSnapshot(snapshot);
    }

    @NotNull
    @VisibleForTesting
    protected String createKeyForType(@NonNull Class<?> type) {
        return createKeyForType(type, null);
    }

    @NotNull
    @VisibleForTesting
    protected String createKeyForType(@NonNull Class<?> type, UUID optionalUUID) {
        String classLevelKey = keyPrefix() + type.getCanonicalName() + KEY_DELIMITER
                + getSerialVersionUid(type);
        if (optionalUUID == null) {
            return classLevelKey;
        } else {
            return classLevelKey + KEY_DELIMITER + optionalUUID.toString();
        }
    }

    private final Map<Class<?>, Long> serials = new HashMap<>();

    @VisibleForTesting
    protected Long getSerialVersionUid(Class<?> type) {
        return serials.computeIfAbsent(type, t -> {
            try {
                Field field = t.getDeclaredField("serialVersionUID");
                field.setAccessible(true);
                return field.getLong(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                return 0L;
            }
        });
    }

    protected String keyPrefix() {
        return getClass().getCanonicalName() + KEY_DELIMITER;
    }

}
