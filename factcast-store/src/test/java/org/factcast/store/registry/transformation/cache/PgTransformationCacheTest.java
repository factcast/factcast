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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import lombok.NonNull;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@SuppressWarnings("ALL")
@ExtendWith(MockitoExtension.class)
class PgTransformationCacheTest {

  private static final int MAX_BUFFER_SIZE = 24;
  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private NamedParameterJdbcTemplate namedJdbcTemplate;
  RegistryMetrics registryMetrics = spy(new NOPRegistryMetrics());

  @Nested
  class WhenPuting {
    @Mock private TransformationCache.@NonNull Key key;
    @Mock private @NonNull Fact f;
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest =
          spy(new PgTransformationCache(jdbcTemplate, namedJdbcTemplate, registryMetrics, 10));
    }

    @Test
    void buffers() {
      underTest.put(key, f);

      Mockito.verify(underTest).registerWrite(key, f);
      assertThat(underTest.buffer().get(key)).isEqualTo(f);
    }

    @Test
    void overwritesAccess() {
      underTest.registerAccess(key);
      underTest.put(key, f);

      Mockito.verify(underTest).registerWrite(key, f);
      assertThat(underTest.buffer().get(key)).isEqualTo(f);
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
      underTest =
          spy(new PgTransformationCache(jdbcTemplate, namedJdbcTemplate, registryMetrics, 10));
    }

    @Test
    void findsUnflushed() {
      underTest.put(key, f);
      //noinspection OptionalGetWithoutIsPresent
      assertThat(underTest.find(key)).containsSame(f);
    }

    @Test
    void findsFlushed() {
      //noinspection OptionalGetWithoutIsPresent
      Mockito.when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
          .thenReturn(Collections.singletonList(f));
      assertThat(underTest.find(key)).containsSame(f);
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
      underTest =
          spy(new PgTransformationCache(jdbcTemplate, namedJdbcTemplate, registryMetrics, 10));
    }

    @Test
    void findsBoth() {
      underTest.put(key, f);
      ArgumentCaptor<SqlParameterSource> cap = ArgumentCaptor.forClass(SqlParameterSource.class);
      Mockito.when(namedJdbcTemplate.query(anyString(), cap.capture(), any(RowMapper.class)))
          .thenReturn(Collections.singletonList(f2));

      assertThat(underTest.findAll(Lists.newArrayList(key, key2)))
          .hasSize(2)
          .containsExactlyInAnyOrder(f, f2);

      // only one key is looked for in persistent cache
      Collection ids = (Collection) cap.getValue().getValue("ids");
      assertThat(ids).hasSize(1).doesNotContain(key);
    }

    @Test
    void findsAllInCache() {
      Mockito.when(
              namedJdbcTemplate.query(
                  anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
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
      underTest =
          spy(new PgTransformationCache(jdbcTemplate, namedJdbcTemplate, registryMetrics, 10));
    }

    @Test
    void happyPath() {
      underTest.registerAccess(cacheKey);
      assertThat(underTest.buffer().containsKey(cacheKey)).isTrue();
      assertThat(underTest.buffer().get(cacheKey)).isNull();
    }

    @Test
    void doesNotOverwriteWrite() {
      underTest.put(cacheKey, f);
      underTest.registerAccess(cacheKey);

      Mockito.verify(underTest).registerAccess(cacheKey);
      // the write is still there
      assertThat(underTest.buffer().get(cacheKey)).isEqualTo(f);
    }
  }

  @Nested
  class WhenRegisteringWrite {
    private PgTransformationCache underTest;
    @Mock private TransformationCache.Key cacheKey;
    @Mock private Fact f;

    @BeforeEach
    void setup() {
      underTest = new PgTransformationCache(jdbcTemplate, namedJdbcTemplate, registryMetrics, 10);
    }

    @Test
    void happyPath() {
      underTest.registerWrite(cacheKey, f);
      assertThat(underTest.buffer().containsKey(cacheKey)).isTrue();
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
      underTest =
          spy(new PgTransformationCache(jdbcTemplate, namedJdbcTemplate, registryMetrics, 2));
    }

    @SneakyThrows
    @Test
    void happyPath() {
      underTest.registerWrite(cacheKey, f);
      assertThat(underTest.buffer().size()).isEqualTo(1);
      underTest.registerAccess(otherCacheKey).get();
      assertThat(underTest.buffer().size()).isZero();

      Mockito.verify(underTest, Mockito.times(2)).flushIfNecessary();
    }
  }

  @Nested
  class WhenCompacting {
    private final ZonedDateTime THRESHOLD_DATE = ZonedDateTime.now().minusYears(99);
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest =
          spy(new PgTransformationCache(jdbcTemplate, namedJdbcTemplate, registryMetrics, 2));
    }

    @Test
    void deletesFromDatabase() {

      underTest.compact(THRESHOLD_DATE);

      Mockito.verify(underTest).flush();
      Mockito.verify(jdbcTemplate)
          .update(
              "DELETE FROM transformationcache WHERE last_access < ?",
              new Date(THRESHOLD_DATE.toInstant().toEpochMilli()));
    }
  }

