/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal.catchup.chunkedwithhold;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.*;
import java.time.Duration;
import java.util.concurrent.atomic.*;
import lombok.*;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.*;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.pipeline.*;
import org.factcast.store.internal.query.*;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.*;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PgChunkedWithHoldCursorCatchupTest {
  @Mock StoreConfigurationProperties props;
  @Mock PgMetrics metrics;
  @Mock SubscriptionRequestTO req;
  @Mock ServerPipeline pipeline;
  @Mock CurrentStatementHolder statementHolder;
  @Mock SingleConnectionDataSource ds;
  @Mock PgCatchupFactory.Phase phase;
  @Mock AtomicLong serial;
  @Mock PgChunkedWithHoldCursorCatchup.Cursor cursor;
  @Mock Connection connection;

  PgChunkedWithHoldCursorCatchup underTest;

  @BeforeEach
  void setup() {
    underTest =
        Mockito.spy(
            new PgChunkedWithHoldCursorCatchup(
                props, metrics, req, pipeline, serial, statementHolder, ds, phase));
    ReflectionTestUtils.setField(underTest, "connection", connection);
  }

  @Nested
  class Run {
    @Test
    @SneakyThrows
    void testRun() {
      when(props.getChunkSize()).thenReturn(1000);
      doReturn(cursor).when(underTest).createCursor(anyInt());
      doReturn(true).when(underTest).fetchAll(any());

      underTest.run();

      verify(pipeline).process(any(Signal.class));
      verify(cursor).close();
    }
  }

  @Nested
  class LogIfAboveThreshold {
    @Test
    void testLogIfAboveThreshold() {
      Logger mockLogger = mock(Logger.class);
      underTest.logIfAboveThreshold(mockLogger, Duration.ofMillis(500));
      verifyNoInteractions(mockLogger);

      underTest.logIfAboveThreshold(mockLogger, Duration.ofSeconds(2));
      verify(mockLogger, atLeastOnce()).info(anyString(), any(Object.class), any(Object.class));
    }
  }

  @Nested
  class FetchAll {
    @Test
    @SneakyThrows
    void testFetchAllCanceled() {
      when(statementHolder.wasCanceled()).thenReturn(true);
      boolean result = underTest.fetchAll(cursor);
      assertThat(result).isFalse();
    }

    @Test
    @SneakyThrows
    void testFetchAll_Null() {
      doReturn(null)
          .when(underTest)
          .inTransaction(any(PgChunkedWithHoldCursorCatchup.ThrowingCallable.class));

      boolean result = underTest.fetchAll(cursor);

      assertThat(result).isFalse();
    }

    @Test
    @SneakyThrows
    void testFetchAll_MoreToFetch() {
      doReturn(true)
          .when(underTest)
          .inTransaction(any(PgChunkedWithHoldCursorCatchup.ThrowingCallable.class));
      doNothing().when(underTest).continueFetchingUntilExhausted(any(), any());

      boolean result = underTest.fetchAll(cursor);

      assertThat(result).isTrue();
      verify(underTest).inTransaction(any(PgChunkedWithHoldCursorCatchup.ThrowingCallable.class));
    }

    @Test
    @SneakyThrows
    void testContinueFetchingUntilExhausted_LoopExit() {
      when(statementHolder.wasCanceled()).thenReturn(false);
      when(cursor.chunkSize()).thenReturn(1000);
      when(cursor.fetchChunk(any())).thenReturn(0); // < 1000, should return

      PgFactExtractor extractor = mock(PgFactExtractor.class);
      underTest.inTransaction(() -> underTest.continueFetchingUntilExhausted(cursor, extractor));

      verify(cursor, times(1)).fetchChunk(any());
    }

    @Test
    @SneakyThrows
    void testContinueFetchingUntilExhausted_Loop() {
      when(statementHolder.wasCanceled()).thenReturn(false);
      when(cursor.chunkSize()).thenReturn(1000);
      when(cursor.fetchChunk(any())).thenReturn(1000, 1000, 750); // < 1000, should return

      PgFactExtractor extractor = mock(PgFactExtractor.class);
      underTest.continueFetchingUntilExhausted(cursor, extractor);

      verify(cursor, times(3)).fetchChunk(any());
      verify(connection, times(3)).commit();
    }

    @Test
    @SneakyThrows
    void testContinueFetchingUntilExhausted_Canceled() {
      when(statementHolder.wasCanceled()).thenReturn(false, true);
      when(cursor.chunkSize()).thenReturn(1000);
      when(cursor.fetchChunk(any())).thenReturn(1000);

      PgFactExtractor extractor = mock(PgFactExtractor.class);
      underTest.inTransaction(() -> underTest.continueFetchingUntilExhausted(cursor, extractor));

      verify(cursor, times(1)).fetchChunk(any());
    }
  }

  @Nested
  class CursorTest {
    @Mock PreparedStatement ps;
    @Mock ResultSet rs;
    @Mock Statement statement;
    @Mock PgQueryBuilder queryBuilder;
    @Mock PgFactExtractor extractor;
    @Mock PreparedStatementSetter pss;

    @BeforeEach
    @SneakyThrows
    void setupCursor() {
      lenient().when(ds.getConnection()).thenReturn(connection);
    }

    @Test
    @SneakyThrows
    void testConstructor() {
      PgChunkedWithHoldCursorCatchup.Cursor cursor = underTest.new Cursor(1000);
      assertThat(cursor.chunkSize()).isEqualTo(1000);
      assertThat(cursor.name()).startsWith("catchup_");
    }

    @Test
    @SneakyThrows
    void testClose() {
      when(connection.prepareStatement(anyString())).thenReturn(ps);
      PgChunkedWithHoldCursorCatchup.Cursor cursor = underTest.new Cursor(1000);
      cursor.close();
      verify(ps).execute();
    }

    @Test
    @SneakyThrows
    void testDeclare() {
      when(connection.prepareStatement(anyString())).thenReturn(ps);
      when(queryBuilder.createStatementSetter(any())).thenReturn(pss);

      PgChunkedWithHoldCursorCatchup.Cursor cursor = underTest.new Cursor(1000);
      cursor.declare(queryBuilder, new AtomicLong(0));

      verify(ps).execute();
      verify(pss).setValues(ps);
    }

    @Test
    @SneakyThrows
    void testFetchChunk() {
      when(connection.createStatement()).thenReturn(statement);
      when(statement.executeQuery(anyString())).thenReturn(rs);
      when(rs.next()).thenReturn(false);

      PgChunkedWithHoldCursorCatchup.Cursor cursor = underTest.new Cursor(1000);
      int rows = cursor.fetchChunk(extractor);

      assertThat(rows).isEqualTo(0);
      verify(statement).setFetchSize(anyInt());
      verify(statementHolder).statement(statement);
    }

    @Test
    @SneakyThrows
    void testFetchChunk_MultipleRows() {
      when(connection.createStatement()).thenReturn(statement);
      when(statement.executeQuery(anyString())).thenReturn(rs);
      when(rs.next()).thenReturn(true, true, false); // 2 rows
      when(extractor.mapRow(any(), anyInt())).thenReturn(mock(PgFact.class));

      PgChunkedWithHoldCursorCatchup.Cursor cursor = underTest.new Cursor(1000);
      int rows = cursor.fetchChunk(extractor);

      assertThat(rows).isEqualTo(2);
      verify(pipeline, times(2)).process(any());
    }

    @Test
    @SneakyThrows
    void testFetchChunk_Canceled() {
      when(connection.createStatement()).thenReturn(statement);
      when(statement.executeQuery(anyString())).thenReturn(rs);
      when(rs.next()).thenReturn(true, true, false);
      when(statementHolder.wasCanceled()).thenReturn(false, true);
      when(extractor.mapRow(any(), anyInt())).thenReturn(mock(PgFact.class));

      PgChunkedWithHoldCursorCatchup.Cursor cursor = underTest.new Cursor(1000);
      int rows = cursor.fetchChunk(extractor);

      assertThat(rows).isEqualTo(1);
      verify(pipeline, times(1)).process(any());
    }

    @Test
    @SneakyThrows
    void testFetchChunk_Callback() {
      when(connection.createStatement()).thenReturn(statement);
      when(statement.executeQuery(anyString())).thenReturn(rs);
      when(rs.next()).thenReturn(false);

      PgChunkedWithHoldCursorCatchup.Cursor cursor = underTest.new Cursor(1000);
      Runnable callback = mock(Runnable.class);
      int rows = cursor.fetchChunk(extractor, callback);

      assertThat(rows).isEqualTo(0);
      verify(callback).run();
    }
  }

  @Nested
  class TransactionTest {
    @Test
    @SneakyThrows
    void testDoInTransactionSuccess() {
      underTest.inTransaction(() -> "success");
      verify(connection).setAutoCommit(false);
      verify(connection).commit();
      verify(connection, never()).rollback();
    }

    @Test
    @SneakyThrows
    void testDoInTransactionFailure() {
      assertThrows(
          SQLException.class,
          () ->
              underTest.inTransaction(
                  () -> {
                    throw new SQLException("failure");
                  }));
      verify(connection).setAutoCommit(false);
      verify(connection).rollback();
      verify(connection, never()).commit();
    }
  }

  @Nested
  class DeclareAndFetchFirstTest {
    @Test
    @SneakyThrows
    void testDeclareAndFetchFirst_Empty() {
      when(props.getChunkSize()).thenReturn(1000);
      when(cursor.fetchChunk(any(), any())).thenReturn(0);

      Boolean result =
          underTest.declareAndFetchFirst(
              cursor, mock(PgQueryBuilder.class), new AtomicLong(0), mock(PgFactExtractor.class));
      assertThat(result).isNull();
    }

    @Test
    @SneakyThrows
    void testDeclareAndFetchFirst_QuickCatchup() {
      when(props.getChunkSize()).thenReturn(1000);
      when(cursor.fetchChunk(any(), any())).thenReturn(500);

      Boolean result =
          underTest.declareAndFetchFirst(
              cursor, mock(PgQueryBuilder.class), new AtomicLong(0), mock(PgFactExtractor.class));
      assertThat(result).isFalse();
    }

    @Test
    @SneakyThrows
    void testDeclareAndFetchFirst_MoreToFetch() {
      when(props.getChunkSize()).thenReturn(1000);
      when(cursor.fetchChunk(any(), any())).thenReturn(1000);

      Boolean result =
          underTest.declareAndFetchFirst(
              cursor, mock(PgQueryBuilder.class), new AtomicLong(0), mock(PgFactExtractor.class));
      assertThat(result).isTrue();
    }
  }
}
