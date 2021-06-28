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

import com.google.common.eventbus.EventBus;
import io.micrometer.core.instrument.DistributionSummary;
import lombok.val;
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

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    when(metrics.measurement(any())).thenReturn(distributionSummary);
    assertThat(uut.calculateLogLevel(1000, 5)).isSameAs(RatioLogLevel.DEBUG);
    verify(distributionSummary).record(5);
  }

  @Test
  void infoLevelIfRatioSignificant() {
    when(metrics.measurement(any())).thenReturn(distributionSummary);
    assertThat(uut.calculateLogLevel(1000, 10)).isSameAs(RatioLogLevel.INFO);
    verify(distributionSummary).record(10);
  }

  @Test
  void warnLevelIfRatioTooHigh() {
    when(metrics.measurement(any())).thenReturn(distributionSummary);
    assertThat(uut.calculateLogLevel(1000, 20)).isSameAs(RatioLogLevel.WARN);
    verify(distributionSummary).record(20);
  }

  @Test
  void logsWarnLevel() {
    val logger = Slf4jHelper.replaceLogger(uut);

    when(metrics.measurement(any())).thenReturn(distributionSummary);
    when(sub.factsTransformed()).thenReturn(new AtomicLong(50L));
    when(sub.factsNotTransformed()).thenReturn(new AtomicLong(50L));

    uut.logCatchupTransformationStats();

    assertThat(logger.contains(LogLevel.WarnLevel, "CatchupTransformationRatio")).isTrue();
    verify(distributionSummary).record(50);
  }

  @Test
  void logsInfoLevel() {
    val logger = Slf4jHelper.replaceLogger(uut);

    when(metrics.measurement(any())).thenReturn(distributionSummary);
    when(sub.factsTransformed()).thenReturn(new AtomicLong(10L));
    when(sub.factsNotTransformed()).thenReturn(new AtomicLong(90L));

    uut.logCatchupTransformationStats();

    assertThat(logger.contains(LogLevel.InfoLevel, "CatchupTransformationRatio")).isTrue();
    verify(distributionSummary).record(10);
  }

  @Test
  void logsDebugLevel() {
    val logger = Slf4jHelper.replaceLogger(uut);

    when(metrics.measurement(any())).thenReturn(distributionSummary);
    when(sub.factsTransformed()).thenReturn(new AtomicLong(1L));
    when(sub.factsNotTransformed()).thenReturn(new AtomicLong(90L));

    uut.logCatchupTransformationStats();

    assertThat(logger.contains(LogLevel.DebugLevel, "CatchupTransformationRatio")).isTrue();
    verify(distributionSummary).record(1);
  }
}
