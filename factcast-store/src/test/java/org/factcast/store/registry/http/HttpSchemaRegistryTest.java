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
package org.factcast.store.registry.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.factcast.core.Fact;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.NOPRegistryMetrics;
import org.factcast.store.registry.RegistryIndex;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.TransformationConflictException;
import org.factcast.store.registry.transformation.TransformationKey;
import org.factcast.store.registry.transformation.TransformationSource;
import org.factcast.store.registry.transformation.TransformationStore;
import org.factcast.store.registry.transformation.store.InMemTransformationStoreImpl;
import org.factcast.store.registry.validation.FactValidationError;
import org.factcast.store.registry.validation.FactValidator;
import org.factcast.store.registry.validation.schema.SchemaConflictException;
import org.factcast.store.registry.validation.schema.SchemaKey;
import org.factcast.store.registry.validation.schema.SchemaSource;
import org.factcast.store.registry.validation.schema.SchemaStore;
import org.factcast.store.registry.validation.schema.store.InMemSchemaStoreImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

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

  final SchemaStore schemaStore = Mockito.spy(new InMemSchemaStoreImpl(registryMetrics));

  final TransformationStore transformationStore =
      spy(new InMemTransformationStoreImpl(registryMetrics));

  final LockProvider lockProvider =
      mock(LockProvider.class, withSettings().strictness(Strictness.LENIENT));
  final SimpleLock lock = mock(SimpleLock.class);

  @BeforeEach
  public void setup() throws IOException {
    index.schemes(Lists.newArrayList(source1, source2));
    index.transformations(
        Lists.newArrayList(transformationSource1, transformationSource2, transformationSource3));
    when(lockProvider.lock(any())).thenReturn(Optional.of(lock));
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
            new StoreConfigurationProperties(),
            lockProvider);
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
            new StoreConfigurationProperties(),
            lockProvider);
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

    verify(registryMetrics).timed(eq(RegistryMetrics.OP.REFRESH_REGISTRY), any(Runnable.class));
  }

  @Test
  public void testAllowReplaceFalseForSchemes()
      throws InterruptedException, ExecutionException, IOException {
    var testSource = new SchemaSource("http://foo/1", "123", "ns", "type", 1);
    index.schemes(Lists.newArrayList(testSource));

    HttpSchemaRegistry uut =
        new HttpSchemaRegistry(
            schemaStore,
            transformationStore,
            indexFetcher,
            fileFetcher,
            registryMetrics,
            new StoreConfigurationProperties(),
            lockProvider);
    uut.fetchInitial();

    index.schemes(Lists.newArrayList(testSource.hash("changed")));

    assertThrows(SchemaConflictException.class, uut::refresh);
  }

  @Test
  public void testReplacementIsUsedForValidationAfterCacheWasWarmed() throws IOException {
    var testSource = new SchemaSource("http://foo/1", "123", "ns", "type", 1);
    index.schemes(Lists.newArrayList(testSource));

    String oldSchema = "{\"type\":\"object\",\"required\":[\"jobId\"]}";
    String newSchema = "{\"type\":\"object\",\"required\":[\"jobRunId\"]}";
    when(fileFetcher.fetchSchema(any())).thenReturn(oldSchema, newSchema);

    var properties =
        new StoreConfigurationProperties()
            .setSchemaRegistryUrl("http://foo")
            .setPersistentRegistry(false)
            .setAllowSchemaReplace(true);

    HttpSchemaRegistry uut =
        new HttpSchemaRegistry(
            schemaStore,
            transformationStore,
            indexFetcher,
            fileFetcher,
            registryMetrics,
            properties,
            lockProvider);
    var validator = new FactValidator(properties, uut, registryMetrics);
    Fact oldFact = Fact.builder().ns("ns").type("type").version(1).build("{\"jobId\":\"123\"}");
    Fact newFact = Fact.builder().ns("ns").type("type").version(1).build("{\"jobRunId\":\"123\"}");
    uut.fetchInitial();

    // Validate before refresh so replacement must also update a previously cached schema.
    assertThat(validator.validate(oldFact)).isEmpty();
    assertThat(validator.validate(newFact))
        .extracting(FactValidationError::message)
        .containsExactly("#: required key [jobId] not found");

    index.schemes(Lists.newArrayList(testSource.hash("changed")));
    uut.refresh();

    assertThat(schemaStore.get(testSource.toKey())).hasValue(newSchema);
    assertThat(validator.validate(newFact)).isEmpty();
    assertThat(validator.validate(oldFact))
        .extracting(FactValidationError::message)
        .containsExactly("#: required key [jobRunId] not found");
  }

  @Test
  public void testNewSchemaIsUsedForValidationAfterCachedMiss() {
    var properties =
        new StoreConfigurationProperties()
            .setSchemaRegistryUrl("http://foo")
            .setPersistentRegistry(false);
    HttpSchemaRegistry uut =
        new HttpSchemaRegistry(
            schemaStore,
            transformationStore,
            indexFetcher,
            fileFetcher,
            registryMetrics,
            properties,
            lockProvider);
    var validator = new FactValidator(properties, uut, registryMetrics);
    Fact fact = Fact.builder().ns("ns").type("type").version(1).build("{}");

    // A missing schema is cached too; registering it must allow the next validation to find it.
    assertThat(validator.validate(fact)).hasSize(1);
    assertThat(uut.get(source1.toKey())).isEmpty();

    uut.refresh();

    assertThat(validator.validate(fact)).isEmpty();
  }

  @Test
  public void testAllowReplaceFalseForTransformations()
      throws InterruptedException, ExecutionException, IOException {
    var testSource = new TransformationSource("http://foo/1", "hash", "ns", "type", 1, 2);
    index.transformations(Lists.newArrayList(testSource));

    HttpSchemaRegistry uut =
        new HttpSchemaRegistry(
            schemaStore,
            transformationStore,
            indexFetcher,
            fileFetcher,
            registryMetrics,
            new StoreConfigurationProperties(),
            lockProvider);
    uut.fetchInitial();

    index.transformations(Lists.newArrayList(testSource.hash("changed")));

    assertThrows(TransformationConflictException.class, uut::refresh);
  }

  @Test
  public void testAllowReplaceTrueForTransformations()
      throws InterruptedException, ExecutionException, IOException {
    var testSource = new TransformationSource("http://foo/1", "hash", "ns", "type", 1, 2);
    index.transformations(Lists.newArrayList(testSource));

    when(fileFetcher.fetchTransformation(any())).thenReturn("").thenReturn("bar");

    HttpSchemaRegistry uut =
        new HttpSchemaRegistry(
            schemaStore,
            transformationStore,
            indexFetcher,
            fileFetcher,
            registryMetrics,
            new StoreConfigurationProperties().setAllowSchemaReplace(true),
            lockProvider);
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
}
