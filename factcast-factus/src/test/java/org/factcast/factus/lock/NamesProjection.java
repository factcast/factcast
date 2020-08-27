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
package org.factcast.factus.lock;

import java.util.HashSet;
import java.util.Set;

import org.factcast.factus.Handler;
import org.factcast.factus.projection.LocalManagedProjection;

public class NamesProjection extends LocalManagedProjection {

    private final Set<String> names = new HashSet<>();

    public boolean contains(String name) {
        return names.contains(name);
    }

    @Handler
    void handle(UserCreated evt) {
        names.add(evt.name());
    }
}
