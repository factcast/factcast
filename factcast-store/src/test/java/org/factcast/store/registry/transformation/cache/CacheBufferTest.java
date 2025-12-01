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

import java.util.*;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.store.internal.PgFact;
import org.factcast.store.registry.NOPRegistryMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
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
      PgFact f = Mockito.mock(PgFact.class);
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
      PgFact f = Mockito.mock(PgFact.class);
      underTest.put(cacheKey, f);
      assertThat(underTest.buffer().get(cacheKey)).isSameAs(f);
    }

    @Test
    void putNullDoesNotHide() {
      PgFact f = Mockito.mock(PgFact.class);
      underTest.put(cacheKey, f);
      underTest.put(cacheKey, null);
      assertThat(underTest.buffer().get(cacheKey)).isSameAs(f);
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
      PgFact f = Mockito.mock(PgFact.class);
      assertThat(underTest.buffer()).isEmpty();
      underTest.put(cacheKey, f);
      assertThat(underTest.buffer()).hasSize(1);
    }
  }

  @Nested
  class WhenClearingAfter {
    @Mock private TransformationCache.@NonNull Key cacheKey;
    @Mock private PgFact factOrNull;

    @Test
    void clears() {
      underTest.put(cacheKey, factOrNull);
      assertThat(underTest.buffer()).hasSize(1);
      underTest.clearAfter(bufferCopy -> assertThat(bufferCopy).hasSize(1));
      assertThat(underTest.buffer()).isEmpty();
      assertThat(underTest.flushingBuffer()).isEmpty();
      assertThat(underTest.get(cacheKey)).isNull();
      assertThat(underTest.bufferSizeMetric().get()).isEqualTo(1);
      assertThat(underTest.flushingBufferSizeMetric().get()).isEqualTo(1);
    }

    @Test
    void ensuresConsistentRead() {
      underTest.put(cacheKey, factOrNull);
      assertThat(underTest.buffer()).hasSize(1);
      underTest.clearAfter(
          bufferCopy -> {
            assertThat(bufferCopy).hasSize(1);
            assertThat(underTest.buffer()).isEmpty();
            assertThat(underTest.flushingBuffer()).hasSize(1);
            // ensure consistent read while processing the buffer
            assertThat(underTest.get(cacheKey)).isEqualTo(factOrNull);
          });
    }

    @Test
    void propagatesConsumerExceptionAndClearsBuffers() {
      underTest.put(cacheKey, factOrNull);
      assertThat(underTest.buffer()).hasSize(1);
      try {
        underTest.clearAfter(
            bufferCopy -> {
              throw new RuntimeException("testing");
            });
      } catch (RuntimeException e) {
        assertThat(e).hasMessage("testing");
      }
      assertThat(underTest.buffer()).isEmpty();
      assertThat(underTest.flushingBuffer()).isEmpty();
    }

    @Test
    void preventsConcurrentModification() {
      underTest.put(cacheKey, factOrNull);
      underTest.clearAfter(
          bufferCopy -> {
            var iterator = bufferCopy.entrySet().iterator();
            underTest.clearAfter(c -> {});
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
      PgFact f = Mockito.mock(PgFact.class);
      underTest.put(key1, f);
      assertThat(underTest.buffer()).hasSize(1);
      underTest.putAllNull(Set.of(key1, key2, key3));
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
      underTest.putAllNull(Set.of(key));
      assertThat(underTest.containsKey(key)).isTrue();
    }
  }
}
