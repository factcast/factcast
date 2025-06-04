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

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.internal.listen.*;
import org.factcast.store.internal.pipeline.ServerPipeline;
import org.factcast.store.internal.pipeline.Signal;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@ExtendWith(MockitoExtension.class)
class PgSynchronizedQueryTest {

  PgSynchronizedQuery uut;

  final String sql = "SELECT 42";

  @Mock PreparedStatementSetter setter;

  @Mock RowCallbackHandler rowHandler;

  @Mock AtomicLong serialToContinueFrom;

  @Mock PgLatestSerialFetcher fetcher;
  @Mock CurrentStatementHolder statementHolder;
  @Mock ServerPipeline pipeline;
  @Mock PgConnectionSupplier connectionSupplier;

  @SneakyThrows
  @Test
  void testRunWithIndex() {
    SingleConnectionDataSource ds = Mockito.mock(SingleConnectionDataSource.class);
    Connection con = Mockito.mock(Connection.class);
    PreparedStatement p = mock(PreparedStatement.class);

    ArgumentCaptor<List<ConnectionFilter>> cap = ArgumentCaptor.forClass(List.class);
    when(connectionSupplier.getPooledAsSingleDataSource(cap.capture())).thenReturn(ds);
    when(ds.getConnection()).thenReturn(con);
    when(con.prepareStatement(anyString())).thenReturn(p);
    ResultSet rs = Mockito.mock(ResultSet.class);
    Mockito.when(rs.next()).thenReturn(false);
    when(p.executeQuery()).thenReturn(rs);

    uut =
        new PgSynchronizedQuery(
            pipeline,
            connectionSupplier,
            sql,
            setter,
            () -> true,
            serialToContinueFrom,
            fetcher,
            statementHolder);

    uut.run(true);

    assertThat(cap.getValue()).doesNotContain(ConnectionFilter.withBitmapScanDisabled());
  }

  @SneakyThrows
  @Test
  void testRunWithoutIndex() {
    SingleConnectionDataSource ds = Mockito.mock(SingleConnectionDataSource.class);
    Connection con = Mockito.mock(Connection.class);
    PreparedStatement p = mock(PreparedStatement.class);

    ArgumentCaptor<List<ConnectionFilter>> cap = ArgumentCaptor.forClass(List.class);
    when(connectionSupplier.getPooledAsSingleDataSource(cap.capture())).thenReturn(ds);
    when(ds.getConnection()).thenReturn(con);
    when(con.prepareStatement(anyString())).thenReturn(p);
    ResultSet rs = Mockito.mock(ResultSet.class);
    Mockito.when(rs.next()).thenReturn(false);
    when(p.executeQuery()).thenReturn(rs);
    uut =
        new PgSynchronizedQuery(
            pipeline,
            connectionSupplier,
            sql,
            setter,
            () -> true,
            serialToContinueFrom,
            fetcher,
            statementHolder);
    uut.run(false);
    assertThat(cap.getValue()).contains(ConnectionFilter.withBitmapScanDisabled());
  }

  @Test
  @SneakyThrows
  void test_exception_during_query() {
    uut =
        new PgSynchronizedQuery(
            pipeline,
            connectionSupplier,
            sql,
            setter,
            () -> true,
            serialToContinueFrom,
            fetcher,
            statementHolder);
    SingleConnectionDataSource ds = Mockito.mock(SingleConnectionDataSource.class);
    Connection con = Mockito.mock(Connection.class);
    PreparedStatement p = mock(PreparedStatement.class);
    DataAccessResourceFailureException exc = new DataAccessResourceFailureException("oh my");

    when(statementHolder.wasCanceled()).thenReturn(false);
    when(connectionSupplier.getPooledAsSingleDataSource(any(List.class))).thenReturn(ds);
    when(ds.getConnection()).thenReturn(con);
    when(con.prepareStatement(anyString())).thenReturn(p);
    when(p.executeQuery()).thenThrow(exc);

    assertThatThrownBy(
            () -> {
              uut.run(false);
            })
        // should be thrown unchanged
        .isSameAs(exc);
  }

