/*
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
package org.factcast.grpc.api.conv;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.util.FactCastJson;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class TestFact implements Fact {

    UUID id = UUID.randomUUID();

    Set<UUID> aggIds = new LinkedHashSet<>();

    String type;

    String ns = "default";

    String jsonPayload = "{}";

    Map<String, String> meta = new HashMap<>();

    @Override
    public String meta(String key) {
        return meta.get(key);
    }

    public TestFact meta(String key, String value) {
        meta.put(key, value);
        return this;
    }

    @Override
    @SneakyThrows
    public String jsonHeader() {
        return FactCastJson.writeValueAsString(this);
    }

    public TestFact aggId(@NonNull UUID aggId, UUID... otherAggIds) {
        aggIds.add(aggId);
        if (otherAggIds != null) {
            aggIds.addAll(Arrays.asList(otherAggIds));
        }
        return this;
    }
}
