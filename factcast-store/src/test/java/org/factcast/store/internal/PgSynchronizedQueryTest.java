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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import java.sql.ResultSet;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import org.assertj.core.api.Assertions;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.internal.pipeline.FactPipeline;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;
import slf4jtest.LogLevel;

@ExtendWith(MockitoExtension.class)
class PgSynchronizedQueryTest {

  PgSynchronizedQuery uut;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  JdbcTemplate jdbcTemplate;

  final String sql = "SELECT 42";

  @Mock PreparedStatementSetter setter;

  @Mock RowCallbackHandler rowHandler;

  @Mock AtomicLong serialToContinueFrom;

  @Mock PgLatestSerialFetcher fetcher;
  @Mock CurrentStatementHolder statementHolder;

  @Test
  void testRunWithIndex() {
    uut =
        new PgSynchronizedQuery(
            jdbcTemplate, sql, setter, rowHandler, serialToContinueFrom, fetcher, statementHolder);
    uut.run(true);
    verify(jdbcTemplate, never()).execute(startsWith("SET LOCAL enable_bitmapscan"));
  }

  @Test
  void testRunWithoutIndex() {
    uut =
        new PgSynchronizedQuery(
            jdbcTemplate, sql, setter, rowHandler, serialToContinueFrom, fetcher, statementHolder);
    uut.run(false);
    verify(jdbcTemplate).execute(startsWith("SET LOCAL enable_bitmapscan"));
  }

  @Test
  @SneakyThrows
  void test_exception_during_query() {
    uut =
        new PgSynchronizedQuery(
            jdbcTemplate, sql, setter, rowHandler, serialToContinueFrom, fetcher, statementHolder);
    when(statementHolder.wasCanceled()).thenReturn(false);
    DataAccessResourceFailureException exc = new DataAccessResourceFailureException("oh my");
    doThrow(exc)
        .when(jdbcTemplate)
        .query(anyString(), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));

    Assertions.assertThatThrownBy(
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
            jdbcTemplate, sql, setter, rowHandler, serialToContinueFrom, fetcher, statementHolder);
    when(statementHolder.wasCanceled()).thenReturn(true);
    doThrow(DataAccessResourceFailureException.class)
        .when(jdbcTemplate)
        .query(anyString(), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));

    uut.run(false);

    // make sure suppressed exceptzion was trace-logged
    assertThat(logCaptor.getLogs()).hasSize(1);
    assertThat(logCaptor.getLogEvents().stream())
        .anyMatch(l -> l.getLevel() == LogLevel.TraceLevel.toString())
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
    @Mock FactPipeline pipe;

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
      verify(pipe).error(mockException);
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
      verify(pipe).error(any(RuntimeException.class));
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

      verify(pipe, times(1)).fact(any());
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
      doThrow(exception).when(pipe).fact(any());

      uut.processRow(rs);

      verify(pipe).fact(any());
      verify(pipe).error(exception);
      verify(rs).close();
      verify(serial, never()).set(10L);
    }
  }
}
