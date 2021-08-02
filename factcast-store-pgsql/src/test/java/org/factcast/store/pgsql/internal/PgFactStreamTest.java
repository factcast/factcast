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
package org.factcast.store.pgsql.internal;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.eventbus.EventBus;
import io.micrometer.core.instrument.DistributionSummary;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import lombok.SneakyThrows;

import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.store.pgsql.internal.PgFactStream.RatioLogLevel;
import org.factcast.store.pgsql.internal.catchup.PgCatchupFactory;
import org.factcast.store.pgsql.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.pgsql.internal.query.PgLatestSerialFetcher;
import org.factcast.test.Slf4jHelper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import slf4jtest.LogLevel;

@ExtendWith(MockitoExtension.class)
public class PgFactStreamTest {

  @Mock SubscriptionRequest req;
  @Mock SubscriptionImpl sub;
  @Mock PgSynchronizedQuery query;
  @Mock FastForwardTarget ffwdTarget;
  @Mock PgMetrics metrics;
  @Mock SubscriptionRequestTO reqTo;
  @Mock PgFactIdToSerialMapper id2ser;
  @Mock JdbcTemplate jdbc;
  @Mock PgLatestSerialFetcher fetcher;
  @Mock DistributionSummary distributionSummary;
  @InjectMocks PgFactStream uut;

  @Test
  public void testConnectNullParameter() {
    assertThrows(NullPointerException.class, () -> uut.connect(null));
  }

  @SuppressWarnings({"unused", "UnstableApiUsage"})
  @Nested
  class FastForward {

    @Mock JdbcTemplate jdbcTemplate;

    @Mock EventBus eventBus;

    @Mock PgFactIdToSerialMapper idToSerMapper;

    @Mock SubscriptionImpl subscription;

    @Mock final AtomicLong serial = new AtomicLong(0);

    @Mock final AtomicBoolean disconnected = new AtomicBoolean(false);

    @Mock PgLatestSerialFetcher fetcher;

    @Mock PgCatchupFactory pgCatchupFactory;
    @Mock FastForwardTarget ffwdTarget;
    @Mock SubscriptionRequest request;
    @InjectMocks PgFactStream underTest;

    @Test
    void noFfwdNotConnected() {

      underTest.close();
      underTest.fastForward(request, subscription);

      verifyNoInteractions(subscription);
    }

    @Test
    void noFfwdFromScratch() {
      when(request.startingAfter()).thenReturn(Optional.empty());

      underTest.fastForward(request, subscription);

      verifyNoInteractions(subscription);
    }

    @Test
    void noFfwdIfNoTarget() {
      UUID uuid = UUID.randomUUID();
      when(request.startingAfter()).thenReturn(Optional.of(uuid));
      when(idToSerMapper.retrieve(uuid)).thenReturn(10L);
      when(ffwdTarget.targetId()).thenReturn(null);

      underTest.fastForward(request, subscription);

      verifyNoInteractions(subscription);
    }

    @Test
    void noFfwdIfFactsHaveBeenSent() {
      UUID uuid = UUID.randomUUID();
      when(request.startingAfter()).thenReturn(Optional.of(uuid));
      when(idToSerMapper.retrieve(uuid)).thenReturn(10L);
      when(ffwdTarget.targetId()).thenReturn(UUID.randomUUID());
      serial.set(100);

      underTest.fastForward(request, subscription);

      verifyNoInteractions(subscription);
    }

    @Test
    void noFfwdIfTargetBehind() {
      UUID uuid = UUID.randomUUID();
      when(request.startingAfter()).thenReturn(Optional.of(uuid));
      when(idToSerMapper.retrieve(uuid)).thenReturn(10L);
      when(ffwdTarget.targetId()).thenReturn(UUID.randomUUID());
      when(ffwdTarget.targetSer()).thenReturn(9L);

      underTest.fastForward(request, subscription);

      verifyNoInteractions(subscription);
    }

    @Test
    void ffwdIfTargetAhead() {
      UUID uuid = UUID.randomUUID();
      when(request.startingAfter()).thenReturn(Optional.of(uuid));
      when(idToSerMapper.retrieve(uuid)).thenReturn(10L);
      UUID target = UUID.randomUUID();
      when(ffwdTarget.targetId()).thenReturn(target);
      when(ffwdTarget.targetSer()).thenReturn(90L);

      underTest.fastForward(request, subscription);

      verify(subscription).notifyFastForward(target);
    }
  }

  @Test
  void logsCatchupTransformationStats() {
    uut = spy(uut);
    doNothing().when(uut).catchup(any());
    doNothing().when(uut).logCatchupTransformationStats();

    uut.catchupAndFollow(req, sub, query);

    verify(uut).logCatchupTransformationStats();
  }

  @Test
  void debugLevelIfToFewFacts() {
    assertThat(uut.calculateLogLevel(5, 100)).isSameAs(RatioLogLevel.DEBUG);
    assertThat(uut.calculateLogLevel(5, 0)).isSameAs(RatioLogLevel.DEBUG);
    assertThat(uut.calculateLogLevel(32, 80)).isSameAs(RatioLogLevel.DEBUG);
    verifyNoInteractions(metrics);
  }

  @Test
  void debugLevelIfLowRatio() {
    when(metrics.distributionSummary(any())).thenReturn(distributionSummary);
    assertThat(uut.calculateLogLevel(1000, 5)).isSameAs(RatioLogLevel.DEBUG);
    verify(distributionSummary).record(5);
  }

