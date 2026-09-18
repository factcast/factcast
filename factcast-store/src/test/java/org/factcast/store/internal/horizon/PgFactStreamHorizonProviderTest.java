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
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.factcast.core.subscription.observer.HighWaterMark;
import org.factcast.store.internal.PgConstants;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.lock.FactTableWriteLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
final class PgFactStreamHorizonProviderTest {

  @Mock DataSource dataSource;
  @Mock JdbcTemplate jdbcTemplate;
  @Mock FactTableWriteLock factTableWriteLock;
  @Mock PlatformTransactionManager transactionManager;

  PgFactStreamHorizonProvider underTest;

  @BeforeEach
  void setUp() {
    when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenReturn(new SimpleTransactionStatus());
    underTest =
        new PgFactStreamHorizonProvider(
            dataSource,
            jdbcTemplate,
            factTableWriteLock,
            new PgMetrics(new SimpleMeterRegistry()),
            transactionManager);
  }

  @Test
  @SuppressWarnings("unchecked")
  void locksBeforeReadingAndPublishesHorizonOnlyAfterCommit() {
    UUID id = UUID.randomUUID();
    HighWaterMark liveHighWaterMark = HighWaterMark.of(id, 42);
    FactStreamHorizon persisted = new FactStreamHorizon(liveHighWaterMark, 11);
    when(jdbcTemplate.query(eq(PgConstants.HIGHWATER_MARK), any(RowMapper.class)))
        .thenReturn(List.of(liveHighWaterMark));
    when(jdbcTemplate.queryForObject(
            PgFactStreamHorizonProvider.HIGHWATER_NOTIFICATION, Long.class))
        .thenReturn(10L);
    when(jdbcTemplate.query(
            eq(PgFactStreamHorizonProvider.UPDATE_HORIZON),
            any(RowMapper.class),
            any(Object[].class)))
        .thenReturn(List.of(persisted));
    doAnswer(
            ignored -> {
              assertThat(underTest.current()).isEqualTo(FactStreamHorizon.empty());
              return null;
            })
        .when(transactionManager)
        .commit(any());

    assertThat(underTest.advance()).isEqualTo(persisted);
    assertThat(underTest.current()).isEqualTo(persisted);

    InOrder order = inOrder(transactionManager, factTableWriteLock, jdbcTemplate);
    order.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    order.verify(factTableWriteLock).acquireExclusiveTXLock();
    order.verify(jdbcTemplate).query(eq(PgConstants.HIGHWATER_MARK), any(RowMapper.class));
    order
        .verify(jdbcTemplate)
        .queryForObject(PgFactStreamHorizonProvider.HIGHWATER_NOTIFICATION, Long.class);
    order
        .verify(jdbcTemplate)
        .query(
            eq(PgFactStreamHorizonProvider.UPDATE_HORIZON),
            any(RowMapper.class),
            any(Object[].class));
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
    HighWaterMark highWaterMark = HighWaterMark.of(UUID.randomUUID(), 42);
    FactStreamHorizon persisted = new FactStreamHorizon(highWaterMark, 10);
    when(jdbcTemplate.query(eq(PgConstants.HIGHWATER_MARK), any(RowMapper.class)))
        .thenReturn(List.of(highWaterMark));
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(10L);
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(persisted));
    doThrow(new TransactionSystemException("commit failed")).when(transactionManager).commit(any());

    assertThatThrownBy(underTest::advance).isInstanceOf(TransactionSystemException.class);

    assertThat(underTest.current()).isEqualTo(FactStreamHorizon.empty());
  }

  @Test
  @SuppressWarnings("unchecked")
  void failedHorizonWriteRollsBackAndPreservesCurrentHorizon() {
    HighWaterMark highWaterMark = HighWaterMark.of(UUID.randomUUID(), 42);
    when(jdbcTemplate.query(eq(PgConstants.HIGHWATER_MARK), any(RowMapper.class)))
        .thenReturn(List.of(highWaterMark));
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(10L);
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenThrow(new IllegalStateException("write failed"));

    assertThatThrownBy(underTest::advance)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("write failed");

    verify(transactionManager).rollback(any());
    assertThat(underTest.current()).isEqualTo(FactStreamHorizon.empty());
  }
}
