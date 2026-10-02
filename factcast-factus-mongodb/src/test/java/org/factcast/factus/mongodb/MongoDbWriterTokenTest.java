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

import com.mongodb.MongoException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoDbWriterTokenTest {

  private static final Duration LOCK_AT_MOST_FOR = Duration.ofSeconds(1);
  private static final Duration LOCK_AT_LEAST_FOR = Duration.ofMillis(10);
  private static final Duration SHORT_KEEPALIVE = Duration.ofMillis(50);
  private static final Duration KEEPALIVE_BEYOND_TEST_RUNTIME = Duration.ofMinutes(5);
  private static final Duration PATIENTLY = Duration.ofSeconds(5);

  private final LockConfiguration lockConfiguration =
      new LockConfiguration(Instant.now(), "key_lock", LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);

  private final AtomicLong clock = new AtomicLong();

  @Mock SimpleLock lock;
  MongoDbWriterToken uut;

  @AfterEach
  void cancelKeepalive() {
    if (uut != null) {
      uut.close();
    }
  }

  private MongoDbWriterToken tokenWith(Duration keepaliveInterval) {
    uut = new MongoDbWriterToken(lock, lockConfiguration, keepaliveInterval, clock::get);
    return uut;
  }

  private void advanceTo(Duration elapsed) {
    clock.set(elapsed.toNanos());
  }

  private SimpleLock stubExtension(SimpleLock extending) {
    SimpleLock extendedLock = mock(SimpleLock.class);
    when(extending.extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR))
        .thenReturn(Optional.of(extendedLock));
    return extendedLock;
  }

  @Nested
  class WhenCheckingValidity {

    @Test
    @DisplayName("isValid returns true while the lease of the last extension still holds")
    void isValidReturnsTrueWhileLeaseHolds() {
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);

      advanceTo(LOCK_AT_MOST_FOR.minusNanos(1));

      assertThat(uut.isValid()).isTrue();
      verifyNoInteractions(lock);
    }

    @Test
    @DisplayName("isValid returns false once the lease of the last extension expired")
    void isValidReturnsFalseWhenLeaseExpired() {
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);

      advanceTo(LOCK_AT_MOST_FOR);

      assertThat(uut.isValid()).isFalse();
    }

    @Test
    @DisplayName("isValid never extends the lock, not even repeatedly on an expired lease")
    void isValidNeverExtendsTheLock() {
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);
      advanceTo(LOCK_AT_MOST_FOR);

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
    @DisplayName("close releases the lock returned by the last extension")
    void closeReleasesTheLockOfTheLastExtension() {
      SimpleLock extendedLock = stubExtension(lock);
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);

      uut.extendLock();
      uut.close();

      verify(extendedLock).unlock();
      verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("close keeps its hands off a lease that aged out")
    void closeDoesNotReleaseAnAgedOutLease() {
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);

      advanceTo(LOCK_AT_MOST_FOR);
      uut.close();

      verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("close keeps its hands off a lease that was lost")
    void closeDoesNotReleaseALostLease() {
      when(lock.extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR)).thenReturn(Optional.empty());
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);

      uut.extendLock();
      uut.close();

      verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("close stops the keepalive")
    void closeStopsTheKeepalive() {
      tokenWith(SHORT_KEEPALIVE);

      uut.close();

      verify(lock, after(SHORT_KEEPALIVE.multipliedBy(10).toMillis()).never()).extend(any(), any());
      verify(lock).unlock();
    }
  }

  @Nested
  class WhenRefreshing {

    @Test
    @DisplayName("schedules a task to extend the lock periodically")
    void schedulesTask() {
      SimpleLock extendedLock = stubExtension(lock);
      when(extendedLock.extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR))
          .thenReturn(Optional.of(extendedLock));
      tokenWith(SHORT_KEEPALIVE);

      verify(lock, timeout(PATIENTLY.toMillis())).extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
      verify(extendedLock, timeout(PATIENTLY.toMillis()).atLeast(2))
          .extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);

      // a SimpleLock may only be extended once, the successor takes over
      verify(lock, times(1)).extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
      assertThat(uut.isValid()).isTrue();
    }

    @Test
    @DisplayName("extends the lock returned by the last extension")
    void extendsTheLockReturnedByTheLastExtension() {
      SimpleLock extendedLock = stubExtension(lock);
      when(extendedLock.extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR))
          .thenReturn(Optional.of(mock(SimpleLock.class)));
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);

      uut.extendLock();
      uut.extendLock();

      verify(lock, times(1)).extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
      verify(extendedLock, times(1)).extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
    }

    @Test
    @DisplayName("a successful extension moves the liveness window forward")
    void movesTheLivenessWindowForwardOnSuccess() {
      stubExtension(lock);
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);

      advanceTo(Duration.ofMillis(300));
      uut.extendLock();

      advanceTo(Duration.ofMillis(1299));
      assertThat(uut.isValid()).isTrue();

      advanceTo(Duration.ofMillis(1300));
      assertThat(uut.isValid()).isFalse();
    }

    @Test
    @DisplayName("the liveness window starts before the extending round trip, not after it")
    void stampsTheLeaseWindowFromBeforeTheExtendCall() {
      when(lock.extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR))
          .thenAnswer(
              invocation -> {
                clock.addAndGet(Duration.ofMillis(200).toNanos());
                return Optional.of(mock(SimpleLock.class));
              });
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);

      advanceTo(Duration.ofMillis(300));
      uut.extendLock();

      assertThat(uut.liveness()).hasValue(Duration.ofMillis(300).toNanos());
    }

    @Test
    @DisplayName("the keepalive invalidates the token if the lock cannot be extended")
    void invalidatesWhenExtendReturnsEmpty() {
      when(lock.extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR)).thenReturn(Optional.empty());
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);

      uut.extendLock();

      assertThat(uut.isValid()).isFalse();
    }

    @Test
    @DisplayName("a lost lease stops the keepalive from hitting the lock again")
    void stopsTheKeepaliveOnceTheLeaseIsLost() {
      when(lock.extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR)).thenReturn(Optional.empty());
      tokenWith(SHORT_KEEPALIVE);

      verify(lock, after(SHORT_KEEPALIVE.multipliedBy(10).toMillis()).times(1))
          .extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
      assertThat(uut.isValid()).isFalse();
    }

    @Test
    @DisplayName("a driver exception is not treated as a lost lease, the next tick retries")
    void survivesATransientFailureAndRetriesOnTheSameLock() {
      when(lock.extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR))
          .thenThrow(new MongoException("connection reset"))
          .thenReturn(Optional.of(mock(SimpleLock.class)));
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);

      advanceTo(Duration.ofMillis(300));
      uut.extendLock();

      assertThat(uut.isValid()).isTrue();

      advanceTo(Duration.ofMillis(600));
      uut.extendLock();

      advanceTo(Duration.ofMillis(1599));
      assertThat(uut.isValid()).isTrue();
      verify(lock, times(2)).extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
    }

    @Test
    @DisplayName("a driver exception does not kill the Timer thread running the keepalive")
    void keepaliveSurvivesADriverExceptionAndKeepsTicking() {
      when(lock.extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR))
          .thenThrow(new MongoException("connection reset"));
      tokenWith(SHORT_KEEPALIVE);

      verify(lock, timeout(PATIENTLY.toMillis()).atLeast(3))
          .extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
      assertThat(uut.isValid()).isTrue();
    }

    @Test
    @DisplayName("the keepalive gives up once the lease ran out while extending kept failing")
    void givesUpOnceTheLeaseRanOutWhileExtendingKeptFailing() {
      when(lock.extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR))
          .thenThrow(new MongoException("connection reset"));
      tokenWith(KEEPALIVE_BEYOND_TEST_RUNTIME);

      uut.extendLock();
      advanceTo(LOCK_AT_MOST_FOR);
      uut.extendLock();

      assertThat(uut.isValid()).isFalse();
      verify(lock, times(1)).extend(LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
    }
  }
}
