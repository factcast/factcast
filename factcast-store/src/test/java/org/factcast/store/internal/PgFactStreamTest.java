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
package org.factcast.store.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.assertj.core.api.Assertions;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.TestFactStreamPosition;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.store.internal.catchup.PgCatchup;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.pipeline.FactPipeline;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.jdbc.core.JdbcTemplate;

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

  @Mock PgCatchupFactory pgCatchupFactory;
  @InjectMocks PgFactStream uut;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
  }

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
    @Mock FactPipeline pipeline;
    @InjectMocks PgFactStream underTest;

    @BeforeEach
    void setup() {
      MockitoAnnotations.openMocks(this);
    }

    @Test
    void noFfwdNotConnected() {

      underTest.close();
      underTest.fastForward(request);

      verifyNoInteractions(subscription);
    }

    @Test
    void noFfwdFromScratch() {
      when(request.startingAfter()).thenReturn(Optional.empty());

      underTest.fastForward(request);

      verifyNoInteractions(subscription);
    }

    @Test
    void noFfwdIfNoTarget() {
      UUID uuid = UUID.randomUUID();
      when(request.startingAfter()).thenReturn(Optional.of(uuid));
      when(idToSerMapper.retrieve(uuid)).thenReturn(10L);
      when(ffwdTarget.targetId()).thenReturn(null);

      underTest.fastForward(request);

      verifyNoInteractions(subscription);
    }

    @Test
    void ffwdIfFactsHaveBeenSent() {
      UUID uuid = UUID.randomUUID();
      when(request.startingAfter()).thenReturn(Optional.of(uuid));
      when(idToSerMapper.retrieve(uuid)).thenReturn(10L);
      FactStreamPosition target = TestFactStreamPosition.random();
      when(ffwdTarget.targetId()).thenReturn(target.factId());
      when(ffwdTarget.targetSer()).thenReturn(target.serial());

      underTest.fastForward(request);

      verify(pipeline).fastForward(target);
    }

    @Test
    void noFfwdIfTargetBehind() {
      UUID uuid = UUID.randomUUID();
      when(request.startingAfter()).thenReturn(Optional.of(uuid));
      when(idToSerMapper.retrieve(uuid)).thenReturn(10L);
      when(ffwdTarget.targetId()).thenReturn(UUID.randomUUID());
      when(ffwdTarget.targetSer()).thenReturn(9L);

      underTest.fastForward(request);

      verifyNoInteractions(subscription);
    }

    @Test
    void ffwdIfTargetAhead() {
      UUID uuid = UUID.randomUUID();
      when(request.startingAfter()).thenReturn(Optional.of(uuid));
      when(idToSerMapper.retrieve(uuid)).thenReturn(10L);
      FactStreamPosition target = TestFactStreamPosition.random();
      when(ffwdTarget.targetId()).thenReturn(target.factId());
      when(ffwdTarget.targetSer()).thenReturn(target.serial());

      underTest.fastForward(request);

      verify(pipeline).fastForward(target);
    }
  }

  @Nested
  class FactRowCallbackHandlerTest {
    @Mock(lenient = true)
    private ResultSet rs;

    @Mock Supplier<Boolean> isConnectedSupplier;

    @Mock AtomicLong serial;

    @Mock SubscriptionRequestTO request;
    @Mock FactPipeline factPipeline;
    @Mock CurrentStatementHolder statementHolder;

    @InjectMocks private PgSynchronizedQuery.FactRowCallbackHandler uut;

    @BeforeEach
    void setup() {
      MockitoAnnotations.openMocks(this);
    }

    @Test
    @SneakyThrows
    void test_notConnected() {
      when(isConnectedSupplier.get()).thenReturn(false);

      uut.processRow(rs);

      verifyNoInteractions(rs, factPipeline, serial, request);
    }

    @Test
    @SneakyThrows
    void swallowsExceptionAfterCancel() {
      when(isConnectedSupplier.get()).thenReturn(true);
      when(statementHolder.wasCanceled()).thenReturn(true);

      // it should appear open,
      when(rs.isClosed()).thenReturn(false);
      // until
      PSQLException mockException = new PSQLException(new ServerErrorMessage("och"));
      when(rs.getString(anyString())).thenThrow(mockException);
      uut.processRow(rs);
      verifyNoMoreInteractions(factPipeline);
    }

    @Test
    @SneakyThrows
    void returnsIfCancelled() {
      when(isConnectedSupplier.get()).thenReturn(true);
      when(statementHolder.wasCanceled()).thenReturn(true);
      when(rs.isClosed()).thenReturn(true);
      uut.processRow(rs);
      verifyNoMoreInteractions(factPipeline);
    }

    @Test
    @SneakyThrows
    void notifiesErrorWhenNotCanceled() {
      when(isConnectedSupplier.get()).thenReturn(true);

      // it should appear open,
      when(rs.isClosed()).thenReturn(false);
      // until
      PSQLException mockException =
          mock(PSQLException.class, withSettings().strictness(Strictness.LENIENT));
      when(rs.getString(anyString())).thenThrow(mockException);

      uut.processRow(rs);
      verify(factPipeline).error(mockException);
    }

    @Test
    @SneakyThrows
    void notifiesErrorWhenCanceledButUnexpectedException() {
      when(isConnectedSupplier.get()).thenReturn(true);
      // it should appear open,
      when(rs.isClosed()).thenReturn(false);
      // until
      when(rs.getString(anyString())).thenThrow(RuntimeException.class);

      uut.processRow(rs);
      verify(factPipeline).error(any(RuntimeException.class));
    }

    @Test
    @SneakyThrows
    void test_rsClosed() {
      when(isConnectedSupplier.get()).thenReturn(true);
      when(rs.isClosed()).thenReturn(true);

      Assertions.assertThatThrownBy(() -> uut.processRow(rs))
          .isInstanceOf(IllegalStateException.class);

      verifyNoInteractions(factPipeline, serial, request);
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

      uut.processRow(rs);

      verify(factPipeline, times(1)).fact(any());
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

      var exception = new IllegalArgumentException();
      doThrow(exception).when(factPipeline).fact(any());

      uut.processRow(rs);

      verify(factPipeline, times(1)).fact(any());
      verify(factPipeline).error(exception);
      verify(rs).close();
      verify(serial, never()).set(10L);
    }
  }

  @Nested
  class WhenCatchingUp {
    @BeforeEach
    void setup() {
      MockitoAnnotations.openMocks(this);
    }

    @Test
    void ifDisconnected_doNothing() {
      uut = spy(uut);
      when(uut.isConnected()).thenReturn(false);

      uut.catchup();

      verifyNoInteractions(pgCatchupFactory);
    }

    @Test
    void ifConnected_catchupTwice() {
      uut = spy(uut);
      PgCatchup catchup1 = mock(PgCatchup.class);
      PgCatchup catchup2 = mock(PgCatchup.class);
      when(uut.isConnected()).thenReturn(true);
      when(pgCatchupFactory.create(any(), any(), any(), any())).thenReturn(catchup1, catchup2);

      uut.catchup();

      verify(catchup1, times(1)).run();
      verify(catchup2, times(1)).run();
    }
  }

  @Nested
  class WhenInitializingSerialToStartAfter {
    @BeforeEach
    void setup() {
      MockitoAnnotations.openMocks(this);
    }

    @Test
    void fromScratch() {
      when(reqTo.startingAfter()).thenReturn(Optional.empty());
      uut.request = reqTo;
      uut.initializeSerialToStartAfter();
      assertThat(uut.serial()).hasValue(0);
    }

    @Test
    void fromId() {
      UUID id = UUID.randomUUID();
      when(reqTo.startingAfter()).thenReturn(Optional.of(id));
      when(id2ser.retrieve(id)).thenReturn(123L);
      uut.request = reqTo;
      uut.initializeSerialToStartAfter();
      assertThat(uut.serial()).hasValue(123L);
    }

    @Test
    void fromUnknownId() {
      UUID id = UUID.randomUUID();
      when(reqTo.startingAfter()).thenReturn(Optional.of(id));
      when(id2ser.retrieve(id)).thenReturn(0L);
      uut.request = reqTo;
      uut.initializeSerialToStartAfter();
      assertThat(uut.serial()).hasValue(0);
    }
  }
}
