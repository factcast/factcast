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
import static org.mockito.Mockito.*;

import com.google.common.cache.LoadingCache;
import java.util.*;
import lombok.NonNull;
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
  @Mock private @NonNull IndexFetcher indexFetcher;
  @Mock private @NonNull RegistryFileFetcher registryFileFetcher;
  @Mock private @NonNull SchemaStore schemaStore;
  @Mock private @NonNull TransformationStore transformationStore;
  @Mock private @NonNull RegistryMetrics registryMetrics;
  @Mock private @NonNull StoreConfigurationProperties pgConfigurationProperties;
  @Mock private Object mutex;
  @Mock private LoadingCache<SchemaKey, Schema> cache;
  @InjectMocks private SomeSchemaRegistry underTest;

  @Nested
  class WhenFetchingInitial {
    @BeforeEach
    void setup() {}

    @Test
    void justCountsWhenSchemaPersistent() {
      when(indexFetcher.fetchIndex()).thenThrow(IllegalStateException.class);
      when(pgConfigurationProperties.isPersistentRegistry()).thenReturn(true);

      underTest.fetchInitial();
      verify(registryMetrics).count(EVENT.SCHEMA_UPDATE_FAILURE);
    }

    @Test
    void throwsWhenSchemaIsNotPersistent() {
      when(indexFetcher.fetchIndex()).thenThrow(IllegalStateException.class);
      when(pgConfigurationProperties.isPersistentRegistry()).thenReturn(false);

      assertThatThrownBy(
              () -> {
                underTest.fetchInitial();
              })
          .isInstanceOf(InitialRegistryFetchFailed.class);

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
  }
}
