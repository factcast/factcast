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
package org.factcast.client.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CachingFactLookupTest {

    private CachingFactLookup uut;

    private FactStore store;

    @BeforeEach
    void setUp() {
        store = mock(FactStore.class);
        uut = new CachingFactLookup(store);
    }

    @Test
    void testLookupFails() {
        when(store.fetchById(any())).thenReturn(Optional.empty());
        final UUID id = UUID.randomUUID();
        Optional<Fact> lookup = uut.lookup(id);
        assertFalse(lookup.isPresent());
        verify(store).fetchById(id);
    }

    @Test
    void testLookupWorks() {
        final Fact f = Fact.builder().ns("test").build("{}");
        when(store.fetchById(f.id())).thenReturn(Optional.of(f));
        Optional<Fact> lookup = uut.lookup(f.id());
        assertTrue(lookup.isPresent());
        assertEquals(f, lookup.get());
        verify(store).fetchById(f.id());
    }

    @Test
    void testConstructorNullParam() {
        Assertions.assertThrows(NullPointerException.class, () -> new CachingFactLookup(null));
    }
}
