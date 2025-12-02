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
import static org.mockito.Mockito.*;

import com.google.common.eventbus.EventBus;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.TestFactStreamPosition;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.*;
import org.factcast.store.internal.catchup.PgCatchup;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.pipeline.ServerPipeline;
import org.factcast.store.internal.pipeline.Signal;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.telemetry.PgStoreTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@ExtendWith(MockitoExtension.class)
class PgFactStreamTest {

  @Mock PgConnectionSupplier connectionSupplier;
  @Mock EventBus eventBus;
  @Mock PgFactIdToSerialMapper id2ser;
  @Mock PgCatchupFactory pgCatchupFactory;
  @Mock HighWaterMarkFetcher hwmFetcher;
  @Mock ServerPipeline pipeline;
  @Mock PgStoreTelemetry telemetry;
  @Mock SubscriptionRequestTO reqTo;

  @InjectMocks @Spy PgFactStream uut;

  @Nested
  class WhenConnecting {
    @Mock SingleConnectionDataSource ds;
    @Mock PgSynchronizedQuery pgSynchronizedQuery;
    final HighWaterMark hwm = HighWaterMark.empty();

    @BeforeEach
    void setup() {
      doReturn(ds).when(uut).createSingleDataSource(reqTo);
      doReturn(pgSynchronizedQuery).when(uut).createPgSynchronizedQuery();
      doNothing().when(uut).catchupAndFastForward(any(), any(), any());
      doNothing().when(uut).follow(any(), any());
      when(hwmFetcher.highWaterMark(ds)).thenReturn(hwm);
    }

    @Test
    void catchesUpAndFollows() {
      uut.connect();

      verify(telemetry).onConnect(reqTo);
      verify(uut).initializeSerialToStartAfter();
      verify(uut).catchupAndFastForward(reqTo, hwm, ds);
      verify(uut).follow(reqTo, pgSynchronizedQuery);
    }

    @Test
    void sendsStreamInfoSignal() {
      when(reqTo.streamInfo()).thenReturn(true);

      uut.connect();

      verify(pipeline, times(1)).process(any(Signal.FactStreamInfoSignal.class));
    }
  }

  @Nested
  class WhenCatchingUpAndFastForwarding {
    @Mock DataSource ds;
    final HighWaterMark hwm = HighWaterMark.of(UUID.randomUUID(), 66L);

    @BeforeEach
    void setup() {
      lenient().doNothing().when(uut).catchup(anyLong(), any());
      doNothing().when(uut).fastForward(any());
    }

    @Test
    void nonEphemeralRequest() {
      when(reqTo.ephemeral()).thenReturn(false);

      uut.catchupAndFastForward(reqTo, hwm, ds);

      assertThat(uut.serial().get()).isEqualTo(0L);
      verify(uut).catchup(hwm.targetSer(), ds);
    }

    @Test
    void onlyFastForwardsOnEphemeralRequest() {
      when(reqTo.ephemeral()).thenReturn(true);

      uut.catchupAndFastForward(reqTo, hwm, ds);

      assertThat(uut.serial().get()).isEqualTo(hwm.targetSer());
      verify(uut, never()).catchup(anyLong(), any());
    }

    @Test
    void signalsCatchup() {
      doReturn(true).when(uut).isConnected();

      uut.catchupAndFastForward(reqTo, hwm, ds);

      verify(telemetry).onCatchup(reqTo);
      verify(pipeline, times(1)).process(any(Signal.CatchupSignal.class));
    }
  }

  @Nested
  class WhenFollowing {
    @Mock PgSynchronizedQuery query;
    @Mock CondensedQueryExecutor condensedExecutor;

    @Test
    void doesNothingIfNotConnected() {
      doReturn(false).when(uut).isConnected();

      uut.follow(reqTo, query);

      verifyNoInteractions(telemetry);
      verifyNoInteractions(eventBus);
      verifyNoInteractions(pipeline);
    }

    @Test
    void registersCondensedExecutorIfRequestIsContinuous() {
      var maxBatchDelay = 0L;
      doReturn(true).when(uut).isConnected();
      doReturn(condensedExecutor).when(uut).createCondensedExecutor(reqTo, query, maxBatchDelay);
      when(reqTo.continuous()).thenReturn(true);
      when(reqTo.maxBatchDelayInMs()).thenReturn(maxBatchDelay);

      uut.follow(reqTo, query);

      verify(telemetry, times(1)).onFollow(reqTo);
      verify(eventBus, times(1)).register(condensedExecutor);
      verify(condensedExecutor, times(1)).trigger();
      verifyNoInteractions(pipeline);
    }

