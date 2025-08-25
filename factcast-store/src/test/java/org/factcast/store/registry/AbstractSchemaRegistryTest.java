/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.store.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import lombok.experimental.FieldDefaults;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.everit.json.schema.Schema;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics.EVENT;
import org.factcast.store.registry.transformation.TransformationStore;
import org.factcast.store.registry.validation.schema.SchemaKey;
import org.factcast.store.registry.validation.schema.SchemaStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractSchemaRegistryTest {
  @Mock private IndexFetcher indexFetcher;
  @Mock private RegistryFileFetcher registryFileFetcher;
  @Mock private SchemaStore schemaStore;
  @Mock private TransformationStore transformationStore;
  @Mock private RegistryMetrics registryMetrics;
  @Mock private StoreConfigurationProperties storeConfigurationProperties;
  @Mock private SimpleLock lock;
  @Mock private LockProvider lockProvider;
  @Mock private Object mutex;
  @InjectMocks private SomeSchemaRegistry underTest;

  @Nested
  class WhenFetchingInitial {

    @BeforeEach
    void setup() {}

    @Test
    void justCountsWhenSchemaPersistent() {
      when(indexFetcher.fetchIndex()).thenThrow(IllegalStateException.class);
      when(storeConfigurationProperties.isPersistentRegistry()).thenReturn(true);
      when(lockProvider.lock(any())).thenReturn(Optional.of(lock));
      underTest.fetchInitial();
      verify(registryMetrics).count(EVENT.SCHEMA_UPDATE_FAILURE);
    }

    @Test
    void doesNothingWhenLockCannotBeAcquired() {
      when(storeConfigurationProperties.isPersistentRegistry()).thenReturn(true);
      underTest.fetchInitial();
      // failed to lock, so...
      verify(lockProvider).lock(any());
      // .. no more interaction expected
      verifyNoInteractions(indexFetcher);
    }

    @Test
    void fetchesWhenLockCanBeAcquired() {
      when(storeConfigurationProperties.isPersistentRegistry()).thenReturn(true);
      when(lockProvider.lock(any())).thenReturn(Optional.of(lock));
      underTest.fetchInitial();
      // lock acquired, so...
      verify(lockProvider).lock(any());
      // .. it should fetch
      verify(indexFetcher).fetchIndex();
    }

    @Test
    void throwsWhenSchemaIsNotPersistent() {
      when(indexFetcher.fetchIndex()).thenThrow(IllegalStateException.class);
      when(storeConfigurationProperties.isPersistentRegistry()).thenReturn(false);

      assertThatThrownBy(underTest::fetchInitial).isInstanceOf(InitialRegistryFetchFailed.class);

      verify(registryMetrics).count(EVENT.SCHEMA_UPDATE_FAILURE);
    }
  }

  @Nested
  class WhenClearingNearCache {
    @BeforeEach
    void setup() {}

    @Test
    @SuppressWarnings("unchecked")
    void refetchesSchemaFromStoreAfterClearing() {

      SchemaKey key = SchemaKey.of("foo", "bar", 122);
      String schema1 = "{}";
      String schema2 = "{}";
      when(schemaStore.get(key)).thenReturn(Optional.of(schema1), Optional.of(schema2));
      Schema firstSchema = underTest.get(key).get();
      assertThat(underTest.get(key)).hasValue(firstSchema); // this is mocked, but
      assertThat(underTest.get(key)).hasValue(firstSchema); // this comes from near cache
      assertThat(underTest.get(key)).hasValue(firstSchema); // again

      // act
      underTest.clearNearCache();

      Optional<Schema> actual = underTest.get(key);
      assertThat(actual).isNotEmpty();
      // new value freshly picked from store
      assertThat(actual.get()).isNotSameAs(firstSchema);
    }

    @Test
    @SuppressWarnings("unchecked")
    void refetchesSchemaFromStoreAfterInvalidation() {
      // arrange
      SchemaKey key = SchemaKey.of("foo", "bar", 122);
      String schemaString1 = "{}";
      String schemaString2 = "{}";

      when(schemaStore.get(key))
          .thenReturn(Optional.empty(), Optional.of(schemaString1), Optional.of(schemaString2));

      // assert #0 - nothing can be fetched, "nothing" is cached
      assertThat(underTest.get(key)).isEmpty();
      assertThat(underTest.get(key)).isEmpty();
      assertThat(underTest.get(key)).isEmpty();

      // act #1
      underTest.invalidateNearCache(key);

      // assert #1 - after invalidation schema1 is fetched and cached
      Optional<Schema> schema1 = underTest.get(key);
      assertThat(schema1).isPresent();
      assertThat(underTest.get(key)).hasValue(schema1.get());
      assertThat(underTest.get(key)).hasValue(schema1.get());
      assertThat(underTest.get(key)).hasValue(schema1.get());

      // act #2
      underTest.invalidateNearCache(key);

      // assert #2 - after invalidation schema2 is fetched and cached
      Optional<Schema> schema2 = underTest.get(key);
      assertThat(schema2).isPresent();
      assertThat(schema2.get()).isNotSameAs(schema1.get());
      assertThat(underTest.get(key)).hasValue(schema2.get());
      assertThat(underTest.get(key)).hasValue(schema2.get());
      assertThat(underTest.get(key)).hasValue(schema2.get());
    }
  }

  @Test
  void shouldBeActive() {
    // only false for NOP
    assertThat(underTest.isActive()).isTrue();
  }

  @Nested
  @FieldDefaults(makeFinal = true)
  class WhenEnumerating {

    SchemaKey key1 = SchemaKey.of("ns1", "t1", 1);
    SchemaKey key2 = SchemaKey.of("ns1", "t1", 2);
    SchemaKey key3 = SchemaKey.of("ns1", "t2", 1);
    SchemaKey key4 = SchemaKey.of("ns2", "t3", 1);
    Set<SchemaKey> allKeys = Set.of(key1, key2, key3, key4);

    @Test
    void passesAllNamespaces() {
      when(schemaStore.getAllSchemaKeys()).thenReturn(allKeys);
      assertThat(underTest.enumerateNamespaces()).containsExactlyInAnyOrder("ns1", "ns2");
    }

    @Test
    void noTypesInUnknownNS() {
      when(schemaStore.getAllSchemaKeys()).thenReturn(allKeys);
      assertThat(underTest.enumerateTypes("unknown")).isEmpty();
    }

    @Test
    void enumeratesTypesForNamespace() {
      when(schemaStore.getAllSchemaKeys()).thenReturn(allKeys);
      assertThat(underTest.enumerateTypes("ns1")).containsExactlyInAnyOrder("t1", "t2");
      assertThat(underTest.enumerateTypes("ns2")).containsExactlyInAnyOrder("t3");
    }

    @Test
    void enumeratesVersionsForNamespaceAndType() {
      when(schemaStore.getAllSchemaKeys()).thenReturn(allKeys);
      assertThat(underTest.enumerateVersions("ns1", "t1")).containsExactlyInAnyOrder(1, 2);
      assertThat(underTest.enumerateVersions("ns1", "t2")).containsExactlyInAnyOrder(1);
    }
  }
}
