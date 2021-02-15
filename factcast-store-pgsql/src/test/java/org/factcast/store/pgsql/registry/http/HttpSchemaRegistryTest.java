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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import lombok.val;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.registry.NOPRegistryMetrics;
import org.factcast.store.pgsql.registry.RegistryIndex;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics.OP;
import org.factcast.store.pgsql.registry.transformation.*;
import org.factcast.store.pgsql.registry.transformation.store.InMemTransformationStoreImpl;
import org.factcast.store.pgsql.registry.validation.schema.SchemaConflictException;
import org.factcast.store.pgsql.registry.validation.schema.SchemaKey;
import org.factcast.store.pgsql.registry.validation.schema.SchemaSource;
import org.factcast.store.pgsql.registry.validation.schema.SchemaStore;
import org.factcast.store.pgsql.registry.validation.schema.store.InMemSchemaStoreImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HttpSchemaRegistryTest {
  @Spy final RegistryMetrics registryMetrics = new NOPRegistryMetrics();

  final HttpIndexFetcher indexFetcher = mock(HttpIndexFetcher.class);

  final HttpRegistryFileFetcher fileFetcher = mock(HttpRegistryFileFetcher.class);

  final RegistryIndex index = new RegistryIndex();

  final SchemaSource source1 = new SchemaSource("http://foo/1", "123", "ns", "type", 1);

  final SchemaSource source2 = new SchemaSource("http://foo/2", "123", "ns", "type", 2);

  final TransformationSource transformationSource1 =
      new TransformationSource("http://foo/1", "hash", "ns", "type", 1, 2);

  final TransformationSource transformationSource2 =
      new TransformationSource("synthetic/http://foo/2", "hash", "ns", "type", 2, 1);

  final TransformationSource transformationSource3 =
      new TransformationSource("http://foo/3", "hash", "ns", "type2", 1, 2);

  final SchemaStore schemaStore = spy(new InMemSchemaStoreImpl(registryMetrics));

  final TransformationStore transformationStore =
      spy(new InMemTransformationStoreImpl(registryMetrics));

  @BeforeEach
  public void setup() throws IOException {
    index.schemes(Lists.newArrayList(source1, source2));
    index.transformations(
        Lists.newArrayList(transformationSource1, transformationSource2, transformationSource3));

    when(indexFetcher.fetchIndex()).thenReturn(Optional.of(index));

    when(fileFetcher.fetchSchema(any())).thenReturn("{}");
    when(fileFetcher.fetchTransformation(any())).thenReturn("");
  }

  @Test
  public void testInitial() throws InterruptedException, ExecutionException, IOException {
    HttpSchemaRegistry uut =
        new HttpSchemaRegistry(
            schemaStore,
            transformationStore,
            indexFetcher,
            fileFetcher,
            registryMetrics,
            new PgConfigurationProperties());
    uut.fetchInitial();

    verify(schemaStore, times(2)).register(Mockito.any(), Mockito.any());
    verify(transformationStore, times(3)).store(Mockito.any(), Mockito.any());

    verify(fileFetcher, times(2)).fetchSchema(Mockito.any());
    verify(fileFetcher, times(2)).fetchTransformation(Mockito.any());

    assertTrue(schemaStore.get(SchemaKey.of("ns", "type", 1)).isPresent());
    assertTrue(schemaStore.get(SchemaKey.of("ns", "type", 2)).isPresent());
    assertFalse(schemaStore.get(SchemaKey.of("ns", "type", 3)).isPresent());

    assertEquals(2, transformationStore.get(TransformationKey.of("ns", "type")).size());
    assertEquals(1, transformationStore.get(TransformationKey.of("ns", "type2")).size());
  }

  @Test
  public void testRefresh() throws InterruptedException, ExecutionException, IOException {
    HttpSchemaRegistry uut =
        new HttpSchemaRegistry(
            schemaStore,
            transformationStore,
            indexFetcher,
            fileFetcher,
            registryMetrics,
            new PgConfigurationProperties());
    uut.refresh();

    verify(schemaStore, times(2)).register(Mockito.any(), Mockito.any());
    verify(transformationStore, times(3)).store(Mockito.any(), Mockito.any());

    verify(fileFetcher, times(2)).fetchSchema(Mockito.any());
    verify(fileFetcher, times(2)).fetchTransformation(Mockito.any());

    assertTrue(schemaStore.get(SchemaKey.of("ns", "type", 1)).isPresent());
    assertTrue(schemaStore.get(SchemaKey.of("ns", "type", 2)).isPresent());
    assertFalse(schemaStore.get(SchemaKey.of("ns", "type", 3)).isPresent());

    assertEquals(2, transformationStore.get(TransformationKey.of("ns", "type")).size());
    assertEquals(1, transformationStore.get(TransformationKey.of("ns", "type2")).size());

    verify(registryMetrics).timed(eq(OP.REFRESH_REGISTRY), any(Runnable.class));
  }

  @Test
  public void testAllowReplaceFalseForSchemes()
      throws InterruptedException, ExecutionException, IOException {
    val testSource = new SchemaSource("http://foo/1", "123", "ns", "type", 1);
    index.schemes(Lists.newArrayList(testSource));

    HttpSchemaRegistry uut =
        new HttpSchemaRegistry(
            schemaStore,
            transformationStore,
            indexFetcher,
            fileFetcher,
            registryMetrics,
            new PgConfigurationProperties());
    uut.fetchInitial();

    index.schemes(Lists.newArrayList(testSource.hash("changed")));

    assertThrows(SchemaConflictException.class, uut::refresh);
  }

  @Test
  public void testAllowReplaceTrueForSchemes()
      throws InterruptedException, ExecutionException, IOException {
    val testSource = new SchemaSource("http://foo/1", "123", "ns", "type", 1);
    index.schemes(Lists.newArrayList(testSource));

    when(fileFetcher.fetchSchema(any())).thenReturn("{}").thenReturn("{\"foo\":\"bar\"}");

    HttpSchemaRegistry uut =
        new HttpSchemaRegistry(
            schemaStore,
            transformationStore,
            indexFetcher,
            fileFetcher,
            registryMetrics,
            new PgConfigurationProperties().setAllowSchemaReplace(true));
    uut.fetchInitial();

    assertThat(schemaStore.get(testSource.toKey())).isPresent().hasValue("{}");

    index.schemes(Lists.newArrayList(testSource.hash("changed")));
    uut.refresh();

    assertThat(schemaStore.get(testSource.toKey())).isPresent().hasValue("{\"foo\":\"bar\"}");
  }

  @Test
  public void testAllowReplaceFalseForTransformations()
      throws InterruptedException, ExecutionException, IOException {
    val testSource = new TransformationSource("http://foo/1", "hash", "ns", "type", 1, 2);
    index.transformations(Lists.newArrayList(testSource));

    HttpSchemaRegistry uut =
        new HttpSchemaRegistry(
            schemaStore,
            transformationStore,
            indexFetcher,
            fileFetcher,
            registryMetrics,
            new PgConfigurationProperties());
    uut.fetchInitial();

    index.transformations(Lists.newArrayList(testSource.hash("changed")));

    assertThrows(TransformationConflictException.class, uut::refresh);
  }

  @Test
  public void testAllowReplaceTrueForTransformations()
      throws InterruptedException, ExecutionException, IOException {
    val testSource = new TransformationSource("http://foo/1", "hash", "ns", "type", 1, 2);
    index.transformations(Lists.newArrayList(testSource));

    when(fileFetcher.fetchTransformation(any())).thenReturn("").thenReturn("bar");

    HttpSchemaRegistry uut =
        new HttpSchemaRegistry(
            schemaStore,
            transformationStore,
            indexFetcher,
            fileFetcher,
            registryMetrics,
            new PgConfigurationProperties().setAllowSchemaReplace(true));
    uut.fetchInitial();

    assertThat(transformationStore.get(testSource.toKey()).get(0).transformationCode())
        .isPresent()
        .hasValue("");

    index.transformations(Lists.newArrayList(testSource.hash("changed")));
    uut.refresh();

    assertThat(transformationStore.get(testSource.toKey()).get(0).transformationCode())
        .isPresent()
        .hasValue("bar");
  }

  @Test
  void testNullContracts() throws Exception {
    assertThrows(
        NullPointerException.class,
        () -> {
          new HttpSchemaRegistry(
              null,
              mock(SchemaStore.class),
              mock(TransformationStore.class),
              registryMetrics,
              new PgConfigurationProperties());
        });

    assertThrows(
        NullPointerException.class,
        () -> {
          new HttpSchemaRegistry(
              new URL("http://ibm.com"),
              null,
              mock(TransformationStore.class),
              registryMetrics,
              new PgConfigurationProperties());
        });

    assertThrows(
        NullPointerException.class,
        () -> {
          new HttpSchemaRegistry(
              new URL("http://ibm.com"),
              mock(SchemaStore.class),
              null,
              registryMetrics,
              new PgConfigurationProperties());
        });

    assertThrows(
        NullPointerException.class,
        () -> {
          new HttpSchemaRegistry(
              new URL("http://ibm.com"),
              mock(SchemaStore.class),
              mock(TransformationStore.class),
              null,
              new PgConfigurationProperties());
        });
  }
}
