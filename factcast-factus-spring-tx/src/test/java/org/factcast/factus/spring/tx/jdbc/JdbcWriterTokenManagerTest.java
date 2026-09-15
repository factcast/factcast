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
package org.factcast.factus.spring.tx.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.support.LockException;
import org.factcast.factus.projection.WriterToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.CannotCreateTransactionException;

@ExtendWith(MockitoExtension.class)
class JdbcWriterTokenManagerTest {

  private static final String KEY = "org.example.MyProjection_1";

  @Mock private LockProvider lockProvider;
  @Mock private ScheduledExecutorService scheduler;

  private final VirtualTiming timing = new VirtualTiming();

  private final ArgumentCaptor<LockConfiguration> lockConfigCaptor =
      ArgumentCaptor.forClass(LockConfiguration.class);

  private JdbcWriterTokenManager uut;

  @BeforeEach
  void setUp() {
    uut = managerFor(KEY);
  }

  private JdbcWriterTokenManager managerFor(String key) {
    return new JdbcWriterTokenManager(
        lockProvider,
        key,
        JdbcWriterTokenManager.DEFAULT_LOCK_AT_MOST_FOR,
        JdbcWriterTokenManager.DEFAULT_LOCK_AT_LEAST_FOR,
        scheduler,
        timing);
  }

  @Nested
  class WhenAcquiringWriteToken {

    @Test
    void returnsTokenOnFirstAttempt() {
      when(lockProvider.lock(any(LockConfiguration.class)))
          .thenReturn(Optional.of(mock(SimpleLock.class)));

      WriterToken token = uut.acquireWriteToken(Duration.ofSeconds(60));

      assertThat(token).isNotNull();
      assertThat(timing.sleeps).isEmpty();
      verify(lockProvider).lock(lockConfigCaptor.capture());
      LockConfiguration config = lockConfigCaptor.getValue();
      assertThat(config.getName()).isEqualTo(KEY + "_lock");
      assertThat(config.getLockAtMostFor()).isEqualTo(Duration.ofSeconds(60));
      assertThat(config.getLockAtLeastFor()).isEqualTo(Duration.ZERO);
    }

    @Test
    void returnsTokenAfterRetrying() {
      when(lockProvider.lock(any(LockConfiguration.class)))
          .thenReturn(Optional.empty())
          .thenReturn(Optional.empty())
          .thenReturn(Optional.of(mock(SimpleLock.class)));

      assertThat(uut.acquireWriteToken(Duration.ofSeconds(60))).isNotNull();

      verify(lockProvider, times(3)).lock(any(LockConfiguration.class));
      assertThat(timing.sleeps).containsExactly(500L, 1000L);
    }

    @Test
    void returnsNullWhenLockCannotBeHad() {
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());

      assertThat(uut.acquireWriteToken(Duration.ofSeconds(2))).isNull();
    }

    @Test
    void doesNotOvershootMaxWait() {
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());

      uut.acquireWriteToken(Duration.ofSeconds(2));

