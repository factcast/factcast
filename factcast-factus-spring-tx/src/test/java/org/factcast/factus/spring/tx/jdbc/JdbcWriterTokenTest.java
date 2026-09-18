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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.TransientDataAccessResourceException;

@ExtendWith(MockitoExtension.class)
class JdbcWriterTokenTest {

  private static final String LOCK_NAME = "org.example.MyProjection_1_lock";
  private static final Duration LEASE = Duration.ofSeconds(60);

  @Mock private SimpleLock lock;
  @Mock private ScheduledExecutorService scheduler;
  @Mock private ScheduledFuture<Object> keepalive;

  private final AtomicLong clock = new AtomicLong();
  private Runnable keepaliveTask;

  @BeforeEach
  void setUp() {
    when(scheduler.scheduleAtFixedRate(any(), anyLong(), anyLong(), any()))
        .thenAnswer(
            invocation -> {
              keepaliveTask = invocation.getArgument(0);
              return keepalive;
            });
  }

  private JdbcWriterToken tokenFor(SimpleLock initialLock) {
    return new JdbcWriterToken(
        initialLock, LOCK_NAME, LEASE, Duration.ZERO, clock.get(), scheduler, clock::get);
  }

  private void advanceTo(Duration elapsed) {
    clock.set(elapsed.toNanos());
  }

  @Nested
  class WhenCreated {

