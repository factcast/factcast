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
package org.factcast.store.registry.transformation.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

import java.util.*;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.store.internal.PgFact;
import org.factcast.store.registry.NOPRegistryMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CacheBufferTest {
  @Mock private Object mutex;
  @Mock private Map<TransformationCache.Key, Fact> buffer;
  @Spy private NOPRegistryMetrics registryMetrics = new NOPRegistryMetrics();
  @InjectMocks private CacheBuffer underTest;

  @Nested
  class WhenGetting {
    @Mock private TransformationCache.@NonNull Key key;

    @BeforeEach
    void setup() {}

    @Test
    void get() {
      PgFact f = mock(PgFact.class);
      underTest.buffer().put(key, f);
      assertThat(underTest.get(key)).isSameAs(f);
    }
  }

  @Nested
  class WhenPutting {
    @Mock private TransformationCache.@NonNull Key cacheKey;
    @Mock private Fact factOrNull;

    @BeforeEach
    void setup() {}

    @Test
    void put() {
      PgFact f = mock(PgFact.class);
      underTest.put(cacheKey, f);
      assertThat(underTest.buffer().get(cacheKey)).isSameAs(f);
    }

    @Test
    void putNullDoesNotHide() {
      PgFact f = mock(PgFact.class);
      underTest.put(cacheKey, f);
      assertThat(underTest.get(cacheKey)).isSameAs(f);
    }
  }

  @Nested
  class WhenSizing {
    @Mock private TransformationCache.@NonNull Key cacheKey;
    @Mock private Fact factOrNull;

    @BeforeEach
    void setup() {}

    @Test
    void size() {
      PgFact f = mock(PgFact.class);
      assertThat(underTest.buffer()).isEmpty();
      underTest.put(cacheKey, f);
      assertThat(underTest.buffer()).hasSize(1);
    }
  }

  @Nested
  class WhenClearingAfter {
    @Mock private TransformationCache.@NonNull Key cacheKey;
    @Mock private PgFact fact = mock(PgFact.class);

    @Test
    void clears() {
      underTest.put(cacheKey, fact);
      assertThat(underTest.buffer()).hasSize(1);
      underTest.iterateSnapshotAndClear(bufferCopy -> assertThat(bufferCopy).hasSize(1));
      assertThat(underTest.buffer()).isEmpty();
      assertThat(underTest.get(cacheKey)).isNull();
      assertThat(underTest.bufferSizeMetric().get()).isEqualTo(1);
    }

    @Test
    void ensuresConsistentRead() {
      underTest.put(cacheKey, fact);
      assertThat(underTest.buffer()).hasSize(1);
      underTest.iterateSnapshotAndClear(
          bufferCopy -> {
            assertThat(bufferCopy).hasSize(1);
            assertThat(underTest.buffer()).isEmpty();
            // this, we gave up upon
            //            assertThat(underTest.get(cacheKey)).isEqualTo(fact);
          });
    }

    @Test
    void propagatesConsumerExceptionAndClearsBuffers() {
      underTest.put(cacheKey, fact);
      assertThat(underTest.buffer()).hasSize(1);
      try {
        underTest.iterateSnapshotAndClear(
            bufferCopy -> {
              throw new RuntimeException("testing");
            });
      } catch (RuntimeException e) {
        assertThat(e).hasMessage("testing");
      }
      assertThat(underTest.buffer()).isEmpty();
    }

    @Test
    void preventsConcurrentModification() {
      underTest.put(cacheKey, fact);
      underTest.iterateSnapshotAndClear(
          bufferCopy -> {
            var iterator = bufferCopy.entrySet().iterator();
            underTest.buffer().put(mock(TransformationCache.Key.class), mock(PgFact.class));
            underTest.iterateSnapshotAndClear(c -> {});
            assertDoesNotThrow(iterator::next);
          });
    }
  }

  @Nested
  class WhenPutingAllNull {
    @Mock private TransformationCache.@NonNull Key key1;
    @Mock private TransformationCache.@NonNull Key key2;
    @Mock private TransformationCache.@NonNull Key key3;

    @BeforeEach
    void setup() {}

    @Test
    void doesNotHide() {
      PgFact f = mock(PgFact.class);
      underTest.put(key1, f);
      assertThat(underTest.buffer()).hasSize(1);
      underTest.put(key1, mock(PgFact.class));
      underTest.put(key2, mock(PgFact.class));
      underTest.put(key3, mock(PgFact.class));
      assertThat(underTest.buffer()).hasSize(3);
      assertThat(underTest.buffer().get(key1)).isEqualTo(f);
    }
  }

  @Nested
  class WhenContainsingKey {
    @Mock private TransformationCache.Key key;

    @BeforeEach
    void setup() {}

    @Test
    void containsKeys() {
      assertThat(underTest.containsKey(key)).isFalse();
      underTest.put(key, mock(PgFact.class));
      assertThat(underTest.containsKey(key)).isTrue();
    }
  }
}