  @Test
  @SneakyThrows
  void test_exception_during_query_after_cancel() {
    LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
    lc.getLogger(PgSynchronizedQuery.class).setLevel(Level.TRACE);
    LogCaptor logCaptor = LogCaptor.forClass(PgSynchronizedQuery.class);

    uut =
        new PgSynchronizedQuery(
            pipeline,
            connectionSupplier,
            sql,
            setter,
            () -> true,
            serialToContinueFrom,
            fetcher,
            statementHolder);

    SingleConnectionDataSource ds = Mockito.mock(SingleConnectionDataSource.class);
    Connection con = Mockito.mock(Connection.class);
    PreparedStatement p = mock(PreparedStatement.class);
    DataAccessResourceFailureException exc = new DataAccessResourceFailureException("oh my");

    when(statementHolder.wasCanceled()).thenReturn(true);
    when(connectionSupplier.getPooledAsSingleDataSource(any(List.class))).thenReturn(ds);
    when(ds.getConnection()).thenReturn(con);
    when(con.prepareStatement(anyString())).thenReturn(p);
    when(p.executeQuery()).thenThrow(exc);

    uut.run(false);

    // make sure suppressed exception was trace-logged
    assertThat(logCaptor.getLogs()).hasSize(1);
    assertThat(logCaptor.getLogEvents().stream())
        .anyMatch(l -> Objects.equals(l.getLevel(), Level.TRACE.toString()))
        .isNotEmpty();
  }

  @Nested
  class FactRowCallbackHandlerTest {
    @Mock(lenient = true)
    private ResultSet rs;

    @Mock SubscriptionImpl subscription;

    @Mock Supplier<Boolean> isConnectedSupplier;

    @Mock AtomicLong serial;

    @Mock SubscriptionRequestTO request;
    @Mock ServerPipeline pipe;

    @Mock CurrentStatementHolder statementHolder;
    @InjectMocks private PgSynchronizedQuery.FactRowCallbackHandler uut;

    @Test
    @SneakyThrows
    void test_notConnected() {
      when(isConnectedSupplier.get()).thenReturn(false);

      uut.processRow(rs);

      verifyNoInteractions(rs, serial, request);
    }

    @Test
    @SneakyThrows
    void test_rsClosed() {
      when(isConnectedSupplier.get()).thenReturn(true);
      when(rs.isClosed()).thenReturn(true);

      assertThatThrownBy(() -> uut.processRow(rs)).isInstanceOf(IllegalStateException.class);

      verifyNoInteractions(serial, request);
    }

    @Test
    @SneakyThrows
    void swallowsExceptionAfterCancel() {
      when(isConnectedSupplier.get()).thenReturn(true);
      when(statementHolder.wasCanceled()).thenReturn(true);

      // it should appear open,
      when(rs.isClosed()).thenReturn(false);
      // until
      PSQLException mockException = new PSQLException(new ServerErrorMessage("broken"));
      when(rs.getString(anyString())).thenThrow(mockException);
      uut.processRow(rs);
      verifyNoMoreInteractions(subscription);
    }

    @Test
    @SneakyThrows
    void returnsIfCancelled() {
      when(isConnectedSupplier.get()).thenReturn(true);
      when(statementHolder.wasCanceled()).thenReturn(true);
      when(rs.isClosed()).thenReturn(true);
      uut.processRow(rs);
      verifyNoMoreInteractions(subscription);
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
      verify(pipe).process(Signal.of(mockException));
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
      verify(pipe).process(any(Signal.ErrorSignal.class));
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

      verify(pipe, times(1)).process(any(Signal.FactSignal.class));
      verify(serial).set(10L);
    }

    @Test
    @SneakyThrows
    void test_exception_during_iteration() {
      when(isConnectedSupplier.get()).thenReturn(true);

      when(rs.isClosed()).thenReturn(false);

      when(rs.getString(PgConstants.ALIAS_ID)).thenReturn("550e8400-e29b-11d4-a716-446655440000");
      when(rs.getString(PgConstants.ALIAS_NS)).thenReturn("foo");
      when(rs.getString(PgConstants.COLUMN_HEADER)).thenReturn("{}");
      when(rs.getString(PgConstants.COLUMN_PAYLOAD)).thenReturn("{}");
      when(rs.getLong(PgConstants.COLUMN_SER)).thenReturn(10L);

      var exception = new IllegalArgumentException();
      doThrow(exception).when(pipe).process(any(Signal.FactSignal.class));

      uut.processRow(rs);

      verify(pipe).process(any(Signal.FactSignal.class));
      verify(pipe).process(Signal.of(exception));
      verify(rs).close();
      verify(serial, never()).set(10L);
    }
  }
}
