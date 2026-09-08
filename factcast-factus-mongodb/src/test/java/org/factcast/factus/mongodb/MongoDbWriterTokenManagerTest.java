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
package org.factcast.factus.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.factcast.factus.projection.WriterToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoDbWriterTokenManagerTest {

  private static final String KEY = "key";
  private static final long MAX_RETRY_INTERVAL_MILLISECONDS = 30_000;

  @Mock private LockProvider lockProvider;

  MongoDbWriterTokenManager uut;

  @BeforeEach
  void setUp() {
    uut = new MongoDbWriterTokenManager(lockProvider, KEY);
  }

  /** Replaces sleeping with advancing a virtual clock by exactly the requested amount. */
  static class TimeTravellingTokenManager extends MongoDbWriterTokenManager {
    final List<Long> sleeps = new ArrayList<>();
    private long nanos;

    TimeTravellingTokenManager(LockProvider lockProvider, String projectionKey) {
      super(lockProvider, projectionKey);
    }

    @Override
    long nanoTime() {
      return nanos;
    }

    @Override
    void sleep(long milliseconds) {
      sleeps.add(milliseconds);
      nanos += TimeUnit.MILLISECONDS.toNanos(milliseconds);
    }

    long totalSleepMilliseconds() {
      return sleeps.stream().mapToLong(Long::longValue).sum();
    }
  }

  @Nested
  class WhenAcquiringLock {

    private final ArgumentCaptor<LockConfiguration> captor =
        ArgumentCaptor.forClass(LockConfiguration.class);

    @Test
    @SneakyThrows
    void returnsTokenWhenLockingSuccessful() {
      SimpleLock lock = mock(SimpleLock.class);
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(lock));

      final WriterToken res = uut.acquireWriteToken(Duration.ofSeconds(60L));

      verify(lockProvider).lock(captor.capture());
      final LockConfiguration lockConfig = captor.getValue();
      assertThat(lockConfig.getName()).isEqualTo(KEY + "_lock");
      assertThat(lockConfig.getLockAtLeastFor())
          .isEqualTo(MongoDbWriterTokenManager.MIN_LEASE_DURATION_SECONDS);
      assertThat(lockConfig.getLockAtMostFor())
          .isEqualTo(MongoDbWriterTokenManager.MAX_LEASE_DURATION_SECONDS);

      assertThat(res).isNotNull();
      res.close();
    }

    @Test
    @SneakyThrows
    void returnsLockWhenSuccessfulAfterMultipleAttempts() {
      SimpleLock lock = mock(SimpleLock.class);
      when(lockProvider.lock(any(LockConfiguration.class)))
          .thenReturn(Optional.empty())
          .thenReturn(Optional.empty())
          .thenReturn(Optional.of(lock));
      TimeTravellingTokenManager manager = new TimeTravellingTokenManager(lockProvider, KEY);

      final WriterToken res = manager.acquireWriteToken(Duration.ofSeconds(7));

      verify(lockProvider, times(3)).lock(captor.capture());
      final LockConfiguration lockConfig = captor.getValue();
      assertThat(lockConfig.getName()).isEqualTo(KEY + "_lock");
      assertThat(lockConfig.getLockAtLeastFor())
          .isEqualTo(MongoDbWriterTokenManager.MIN_LEASE_DURATION_SECONDS);
      assertThat(lockConfig.getLockAtMostFor())
          .isEqualTo(MongoDbWriterTokenManager.MAX_LEASE_DURATION_SECONDS);
      assertThat(manager.sleeps).containsExactly(500L, 1000L);

      assertThat(res).isNotNull();
      res.close();
    }

    @Test
    @SneakyThrows
    void returnsNullIfLockCouldNotBeObtainedAfterMultipleAttempts() {
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());

      final WriterToken res = uut.acquireWriteToken(Duration.ofMillis(600));

      verify(lockProvider, atLeast(2)).lock(any(LockConfiguration.class));
      assertThat(res).isNull();
    }

    @Test
    @DisplayName("waiting for the lock stops at maxWait instead of overshooting by one backoff")
    @SneakyThrows
    void doesNotWaitLongerThanMaxWait() {
      Duration maxWait = Duration.ofSeconds(1);
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());
      TimeTravellingTokenManager manager = new TimeTravellingTokenManager(lockProvider, KEY);

      assertThat(manager.acquireWriteToken(maxWait)).isNull();

      // the second backoff would have been 1000ms, which is clamped to the remaining 500ms
      assertThat(manager.sleeps).containsExactly(500L, 500L);
      assertThat(manager.totalSleepMilliseconds()).isEqualTo(maxWait.toMillis());
      verify(lockProvider, times(3)).lock(any(LockConfiguration.class));
    }

    @Test
    @DisplayName("a maxWait below the initial backoff is respected as well")
    @SneakyThrows
    void doesNotWaitLongerThanAShortMaxWait() {
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());
      TimeTravellingTokenManager manager = new TimeTravellingTokenManager(lockProvider, KEY);

      assertThat(manager.acquireWriteToken(Duration.ofMillis(200))).isNull();

      assertThat(manager.sleeps).containsExactly(200L);
      verify(lockProvider, times(2)).lock(any(LockConfiguration.class));
    }

    @Test
    @SneakyThrows
    void doesNotWaitAtAllWhenMaxWaitIsZero() {
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());
      TimeTravellingTokenManager manager = new TimeTravellingTokenManager(lockProvider, KEY);

      assertThat(manager.acquireWriteToken(Duration.ZERO)).isNull();

      assertThat(manager.sleeps).isEmpty();
      verify(lockProvider, times(1)).lock(any(LockConfiguration.class));
    }

    @Test
    @DisplayName("the exponential backoff is capped at 30 seconds")
    @SneakyThrows
    void capsBackoffAt30Seconds() {
      Duration maxWait = Duration.ofMinutes(10);
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());
      TimeTravellingTokenManager manager = new TimeTravellingTokenManager(lockProvider, KEY);

      assertThat(manager.acquireWriteToken(maxWait)).isNull();

      assertThat(manager.sleeps)
          .startsWith(500L, 1000L, 2000L, 4000L, 8000L, 16000L, MAX_RETRY_INTERVAL_MILLISECONDS)
          .allMatch(sleep -> sleep <= MAX_RETRY_INTERVAL_MILLISECONDS);
      assertThat(manager.totalSleepMilliseconds()).isEqualTo(maxWait.toMillis());
    }

    @Test
    @SneakyThrows
    void returnsNullAndKeepsInterruptedFlagWhenInterrupted() {
      when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());
      MongoDbWriterTokenManager manager =
          new MongoDbWriterTokenManager(lockProvider, KEY) {
            @Override
            void sleep(long milliseconds) throws InterruptedException {
              throw new InterruptedException("test");
            }
          };

      assertThat(manager.acquireWriteToken(Duration.ofMinutes(5))).isNull();

      assertThat(Thread.interrupted()).isTrue();
    }
  }
}
