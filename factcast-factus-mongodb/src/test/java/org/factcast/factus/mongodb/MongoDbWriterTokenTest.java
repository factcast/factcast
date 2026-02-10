/*
 * Copyright © 2017-2025 factcast.org
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoDbWriterTokenTest {

  private final long KEEPALIVE_INTERVAL_SECONDS = 2;

  @Mock SimpleLock lock;
  @Mock LockConfiguration lockConfiguration;
  MongoDbWriterToken uut;

  @BeforeEach
  void setUp() {
    uut =
        new MongoDbWriterToken(
            lock, lockConfiguration, Duration.ofSeconds(KEEPALIVE_INTERVAL_SECONDS));
  }

  @Nested
  class WhenCheckingValidity {
    @Test
    @DisplayName(
        "isValid should return true without extending the lock when checkIntervall is not exceeded.")
    void isValidReturnsTrueWhenLivenessNotExpired() {
      assertThat(uut.isValid()).isTrue();

      verifyNoInteractions(lock);
    }

    @Test
    @DisplayName("isValid should return true when lock can be extended")
    void isValidExtendsLockWhenLivenessExpired() {
      when(lock.extend(any(Duration.class), any(Duration.class)))
          .thenReturn(Optional.of(mock(SimpleLock.class)));
      final Duration minDuration = Duration.ofSeconds(1L);
      final Duration maxDuration = Duration.ofSeconds(1L);
      when(lockConfiguration.getLockAtLeastFor()).thenReturn(minDuration);
      when(lockConfiguration.getLockAtMostFor()).thenReturn(maxDuration);
      uut.liveness().set(System.currentTimeMillis() - Duration.ofSeconds(21).toMillis());

      assertThat(uut.isValid()).isTrue();

      verify(lock).extend(maxDuration, minDuration);
    }

    @Test
    @DisplayName("isValid should return false when lock cannot be extended")
    void isValidReturnsFalseIfExtendReturnsEmpty() {
      when(lock.extend(any(Duration.class), any(Duration.class))).thenReturn(Optional.empty());
      final Duration minDuration = Duration.ofSeconds(1L);
      final Duration maxDuration = Duration.ofSeconds(1L);
      when(lockConfiguration.getLockAtLeastFor()).thenReturn(minDuration);
      when(lockConfiguration.getLockAtMostFor()).thenReturn(maxDuration);
      uut.liveness().set(System.currentTimeMillis() - Duration.ofSeconds(21).toMillis());

      assertThat(uut.isValid()).isFalse();

      verify(lock).extend(maxDuration, minDuration);
    }

    @Test
    @DisplayName("isValid should return false when liveness is null.")
    void isValidReturnsFalseWhenLivenessIsExpired() {
      uut.liveness(null);

      assertThat(uut.isValid()).isFalse();

      verifyNoInteractions(lock);
    }

    @Test
    @DisplayName("isValid should return false attempt to extend lock fails")
    void IsValidReturnsFalseWhenExtendingLockFails() {
      // Set liveness to value smaller than the keepalive interval.
      uut.liveness(
          new AtomicLong(
              System.currentTimeMillis()
                  - Duration.ofSeconds(KEEPALIVE_INTERVAL_SECONDS + 3).toMillis()));
      when(lock.extend(any(Duration.class), any(Duration.class)))
          .thenThrow(IllegalStateException.class);
      final Duration minDuration = Duration.ofSeconds(1L);
      final Duration maxDuration = Duration.ofSeconds(1L);
      when(lockConfiguration.getLockAtLeastFor()).thenReturn(minDuration);
      when(lockConfiguration.getLockAtMostFor()).thenReturn(maxDuration);

      assertThat(uut.isValid()).isFalse();
    }
  }

  @Nested
  class WhenClosing {

    @Test
    @DisplayName("close calls unlock")
    void closeSuccessfully() {
      uut.close();

      verify(lock).unlock();
      assertThat(uut.liveness()).isNull();
    }

    @Test
    @DisplayName("close catches the exception when unlock is unsuccessful")
    void closeCatchesExceptionWhenFailing() {
      doThrow(new IllegalStateException()).when(lock).unlock();

      assertThatCode(() -> uut.close()).doesNotThrowAnyException();

      verify(lock).unlock();
      assertThat(uut.liveness()).isNull();
    }
  }

  @Nested
  class WhenRefreshing {
    private final long keepaliveIntervalPassedMillis = (KEEPALIVE_INTERVAL_SECONDS * 1000) + 500;

    @Test
    @DisplayName("schedules a task to extend the lock periodically")
    void schedulesTask() {
      when(lock.extend(any(Duration.class), any(Duration.class)))
          .thenReturn(Optional.of(mock(SimpleLock.class)));
      final Duration minDuration = Duration.ofSeconds(1L);
      final Duration maxDuration = Duration.ofSeconds(1L);
      when(lockConfiguration.getLockAtLeastFor()).thenReturn(minDuration);
      when(lockConfiguration.getLockAtMostFor()).thenReturn(maxDuration);

      // wait for it
      verify(lock, after(KEEPALIVE_INTERVAL_SECONDS - 1).never()).extend(any(), any());

      // half a second after the keepalive interval passed, we expect the lock to be extended.
      verify(lock, timeout(keepaliveIntervalPassedMillis).times(1))
          .extend(maxDuration, minDuration);
    }

    @Test
    @DisplayName("scheduled task expires lock if extending the lock causes an exception")
    void expiresOnFailure() {
      when(lock.extend(any(Duration.class), any(Duration.class)))
          .thenThrow(new IllegalStateException());
      final Duration minDuration = Duration.ofSeconds(1L);
      final Duration maxDuration = Duration.ofSeconds(1L);
      when(lockConfiguration.getLockAtLeastFor()).thenReturn(minDuration);
      when(lockConfiguration.getLockAtMostFor()).thenReturn(maxDuration);

      Awaitility.await()
          .atMost(Duration.ofMillis(keepaliveIntervalPassedMillis))
          .until(() -> uut.liveness() == null);
    }
  }
}
