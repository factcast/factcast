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

import java.util.*;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CacheBufferTest {
  @Mock private Object mutex;
  @Mock private Map<TransformationCache.Key, Fact> buffer;
  @InjectMocks private CacheBuffer underTest;

  @Nested
  class WhenGetting {
    @Mock private TransformationCache.@NonNull Key key;

    @BeforeEach
    void setup() {}

    @Test
    void get() {
      Fact f = Fact.builder().ns("ns").buildWithoutPayload();
      underTest.buffer().put(key, f);
      assertThat(underTest.get(key)).isSameAs(f);
    }
  }

  @Nested
  class WhenPuting {
    @Mock private TransformationCache.@NonNull Key cacheKey;
    @Mock private Fact factOrNull;

    @BeforeEach
    void setup() {}

    @Test
    void put() {
      Fact f = Fact.builder().ns("ns").buildWithoutPayload();
      underTest.put(cacheKey, f);
      assertThat(underTest.buffer().get(cacheKey)).isSameAs(f);
    }

    @Test
    void putNullDoesNotHide() {
      Fact f = Fact.builder().ns("ns").buildWithoutPayload();
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
      Fact f = Fact.builder().ns("ns").buildWithoutPayload();
      assertThat(underTest.buffer()).isEmpty();
      underTest.put(cacheKey, f);
      assertThat(underTest.buffer()).hasSize(1);
    }
  }

  @Nested
  class WhenClearing {
    @Mock private TransformationCache.@NonNull Key cacheKey;
    @Mock private Fact factOrNull;

    @BeforeEach
    void setup() {}

    @Test
    void clear() {
      underTest.put(cacheKey, null);
      assertThat(underTest.buffer()).hasSize(1);
      assertThat(underTest.clear()).hasSize(1);
      assertThat(underTest.buffer()).isEmpty();
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
      Fact f = Fact.builder().ns("ns").buildWithoutPayload();
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
