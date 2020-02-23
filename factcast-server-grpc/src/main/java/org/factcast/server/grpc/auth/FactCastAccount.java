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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicates;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@RequiredArgsConstructor
public class FactCastAccount {
    public static final FactCastAccount GOD = new FactCastAccount("GODMODE", "NO_SECRET") {
        @Override
        public boolean canRead(String ns) {
            return true;
        }

        @Override
        public boolean canWrite(String ns) {
            return true;
        }
    };

    @NonNull
    @Setter
    @Getter
    String id;

    @NonNull
    @Setter
    @Getter
    String secret;

    @NonNull
    @Setter
    @Getter
    @JsonProperty("roles")
    List<String> roleNames = new LinkedList<String>();

    @VisibleForTesting
    @Getter(value = AccessLevel.PROTECTED)
    List<FactCastRole> roles;

    public void initialize(FactCastAccessConfiguration config) {
        if (id == null)
            new IllegalArgumentException(
                    "Account without 'id' found.");

        if (secret == null)
            new IllegalArgumentException(
                    "Account '" + id + "' misses hash.");

        roles = new LinkedList<FactCastRole>();
        roleNames.forEach(n -> {
            Optional<FactCastRole> r = config.findRoleByName(n);
            roles.add(r.orElseThrow(() -> new IllegalArgumentException(
                    "Unknown role '" + n + "'. Definition not found.")));
        });
    }

    public boolean canWrite(String ns) {
        if (roles == null)
            throw new IllegalStateException("Not yet initialized");

        List<Boolean> results = roles.stream()
                .map(r -> r.canWrite(ns))
                .filter(Predicates.notNull())
                .distinct()
                .collect(Collectors.toList());
        if (results.contains(false))
            return false;
        return results.contains(true);
    };

    public boolean canRead(String ns) {
        if (roles == null)
            throw new IllegalStateException("Not yet initialized");

        List<Boolean> results = roles.stream()
                .map(r -> r.canRead(ns))
                .filter(Predicates.notNull())
                .distinct()
                .collect(Collectors.toList());
        if (results.contains(false))
            return false;
        return results.contains(true);

    };

    @VisibleForTesting
    protected FactCastAccount role(@NonNull FactCastRole... other) {
        if (roles == null)
            roles = new LinkedList<>();

        roles.addAll(Arrays.asList(other));
        return this;
    }
}
