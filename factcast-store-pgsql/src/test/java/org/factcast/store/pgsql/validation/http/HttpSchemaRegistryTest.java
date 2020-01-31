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
package org.factcast.store.pgsql.validation.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.factcast.store.pgsql.validation.schema.SchemaKey;
import org.factcast.store.pgsql.validation.schema.SchemaSource;
import org.factcast.store.pgsql.validation.schema.SchemaStore;
import org.factcast.store.pgsql.validation.schema.store.InMemSchemaStoreImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.common.collect.Lists;

public class HttpSchemaRegistryTest {

    @Test
    public void testRoundtrip() throws InterruptedException, ExecutionException, IOException {

        URL url = new URL("http://some.funky/registry/");

        IndexFetcher indexFetcher = mock(IndexFetcher.class);
        SchemaFetcher schemafetcher = mock(SchemaFetcher.class);

        RegistryIndex index = new RegistryIndex();
        SchemaSource source1 = new SchemaSource("http://foo/1", "123", "ns", "type", 1);
        SchemaSource source2 = new SchemaSource("http://foo/2", "123", "ns", "type", 2);
        index.schemes(Lists.newArrayList(source1, source2));
        when(indexFetcher.fetchIndex()).thenReturn(Optional.of(index));

        when(schemafetcher.fetch(any())).thenReturn("{}");

        SchemaStore store = spy(new InMemSchemaStoreImpl());
        HttpSchemaRegistry uut = new HttpSchemaRegistry(store, indexFetcher, schemafetcher);
        uut.refreshVerbose();

        verify(store, times(2)).register(Mockito.any(), Mockito.any());

        assertTrue(store.get(SchemaKey.builder().ns("ns").type("type").version(1).build())
                .isPresent());
        assertTrue(store.get(SchemaKey.builder().ns("ns").type("type").version(2).build())
                .isPresent());
        assertFalse(store.get(SchemaKey.builder().ns("ns").type("type").version(3).build())
                .isPresent());

    }

    @Test
    void testNullContracts() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            new HttpSchemaRegistry(null, mock(SchemaStore.class));
        });

        assertThrows(NullPointerException.class, () -> {
            new HttpSchemaRegistry(new URL("http://ibm.com"), null);
        });
    }
}
