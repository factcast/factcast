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
package org.factcast.store.pgsql.registry.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.factcast.store.pgsql.registry.transformation.TransformationKey;
import org.factcast.store.pgsql.registry.transformation.TransformationSource;
import org.factcast.store.pgsql.registry.transformation.TransformationStore;
import org.factcast.store.pgsql.registry.transformation.store.InMemTransformationStoreImpl;
import org.factcast.store.pgsql.registry.validation.schema.SchemaKey;
import org.factcast.store.pgsql.registry.validation.schema.SchemaSource;
import org.factcast.store.pgsql.registry.validation.schema.SchemaStore;
import org.factcast.store.pgsql.registry.validation.schema.store.InMemSchemaStoreImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.common.collect.Lists;

public class HttpSchemaRegistryTest {

    @Test
    public void testRoundtrip() throws InterruptedException, ExecutionException, IOException {
        IndexFetcher indexFetcher = mock(IndexFetcher.class);
        RegistryFileFetcher fileFetcher = mock(RegistryFileFetcher.class);

        RegistryIndex index = new RegistryIndex();

        SchemaSource source1 = new SchemaSource("http://foo/1", "123", "ns", "type", 1);
        SchemaSource source2 = new SchemaSource("http://foo/2", "123", "ns", "type", 2);

        TransformationSource transformationSource1 = new TransformationSource("http://foo/1", "ns",
                "type", "hash", 1, 2);
        TransformationSource transformationSource2 = new TransformationSource(
                "synthetic/http://foo/2", "ns",
                "type", null, 2, 1);
        TransformationSource transformationSource3 = new TransformationSource("http://foo/3", "ns",
                "type2", "hash", 1, 2);

        index.schemes(Lists.newArrayList(source1, source2));
        index.transformations(Lists.newArrayList(transformationSource1, transformationSource2,
                transformationSource3));

        when(indexFetcher.fetchIndex()).thenReturn(Optional.of(index));

        when(fileFetcher.fetchSchema(any())).thenReturn("{}");
        when(fileFetcher.fetchTransformation(any())).thenReturn("");

        SchemaStore schemaStore = spy(new InMemSchemaStoreImpl());
        TransformationStore transformationStore = spy(new InMemTransformationStoreImpl());

        HttpSchemaRegistry uut = new HttpSchemaRegistry(schemaStore, transformationStore,
                indexFetcher, fileFetcher);
        uut.refreshVerbose();

        verify(schemaStore, times(2)).register(Mockito.any(), Mockito.any());
        verify(transformationStore, times(3)).store(Mockito.any(), Mockito.any());

        verify(fileFetcher, times(2)).fetchSchema(Mockito.any());
        verify(fileFetcher, times(2)).fetchTransformation(Mockito.any());

        assertTrue(schemaStore.get(SchemaKey.of("ns", "type", 1))
                .isPresent());
        assertTrue(schemaStore.get(SchemaKey.of("ns", "type", 2))
                .isPresent());
        assertFalse(schemaStore.get(SchemaKey.of("ns", "type", 3))
                .isPresent());

        assertEquals(2, transformationStore.get(TransformationKey.of("ns", "type")).size());
        assertEquals(1, transformationStore.get(TransformationKey.of("ns", "type2")).size());

    }

    @Test
    void testNullContracts() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            new HttpSchemaRegistry(null, mock(SchemaStore.class), mock(TransformationStore.class));
        });

        assertThrows(NullPointerException.class, () -> {
            new HttpSchemaRegistry(new URL("http://ibm.com"), null, mock(
                    TransformationStore.class));
        });

        assertThrows(NullPointerException.class, () -> {
            new HttpSchemaRegistry(new URL("http://ibm.com"), mock(SchemaStore.class), null);
        });
    }
}
