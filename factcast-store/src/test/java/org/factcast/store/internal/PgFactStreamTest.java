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
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import org.factcast.core.*;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.*;
import org.factcast.store.OffloadDataSource;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.catchup.*;
import org.factcast.store.internal.filter.FromScratchCatchupLogSuppressingTurboFilter;
import org.factcast.store.internal.listen.*;
import org.factcast.store.internal.pipeline.*;
import org.factcast.store.internal.query.*;
import org.factcast.store.internal.telemetry.PgStoreTelemetry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.util.*;
import org.slf4j.MDC;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unused"})
class PgFactStreamTest {

  @Mock PgConnectionSupplier connectionSupplier;
  @Mock EventBus eventBus;
  @Mock PgFactIdToSerialMapper id2ser;
  @Mock PgCatchupFactory pgCatchupFactory;
  @Mock HighWaterMarkFetcher hwmFetcher;
  @Mock ServerPipeline pipeline;
  @Mock PgStoreTelemetry telemetry;
  @Mock StoreConfigurationProperties props;
  @Mock SubscriptionRequestTO reqTo;
  @Mock ModifiedSingleConnectionDataSource mds;

  @InjectMocks @Spy PgFactStream uut;

  @Nested
  class WhenConnecting {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    Connection c;

    @Mock private Statement s;

    @Mock SingleConnectionDataSource ds;
    @Mock PgSynchronizedQuery pgSynchronizedQuery;
    final HighWaterMark hwm = HighWaterMark.empty();

    @BeforeEach
    void setup() {
      // doReturn(ds).when(uut).createSingleDataSource(reqTo);
      lenient().doReturn(pgSynchronizedQuery).when(uut).createPgSynchronizedQuery();
      // doNothing().when(uut).catchupAndFastForward(any(), any(), any());
      lenient().doNothing().when(uut).follow(any(), any());
      lenient().when(hwmFetcher.highWaterMark(any())).thenReturn(hwm);
      lenient().when(connectionSupplier.dataSource()).thenReturn(ds);
      lenient().when(reqTo.debugInfo()).thenReturn("foo");
      lenient().when(uut.catchupConnectionModifiers(reqTo)).thenReturn(Collections.emptyList());
      lenient().doReturn(mds).when(uut).createCatchupDataSource(ds);
    }

    @Test
    void catchesUpAndFollows() {
      doNothing().when(uut).doCatchup();

      uut.connect();

      verify(telemetry).onConnect(reqTo);
      verify(uut).initializeSerialToStartAfter();
      verify(uut).doCatchup();
      verify(uut).follow(reqTo, pgSynchronizedQuery);
    }

    @SneakyThrows
    @Test
    void sendsStreamInfoSignal() {

      when(pgCatchupFactory.create(any(), any(), any(), any(), any(), any()))
          .thenReturn(
              new PgCatchup() {
                @Override
                public void fastForward(long serialToStartFrom) {}

                public void run() {}
              });
      lenient().when(uut.catchupPhaseOne(ds)).thenReturn(12L);

      when(reqTo.streamInfo()).thenReturn(true);
      uut.doCatchup();
      verify(pipeline, times(1)).process(any(Signal.FactStreamInfoSignal.class));
    }
  }

  @Nested
  class WhenCreatingSingleDataSource {
    @Mock SingleConnectionDataSource ds;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    Connection c;

    @Mock private Statement s;

    @SneakyThrows
    @Test
    void setsModifiers() {
      when(reqTo.debugInfo()).thenReturn("foo");
      when(ds.getConnection()).thenReturn(c);
      when(uut.catchupConnectionModifiers(reqTo))
          .thenReturn(Collections.singletonList(ConnectionModifier.withCustomPlanForced()));

      ModifiedSingleConnectionDataSource catchupDataSource = uut.createCatchupDataSource(ds);
      assertThat(catchupDataSource.modifiers())
          .isNotNull()
          .containsExactly(ConnectionModifier.withCustomPlanForced());
    }
  }

  @Nested
  class WhenCatchingUpAndFastForwarding {
    @Mock SingleConnectionDataSource ds;
    final HighWaterMark hwm = HighWaterMark.of(UUID.randomUUID(), 66L);

