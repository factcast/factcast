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
package org.factcast.store.pgsql.registry.transformation.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public abstract class AbstractTransformationCacheTest {
    protected TransformationCache uut;

    @BeforeEach
    public void init() {
        this.uut = createUUT();
    }

    protected abstract TransformationCache createUUT();

    @Test
    void testEmptyFind() {
        Optional<Fact> fact = uut.find(UUID.randomUUID(), 1, "1");
        assertThat(fact.isPresent()).isFalse();
    }

    @Test
    void testFindAfterPut() {
        Fact fact = Fact.builder()
                .ns("ns")
                .type("type")
                .id(UUID.randomUUID())
                .version(1)
                .build("{}");
        String chainId = "1-2-3";

        uut.put(fact, chainId);

        Optional<Fact> found = uut.find(fact.id(), fact.version(), chainId);

        assertThat(found.isPresent()).isTrue();
        assertEquals(fact, found.get());
    }

    @Test
    void testCompact() {
        Fact fact = Fact.builder()
                .ns("ns")
                .type("type")
                .id(UUID.randomUUID())
                .version(1)
                .build("{}");
        String chainId = "1-2-3";

        uut.put(fact, chainId);

        // clocks aren't synchronized so Im gonna add an hour here :)
        uut.compact(DateTime.now().plusHours(1));

        Optional<Fact> found = uut.find(fact.id(), fact.version(), chainId);

        assertThat(found.isPresent()).isFalse();
    }

    @Test
    void testNullContracts() throws Exception {
        assertNpe(() -> {
            uut.find(null, 1, "1");
        });
        assertNpe(() -> {
            uut.find(UUID.randomUUID(), 1, null);
        });
        assertNpe(() -> {
            uut.put(null, "");
        });
        assertNpe(() -> {
            uut.put(Fact.builder().buildWithoutPayload(), null);
        });

    }

    private void assertNpe(Executable r) {
        assertThrows(NullPointerException.class, r);
    }
}
