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
import static org.mockito.Mockito.*;

import com.google.common.collect.Lists;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgFact;
import org.factcast.store.registry.NOPRegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.transaction.*;

@SuppressWarnings("ALL")
@ExtendWith(MockitoExtension.class)
class PgTransformationCacheTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private NamedParameterJdbcTemplate namedJdbcTemplate;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private PlatformTransactionManager platformTransactionManager;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private TransactionStatus transaction;

  RegistryMetrics registryMetrics = spy(new NOPRegistryMetrics());

  @Mock StoreConfigurationProperties storeConfigurationProperties;

  @BeforeEach
  void setup() {
    when(platformTransactionManager.getTransaction(any())).thenReturn(transaction);
  }

  @Nested
  class WhenPuting {
    @Mock private TransformationCache.@NonNull Key key;
    @Mock private @NonNull PgFact f;
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest =
          spy(
              new PgTransformationCache(
                  platformTransactionManager,
                  jdbcTemplate,
                  namedJdbcTemplate,
                  registryMetrics,
                  storeConfigurationProperties,
                  10));
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
    @Mock private PgFact f;
    @Mock private PgFact f2;
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest =
          spy(
              new PgTransformationCache(
                  platformTransactionManager,
                  jdbcTemplate,
                  namedJdbcTemplate,
                  registryMetrics,
                  storeConfigurationProperties,
                  10));
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
      Mockito.when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
          .thenReturn(Collections.singletonList(f));
      assertThat(underTest.find(key)).containsSame(f);
    }

    @Test
    void registersMiss() {
      Mockito.when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
          .thenReturn(Collections.emptyList());
      assertThat(underTest.find(key)).isEmpty();
      Mockito.verify(registryMetrics).count(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_MISS);
    }

    @Test
    void registersHit() {
      Mockito.when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
          .thenReturn(Collections.singletonList(f));
      assertThat(underTest.find(key)).isNotEmpty();
      Mockito.verify(registryMetrics).count(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_HIT);
    }
  }

  @Nested
  class WhenFindingAll {
    @Mock private TransformationCache.Key key;
    @Mock private TransformationCache.Key key2;
    @Mock private PgFact f;
    @Mock private PgFact f2;
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest =
          spy(
              new PgTransformationCache(
                  platformTransactionManager,
                  jdbcTemplate,
                  namedJdbcTemplate,
                  registryMetrics,
                  storeConfigurationProperties,
                  10));
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

      verify(underTest).registerAccess(Lists.newArrayList(key2));
    }

    @Test
    void findsAllInCache() {
      final var keysToFind = Lists.newArrayList(key, key2);

      Mockito.when(
              namedJdbcTemplate.query(
                  anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
          .thenReturn(Lists.newArrayList(f, f2));

      assertThat(underTest.findAll(keysToFind)).hasSize(2).containsExactlyInAnyOrder(f, f2);

      verify(underTest).registerAccess(keysToFind);
    }
  }

  @Nested
  class WhenRegisteringAccess {
    private PgTransformationCache underTest;
    @Mock private TransformationCache.Key cacheKey;
    @Mock private PgFact f;

    @BeforeEach
    void setup() {
      underTest =
          spy(
              new PgTransformationCache(
                  platformTransactionManager,
                  jdbcTemplate,
                  namedJdbcTemplate,
                  registryMetrics,
                  storeConfigurationProperties,
                  10));
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
    @Mock private PgFact f;

    @BeforeEach
    void setup() {
      underTest =
          new PgTransformationCache(
              platformTransactionManager,
              jdbcTemplate,
              namedJdbcTemplate,
              registryMetrics,
              storeConfigurationProperties,
              10);
    }

    @Test
    void happyPath() {
      underTest.registerWrite(cacheKey, f);
      assertThat(underTest.buffer().containsKey(cacheKey)).isTrue();
      assertThat(underTest.buffer().get(cacheKey)).isNotNull();
    }
  }

  @Nested
  class WhenRunningInTransactionWithLock {
    private PgTransformationCache underTest;

    @Mock Runnable r;

    @BeforeEach
    void setup() {
      underTest =
          spy(
              new PgTransformationCache(
                  platformTransactionManager,
                  jdbcTemplate,
                  namedJdbcTemplate,
                  registryMetrics,
                  storeConfigurationProperties,
                  2));
    }

    @SneakyThrows
    @Test
    void locks() {
      underTest.inTransactionWithLock(r);

      InOrder inOrder = inOrder(jdbcTemplate, r);
      inOrder.verify(jdbcTemplate).execute("LOCK TABLE transformationcache IN EXCLUSIVE MODE");
      inOrder.verify(r).run();
    }
  }

  @Nested
  class WhenFlushingIfNecessary {
    private PgTransformationCache underTest;
    @Mock private TransformationCache.Key cacheKey;
    @Mock private TransformationCache.Key otherCacheKey;
    @Mock private PgFact f;

    @BeforeEach
    void setup() {
      underTest =
          spy(
              new PgTransformationCache(
                  platformTransactionManager,
                  jdbcTemplate,
                  namedJdbcTemplate,
                  registryMetrics,
                  storeConfigurationProperties,
                  2));
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
          spy(
              new PgTransformationCache(
                  platformTransactionManager,
                  jdbcTemplate,
                  namedJdbcTemplate,
                  registryMetrics,
                  storeConfigurationProperties,
                  2));
    }

    @Test
    void deletesFromDatabase() {

      underTest.compact(THRESHOLD_DATE);

      Mockito.verify(underTest).flush();
      Mockito.verify(jdbcTemplate).execute("LOCK TABLE transformationcache IN EXCLUSIVE MODE");

      Mockito.verify(jdbcTemplate)
          .update(
              "DELETE FROM transformationcache WHERE cache_key in (SELECT cache_key FROM transformationcache_access WHERE last_access < ?)",
              Timestamp.from(THRESHOLD_DATE.toInstant()));
      Mockito.verify(jdbcTemplate)
          .update(
              "DELETE FROM transformationcache_access WHERE last_access < ?",
              Timestamp.from(THRESHOLD_DATE.toInstant()));
    }

    @Test
    void doesNotCompactIfInReadOnlyMode() {
      when(storeConfigurationProperties.isReadOnlyModeEnabled()).thenReturn(true);
      underTest.registerAccess(TransformationCache.Key.of(UUID.randomUUID(), 1, "someChainId"));
      Assertions.assertThat(underTest.buffer().size()).isOne();

      underTest.compact(THRESHOLD_DATE);

      Assertions.assertThat(underTest.buffer().size()).isZero();
      Mockito.verifyNoInteractions(jdbcTemplate);
    }
  }

  @Nested
  class WhenFlushing {
    @Mock private TransformationCache.@NonNull Key key;
    @Mock private TransformationCache.@NonNull Key key2;
    @Mock private @NonNull PgFact f;
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest =
          spy(
              new PgTransformationCache(
                  platformTransactionManager,
                  jdbcTemplate,
                  namedJdbcTemplate,
                  registryMetrics,
                  storeConfigurationProperties,
                  10));
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
      Mockito.verify(jdbcTemplate).execute("LOCK TABLE transformationcache IN EXCLUSIVE MODE");
      assertThat(underTest.buffer().size()).isZero();
    }

    @Test
    @SneakyThrows
    void afterAcess_list() {
      underTest.registerAccess(List.of(key, key2)).get();

      assertThat(underTest.buffer().size()).isEqualTo(2);
      assertThat(underTest.buffer().buffer().values()).allMatch(x -> x == null);
      verify(underTest).flushIfNecessary();
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

    @Test
    void doesnotFlushInReadOnlyMode() {
      when(storeConfigurationProperties.isReadOnlyModeEnabled()).thenReturn(true);

      underTest.registerAccess(key);
      assertThat(underTest.buffer().size()).isPositive();

      underTest.flush();
      assertThat(underTest.buffer().size()).isZero();

      verifyNoInteractions(jdbcTemplate);
    }
  }

  @Nested
  class WhenInsertingBufferedTransformations {
    CacheBuffer buffer;

    @Mock private TransformationCache.@NonNull Key key;
    @Mock private @NonNull Fact f;
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest =
          spy(
              new PgTransformationCache(
                  platformTransactionManager,
                  jdbcTemplate,
                  namedJdbcTemplate,
                  registryMetrics,
                  storeConfigurationProperties,
                  10));
      buffer = underTest.buffer();
    }

    @Test
    void insertsAll() {

      buffer.put(Mockito.mock(TransformationCache.Key.class), Mockito.mock(PgFact.class));
      buffer.put(Mockito.mock(TransformationCache.Key.class), Mockito.mock(PgFact.class));
      buffer.put(Mockito.mock(TransformationCache.Key.class), null);
      buffer.put(Mockito.mock(TransformationCache.Key.class), Mockito.mock(PgFact.class));
      buffer.put(Mockito.mock(TransformationCache.Key.class), null);

      underTest.flush();

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<Object[]>> m = ArgumentCaptor.forClass(List.class);
      Mockito.verify(jdbcTemplate).execute("LOCK TABLE transformationcache IN EXCLUSIVE MODE");

      Mockito.verify(jdbcTemplate)
          .batchUpdate(matches("INSERT INTO transformationcache .*"), m.capture());

      assertThat(m.getValue()).hasSize(3);
    }
  }

  @Nested
  class WhenInsertingBufferedAccesses {
    CacheBuffer buffer;

    @Mock private TransformationCache.@NonNull Key key;
    @Mock private @NonNull Fact f;
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest =
          spy(
              new PgTransformationCache(
                  platformTransactionManager,
                  jdbcTemplate,
                  namedJdbcTemplate,
                  registryMetrics,
                  storeConfigurationProperties,
                  10));
      buffer = underTest.buffer();
    }

    @Test
    void insertsAll() {

      buffer.put(Mockito.mock(TransformationCache.Key.class), Mockito.mock(PgFact.class));
      buffer.put(Mockito.mock(TransformationCache.Key.class), Mockito.mock(PgFact.class));
      buffer.put(Mockito.mock(TransformationCache.Key.class), null);
      buffer.put(Mockito.mock(TransformationCache.Key.class), Mockito.mock(PgFact.class));
      buffer.put(Mockito.mock(TransformationCache.Key.class), null);

      underTest.flush();

      ArgumentCaptor<List<Object[]>> m = ArgumentCaptor.forClass(List.class);

      Mockito.verify(jdbcTemplate, times(1))
          .execute("LOCK TABLE transformationcache IN EXCLUSIVE MODE");
      Mockito.verify(jdbcTemplate, times(1))
          .batchUpdate(matches("INSERT INTO transformationcache .*"), m.capture());
      assertThat((Collection) m.getValue()).isNotNull().hasSize(3);
      Mockito.verify(jdbcTemplate, times(1))
          .batchUpdate(
              matches("/\\* insert \\*/ INSERT INTO transformationcache_access.*"), m.capture());
      assertThat((Collection) m.getValue()).isNotNull().hasSize(3);

      ArgumentCaptor<List<Object[]>> ids = ArgumentCaptor.forClass(List.class);
      Mockito.verify(jdbcTemplate, times(1))
          .batchUpdate(
              matches("/\\* touch \\*/ INSERT INTO transformationcache_access.*"), ids.capture());
      assertThat((Collection) (ids.getValue())).isNotNull().hasSize(2);
    }
  }

  @Nested
  class WhenInvalidatingTransformationForNamespaceAndType {
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest =
          spy(
              new PgTransformationCache(
                  platformTransactionManager,
                  jdbcTemplate,
                  namedJdbcTemplate,
                  registryMetrics,
                  storeConfigurationProperties,
                  10));
    }

    @Test
    void clearsAndFlushesAccessesOnly() {
      underTest.invalidateTransformationFor("theNamespace", "theType");

      Mockito.verify(jdbcTemplate).execute("LOCK TABLE transformationcache IN EXCLUSIVE MODE");
      verify(underTest, times(1)).flush();
    }

    @Test
    void deletesFromTransformationCache() {
      underTest.invalidateTransformationFor("theNamespace", "theType");

      ArgumentCaptor<String> ns = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);

      Mockito.verify(jdbcTemplate).execute("LOCK TABLE transformationcache IN EXCLUSIVE MODE");
      Mockito.verify(jdbcTemplate)
          .update(
              matches("DELETE FROM transformationcache WHERE .*"), ns.capture(), type.capture());

      assertThat(ns.getAllValues().get(0)).isEqualTo("theNamespace");
      assertThat(type.getAllValues().get(0)).isEqualTo("theType");
    }

    @Test
    void doesNotDeleteWhenReadOnlyMode() {
      when(storeConfigurationProperties.isReadOnlyModeEnabled()).thenReturn(true);

      underTest.invalidateTransformationFor("theNamespace", "theType");

      verifyNoInteractions(jdbcTemplate);
    }
  }

  @Nested
  class WhenInvalidatingTransformationForFactId {
    private PgTransformationCache underTest;

    @BeforeEach
    void setup() {
      underTest =
          spy(
              new PgTransformationCache(
                  platformTransactionManager,
                  jdbcTemplate,
                  namedJdbcTemplate,
                  registryMetrics,
                  storeConfigurationProperties,
                  10));
    }

    @Test
    void clearsAndFlushesAccessesOnly() {
      underTest.invalidateTransformationFor(UUID.randomUUID());

      Mockito.verify(jdbcTemplate).execute("LOCK TABLE transformationcache IN EXCLUSIVE MODE");
      verify(underTest, times(1)).flush();
    }

    @Test
    void deletesFromTransformationCache() {
      final var factId = UUID.randomUUID();

      underTest.invalidateTransformationFor(factId);

      ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);

      Mockito.verify(jdbcTemplate).execute("LOCK TABLE transformationcache IN EXCLUSIVE MODE");
      Mockito.verify(jdbcTemplate)
          .update(matches("DELETE FROM transformationcache WHERE cache_key LIKE ?"), id.capture());

      assertThat(id.getAllValues().get(0)).isEqualTo(factId + "%");
    }

    @Test
    void doesNotDeleteWhenReadOnlyMode() {
      when(storeConfigurationProperties.isReadOnlyModeEnabled()).thenReturn(true);

      underTest.invalidateTransformationFor(UUID.randomUUID());

      verifyNoInteractions(jdbcTemplate);
    }
  }
}