    @BeforeEach
    void setup() {
      lenient().doNothing().when(uut).doCatchup();
      lenient().when(reqTo.debugInfo()).thenReturn("foo");
      lenient().when(hwmFetcher.highWaterMark(any())).thenReturn(hwm);
    }

    @Test
    void nonEphemeralRequestCatchesUp() {
      when(reqTo.ephemeral()).thenReturn(false);

      uut.connect();

      assertThat(uut.serial().get()).isZero();
      verify(uut).doCatchup();
    }

    @Test
    void onlyFastForwardsOnEphemeralRequest() {
      when(reqTo.ephemeral()).thenReturn(true);

      uut.connect();

      assertThat(uut.serial().get()).isEqualTo(hwm.targetSer());
      verify(uut, never()).doCatchup();
    }

    @Test
    void signalsCatchup() {
      doReturn(true).when(uut).isConnected();

      uut.connect();

      verify(telemetry).onCatchup(reqTo);
      verify(pipeline, times(1)).process(any(Signal.CatchupSignal.class));
    }
  }

  @Nested
  class WhenFollowing {
    @Mock PgSynchronizedQuery query;
    @Mock QueryExecutor queryExecutor;

    @Test
    void doesNothingIfNotConnected() {
      doReturn(false).when(uut).isConnected();

      uut.follow(reqTo, query);

      verifyNoInteractions(telemetry);
      verifyNoInteractions(eventBus);
      verifyNoInteractions(pipeline);
    }

    @Test
    void registersQueryExecutorIfRequestIsContinuous() {
      var maxBatchDelay = 0L;
      doReturn(true).when(uut).isConnected();
      doReturn(queryExecutor).when(uut).createQueryExecutor(reqTo, query);
      when(reqTo.continuous()).thenReturn(true);

      uut.follow(reqTo, query);

      verify(telemetry, times(1)).onFollow(reqTo);
      verify(eventBus, times(1)).register(queryExecutor);
      verify(queryExecutor, times(1)).trigger();
      verifyNoInteractions(pipeline);
    }

