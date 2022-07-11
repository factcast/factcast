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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.Lists;
import java.time.ZonedDateTime;
import java.util.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.Fact;
import org.factcast.store.registry.NOPRegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;

@SuppressWarnings("ALL")
@ExtendWith(MockitoExtension.class)
class PgTransformationCacheTest {

  private static final int MAX_BUFFER_SIZE = 24;
  @Mock private JdbcTemplate jdbcTemplate;
  RegistryMetrics registryMetrics = spy(new NOPRegistryMetrics());

  @Nested
  class WhenPuting {
    @Mock private TransformationCache.@NonNull Key key;
    @Mock private @NonNull Fact f;
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest = spy(new PgTransformationCache(jdbcTemplate, registryMetrics, 10));
    }

    @Test
    void buffers() {
      underTest.put(key, f);

      Mockito.verify(underTest).registerWrite(key, f);
      assertThat(underTest.buffer()).containsEntry(key, f);
    }

    @Test
    void overwritesAccess() {
      underTest.registerAccess(key);
      underTest.put(key, f);

      Mockito.verify(underTest).registerWrite(key, f);
      assertThat(underTest.buffer()).containsEntry(key, f);
    }
  }

  @Nested
  class WhenFinding {
    @Mock private TransformationCache.Key key;
    @Mock private TransformationCache.Key key2;
    @Mock private Fact f;
    @Mock private Fact f2;
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest = spy(new PgTransformationCache(jdbcTemplate, registryMetrics, 10));
    }

    @Test
    void findsUnflushed() {
      underTest.put(key, f);
      //noinspection OptionalGetWithoutIsPresent
      assertThat(underTest.find(key).get()).isSameAs(f);
    }

    @Test
    void findsFlushed() {
      //noinspection OptionalGetWithoutIsPresent
      Mockito.when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
          .thenReturn(Collections.singletonList(f));
      assertThat(underTest.find(key).get()).isSameAs(f);
    }

    @Test
    void registersMiss() {
      Mockito.when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
          .thenReturn(Collections.emptyList());
      assertThat(underTest.find(key)).isEmpty();
      Mockito.verify(registryMetrics).count(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_MISS);
    }

    @Test
    void registersHit() {
      Mockito.when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
          .thenReturn(Collections.singletonList(f));
      assertThat(underTest.find(key)).isNotEmpty();
      Mockito.verify(registryMetrics).count(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_HIT);
    }
  }

  @Nested
  class WhenFindingAll {
    @Mock private TransformationCache.Key key;
    @Mock private TransformationCache.Key key2;
    @Mock private Fact f;
    @Mock private Fact f2;
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest = spy(new PgTransformationCache(jdbcTemplate, registryMetrics, 10));
    }

    @Test
    void findsBoth() {
      underTest.put(key, f);
      Mockito.when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(RowMapper.class)))
          .thenReturn(Collections.singletonList(f2));
      assertThat(underTest.findAll(Lists.newArrayList(key, key2)))
          .hasSize(2)
          .containsExactlyInAnyOrder(f, f2);
    }

    @Test
    void findsAllInCache() {
      Mockito.when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(RowMapper.class)))
          .thenReturn(Lists.newArrayList(f, f2));
      assertThat(underTest.findAll(Lists.newArrayList(key, key2)))
          .hasSize(2)
          .containsExactlyInAnyOrder(f, f2);
    }
  }

  @Nested
  class WhenRegisteringAccess {
    private PgTransformationCache underTest;
    @Mock private TransformationCache.Key cacheKey;
    @Mock private Fact f;

    @BeforeEach
    void setup() {
      underTest = spy(new PgTransformationCache(jdbcTemplate, registryMetrics, 10));
    }

    @Test
    void happyPath() {
      underTest.registerAccess(cacheKey);
      assertThat(underTest.buffer()).containsKey(cacheKey);
      assertThat(underTest.buffer().get(cacheKey)).isNull();
    }

    @Test
    void doesNotOverwriteWrite() {
      underTest.put(cacheKey, f);
      underTest.registerAccess(cacheKey);

      Mockito.verify(underTest).registerAccess(cacheKey);
      // the write is still there
      assertThat(underTest.buffer()).containsEntry(cacheKey, f);
    }
  }

  @Nested
  class WhenRegisteringWrite {
    private PgTransformationCache underTest;
    @Mock private TransformationCache.Key cacheKey;
    @Mock private Fact f;

    @BeforeEach
    void setup() {
      underTest = new PgTransformationCache(jdbcTemplate, registryMetrics, 10);
    }

    @Test
    void happyPath() {
      underTest.registerWrite(cacheKey, f);
      assertThat(underTest.buffer()).containsKey(cacheKey);
      assertThat(underTest.buffer().get(cacheKey)).isNotNull();
    }
  }

  @Nested
  class WhenFlushingIfNecessary {
    private PgTransformationCache underTest;
    @Mock private TransformationCache.Key cacheKey;
    @Mock private TransformationCache.Key otherCacheKey;
    @Mock private Fact f;

    @BeforeEach
    void setup() {
      underTest = spy(new PgTransformationCache(jdbcTemplate, registryMetrics, 2));
    }

    @SneakyThrows
    @Test
    void happyPath() {
      underTest.registerWrite(cacheKey, f);
      assertThat(underTest.buffer()).hasSize(1);
      underTest.registerAccess(otherCacheKey).get();
      assertThat(underTest.buffer()).isEmpty();

      Mockito.verify(underTest, Mockito.times(2)).flushIfNecessary();
    }
  }

  @Nested
  class WhenCompacting {
    private final ZonedDateTime THRESHOLD_DATE = ZonedDateTime.now().minusYears(99);
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest = spy(new PgTransformationCache(jdbcTemplate, registryMetrics, 2));
    }

    @Test
    void deletesFromDatabase() {

      underTest.compact(THRESHOLD_DATE);

      Mockito.verify(underTest).flush();
      Mockito.verify(jdbcTemplate)
          .update(
              eq("DELETE FROM transformationcache WHERE last_access < ?"),
              eq(new Date(THRESHOLD_DATE.toInstant().toEpochMilli())));
    }
  }

  @Nested
  class WhenFlushing {
    @Mock private TransformationCache.@NonNull Key key;
    @Mock private @NonNull Fact f;
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest = spy(new PgTransformationCache(jdbcTemplate, registryMetrics, 10));
    }

    @Test
    void afterPut() {
      underTest.put(key, f);
      assertThat(underTest.buffer()).isNotEmpty();
      underTest.flush();
      assertThat(underTest.buffer()).isEmpty();
    }

    @Test
    void flushOnEmptyBuffer() {
      underTest.flush();
      verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void afterAcess() {
      underTest.registerAccess(key);
      assertThat(underTest.buffer()).isNotEmpty();
      underTest.flush();
      assertThat(underTest.buffer()).isEmpty();
    }

    @Test
    void logsException() {
      underTest.registerAccess(key);
      when(jdbcTemplate.batchUpdate(anyString(), any(List.class)))
          .thenThrow(IllegalArgumentException.class);
      // TODO use logcaptor after merge with #2075
      underTest.flush();
    }
  }

  @Nested
  class WhenInsertingBufferedTransformations {
    final HashMap<TransformationCache.Key, Fact> buffer = new HashMap<>();

    @Mock private TransformationCache.@NonNull Key key;
    @Mock private @NonNull Fact f;
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest = spy(new PgTransformationCache(jdbcTemplate, registryMetrics, 10));
    }

    @Test
    void insertsAll() {

      buffer.put(Mockito.mock(TransformationCache.Key.class), Mockito.mock(Fact.class));
      buffer.put(Mockito.mock(TransformationCache.Key.class), Mockito.mock(Fact.class));
      buffer.put(Mockito.mock(TransformationCache.Key.class), null);
      buffer.put(Mockito.mock(TransformationCache.Key.class), Mockito.mock(Fact.class));
      buffer.put(Mockito.mock(TransformationCache.Key.class), null);

      underTest.insertBufferedTransformations(buffer);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<Object[]>> m = ArgumentCaptor.forClass(List.class);

      Mockito.verify(jdbcTemplate)
          .batchUpdate(matches("INSERT INTO transformationcache .*"), m.capture());

      assertThat(m.getValue()).hasSize(3);
    }
  }

  @Nested
  class WhenInsertingBufferedAccesses {
    final HashMap<TransformationCache.Key, Fact> buffer = new HashMap<>();

    @Mock private TransformationCache.@NonNull Key key;
    @Mock private @NonNull Fact f;
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest = spy(new PgTransformationCache(jdbcTemplate, registryMetrics, 10));
    }

    @Test
    void insertsAll() {

      buffer.put(Mockito.mock(TransformationCache.Key.class), Mockito.mock(Fact.class));
      buffer.put(Mockito.mock(TransformationCache.Key.class), Mockito.mock(Fact.class));
      buffer.put(Mockito.mock(TransformationCache.Key.class), null);
      buffer.put(Mockito.mock(TransformationCache.Key.class), Mockito.mock(Fact.class));
      buffer.put(Mockito.mock(TransformationCache.Key.class), null);

      underTest.insertBufferedAccesses(buffer);

      ArgumentCaptor<PreparedStatementCreator> m =
          ArgumentCaptor.forClass(PreparedStatementCreator.class);

      Mockito.verify(jdbcTemplate).update(m.capture());

      assertThat(m.getValue())
          .hasFieldOrPropertyWithValue(
              "sql", "UPDATE transformationcache SET last_access=now() WHERE cache_key IN (?, ?)");
    }
  }
}