    @Test
    void signalsCompleteIfRequestIsNotContinuous() {
      doReturn(true).when(uut).isConnected();
      when(reqTo.continuous()).thenReturn(false);

      uut.follow(reqTo, query);

      verify(pipeline, times(1)).process(any(Signal.CompleteSignal.class));
      verify(telemetry, times(1)).onComplete(reqTo);
      verifyNoInteractions(eventBus);
    }
  }

  @SuppressWarnings({"unused"})
  @Nested
  class FastForward {

    @Test
    void noFfwdNotConnected() {
      uut.close();
      uut.fastForward(HighWaterMark.of(UUID.randomUUID(), 1000));

      verifyNoInteractions(pipeline);
    }

    @Test
    void noFfwdIfNoTarget() {
      UUID uuid = UUID.randomUUID();

      uut.fastForward(HighWaterMark.empty());

      verifyNoInteractions(pipeline);
    }

    @Test
    void ffwdIfTargetAhead() {
      UUID uuid = UUID.randomUUID();
      FactStreamPosition target = TestFactStreamPosition.random();

      UUID targetId = UUID.randomUUID();
      long targetSer = 1000;
      uut.fastForward(HighWaterMark.of(targetId, targetSer));

      verify(pipeline).process(Signal.of(FactStreamPosition.of(targetId, targetSer)));
    }

    @Test
    void noFfwdIfTargetBehind() {
      uut.serial().set(10);

      uut.fastForward(HighWaterMark.of(UUID.randomUUID(), 9));

      verifyNoInteractions(pipeline);
    }

    @Test
    void noFfwdIfTargetBehindConsumed() {
      UUID uuid = UUID.randomUUID();
      // when(reqTo.startingAfter()).thenReturn(Optional.empty());
      uut.serial().set(6);

      uut.fastForward(HighWaterMark.of(UUID.randomUUID(), 5));

      verifyNoInteractions(pipeline);
    }
  }

  @Nested
  class FactRowCallbackHandlerTest {
    @Mock(strictness = Mock.Strictness.LENIENT)
    private ResultSet rs;

    @Mock Supplier<Boolean> isConnectedSupplier;

    @Mock AtomicLong serial;

    @Mock SubscriptionRequestTO request;
    @Mock ServerPipeline factPipeline;
    @Mock CurrentStatementHolder statementHolder;

    @InjectMocks private PgSynchronizedQuery.FactRowCallbackHandler uut;

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
      verify(factPipeline).process(Signal.of(mockException));
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
      verify(factPipeline).process(any(Signal.ErrorSignal.class));
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

      verify(factPipeline, times(1)).process(any(Signal.FactSignal.class));
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
      doThrow(exception).when(factPipeline).process(any(Signal.FactSignal.class));

      uut.processRow(rs);

      verify(factPipeline, times(1)).process(any(Signal.FactSignal.class));
      verify(factPipeline).process(Signal.of(exception));
      verify(rs).close();
      verify(serial, never()).set(10L);
    }
  }

  @Nested
  class WhenCatchingUp {
    @Mock DataSource ds;

    @Test
    void ifDisconnected_doNothing() {
      when(uut.isConnected()).thenReturn(false);

      uut.catchup(0, ds);

      verifyNoInteractions(pgCatchupFactory);
    }

    @Test
    void ifConnected_catchupTwice() {
      PgCatchup catchup1 = mock(PgCatchup.class);
      PgCatchup catchup2 = mock(PgCatchup.class);
      when(uut.isConnected()).thenReturn(true);
      when(pgCatchupFactory.create(any(), any(), any(), any(), any(), any()))
          .thenReturn(catchup1, catchup2);

      uut.catchup(0, ds);

      verify(catchup1, times(1)).run();
      verify(catchup2, times(1)).run();
    }
  }

  @Nested
  class WhenInitializingSerialToStartAfter {

    @Test
    void fromScratch() {
      when(reqTo.startingAfter()).thenReturn(Optional.empty());
      uut.initializeSerialToStartAfter();
      assertThat(uut.serial()).hasValue(0);
    }

    @Test
    void fromId() {
      UUID id = UUID.randomUUID();
      when(reqTo.startingAfter()).thenReturn(Optional.of(id));
      when(id2ser.retrieve(id)).thenReturn(123L);
      uut.initializeSerialToStartAfter();
      assertThat(uut.serial()).hasValue(123L);
    }

    @Test
    void fromUnknownId() {
      UUID id = UUID.randomUUID();
      when(reqTo.startingAfter()).thenReturn(Optional.of(id));
      when(id2ser.retrieve(id)).thenReturn(0L);
      uut.initializeSerialToStartAfter();
      assertThat(uut.serial()).hasValue(0);
    }
  }
}
