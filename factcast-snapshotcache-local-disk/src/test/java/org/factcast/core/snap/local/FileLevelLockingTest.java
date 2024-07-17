/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.core.snap.local;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("JoinAssertThatStatements")
@ExtendWith(MockitoExtension.class)
class FileLevelLockingTest {

  private static final long SLEEP_TIME = 500;
  @InjectMocks private FileLevelLocking underTest;

  @Nested
  class WhenWithingReadLockOn {

    @Test
    void createsLock() {
      File f = new File("foo");
      StampedLock lock = underTest.fileSystemLevelLocks().getUnchecked(f.toPath());
      Assertions.assertThat(lock).isNotNull();
    }

    @Test
    void flipsReadRun() {
      File f = new File("foo");
      StampedLock lock = underTest.fileSystemLevelLocks().getUnchecked(f.toPath());
      Assertions.assertThat(lock.isReadLocked()).isFalse();

      underTest.withReadLockOn(
          f,
          () -> {
            Assertions.assertThat(lock.isReadLocked()).isTrue();
          });

      Assertions.assertThat(lock.isReadLocked()).isFalse();
    }

    @Test
    void flipsReadSupply() {
      File f = new File("foo");
      StampedLock lock = underTest.fileSystemLevelLocks().getUnchecked(f.toPath());
      Assertions.assertThat(lock.isReadLocked()).isFalse();

      underTest.withReadLockOn(
          f,
          () -> {
            Assertions.assertThat(lock.isReadLocked()).isTrue();
            return null;
          });

      Assertions.assertThat(lock.isReadLocked()).isFalse();
    }

    @SneakyThrows
    @Test
    void flipsWrite() {
      File f = new File("foo");
      StampedLock lock = underTest.fileSystemLevelLocks().getUnchecked(f.toPath());
      Assertions.assertThat(lock.isWriteLocked()).isFalse();

      underTest
          .withWriteLockOnAsync(
              f,
              () -> {
                Assertions.assertThat(lock.isWriteLocked()).isTrue();
              })
          .get();

      Assertions.assertThat(lock.isWriteLocked()).isFalse();
    }
  }

  @Nested
  class WhenHoldingReadLock {
    @SneakyThrows
    @Test
    void blocksWrite() {
      CountDownLatch cdl = new CountDownLatch(1);
      File f = new File("foo");
      StampedLock lock = underTest.fileSystemLevelLocks().getUnchecked(f.toPath());
      Assertions.assertThat(lock.isReadLocked()).isFalse();
      // lock for read
      underTest.withReadLockOnAsync(
          f,
          () -> {
            try {
              Thread.sleep(SLEEP_TIME);
              cdl.countDown();
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          });
      Assertions.assertThat(cdl.getCount()).isNotZero();

      underTest
          .withWriteLockOnAsync(
              f,
              () -> {
                // will be blocked, until
                Assertions.assertThat(cdl.getCount()).isZero();
              })
          .get();
    }
  }

  @Nested
  class WhenHoldingWriteLock {
    @SneakyThrows
    @Test
    void blocksRead() {
      CountDownLatch cdl = new CountDownLatch(1);
      File f = new File("foo");
      StampedLock lock = underTest.fileSystemLevelLocks().getUnchecked(f.toPath());
      Assertions.assertThat(lock.isWriteLocked()).isFalse();
      // lock for read
      underTest.withWriteLockOnAsync(
          f,
          () -> {
            try {
              Thread.sleep(SLEEP_TIME);
              cdl.countDown();
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          });
      Assertions.assertThat(cdl.getCount()).isNotZero();

      underTest
          .withReadLockOnAsync(
              f,
              () -> {
                // will be blocked, until
                Assertions.assertThat(cdl.getCount()).isZero();
              })
          .get();
    }
  }

  @Nested
  class WhenSupplyAndUnlock {
    @Mock Supplier<String> s;
    @Mock Lock l;

    @Test
    void happyPath() {
      String narf = "narf";
      Mockito.when(s.get()).thenReturn(narf);
      Assertions.assertThat(FileLevelLocking.supplyAndUnlock(s, l)).isEqualTo(narf);
      Mockito.verify(l).unlock();
    }

    @Test
    void exceptionalStillUnlocks() {
      String narf = "narf";
      Mockito.when(s.get()).thenThrow(IllegalStateException.class);
      assertThatThrownBy(() -> FileLevelLocking.supplyAndUnlock(s, l))
          .isInstanceOf(IllegalStateException.class);
      Mockito.verify(l).unlock();
    }
  }

  @Nested
  class WhenRunningAndUnlock {
    @Mock Runnable r;
    @Mock Lock l;

    @Test
    void happyPath() {
      FileLevelLocking.runAndUnlock(r, l);
      Mockito.verify(r).run();
      Mockito.verify(l).unlock();
    }
  }

  @Nested
  class WhenLockingOn {
    @Mock Supplier<String> s;
    @Mock Lock l;
    File f = new File("x");

    @Test
    void happyPath() {
      Lock lock = underTest.lockOn(FileLevelLocking.Mode.READ, f);
      Assertions.assertThat(lock).isNotNull();
      StampedLock stamped = underTest.fileSystemLevelLocks().getUnchecked(f.toPath());
      Assertions.assertThat(stamped.isReadLocked()).isTrue();
      Assertions.assertThat(stamped.isWriteLocked()).isFalse();

      f = new File("y");
      lock = underTest.lockOn(FileLevelLocking.Mode.WRITE, f);
      Assertions.assertThat(lock).isNotNull();
      stamped = underTest.fileSystemLevelLocks().getUnchecked(f.toPath());
      Assertions.assertThat(stamped.isReadLocked()).isFalse();
      Assertions.assertThat(stamped.isWriteLocked()).isTrue();
    }
  }
}