  @Test
  void infoLevelIfRatioSignificant() {
    when(metrics.distributionSummary(any())).thenReturn(distributionSummary);
    assertThat(uut.calculateLogLevel(1000, 10)).isSameAs(RatioLogLevel.INFO);
    verify(distributionSummary).record(10);
  }

  @Test
  void warnLevelIfRatioTooHigh() {
    when(metrics.distributionSummary(any())).thenReturn(distributionSummary);
    assertThat(uut.calculateLogLevel(1000, 20)).isSameAs(RatioLogLevel.WARN);
    verify(distributionSummary).record(20);
  }

  @Test
  void logsWarnLevel() {
    final var logger = Slf4jHelper.replaceLogger(uut);

    when(metrics.distributionSummary(any())).thenReturn(distributionSummary);
    when(sub.factsTransformed()).thenReturn(new AtomicLong(50L));
    when(sub.factsNotTransformed()).thenReturn(new AtomicLong(50L));

    uut.logCatchupTransformationStats();

    assertThat(logger.contains(LogLevel.WarnLevel, "CatchupTransformationRatio")).isTrue();
    verify(distributionSummary).record(50);
  }

  @Test
  void logsInfoLevel() {
    final var logger = Slf4jHelper.replaceLogger(uut);

    when(metrics.distributionSummary(any())).thenReturn(distributionSummary);
    when(sub.factsTransformed()).thenReturn(new AtomicLong(10L));
    when(sub.factsNotTransformed()).thenReturn(new AtomicLong(90L));

    uut.logCatchupTransformationStats();

    assertThat(logger.contains(LogLevel.InfoLevel, "CatchupTransformationRatio")).isTrue();
    verify(distributionSummary).record(10);
  }

  @Test
  void logsDebugLevel() {
    final var logger = Slf4jHelper.replaceLogger(uut);

    when(metrics.distributionSummary(any())).thenReturn(distributionSummary);
    when(sub.factsTransformed()).thenReturn(new AtomicLong(1L));
    when(sub.factsNotTransformed()).thenReturn(new AtomicLong(90L));

    uut.logCatchupTransformationStats();

    assertThat(logger.contains(LogLevel.DebugLevel, "CatchupTransformationRatio")).isTrue();
    verify(distributionSummary).record(1);
  }

  @Nested
  class FactRowCallbackHandlerTest {
    @Mock(lenient = true)
    private ResultSet rs;

    @Mock private SubscriptionImpl subscription;

    @Mock private PgPostQueryMatcher postQueryMatcher;

    @Mock private Supplier<Boolean> isConnectedSupplier;

    @Mock private AtomicLong serial;

    @Mock private SubscriptionRequestTO request;

    @InjectMocks private PgFactStream.FactRowCallbackHandler uut;

    @Test
    @SneakyThrows
    void test_notConnected() {
      when(isConnectedSupplier.get()).thenReturn(false);

      uut.processRow(rs);

      verifyNoInteractions(rs, postQueryMatcher, serial, request);
    }

    @Test
    @SneakyThrows
    void test_rsClosed() {
      when(isConnectedSupplier.get()).thenReturn(true);
      when(rs.isClosed()).thenReturn(true);

      assertThatThrownBy(() -> uut.processRow(rs)).isInstanceOf(IllegalStateException.class);

      verifyNoInteractions(postQueryMatcher, serial, request);
    }

    @Test
    @SneakyThrows
    void test_happyCase() {
      when(isConnectedSupplier.get()).thenReturn(true);

      when(rs.isClosed()).thenReturn(false);
      when(rs.getString(PgConstants.ALIAS_ID)).thenReturn("550e8400-e29b-11d4-a716-446655440000");
      when(rs.getString(PgConstants.ALIAS_NS)).thenReturn("foo");
      when(rs.getString(PgConstants.COLUMN_HEADER)).thenReturn("{}");
      when(rs.getString(PgConstants.COLUMN_PAYLOAD)).thenReturn("{}");
      when(rs.getLong(PgConstants.COLUMN_SER)).thenReturn(10L);

      when(postQueryMatcher.test(any())).thenReturn(true);

      uut.processRow(rs);

      verify(postQueryMatcher).test(any());
      verify(subscription).notifyElement(any());
      verify(serial).set(10L);
    }

    @Test
    @SneakyThrows
    void test_exception() {
      when(isConnectedSupplier.get()).thenReturn(true);

      when(rs.isClosed()).thenReturn(false);
      when(rs.getString(PgConstants.ALIAS_ID)).thenReturn("550e8400-e29b-11d4-a716-446655440000");
      when(rs.getString(PgConstants.ALIAS_NS)).thenReturn("foo");
      when(rs.getString(PgConstants.COLUMN_HEADER)).thenReturn("{}");
      when(rs.getString(PgConstants.COLUMN_PAYLOAD)).thenReturn("{}");
      when(rs.getLong(PgConstants.COLUMN_SER)).thenReturn(10L);

      final var exception = new IllegalArgumentException();
      doThrow(exception).when(subscription).notifyElement(any());

      when(postQueryMatcher.test(any())).thenReturn(true);

      uut.processRow(rs);

      verify(postQueryMatcher).test(any());
      verify(subscription).notifyError(exception);
      verify(rs).close();
      verify(serial).set(10L);
    }
  }
}
