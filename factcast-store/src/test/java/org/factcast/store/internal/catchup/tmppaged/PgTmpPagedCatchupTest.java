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
package org.factcast.store.internal.catchup.tmppaged;

import java.util.*;
import java.util.concurrent.atomic.*;

import org.assertj.core.util.Lists;
import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgConstants;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.PgPostQueryMatcher;
import org.factcast.store.internal.StoreMetrics;
import org.factcast.store.internal.blacklist.PgBlacklist;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.jdbc.PgConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementSetter;

import io.micrometer.core.instrument.Counter;

import lombok.NonNull;
import lombok.SneakyThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PgTmpPagedCatchupTest {

  @Mock @NonNull PgConnectionSupplier connectionSupplier;
  @Mock @NonNull StoreConfigurationProperties props;
  @Mock @NonNull SubscriptionRequestTO request;
  @Mock @NonNull PgPostQueryMatcher postQueryMatcher;
  @Mock @NonNull SubscriptionImpl subscription;
  @Mock @NonNull AtomicLong serial;
  @Mock @NonNull PgMetrics metrics;
  @Mock @NonNull Counter counter;
  @Mock @NonNull PgBlacklist blacklist;
  @Mock @NonNull CurrentStatementHolder statementHolder;
  @InjectMocks PgTmpPagedCatchup underTest;

  @Nested
  class WhenRunning {
    @BeforeEach
    void setup() {}

    @SneakyThrows
    @Test
    void connectionHandling() {
      PgConnection con = mock(PgConnection.class);
      when(connectionSupplier.get()).thenReturn(con);

      var uut = spy(underTest);
      doNothing().when(uut).fetch(any());

      uut.run();

      verify(con).close();
    }

    @SneakyThrows
    @Test
    void removesStatement() {
      PgConnection con = mock(PgConnection.class);
      when(connectionSupplier.get()).thenReturn(con);

      var uut = spy(underTest);
      doNothing().when(uut).fetch(any());

      uut.run();

      verify(statementHolder).statement(null);
    }
  }

  @Nested
  class WhenFetching {
    @Mock(lenient = true)
    @NonNull
    JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
      doNothing().when(jdbc).execute(anyString());

      when(postQueryMatcher.canBeSkipped()).thenReturn(true);
    }

    @Test
    void createsTempTable() {
      when(jdbc.execute(anyString(), any(PreparedStatementCallback.class))).thenReturn(0L);
      underTest.fetch(jdbc);
      verify(jdbc).execute("CREATE TEMPORARY TABLE catchup(ser bigint)");
    }

    @Test
    void createsIndex() {
      when(jdbc.execute(anyString(), any(PreparedStatementCallback.class))).thenReturn(0L);
      underTest.fetch(jdbc);
      verify(jdbc).execute("CREATE INDEX catchup_tmp_idx1 ON catchup(ser ASC)");
    }

    @Test
    void notifies() {
      when(jdbc.execute(anyString(), any(PreparedStatementCallback.class))).thenReturn(1L);
      List<Fact> testFactList = new ArrayList<Fact>();
      Fact testFact = Fact.builder().buildWithoutPayload();
      testFactList.add(testFact);
      when(jdbc.query(
              eq(PgConstants.SELECT_FACT_FROM_CATCHUP),
              any(PreparedStatementSetter.class),
              any(PgFactExtractor.class)))
          .thenReturn(testFactList)
          .thenReturn(new ArrayList<Fact>());
      // stop iteration after first fetch
      when(metrics.counter(StoreMetrics.EVENT.CATCHUP_FACT)).thenReturn(mock(Counter.class));
      underTest.fetch(jdbc);
      verify(subscription).notifyElement(testFact);
    }

    @Test
    void filtersBlacklisted() {
      when(jdbc.execute(anyString(), any(PreparedStatementCallback.class))).thenReturn(2L);
      List<Fact> testFactList = new ArrayList<Fact>();
      UUID id1 = UUID.randomUUID();
      UUID id2 = UUID.randomUUID();
      when(blacklist.isBlocked(id1)).thenReturn(true);
      when(blacklist.isBlocked(id2)).thenReturn(false);
      Fact testFact1 = Fact.builder().id(id1).buildWithoutPayload();
      Fact testFact2 = Fact.builder().id(id2).buildWithoutPayload();
      testFactList.add(testFact1);
      testFactList.add(testFact2);
      when(jdbc.query(
              eq(PgConstants.SELECT_FACT_FROM_CATCHUP),
              any(PreparedStatementSetter.class),
              any(PgFactExtractor.class)))
          .thenReturn(testFactList)
          .thenReturn(new ArrayList<Fact>());
      // stop iteration after first fetch
      when(metrics.counter(StoreMetrics.EVENT.CATCHUP_FACT)).thenReturn(mock(Counter.class));
      underTest.fetch(jdbc);
      verify(subscription, never()).notifyElement(testFact1);
      verify(subscription, times(1)).notifyElement(testFact2);
    }

    @Test
    void counts() {
      when(metrics.counter(any())).thenReturn(counter);
      when(jdbc.execute(anyString(), any(PreparedStatementCallback.class))).thenReturn(1L);
      List<Fact> testFactList = Lists.newArrayList(new TestFact(), new TestFact());
      when(jdbc.query(
              eq(PgConstants.SELECT_FACT_FROM_CATCHUP),
              any(PreparedStatementSetter.class),
              any(PgFactExtractor.class)))
          .thenReturn(testFactList)
          .thenReturn(Collections.emptyList());

      underTest.fetch(jdbc);

      verify(counter).increment(testFactList.size());
    }
  }
}
