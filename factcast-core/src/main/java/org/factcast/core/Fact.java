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

import java.util.Set;
import java.util.UUID;

import lombok.NonNull;

/**
 * Defines a fact to be either published or consumed. Consists of two JSON
 * Strings: jsonHeader and jsonPayload. Also provides convenience getters for
 * id,ns,type and aggId.
 *
 * Only generated code, does not need unit testing.
 *
 * @author uwe.schaefer@mercateo.com
 *
 */
// TODO add schema
public interface Fact {

    @NonNull
    UUID id();

    @NonNull
    String ns();

    String type();

    Set<UUID> aggIds();

    @NonNull
    String jsonHeader();

    @NonNull
    String jsonPayload();

    String meta(String key);

    default long serial() {
        String s = meta("_ser");
        if (s != null) {
            return Long.valueOf(s).longValue();
        } else {
            throw new IllegalStateException("'_ser' Meta attribute not found");
        }
    }

    // hint to where to get the default from
    static Fact of(@NonNull String jsonHeader, @NonNull String jsonPayload) {
        return DefaultFact.of(jsonHeader, jsonPayload);
    }

    default boolean before(Fact other) {
        return serial() < other.serial();
    }
}