  @Nested
  class WhenFlushing {
    @Mock private TransformationCache.@NonNull Key key;
    @Mock private TransformationCache.@NonNull Key key2;
    @Mock private @NonNull Fact f;
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest =
          spy(new PgTransformationCache(jdbcTemplate, namedJdbcTemplate, registryMetrics, 10));
    }

    @Test
    void afterPut() {
      underTest.put(key, f);
      assertThat(underTest.buffer().size()).isPositive();
      underTest.flush();
      assertThat(underTest.buffer().size()).isZero();
    }

    @Test
    void flushOnEmptyBuffer() {
      underTest.flush();
      verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void afterAcess() {
      underTest.registerAccess(key);
      assertThat(underTest.buffer().size()).isPositive();
      underTest.flush();
      assertThat(underTest.buffer().size()).isZero();
    }

    @Test
    void logsException() {
      underTest.registerAccess(key2);
      underTest.registerWrite(key, f);
      when(jdbcTemplate.batchUpdate(anyString(), any(List.class)))
          .thenThrow(IllegalArgumentException.class);
      LogCaptor logCaptor = LogCaptor.forClass(PgTransformationCache.class);

      underTest.flush();

      assertThat(logCaptor.getErrorLogs())
          .containsExactly(
              "Could not complete batch update of transformations on transformation cache.");
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
      underTest =
          spy(new PgTransformationCache(jdbcTemplate, namedJdbcTemplate, registryMetrics, 10));
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
      underTest =
          spy(new PgTransformationCache(jdbcTemplate, namedJdbcTemplate, registryMetrics, 10));
    }

    @Test
    void insertsAll() {

      buffer.put(Mockito.mock(TransformationCache.Key.class), Mockito.mock(Fact.class));
      buffer.put(Mockito.mock(TransformationCache.Key.class), Mockito.mock(Fact.class));
      buffer.put(Mockito.mock(TransformationCache.Key.class), null);
      buffer.put(Mockito.mock(TransformationCache.Key.class), Mockito.mock(Fact.class));
      buffer.put(Mockito.mock(TransformationCache.Key.class), null);

      underTest.insertBufferedAccesses(buffer);

      ArgumentCaptor<SqlParameterSource> m = ArgumentCaptor.forClass(SqlParameterSource.class);

      Mockito.verify(namedJdbcTemplate).update(anyString(), m.capture());

      Collection ids = (Collection) m.getValue().getValue("ids");
      assertThat(ids).isNotNull().hasSize(2);
    }
  }

  @Nested
  class WhenInvalidatingTransformationFor {
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest =
          spy(new PgTransformationCache(jdbcTemplate, namedJdbcTemplate, registryMetrics, 10));
    }

    @Test
    void clearsAndFlushesAccessesOnly() {
      underTest.invalidateTransformationFor("theNamespace", "theType");

      verify(underTest, times(1)).clearAndFlushAccessesOnly();
    }

    @Test
    void deletesFromTransformationCache() {
      underTest.invalidateTransformationFor("theNamespace", "theType");

      ArgumentCaptor<String> m = ArgumentCaptor.forClass(String.class);

      Mockito.verify(jdbcTemplate)
          .update(matches("DELETE FROM transformationcache WHERE .*"), m.capture());

      assertThat(m.getAllValues()).hasSize(2);
      assertThat(m.getAllValues().get(0)).isEqualTo("theNamespace");
      assertThat(m.getAllValues().get(1)).isEqualTo("theType");
    }
  }

  @Nested
  class WhenClearingAndFlushingAccessOnly {
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest =
          spy(new PgTransformationCache(jdbcTemplate, namedJdbcTemplate, registryMetrics, 10));
    }

    @Test
    void clearsBuffer() {
      underTest.registerWrite(
          Mockito.mock(TransformationCache.Key.class), Mockito.mock(Fact.class));
      underTest.registerAccess(Mockito.mock(TransformationCache.Key.class));

      underTest.clearAndFlushAccessesOnly();

      assertThat(underTest.buffer().size()).isZero();
    }

    @Test
    void flushesAccessesOnly() {
      underTest.registerWrite(
          Mockito.mock(TransformationCache.Key.class), Mockito.mock(Fact.class));
      underTest.registerAccess(Mockito.mock(TransformationCache.Key.class));

      underTest.clearAndFlushAccessesOnly();

      verify(underTest, times(1)).insertBufferedAccesses(any());
      verify(underTest, never()).insertBufferedTransformations(any());
    }
  }
}
