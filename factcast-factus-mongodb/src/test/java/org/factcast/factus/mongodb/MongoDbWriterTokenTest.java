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
import java.time.Instant;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoDbWriterTokenTest {

  private static final Duration LOCK_AT_MOST_FOR = Duration.ofSeconds(1);
  private static final Duration LOCK_AT_LEAST_FOR = Duration.ofMillis(10);
  private static final Duration SHORT_KEEPALIVE = Duration.ofMillis(100);
  private static final Duration KEEPALIVE_BEYOND_TEST_RUNTIME = Duration.ofMinutes(5);
  private static final Duration PATIENTLY = Duration.ofSeconds(5);

  private final LockConfiguration lockConfiguration =
      new LockConfiguration(Instant.now(), "key_lock", LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);

  @Mock SimpleLock lock;
  MongoDbWriterToken uut;

  @AfterEach
  void cancelKeepalive() {
    if (uut != null) {
      uut.close();
    }
  }

  private MongoDbWriterToken tokenWith(Duration keepaliveInterval) {
    uut = new MongoDbWriterToken(lock, lockConfiguration, keepaliveInterval);
    return uut;
  }

  private void expire() {
    uut.liveness().set(System.nanoTime() - LOCK_AT_MOST_FOR.toNanos());
  }

  private SimpleLock stubSuccessfulExtension() {
    SimpleLock extendedLock = mock(SimpleLock.class);
    lenient()
        .when(lock.extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR))
        .thenReturn(Optional.of(extendedLock));
    lenient()
        .when(extendedLock.extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR))
        .thenReturn(Optional.of(extendedLock));
    return extendedLock;
  }

  @Nested
  class WhenCheckingValidity {

    @Test
    @DisplayName("isValid returns true while the lease of the last extension still holds")
    void isValidReturnsTrueWhileLeaseHolds() {
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);

      assertThat(uut.isValid()).isTrue();

      verifyNoInteractions(lock);
    }

    @Test
    @DisplayName("isValid returns false once the lease of the last extension expired")
    void isValidReturnsFalseWhenLeaseExpired() {
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);
      expire();

      assertThat(uut.isValid()).isFalse();
    }

    @Test
    @DisplayName("isValid never extends the lock, not even repeatedly on an expired lease")
    void isValidNeverExtendsTheLock() {
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);
      expire();

      for (int i = 0; i < 5; i++) {
        assertThat(uut.isValid()).isFalse();
      }

      verify(lock, never()).extend(any(), any());
    }

    @Test
    @DisplayName("isValid returns false after close")
    void isValidReturnsFalseAfterClose() {
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);
      uut.close();

      assertThat(uut.isValid()).isFalse();
    }
  }

  @Nested
  class WhenClosing {

    @Test
    @DisplayName("close calls unlock")
    void closeSuccessfully() {
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);

      uut.close();

      verify(lock).unlock();
    }

    @Test
    @DisplayName("close catches the exception when unlock is unsuccessful")
    void closeCatchesExceptionWhenFailing() {
      doThrow(new IllegalStateException()).when(lock).unlock();
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);

      assertThatCode(() -> uut.close()).doesNotThrowAnyException();

      verify(lock).unlock();
      assertThat(uut.isValid()).isFalse();
    }

    @Test
    @DisplayName("close is idempotent")
    void closeIsIdempotent() {
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);

      uut.close();
      uut.close();
      uut.close();

      verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("close stops the keepalive")
    void closeStopsTheKeepalive() {
      tokenWith(SHORT_KEEPALIVE);

      uut.close();

      verify(lock, after(SHORT_KEEPALIVE.multipliedBy(5).toMillis()).never()).extend(any(), any());
      verify(lock).unlock();
    }
  }

  @Nested
  class WhenRefreshing {

    @Test
    @DisplayName("schedules a task to extend the lock periodically")
    void schedulesTask() {
      SimpleLock extendedLock = stubSuccessfulExtension();
      tokenWith(SHORT_KEEPALIVE);

      verify(lock, after(SHORT_KEEPALIVE.dividedBy(2).toMillis()).never()).extend(any(), any());
      verify(lock, timeout(PATIENTLY.toMillis())).extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
      verify(extendedLock, timeout(PATIENTLY.toMillis()).atLeast(2))
          .extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);

      // a SimpleLock may only be extended once, the successor takes over
      verify(lock, times(1)).extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
      assertThat(uut.isValid()).isTrue();
    }

    @Test
    @DisplayName("keepalive refreshes the liveness, so isValid recovers without extending itself")
    void keepaliveRefreshesLiveness() {
      stubSuccessfulExtension();
      tokenWith(SHORT_KEEPALIVE);
      expire();

      assertThat(uut.isValid()).isFalse();

      Awaitility.await().atMost(PATIENTLY).until(() -> uut.isValid());
      verify(lock, times(1)).extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
    }

    @Test
    @DisplayName("scheduled task invalidates the token if the lock cannot be extended")
    void expiresWhenExtendReturnsEmpty() {
      when(lock.extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR)).thenReturn(Optional.empty());
      tokenWith(SHORT_KEEPALIVE);

      Awaitility.await().atMost(PATIENTLY).until(() -> !uut.isValid());
    }

    @Test
    @DisplayName("scheduled task invalidates the token if extending the lock causes an exception")
    void expiresOnFailure() {
      when(lock.extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR)).thenThrow(new IllegalStateException());
      tokenWith(SHORT_KEEPALIVE);

      Awaitility.await().atMost(PATIENTLY).until(() -> !uut.isValid());

      // after giving up, the keepalive stops hitting the lock
      verify(lock, after(SHORT_KEEPALIVE.multipliedBy(5).toMillis()).times(1))
          .extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
    }
  }
}
