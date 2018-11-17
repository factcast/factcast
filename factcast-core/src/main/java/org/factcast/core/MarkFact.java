/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.core;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import lombok.Getter;
import lombok.NonNull;

/**
 * Special fact without payload, that can be used to identify marks in the
 * stream in order to build sync. interfaces on top of eventual consistent
 * views.
 *
 * @author uwe.schaefer@mercateo.com
 */
@Getter
public class MarkFact implements Fact {

    public static final String MARK_TYPE = "_mark";

    public static final String MARK_NS = "_";

    final UUID id = UUID.randomUUID();

    final String ns = MARK_NS;

    final String type = MARK_TYPE;

    final Set<UUID> aggIds = Collections.emptySet();

    /**
     * does not have any payload.
     */
    @Override
    @NonNull
    public String jsonPayload() {
        return "{}";
    }

    @Override
    public String jsonHeader() {
        return String.format("{\"ns\":\"%s\",\"id\":\"%s\",\"type\":\"%s\"}", ns(), id(), type());
    }

    @Override
    public String meta(String key) {
        return null;
    }
}
