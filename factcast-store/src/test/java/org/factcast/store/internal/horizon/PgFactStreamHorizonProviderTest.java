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
package org.factcast.store.internal.horizon;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.factcast.core.subscription.FactStreamHorizon;
import org.factcast.store.internal.PgConstants;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.lock.FactTableWriteLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
final class PgFactStreamHorizonProviderTest {

  @Mock DataSource dataSource;
  @Mock JdbcTemplate jdbcTemplate;
  @Mock JdbcTemplate primaryJdbcTemplate;
  @Mock FactTableWriteLock factTableWriteLock;
  @Mock PlatformTransactionManager transactionManager;

  PgFactStreamHorizonProvider underTest;

  @BeforeEach
  void setUp() {
    lenient()
        .when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenReturn(new SimpleTransactionStatus());
    underTest =
        new PgFactStreamHorizonProvider(
            dataSource,
            jdbcTemplate,
            factTableWriteLock,
            new PgMetrics(new SimpleMeterRegistry()),
            transactionManager) {
          @Override
          protected JdbcTemplate jdbcTemplate(DataSource dataSource) {
            assertThat(dataSource).isSameAs(PgFactStreamHorizonProviderTest.this.dataSource);
            return primaryJdbcTemplate;
          }
        };
  }

  @Test
  @SuppressWarnings("unchecked")
  void locksBeforeReadingAndPublishesHorizonOnlyAfterCommit() {
    UUID id = UUID.randomUUID();
    FactStreamHorizon liveHorizon = new FactStreamHorizon(id, 42, 0);
    FactStreamHorizon persisted = new FactStreamHorizon(id, 42, 11);
    when(jdbcTemplate.query(eq(PgConstants.LATEST_FACT), any(RowMapper.class)))
        .thenReturn(List.of(liveHorizon));
    when(jdbcTemplate.queryForObject(
            PgFactStreamHorizonProvider.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenReturn(10L);
    when(jdbcTemplate.queryForObject(
            eq(PgFactStreamHorizonProvider.UPDATE_HORIZON),
            any(RowMapper.class),
            eq(42L),
            eq(id),
            eq(10L),
            eq(10L)))
        .thenReturn(persisted);
    doAnswer(
            ignored -> {
              assertThat(underTest.currentPrimary()).isEqualTo(FactStreamHorizon.empty());
              return null;
            })
        .when(transactionManager)
        .commit(any());

    assertThat(underTest.advance()).isEqualTo(persisted);
    assertThat(underTest.currentPrimary()).isEqualTo(persisted);

    InOrder order = inOrder(transactionManager, factTableWriteLock, jdbcTemplate);
    order.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    order.verify(factTableWriteLock).acquireExclusiveTXLock();
    order.verify(jdbcTemplate).query(eq(PgConstants.LATEST_FACT), any(RowMapper.class));
    order
        .verify(jdbcTemplate)
        .queryForObject(PgFactStreamHorizonProvider.MAX_NOTIFICATION_SERIAL, Long.class);
    order
        .verify(jdbcTemplate)
        .queryForObject(
            eq(PgFactStreamHorizonProvider.UPDATE_HORIZON),
            any(RowMapper.class),
            eq(42L),
            eq(id),
            eq(10L),
            eq(10L));
    order.verify(transactionManager).commit(any());

    ArgumentCaptor<TransactionDefinition> definition =
        ArgumentCaptor.forClass(TransactionDefinition.class);
    verify(transactionManager).getTransaction(definition.capture());
    assertThat(definition.getValue().getPropagationBehavior())
        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Test
  @SuppressWarnings("unchecked")
  void commitFailureLeavesCurrentHorizonUntouched() {
    FactStreamHorizon previous = new FactStreamHorizon(UUID.randomUUID(), 21, 5);
    stubAdvance(previous);
    underTest.advance();
    FactStreamHorizon liveHorizon = new FactStreamHorizon(UUID.randomUUID(), 42, 0);
    FactStreamHorizon persisted =
        new FactStreamHorizon(liveHorizon.factId(), liveHorizon.factSerial(), 10);
    when(jdbcTemplate.query(eq(PgConstants.LATEST_FACT), any(RowMapper.class)))
        .thenReturn(List.of(liveHorizon));
    when(jdbcTemplate.queryForObject(
            PgFactStreamHorizonProvider.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenReturn(10L);
    when(jdbcTemplate.queryForObject(
            eq(PgFactStreamHorizonProvider.UPDATE_HORIZON),
            any(RowMapper.class),
            eq(42L),
            eq(liveHorizon.factId()),
            eq(10L),
            eq(10L)))
        .thenReturn(persisted);
    doThrow(new TransactionSystemException("commit failed")).when(transactionManager).commit(any());

    assertThatThrownBy(underTest::advance).isInstanceOf(TransactionSystemException.class);

    assertThat(underTest.currentPrimary()).isEqualTo(previous);
  }

  @Test
  @SuppressWarnings("unchecked")
  void failedHorizonWriteRollsBackAndPreservesCurrentHorizon() {
    FactStreamHorizon previous = new FactStreamHorizon(UUID.randomUUID(), 21, 5);
    stubAdvance(previous);
    underTest.advance();
    FactStreamHorizon liveHorizon = new FactStreamHorizon(UUID.randomUUID(), 42, 0);
    when(jdbcTemplate.query(eq(PgConstants.LATEST_FACT), any(RowMapper.class)))
        .thenReturn(List.of(liveHorizon));
    when(jdbcTemplate.queryForObject(
            PgFactStreamHorizonProvider.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenReturn(10L);
    when(jdbcTemplate.queryForObject(
            eq(PgFactStreamHorizonProvider.UPDATE_HORIZON),
            any(RowMapper.class),
            eq(42L),
            eq(liveHorizon.factId()),
            eq(10L),
            eq(10L)))
        .thenThrow(new IllegalStateException("write failed"));

    assertThatThrownBy(underTest::advance)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("write failed");

    verify(transactionManager).rollback(any());
    assertThat(underTest.currentPrimary()).isEqualTo(previous);
  }

  @Test
  @SuppressWarnings("unchecked")
  void joinedTransactionPublishesCurrentHorizonOnlyAfterCommit() {
    FactStreamHorizon liveHorizon = new FactStreamHorizon(UUID.randomUUID(), 42, 0);
    FactStreamHorizon persisted =
        new FactStreamHorizon(liveHorizon.factId(), liveHorizon.factSerial(), 10);
    when(factTableWriteLock.isExclusiveTXLockHeld()).thenReturn(true);
    when(jdbcTemplate.query(eq(PgConstants.LATEST_FACT), any(RowMapper.class)))
        .thenReturn(List.of(liveHorizon));
    when(jdbcTemplate.queryForObject(
            PgFactStreamHorizonProvider.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenReturn(10L);
    when(jdbcTemplate.queryForObject(
            eq(PgFactStreamHorizonProvider.UPDATE_HORIZON),
            any(RowMapper.class),
            eq(42L),
            eq(liveHorizon.factId()),
            eq(10L),
            eq(10L)))
        .thenReturn(persisted);

    TransactionSynchronizationManager.initSynchronization();
    try {
      assertThat(underTest.advance()).isEqualTo(persisted);
      assertThat(underTest.currentPrimary()).isEqualTo(FactStreamHorizon.empty());
      verify(transactionManager, never()).getTransaction(any());

      TransactionSynchronizationManager.getSynchronizations()
          .forEach(synchronization -> synchronization.afterCommit());
      assertThat(underTest.currentPrimary()).isEqualTo(persisted);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void mapsLiveFactAndPersistedHorizonFromResultSets() throws Exception {
    UUID id = UUID.randomUUID();
    ResultSet latestFact = mock(ResultSet.class);
    ResultSet persistedRow = mock(ResultSet.class);
    when(latestFact.getObject(PgConstants.HORIZON_COLUMN_FACT_ID, UUID.class)).thenReturn(id);
    when(latestFact.getLong(PgConstants.HORIZON_COLUMN_FACT_SER)).thenReturn(42L);
    when(persistedRow.getObject(PgConstants.HORIZON_COLUMN_FACT_ID, UUID.class)).thenReturn(id);
    when(persistedRow.getLong(PgConstants.HORIZON_COLUMN_FACT_SER)).thenReturn(42L);
    when(persistedRow.getLong(PgConstants.HORIZON_COLUMN_NOTIFICATION_SER)).thenReturn(11L);
    when(jdbcTemplate.query(eq(PgConstants.LATEST_FACT), any(RowMapper.class)))
        .thenAnswer(
            invocation -> {
              RowMapper<FactStreamHorizon> mapper = invocation.getArgument(1);
              return List.of(mapper.mapRow(latestFact, 0));
            });
    when(jdbcTemplate.queryForObject(
            PgFactStreamHorizonProvider.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenReturn(10L);
    when(jdbcTemplate.queryForObject(
            eq(PgFactStreamHorizonProvider.UPDATE_HORIZON),
            any(RowMapper.class),
            eq(42L),
            eq(id),
            eq(10L),
            eq(10L)))
        .thenAnswer(
            invocation -> {
              RowMapper<FactStreamHorizon> mapper = invocation.getArgument(1);
              return mapper.mapRow(persistedRow, 0);
            });

    FactStreamHorizon expected = new FactStreamHorizon(id, 42, 11);
    assertThat(underTest.advance()).isEqualTo(expected);
    assertThat(underTest.currentPrimary()).isEqualTo(expected);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {0, 7})
  @SuppressWarnings("unchecked")
  void emptyFactTableUsesEmptyFactPosition(Long notificationSerial) {
    long expectedSerial = notificationSerial == null ? 0 : notificationSerial;
    FactStreamHorizon expected = new FactStreamHorizon(null, 0, expectedSerial);
    when(jdbcTemplate.query(eq(PgConstants.LATEST_FACT), any(RowMapper.class)))
        .thenReturn(List.of());
    when(jdbcTemplate.queryForObject(
            PgFactStreamHorizonProvider.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenReturn(notificationSerial);
    when(jdbcTemplate.queryForObject(
            eq(PgFactStreamHorizonProvider.UPDATE_HORIZON),
            any(RowMapper.class),
            eq(0L),
            isNull(),
            eq(expectedSerial),
            eq(expectedSerial)))
        .thenReturn(expected);

    assertThat(underTest.advance()).isEqualTo(expected);
    assertThat(underTest.currentPrimary()).isEqualTo(expected);
  }

  @Test
  void lockFailureRollsBackAndPreservesCurrentHorizon() {
    FactStreamHorizon previous = new FactStreamHorizon(UUID.randomUUID(), 21, 5);
    stubAdvance(previous);
    underTest.advance();
    clearInvocations(jdbcTemplate, transactionManager);
    doThrow(new IllegalStateException("lock failed"))
        .when(factTableWriteLock)
        .acquireExclusiveTXLock();

    assertThatThrownBy(underTest::advance)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("lock failed");

    verify(transactionManager).rollback(any());
    verify(transactionManager, never()).commit(any());
    verifyNoInteractions(jdbcTemplate);
    assertThat(underTest.currentPrimary()).isEqualTo(previous);
  }

  @Test
  void joinedTransactionRollbackPreservesCurrentHorizon() {
    FactStreamHorizon previous = new FactStreamHorizon(UUID.randomUUID(), 21, 5);
    stubAdvance(previous);
    underTest.advance();
    clearInvocations(transactionManager);
    FactStreamHorizon uncommitted = new FactStreamHorizon(UUID.randomUUID(), 42, 10);
    stubAdvance(uncommitted);
    when(factTableWriteLock.isExclusiveTXLockHeld()).thenReturn(true);

    TransactionSynchronizationManager.initSynchronization();
    try {
      assertThat(underTest.advance()).isEqualTo(uncommitted);
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(
              synchronization ->
                  synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

      assertThat(underTest.currentPrimary()).isEqualTo(previous);
      verifyNoInteractions(transactionManager);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void newerFactSerialUpdatesCacheEvenWhenNotificationSerialDecreases() {
    FactStreamHorizon previous = new FactStreamHorizon(UUID.randomUUID(), 42, 20);
    stubAdvance(previous);
    underTest.advance();
    FactStreamHorizon next = new FactStreamHorizon(UUID.randomUUID(), 43, 19);
    stubAdvance(next);

    assertThat(underTest.advance()).isEqualTo(next);
    assertThat(underTest.currentPrimary()).isEqualTo(next);
    verifyNoInteractions(primaryJdbcTemplate);
  }

  @ParameterizedTest
  @ValueSource(longs = {20, 21})
  void unchangedFactSerialAcceptsAnEqualOrNewerNotificationSerial(long notificationSerial) {
    UUID id = UUID.randomUUID();
    FactStreamHorizon previous = new FactStreamHorizon(id, 42, 20);
    stubAdvance(previous);
    underTest.advance();
    FactStreamHorizon next = new FactStreamHorizon(id, 42, notificationSerial);
    stubAdvance(next);

    assertThat(underTest.advance()).isEqualTo(next);
    assertThat(underTest.currentPrimary()).isEqualTo(next);
    verifyNoInteractions(primaryJdbcTemplate);
  }

  @ParameterizedTest
  @MethodSource("outOfOrderHorizons")
  @SuppressWarnings("unchecked")
  void delayedCommitCallbackReloadsPrimaryInsteadOfRegressingCache(
      FactStreamHorizon older, FactStreamHorizon newer) {
    when(factTableWriteLock.isExclusiveTXLockHeld()).thenReturn(true);
    TransactionSynchronizationManager.initSynchronization();
    try {
      stubAdvance(older);
      underTest.advance();
      stubAdvance(newer);
      underTest.advance();
      var callbacks = TransactionSynchronizationManager.getSynchronizations();
      assertThat(callbacks).hasSize(2);
      assertThat(underTest.currentPrimary()).isEqualTo(FactStreamHorizon.empty());
      callbacks.get(1).afterCommit();
      assertThat(underTest.currentPrimary()).isEqualTo(newer);
      when(primaryJdbcTemplate.queryForObject(
              eq(ReadOnlyPgFactStreamHorizonProvider.READ_HORIZON), any(RowMapper.class)))
          .thenReturn(newer);

      callbacks.get(0).afterCommit();

      assertThat(underTest.currentPrimary()).isEqualTo(newer);
      verify(primaryJdbcTemplate)
          .queryForObject(
              eq(ReadOnlyPgFactStreamHorizonProvider.READ_HORIZON), any(RowMapper.class));
      verifyNoInteractions(transactionManager);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  static Stream<Arguments> outOfOrderHorizons() {
    UUID id = UUID.randomUUID();
    return Stream.of(
        Arguments.of(
            new FactStreamHorizon(id, 41, 21), new FactStreamHorizon(UUID.randomUUID(), 42, 20)),
        Arguments.of(new FactStreamHorizon(id, 42, 19), new FactStreamHorizon(id, 42, 20)));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @SuppressWarnings("unchecked")
  void cacheAcceptsDatabaseConfirmedFactOrNotificationReset(boolean resetFacts) {
    UUID id = UUID.randomUUID();
    FactStreamHorizon previous = new FactStreamHorizon(id, 42, 20);
    stubAdvance(previous);
    underTest.advance();
    FactStreamHorizon reset =
        resetFacts ? FactStreamHorizon.empty() : new FactStreamHorizon(id, 42, 0);
    stubAdvance(reset);
    when(primaryJdbcTemplate.queryForObject(
            eq(ReadOnlyPgFactStreamHorizonProvider.READ_HORIZON), any(RowMapper.class)))
        .thenReturn(reset);

    assertThat(underTest.advance()).isEqualTo(reset);
    assertThat(underTest.currentPrimary()).isEqualTo(reset);
    verify(primaryJdbcTemplate)
        .queryForObject(eq(ReadOnlyPgFactStreamHorizonProvider.READ_HORIZON), any(RowMapper.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void concurrentCacheChangeDuringPrimaryReloadRetriesWithLatestHorizon() throws Exception {
    FactStreamHorizon previous = new FactStreamHorizon(UUID.randomUUID(), 42, 20);
    FactStreamHorizon older = new FactStreamHorizon(UUID.randomUUID(), 41, 10);
    FactStreamHorizon newest = new FactStreamHorizon(UUID.randomUUID(), 43, 30);
    stubAdvance(previous);
    underTest.advance();
    stubAdvance(older);
    CountDownLatch readStarted = new CountDownLatch(1);
    CountDownLatch releaseRead = new CountDownLatch(1);
    AtomicInteger reads = new AtomicInteger();
    when(primaryJdbcTemplate.queryForObject(
            eq(ReadOnlyPgFactStreamHorizonProvider.READ_HORIZON), any(RowMapper.class)))
        .thenAnswer(
            ignored -> {
              if (reads.incrementAndGet() == 1) {
                readStarted.countDown();
                assertThat(releaseRead.await(5, TimeUnit.SECONDS)).isTrue();
                return previous;
              }
              return newest;
            });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<FactStreamHorizon> delayedAdvance = executor.submit(underTest::advance);
      assertThat(readStarted.await(5, TimeUnit.SECONDS)).isTrue();
      stubAdvance(newest);
      assertThat(underTest.advance()).isEqualTo(newest);
      releaseRead.countDown();

      assertThat(delayedAdvance.get(5, TimeUnit.SECONDS)).isEqualTo(older);
      assertThat(underTest.currentPrimary()).isEqualTo(newest);
      assertThat(reads).hasValue(2);
    } finally {
      releaseRead.countDown();
      executor.shutdownNow();
    }
  }

  @SuppressWarnings("unchecked")
  private void stubAdvance(FactStreamHorizon persisted) {
    when(jdbcTemplate.query(eq(PgConstants.LATEST_FACT), any(RowMapper.class)))
        .thenReturn(List.of(new FactStreamHorizon(persisted.factId(), persisted.factSerial(), 0)));
    when(jdbcTemplate.queryForObject(
            PgFactStreamHorizonProvider.MAX_NOTIFICATION_SERIAL, Long.class))
        .thenReturn(persisted.notificationSerial());
    when(jdbcTemplate.queryForObject(
            eq(PgFactStreamHorizonProvider.UPDATE_HORIZON),
            any(RowMapper.class),
            eq(persisted.factSerial()),
            eq(persisted.factId()),
            eq(persisted.notificationSerial()),
            eq(persisted.notificationSerial())))
        .thenReturn(persisted);
  }
}
