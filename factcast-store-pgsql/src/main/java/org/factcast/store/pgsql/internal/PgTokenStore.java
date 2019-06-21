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
package org.factcast.store.pgsql.internal;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.store.StateToken;
import org.factcast.core.store.TokenStore;
import org.factcast.core.util.FactCastJson;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PgTokenStore implements TokenStore {

    final JdbcTemplate tpl;

    static class StateJson {

        private final Map<UUID, UUID> lastFactIdByAggregate = new LinkedHashMap<>();

        public static StateJson from(@NonNull Map<UUID, Optional<UUID>> state) {
            StateJson json = new StateJson();
            state.forEach((key, value) -> json.lastFactIdByAggregate.put(key, value.orElse(null)));
            return json;
        }

        public Map<UUID, Optional<UUID>> toMap() {
            HashMap<UUID, Optional<UUID>> ret = new HashMap<>();
            lastFactIdByAggregate.forEach((key, value) -> ret.put(key, Optional.ofNullable(value)));
            return ret;
        }
    }

    @Override
    public @NonNull StateToken create(
            @NonNull Map<UUID, Optional<UUID>> state, @NonNull Optional<String> nsOrNull) {

        String stateAsJson = FactCastJson.writeValueAsString(StateJson.from(state));
        UUID queryForObject = tpl.queryForObject(PgConstants.INSERT_TOKEN,
                new Object[] { nsOrNull.orElse(null), stateAsJson },
                UUID.class);
        return new StateToken(
                queryForObject);
    }

    @Override
    public void invalidate(@NonNull StateToken token) {
        tpl.update(PgConstants.DELETE_TOKEN, token.uuid());
    }

    @Override
    public @NonNull Optional<Map<UUID, Optional<UUID>>> getState(@NonNull StateToken token) {
        try {
            String state = tpl.queryForObject(PgConstants.SELECT_STATE_FROM_TOKEN,
                    new Object[] { token.uuid() },
                    String.class);
            return Optional.of(FactCastJson.readValue(StateJson.class, state).toMap());
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> getNs(@NonNull StateToken token) {
        try {
            String ns = tpl.queryForObject(PgConstants.SELECT_NS_FROM_TOKEN,
                    new Object[] { token.uuid() },
                    String.class);
            return Optional.ofNullable(ns);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

}