    @Test
    void schedulesOneKeepaliveEveryThirdOfTheLease() {
      tokenFor(lock);

      verify(scheduler)
          .scheduleAtFixedRate(any(), eq(20_000L), eq(20_000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void isValidWithoutTouchingTheLock() {
      JdbcWriterToken uut = tokenFor(lock);

      assertThat(uut.isValid()).isTrue();
      assertThat(uut.isValid()).isTrue();

      verifyNoInteractions(lock);
    }
  }

  @Nested
  class WhenTheLeaseAges {

    @Test
    void staysValidWhileTheLeaseHolds() {
      JdbcWriterToken uut = tokenFor(lock);

      advanceTo(LEASE.minusSeconds(1));

      assertThat(uut.isValid()).isTrue();
      verifyNoInteractions(lock);
    }

    @Test
    void turnsInvalidOnceTheLeaseIsUp() {
      JdbcWriterToken uut = tokenFor(lock);

      advanceTo(LEASE);

      assertThat(uut.isValid()).isFalse();
      verifyNoInteractions(lock);
    }
  }

  @Nested
  class WhenTheKeepaliveRuns {

    @Test
    void movesTheLivenessWindowForwardOnSuccess() {
      when(lock.extend(LEASE, Duration.ZERO)).thenReturn(Optional.of(mock(SimpleLock.class)));
      JdbcWriterToken uut = tokenFor(lock);

      advanceTo(Duration.ofSeconds(20));
      keepaliveTask.run();

      advanceTo(Duration.ofSeconds(79));
      assertThat(uut.isValid()).isTrue();

      advanceTo(Duration.ofSeconds(80));
      assertThat(uut.isValid()).isFalse();
    }

    @Test
    void extendsTheLockReturnedByTheLastExtension() {
      SimpleLock extended = mock(SimpleLock.class);
      when(lock.extend(LEASE, Duration.ZERO)).thenReturn(Optional.of(extended));
      when(extended.extend(LEASE, Duration.ZERO)).thenReturn(Optional.of(mock(SimpleLock.class)));
      tokenFor(lock);

      keepaliveTask.run();
      keepaliveTask.run();

      verify(lock).extend(LEASE, Duration.ZERO);
      verify(extended).extend(LEASE, Duration.ZERO);
    }

    @Test
    void invalidatesAndCancelsItselfWhenTheLeaseIsLost() {
      when(lock.extend(LEASE, Duration.ZERO)).thenReturn(Optional.empty());
      JdbcWriterToken uut = tokenFor(lock);

      keepaliveTask.run();

      assertThat(uut.isValid()).isFalse();
      verify(keepalive).cancel(false);
    }

    @Test
    void stampsTheLeaseWindowFromBeforeTheExtendCall() {
      when(lock.extend(LEASE, Duration.ZERO))
          .thenAnswer(
              invocation -> {
                clock.addAndGet(Duration.ofSeconds(10).toNanos());
                return Optional.of(mock(SimpleLock.class));
              });
      JdbcWriterToken uut = tokenFor(lock);

      advanceTo(Duration.ofSeconds(20));
      keepaliveTask.run();

      advanceTo(Duration.ofSeconds(79));
      assertThat(uut.isValid()).isTrue();

      // the lease the database granted started at 20s, not when the round trip returned at 30s
      advanceTo(Duration.ofSeconds(80));
      assertThat(uut.isValid()).isFalse();
    }

    @Test
    void survivesATransientFailureAndRetriesOnTheSameLock() {
      when(lock.extend(LEASE, Duration.ZERO))
          .thenThrow(new TransientDataAccessResourceException("connection reset"))
          .thenReturn(Optional.of(mock(SimpleLock.class)));
      JdbcWriterToken uut = tokenFor(lock);

      advanceTo(Duration.ofSeconds(20));
      keepaliveTask.run();

      assertThat(uut.isValid()).isTrue();
      verify(keepalive, never()).cancel(anyBoolean());

      advanceTo(Duration.ofSeconds(40));
      keepaliveTask.run();

      advanceTo(Duration.ofSeconds(99));
      assertThat(uut.isValid()).isTrue();
      verify(lock, times(2)).extend(LEASE, Duration.ZERO);
    }

    @Test
    void givesUpOnceTheLeaseRanOutWhileExtendingKeptFailing() {
      when(lock.extend(LEASE, Duration.ZERO))
          .thenThrow(new TransientDataAccessResourceException("connection reset"));
      JdbcWriterToken uut = tokenFor(lock);

      keepaliveTask.run();
      advanceTo(LEASE);
      keepaliveTask.run();

      assertThat(uut.isValid()).isFalse();
      verify(lock, times(1)).extend(LEASE, Duration.ZERO);
      verify(keepalive).cancel(false);
    }
  }

  @Nested
  class WhenClosing {

    @Test
    void cancelsKeepaliveUnlocksAndInvalidates() {
      JdbcWriterToken uut = tokenFor(lock);

      uut.close();

      verify(keepalive).cancel(false);
      verify(lock).unlock();
      assertThat(uut.isValid()).isFalse();
    }

    @Test
    void isIdempotent() {
      JdbcWriterToken uut = tokenFor(lock);

      uut.close();
      uut.close();
      uut.close();

      verify(keepalive).cancel(false);
      verify(lock).unlock();
    }

    @Test
    void toleratesAnAlreadyReleasedLock() {
      JdbcWriterToken uut = tokenFor(lock);
      doThrow(new IllegalStateException("Lock is already unlocked")).when(lock).unlock();

      assertThatCode(uut::close).doesNotThrowAnyException();
      assertThat(uut.isValid()).isFalse();
    }

    @Test
    void stopsTheKeepaliveFromExtending() {
      JdbcWriterToken uut = tokenFor(lock);

      uut.close();
      keepaliveTask.run();

      verify(lock, never()).extend(any(), any());
    }

    @Test
    void releasesTheLockReturnedByTheLastExtension() {
      SimpleLock extended = mock(SimpleLock.class);
      when(lock.extend(LEASE, Duration.ZERO)).thenReturn(Optional.of(extended));
      JdbcWriterToken uut = tokenFor(lock);

      keepaliveTask.run();
      uut.close();

      verify(extended).unlock();
      verify(lock, never()).unlock();
    }

    @Test
    void keepsItsHandsOffALeaseThatAgedOut() {
      JdbcWriterToken uut = tokenFor(lock);

      advanceTo(LEASE);
      uut.close();

      verify(keepalive).cancel(false);
      verify(lock, never()).unlock();
    }

    @Test
    void keepsItsHandsOffALeaseThatWasLost() {
      when(lock.extend(LEASE, Duration.ZERO)).thenReturn(Optional.empty());
      JdbcWriterToken uut = tokenFor(lock);

      keepaliveTask.run();
      uut.close();

      verify(lock, never()).unlock();
    }
  }
}
