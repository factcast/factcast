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
package org.factcast.itests.factus;

import java.util.*;

import org.factcast.factus.Handler;
import org.factcast.factus.projection.SnapshotProjection;

public class UserNames implements SnapshotProjection {

    private final Map<UUID, String> existingNames = new HashMap<>();

    @Handler
    void apply(UserCreated created) {
        existingNames.put(created.aggregateId(), created.userName());
    };

    @Handler
    void apply(UserDeleted deleted) {
        existingNames.remove(deleted.aggregateId());
    };

    int count() {
        return existingNames.size();
    }

    boolean contains(String name) {
        return existingNames.values().contains(name);
    }

    Set<UUID> allUserIdsForDeletingInTest() {
        return existingNames.keySet();
    }

}
