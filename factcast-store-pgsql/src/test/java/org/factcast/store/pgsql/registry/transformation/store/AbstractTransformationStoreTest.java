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
package org.factcast.store.pgsql.registry.transformation.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.factcast.store.pgsql.registry.transformation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public abstract class AbstractTransformationStoreTest {

    private TransformationStore uut;

    @BeforeEach
    public void init() {
        this.uut = createUUT();
    }

    protected abstract TransformationStore createUUT();

    TransformationSource s = new TransformationSource();

    @Test
    void testEmptyContains() throws Exception {
        s.id("http://testEmptyContains");
        s.hash("123");

        assertThat(uut.contains(s)).isFalse();
    }

    @Test
    void testEmptyGet() throws Exception {
        List<Transformation> actual = uut.get(TransformationKey.builder()
                .ns("ns")
                .type("testEmptyGet")
                .build());
        assertThat(actual).isEmpty();
    }

    @Test
    void testGetAfterRegister() throws Exception {

        s.id("http://testGetAfterRegister");
        s.hash("123");
        s.ns("ns");
        s.type("type");
        s.from(1);
        s.to(2);
        uut.store(s, "");

        List<Transformation> actual = uut.get(s.toKey());
        assertThat(actual).isNotEmpty();
    }

    @Test
    void testContainsSensesConflict() throws Exception {

        s.id("http://testContainsSensesConflict");
        s.hash("123");
        s.ns("ns");
        s.type("testContainsSensesConflict");
        s.from(1);
        s.to(2);
        uut.store(s, "");

        assertThrows(TransformationConflictException.class, () -> {
            TransformationSource conflicting = new TransformationSource();
            conflicting.id("http://testContainsSensesConflict");
            conflicting.hash("1234");
            uut.contains(conflicting);
        });
    }

    @Test
    void testNullContracts() throws Exception {
        s.id("http://testContainsSensesConflict");
        s.hash("123");
        s.ns("ns");
        s.type("testContainsSensesConflict");
        s.from(1);
        s.to(2);

        assertNpe(() -> {
            uut.contains(null);
        });
        assertNpe(() -> {
            uut.store(null, "{}");
        });
        assertNpe(() -> {
            uut.get(null);
        });

    }

    private void assertNpe(Executable r) {
        assertThrows(NullPointerException.class, r);
    }

    @Test
    public void testMatchingContains() throws Exception {

        s.id("http://testMatchingContains");
        s.hash("123");
        s.ns("ns");
        s.type("testMatchingContains");
        s.from(1);
        s.to(2);
        uut.store(s, "{}");

        assertThat(uut.contains(s)).isTrue();
    }

}
