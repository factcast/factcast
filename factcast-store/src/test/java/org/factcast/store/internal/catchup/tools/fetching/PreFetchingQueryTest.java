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
package org.factcast.store.internal.catchup.tools.fetching;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreFetchingQueryTest {

  @Mock private PreparedStatement ps;
  @Mock private Connection connection;
  @Mock private ResultSet resultSet;
  @Mock private ResultSetMetaData resultSetMetaData;
  @Mock private FetchingQuery.RowProcessor rowProcessor;

  private PreFetchingQuery underTest;

  @BeforeEach
  void setup() throws SQLException {
    underTest = new PreFetchingQuery();
  }

  @Test
  void testFailsIfAutoCommitEnabled() throws SQLException {
    when(ps.getConnection()).thenReturn(connection);
    when(connection.getAutoCommit()).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> underTest.executeAndProcess(ps, rowProcessor));
  }

  @Test
  void testFailsIfFetchSizeZero() throws SQLException {
    when(ps.getConnection()).thenReturn(connection);
    when(connection.getAutoCommit()).thenReturn(false);
    when(ps.getFetchSize()).thenReturn(0);

    assertThrows(
        IllegalArgumentException.class, () -> underTest.executeAndProcess(ps, rowProcessor));
  }

  @Test
  void testSuccessfulProcessing() throws SQLException {
    when(ps.getConnection()).thenReturn(connection);
    when(connection.getAutoCommit()).thenReturn(false);
    when(ps.getFetchSize()).thenReturn(10);
    when(ps.executeQuery()).thenReturn(resultSet);

    // Setup ResultSet for producer loop:
    when(resultSet.getFetchSize()).thenReturn(10);
    when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
    when(resultSetMetaData.getColumnCount()).thenReturn(1);
    when(resultSetMetaData.getColumnName(1)).thenReturn("id");
    when(resultSetMetaData.getColumnType(1)).thenReturn(java.sql.Types.VARCHAR);
    when(resultSetMetaData.getColumnTypeName(1)).thenReturn("VARCHAR");
    when(resultSetMetaData.isNullable(1)).thenReturn(ResultSetMetaData.columnNullable);

    // Producer loop: one row, then exhausted
    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getObject(1)).thenReturn("data");

    assertEquals(1, underTest.executeAndProcess(ps, rowProcessor));

    verify(rowProcessor).process(any(ResultSet.class));
    verify(resultSet, atLeastOnce()).close();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void testConsumerInterruptionFailsInsteadOfReturningPartialCount(int processedRows)
      throws Exception {
    when(ps.getConnection()).thenReturn(connection);
    when(ps.getFetchSize()).thenReturn(2);
    when(ps.executeQuery()).thenReturn(resultSet);
    when(resultSet.getFetchSize()).thenReturn(1);
    when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
    when(resultSetMetaData.getColumnCount()).thenReturn(1);
    when(resultSetMetaData.getColumnName(1)).thenReturn("id");
    when(resultSetMetaData.getColumnType(1)).thenReturn(java.sql.Types.VARCHAR);
    when(resultSetMetaData.getColumnTypeName(1)).thenReturn("VARCHAR");
    when(resultSetMetaData.isNullable(1)).thenReturn(ResultSetMetaData.columnNullable);
    if (processedRows > 0) when(resultSet.getObject(1)).thenReturn("data");

    CountDownLatch producerWaiting = new CountDownLatch(1);
    CountDownLatch releaseProducer = new CountDownLatch(1);
    CountDownLatch producerClosed = new CountDownLatch(1);
    CountDownLatch rowsProcessed = new CountDownLatch(processedRows);
    AtomicInteger sourceRows = new AtomicInteger();
    CompletableFuture<Void> completion = new CompletableFuture<>();

    when(resultSet.next())
        .thenAnswer(
            invocation -> {
              if (sourceRows.getAndIncrement() < processedRows) return true;
              // Hold the next page so the consumer cannot finish before it is interrupted.
              producerWaiting.countDown();
              releaseProducer.await(5, TimeUnit.SECONDS);
              throw new SQLException("Result set closed while fetching the next page");
            });
    if (processedRows > 0)
      doAnswer(
              invocation -> {
                rowsProcessed.countDown();
                return null;
              })
          .when(rowProcessor)
          .process(any(ResultSet.class));

    Thread consumer =
        new Thread(
            () -> {
              try {
                SQLException failure =
                    assertThrows(
                        SQLException.class, () -> underTest.executeAndProcess(ps, rowProcessor));
                assertInstanceOf(InterruptedException.class, failure.getCause());
                assertTrue(Thread.currentThread().isInterrupted());
                completion.complete(null);
              } catch (Throwable failure) {
                completion.completeExceptionally(failure);
              }
            },
            "interrupted-prefetch-consumer");
    doAnswer(
            invocation -> {
              releaseProducer.countDown();
              if (Thread.currentThread() != consumer) producerClosed.countDown();
              return null;
            })
        .when(resultSet)
        .close();

    consumer.start();
    try {
      assertTrue(producerWaiting.await(5, TimeUnit.SECONDS));
      assertTrue(rowsProcessed.await(5, TimeUnit.SECONDS));
      consumer.interrupt();
      completion.get(5, TimeUnit.SECONDS);
      assertTrue(producerClosed.await(5, TimeUnit.SECONDS));
      verify(rowProcessor, times(processedRows)).process(any(ResultSet.class));
      verify(ps).close();
      verify(resultSet, atLeastOnce()).close();
    } finally {
      releaseProducer.countDown();
      consumer.interrupt();
      consumer.join(5000);
      assertFalse(consumer.isAlive());
    }
  }

  @Test
  void testProducerException() throws SQLException, InterruptedException {
    when(ps.getConnection()).thenReturn(connection);
    when(connection.getAutoCommit()).thenReturn(false);
    when(ps.getFetchSize()).thenReturn(10);
    when(ps.executeQuery()).thenReturn(resultSet);

    // Producer throws exception
    when(resultSet.isClosed()).thenThrow(new SQLException("DB error"));

    // Should propagate exception
    assertThrows(SQLException.class, () -> underTest.executeAndProcess(ps, rowProcessor));
  }
}
