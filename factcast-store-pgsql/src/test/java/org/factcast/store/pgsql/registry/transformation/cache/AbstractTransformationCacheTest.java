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

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;

import org.factcast.core.Fact;
import org.junit.jupiter.api.Test;

public abstract class AbstractTransformationCacheTest {
    protected abstract TransformationCache create();

    TransformationCache uut = create();

    @Test
    void testDoesNotFindUnknown() throws Exception {
        uut.find(UUID.randomUUID(), 1, "foo");
    }

    @Test
    void testHappyPath() throws Exception {
        Fact f = Fact.builder().ns("name").type("type").version(1).build("{}");

        uut.put(f, "foo");
        assertThat(uut.find(f.id(), 1, "foo")).contains(f);
    }

    @Test
    void testRespectsVersion() throws Exception {
        Fact f = Fact.builder().ns("name").type("type").version(1).build("{}");

        uut.put(f, "foo");
        assertThat(uut.find(f.id(), 2, "foo")).isEmpty();
    }

    @Test
    void testRespectsChainId() throws Exception {
        Fact f = Fact.builder().ns("name").type("type").version(1).build("{}");

        uut.put(f, "foo");
        assertThat(uut.find(f.id(), 1, "xoo")).isEmpty();
    }
}