      // last sleep clamped from 2000 down to the 500ms that were left
      assertThat(timing.sleeps).containsExactly(500L, 1000L, 500L);
      assertThat(timing.totalSleptMillis()).isEqualTo(2000L);
      verify(lockProvider, times(4)).lock(any(LockConfiguration.class));
    }

    @Test
    void attemptsOnceWithoutSleepingWhenMaxWaitIsZero() {
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());

      assertThat(uut.acquireWriteToken(Duration.ZERO)).isNull();

      verify(lockProvider).lock(any(LockConfiguration.class));
      assertThat(timing.sleeps).isEmpty();
    }

    @Test
    void capsBackoffAt30Seconds() {
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());

      uut.acquireWriteToken(Duration.ofMinutes(10));

      assertThat(timing.sleeps).startsWith(500L, 1000L, 2000L, 4000L, 8000L, 16000L, 30000L);
      assertThat(timing.sleeps).allSatisfy(s -> assertThat(s).isLessThanOrEqualTo(30_000L));
      assertThat(timing.sleeps).contains(30_000L);
      assertThat(timing.totalSleptMillis()).isEqualTo(Duration.ofMinutes(10).toMillis());
    }

    @Test
    void returnsNullWithoutRestoringTheInterruptFlagWhenInterrupted() {
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());
      timing.interruptOnSleep = true;

      try {
        assertThat(uut.acquireWriteToken(Duration.ofSeconds(60))).isNull();
        // a restored flag would make every retry of our callers fail its sleep instantly
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
      } finally {
        Thread.interrupted();
      }
    }

    @Test
    void startsTheLeaseBeforeTheRoundTrip() {
      when(lockProvider.lock(any(LockConfiguration.class)))
          .thenAnswer(
              invocation -> {
                timing.advance(JdbcWriterTokenManager.DEFAULT_LOCK_AT_MOST_FOR.plusSeconds(1));
                return Optional.of(mock(SimpleLock.class));
              });

      WriterToken token = uut.acquireWriteToken(Duration.ofSeconds(60));

      assertThat(token).isNotNull();
      assertThat(token.isValid()).isFalse();
    }

    @Test
    void retriesWhenTheDatabaseIsUnreachable() {
      when(lockProvider.lock(any(LockConfiguration.class)))
          .thenThrow(new CannotCreateTransactionException("pool exhausted"))
          .thenThrow(new CannotGetJdbcConnectionException("connection refused"))
          .thenThrow(new CannotCreateTransactionException("pool exhausted"))
          .thenThrow(new CannotGetJdbcConnectionException("connection refused"));

      assertThat(uut.acquireWriteToken(Duration.ofSeconds(2))).isNull();

      verify(lockProvider, times(4)).lock(any(LockConfiguration.class));
    }

    @Test
    void namesTheUnsupportedDatabaseWhenDbTimeIsUnavailable() {
      when(lockProvider.lock(any(LockConfiguration.class)))
          .thenThrow(
              new UnsupportedOperationException(
                  "useDbTime() is not supported for database product: FooDB"));

      assertThatThrownBy(() -> uut.acquireWriteToken(Duration.ofSeconds(1)))
          .isInstanceOf(LockException.class)
          .hasMessageContaining(KEY)
          .hasMessageContaining("does not support")
          .hasMessageContaining("PostgreSQL")
          .hasRootCauseMessage("useDbTime() is not supported for database product: FooDB");
    }

    @Test
    void namesProjectionAndColumnWidthWhenLockProviderBlowsUp() {
      when(lockProvider.lock(any(LockConfiguration.class)))
          .thenThrow(new LockException("value too long for type character varying(64)"));

      assertThatThrownBy(() -> uut.acquireWriteToken(Duration.ofSeconds(1)))
          .isInstanceOf(LockException.class)
          .hasMessageContaining(KEY)
          .hasMessageContaining(KEY + "_lock")
          .hasMessageContaining(String.valueOf(ProjectionNames.MAX_NAME_LENGTH))
          .hasRootCauseMessage("value too long for type character varying(64)");
    }

    @Test
    void shortensOverlongLockName() {
      String longKey = "y".repeat(300);
      when(lockProvider.lock(any(LockConfiguration.class)))
          .thenReturn(Optional.of(mock(SimpleLock.class)));

      managerFor(longKey).acquireWriteToken(Duration.ofSeconds(1));

      verify(lockProvider).lock(lockConfigCaptor.capture());
      assertThat(lockConfigCaptor.getValue().getName())
          .isEqualTo(ProjectionNames.lockName(longKey))
          .hasSizeLessThanOrEqualTo(ProjectionNames.MAX_NAME_LENGTH);
    }
  }

  @Nested
  class WhenTheLeaseIsTooShortToRenew {

    @Test
    void isRejectedOnConstruction() {
      assertThatThrownBy(
              () ->
                  new JdbcWriterTokenManager(
                      lockProvider, KEY, Duration.ofMillis(2), Duration.ZERO))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("lockAtMostFor")
          .hasMessageContaining(JdbcWriterTokenManager.MINIMUM_LOCK_AT_MOST_FOR.toString());
    }

    @Test
    void isRejectedByTheJdbcTemplateFactory() {
      JdbcTemplate jdbcTemplate = new JdbcTemplate(mock(DataSource.class));

      assertThatThrownBy(
              () ->
                  JdbcWriterTokenManager.create(
                      jdbcTemplate, KEY, "locks", Duration.ofMillis(2), Duration.ZERO))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsTheMinimum() {
      assertThatCode(
              () ->
                  new JdbcWriterTokenManager(
                      lockProvider,
                      KEY,
                      JdbcWriterTokenManager.MINIMUM_LOCK_AT_MOST_FOR,
                      Duration.ZERO))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  class WhenConfiguringTheLockProvider {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    @Test
    void usesDedicatedTableAndDbTime() {
      JdbcTemplateLockProvider.Configuration config =
          JdbcWriterTokenManager.lockProviderConfiguration(
              jdbcTemplate, JdbcWriterTokenManager.DEFAULT_LOCK_TABLE_NAME);

      assertThat(config.getTableName()).isEqualTo("factcast_projection_locks");
      assertThat(config.getUseDbTime()).isTrue();
      assertThat(config.getJdbcTemplate()).isSameAs(jdbcTemplate);
    }

    @Test
    void honoursCustomTableName() {
      assertThat(
              JdbcWriterTokenManager.lockProviderConfiguration(jdbcTemplate, "my_locks")
                  .getTableName())
          .isEqualTo("my_locks");
    }

    @Test
    void distinguishesInstancesOnTheSameHost() {
      String first =
          JdbcWriterTokenManager.lockProviderConfiguration(jdbcTemplate, "locks")
              .getLockedByValue();
      String second =
          JdbcWriterTokenManager.lockProviderConfiguration(jdbcTemplate, "locks")
              .getLockedByValue();

      assertThat(first).isNotEqualTo(second).contains("/");
      assertThat(first.substring(0, first.indexOf('/')))
          .isEqualTo(second.substring(0, second.indexOf('/')));
    }
  }

  static class VirtualTiming implements JdbcWriterTokenManager.Timing {
    final List<Long> sleeps = new ArrayList<>();
    boolean interruptOnSleep = false;
    private long nanos = 0;

    @Override
    public long nanoTime() {
      return nanos;
    }

    void advance(Duration elapsed) {
      nanos += elapsed.toNanos();
    }

    @Override
    public void sleep(long millis) throws InterruptedException {
      if (interruptOnSleep) {
        throw new InterruptedException("test");
      }
      sleeps.add(millis);
      nanos += TimeUnit.MILLISECONDS.toNanos(millis);
    }

    long totalSleptMillis() {
      return sleeps.stream().mapToLong(Long::longValue).sum();
    }
  }
}
