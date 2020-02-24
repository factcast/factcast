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
package org.factcast.server.grpc.auth;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.factcast.core.util.FactCastJson;

import lombok.Getter;

public class FactCastSecretsConfiguration {
    @Getter
    private List<FactCastSecret> secrets = new LinkedList<>();

    public static FactCastSecretsConfiguration read(InputStream is) {
        return FactCastJson.readValue(FactCastSecretsConfiguration.class, is);
    }

    private final Map<String, Optional<String>> cache = new HashMap<>();

    public Optional<String> findSecretForAccountName(String id) {
        return cache.computeIfAbsent(id,
                i -> secrets.stream()
                        .filter(s -> s.id().equals(i))
                        .map(FactCastSecret::secret)
                        .findFirst());
    }
}
