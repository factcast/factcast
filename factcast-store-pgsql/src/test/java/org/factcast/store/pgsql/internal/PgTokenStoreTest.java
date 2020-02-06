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
package org.factcast.store.pgsql.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.store.TokenStore;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.pgsql.internal.PgTokenStore.StateJson;
import org.factcast.store.test.AbstractTokenStoreTest;
import org.factcast.store.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = { PgTestConfiguration.class })
@Sql(scripts = "/test_schema.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
public class PgTokenStoreTest extends AbstractTokenStoreTest {

    @Autowired
    PgTokenStore tokenStore;

    @Override
    protected TokenStore createTokenStore() {
        return tokenStore;
    }

    @Test
    public void testStateFromSymetric() throws Exception {
        Map<UUID, Optional<UUID>> m = new HashMap<>();
        m.put(new UUID(0, 1), Optional.of(new UUID(0, 2)));
        m.put(new UUID(0, 2), Optional.empty());

        StateJson s = PgTokenStore.StateJson.from(m);
        assertThat(s.toMap().size()).isEqualTo(2);
        assertThat(s.toMap()).isEqualTo(m);
    }

    @Test
    public void testStateJsonSerializable() throws Exception {
        Map<UUID, Optional<UUID>> m = new LinkedHashMap<>();
        m.put(new UUID(0, 1), Optional.of(new UUID(0, 2)));
        m.put(new UUID(0, 2), Optional.empty());

        StateJson s = PgTokenStore.StateJson.from(m);

        String json = FactCastJson.writeValueAsString(s);

        assertThat(json).isEqualTo(
                "{\"lastFactIdByAggregate\":{\"00000000-0000-0000-0000-000000000001\":\"00000000-0000-0000-0000-000000000002\",\"00000000-0000-0000-0000-000000000002\":null}}");

        Map<UUID, Optional<UUID>> m2 = FactCastJson.readValue(StateJson.class, json).toMap();
        assertThat(m).isEqualTo(m2);
    }

    @Test
    public void testStateJsonFromNullContract() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            StateJson.from(null);
        });
    }

}