    @Test
    void computesDelayForConsumers() {
      var maxBatchDelay = 100L;
      doReturn(true).when(uut).isConnected();
      when(reqTo.continuous()).thenReturn(true);

      uut.follow(reqTo, query);

      verify(uut).createQueryExecutor(eq(reqTo), eq(query));
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

  @Nested
  class FastForward {

    @Test
    void noFfwdNotConnected() {
      uut.close();
      uut.fastForward(HighWaterMark.of(UUID.randomUUID(), 1000));

      verify(pipeline, never()).process(any(Signal.class));
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
          mock(
              PSQLException.class,
              withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
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

      org.assertj.core.api.Assertions.assertThatThrownBy(() -> uut.processRow(rs))
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
    @Mock SingleConnectionDataSource ds;
    @Mock DataSource p1Ds;
    @Mock Connection p1Connection;

    @BeforeEach
    void setup() {
      lenient().when(reqTo.debugInfo()).thenReturn("test-debug-info");
      lenient()
          .doReturn(HighWaterMark.of(UUID.randomUUID(), 24))
          .when(hwmFetcher)
          .highWaterMark(any());
      lenient().doReturn(ds).when(connectionSupplier).dataSource();
      lenient().doReturn(mds).when(uut).createCatchupDataSource(ds);
      lenient().when(uut.isConnected()).thenReturn(true);
    }

    @Test
    void ifDisconnected_doNothing() {
      when(uut.isConnected()).thenReturn(false);

      uut.doCatchup();

      verifyNoInteractions(pgCatchupFactory);
    }

    @Test
    void ifConnected_catchupTwice() {
      when(uut.isConnected()).thenReturn(true);
      doReturn(12L).when(uut).catchupPhaseOne(any());
      doNothing().when(uut).catchupPhaseTwo(any(), same(12L));
      doReturn(mds).when(uut).createCatchupDataSource(any());
      doReturn(HighWaterMark.of(UUID.randomUUID(), 24)).when(hwmFetcher).highWaterMark(any());
      uut.doCatchup();

      verify(uut).catchupPhaseOne(any());
      verify(uut).catchupPhaseTwo(any(), same(12L));
    }

    @Test
    void usesPrimaryDataSourceForBothPhasesByDefault() {
      PgCatchup catchup1 = mock(PgCatchup.class);
      PgCatchup catchup2 = mock(PgCatchup.class);
      when(uut.isConnected()).thenReturn(true);
      when(pgCatchupFactory.create(any(), any(), any(), any(), any(), any()))
          .thenReturn(catchup1, catchup2);
      uut.doCatchup();

      AtomicLong serial = uut.serial();
      verify(pgCatchupFactory)
          .create(
              same(reqTo),
              same(pipeline),
              same(serial),
              any(CurrentStatementHolder.class),
              same(mds),
              eq(PgCatchupFactory.Phase.PHASE_1));
      verify(pgCatchupFactory)
          .create(
              same(reqTo),
              same(pipeline),
              same(serial),
              any(CurrentStatementHolder.class),
              same(mds),
              eq(PgCatchupFactory.Phase.PHASE_2));

      // or equivalent:
      verify(uut).catchupPhaseOne(mds);
      verify(uut).catchupPhaseTwo(ArgumentMatchers.argThat(p -> p.get() == mds), same(24L));

      verify(catchup2).fastForward(24L);
    }

    @Test
    void phase2UsesPrimaryDataSourceAndStartsFromPhase1Highwatermark() {
      long phase1Hwm = 123L;

      PgCatchup catchup2 = mock(PgCatchup.class);
      when(pgCatchupFactory.create(
              any(), any(), any(), any(), any(), eq(PgCatchupFactory.Phase.PHASE_2)))
          .thenReturn(catchup2);

      doReturn(phase1Hwm).when(uut).catchupPhaseOne(any());

      uut.doCatchup();

      verify(uut)
          .catchupPhaseTwo(
              ArgumentMatchers.argThat(supplier -> supplier.get() == mds), eq(phase1Hwm));
    }

    @Test
    void phaseTwoForwardsToPhase1HwmThenRunsThenFfwdToInitialHwm() {
      long phase1Hwm = 100L;
      HighWaterMark initialHwm = HighWaterMark.of(UUID.randomUUID(), 200L);
      PgCatchup pgCatchup2 = mock(PgCatchup.class);

      when(uut.isConnected()).thenReturn(true);
      DataSource ds = mock(DataSource.class);
      try {
        when(ds.getConnection()).thenReturn(mock(Connection.class));
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
      when(connectionSupplier.dataSource()).thenReturn(ds);
      doReturn(Collections.emptyList()).when(uut).catchupConnectionModifiers(any());
      when(hwmFetcher.highWaterMark(any())).thenReturn(initialHwm);
      when(pgCatchupFactory.create(
              any(), any(), any(), any(), any(), eq(PgCatchupFactory.Phase.PHASE_2)))
          .thenReturn(pgCatchup2);
      doReturn(phase1Hwm).when(uut).catchupPhaseOne(any());

      uut.doCatchup();

      InOrder inOrder = inOrder(pgCatchup2, uut);
      inOrder.verify(pgCatchup2).fastForward(phase1Hwm);
      inOrder.verify(pgCatchup2).run();
      inOrder.verify(uut).fastForward(initialHwm);
    }

    @Nested
    class WhenCheckingOffload {
      @Mock OffloadDataSource offloadDataSource;
      @Mock PgConnectionSupplier connectionSupplier;
      @Mock EventBus eventBus;
      @Mock PgFactIdToSerialMapper idToSerMapper;
      @Mock PgCatchupFactory pgCatchupFactory;
      @Mock HighWaterMarkFetcher hwmFetcher;
      @Mock ServerPipeline pipeline;
      @Mock PgStoreTelemetry telemetry;
      @Mock StoreConfigurationProperties props;
      @Mock SubscriptionRequestTO reqTo;
      @Mock SingleConnectionDataSource ds;
      @Mock ModifiedSingleConnectionDataSource mds;

      @BeforeEach
      void setup() {
        lenient().when(connectionSupplier.dataSource()).thenReturn(ds);
      }

      @Test
      void phase1UsesPrimaryIfOffloadIsNull() {
        PgFactStream uut =
            spy(
                new PgFactStream(
                    connectionSupplier,
                    null,
                    eventBus,
                    idToSerMapper,
                    pgCatchupFactory,
                    hwmFetcher,
                    pipeline,
                    telemetry,
                    props,
                    reqTo));
        lenient().doReturn(true).when(uut).isConnected();
        lenient().doReturn(mds).when(uut).createCatchupDataSource(any(DataSource.class));
        lenient().when(hwmFetcher.highWaterMark(any())).thenReturn(HighWaterMark.empty());
        lenient().doReturn(123L).when(uut).catchupPhaseOne(any());
        lenient().doNothing().when(uut).catchupPhaseTwo(any(), anyLong());

        uut.doCatchup();

        verify(uut).catchupPhaseOne(mds);
      }

      @Test
      void phase1UsesOffloadIfProvided() {
        PgFactStream uut =
            spy(
                new PgFactStream(
                    connectionSupplier,
                    offloadDataSource,
                    eventBus,
                    idToSerMapper,
                    pgCatchupFactory,
                    hwmFetcher,
                    pipeline,
                    telemetry,
                    props,
                    reqTo));
        lenient().doReturn(true).when(uut).isConnected();
        lenient().doReturn(mds).when(uut).createCatchupDataSource(any(DataSource.class));
        lenient().when(hwmFetcher.highWaterMark(any())).thenReturn(HighWaterMark.empty());
        lenient().doReturn(123L).when(uut).catchupPhaseOne(any());
        lenient().doNothing().when(uut).catchupPhaseTwo(any(), anyLong());

        uut.doCatchup();

        verify(uut).catchupPhaseOne(mds);
        // Verify that createCatchupDataSource was called with offloadDataSource
        verify(uut).createCatchupDataSource(offloadDataSource);
      }
    }

    @Test
    void setsMdcDuringFromScratchCatchup() {
      doReturn(ds).when(connectionSupplier).dataSource();
      doReturn(mds).when(uut).createCatchupDataSource(ds);

      // serial is 0 by default → from scratch
      when(props.getFromScratchCatchupMinLogLevel()).thenReturn("DEBUG");
      PgCatchup catchup = mock(PgCatchup.class);
      when(pgCatchupFactory.create(any(), any(), any(), any(), any(), any())).thenReturn(catchup);

      doNothing().when(uut).catchupPhaseTwo(any(), anyLong());

      doAnswer(
              invocation -> {
                assertThat(
                        MDC.get(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH))
                    .isEqualTo("test-debug-info");
                return null;
              })
          .when(catchup)
          .run();

      uut.doCatchup();

      assertThat(MDC.get(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH))
          .isNull();
    }

    @Test
    void doesNotSetMdcWhenNotFromScratch() {
      uut.serial().set(42L);
      PgCatchup catchup = mock(PgCatchup.class);
      when(uut.isConnected()).thenReturn(true);
      when(pgCatchupFactory.create(any(), any(), any(), any(), any(), any()))
          .thenReturn(catchup, catchup);

      doAnswer(
              invocation -> {
                assertThat(
                        MDC.get(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH))
                    .isNull();
                return null;
              })
          .when(catchup)
          .run();

      uut.doCatchup();
    }

    @Test
    void doesNotSetMdcWhenPropertyIsUnset() {
      // serial is 0 → from scratch, but property is null → no MDC marking
      when(props.getFromScratchCatchupMinLogLevel()).thenReturn(null);
      PgCatchup catchup = mock(PgCatchup.class);
      when(uut.isConnected()).thenReturn(true);
      when(pgCatchupFactory.create(any(), any(), any(), any(), any(), any()))
          .thenReturn(catchup, catchup);

      doAnswer(
              invocation -> {
                assertThat(
                        MDC.get(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH))
                    .isNull();
                return null;
              })
          .when(catchup)
          .run();

      uut.doCatchup();
    }

    @Test
    void clearsMdcEvenOnException() {
      // serial is 0 by default → from scratch
      when(props.getFromScratchCatchupMinLogLevel()).thenReturn("DEBUG");
      PgCatchup catchup = mock(PgCatchup.class);
      when(uut.isConnected()).thenReturn(true);
      when(pgCatchupFactory.create(any(), any(), any(), any(), any(), any())).thenReturn(catchup);
      doThrow(new RuntimeException("boom")).when(catchup).run();

      try {
        uut.doCatchup();
      } catch (RuntimeException e) {
        // expected
      }

      assertThat(MDC.get(FromScratchCatchupLogSuppressingTurboFilter.MDC_KEY_FROM_SCRATCH))
          .isNull();
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
