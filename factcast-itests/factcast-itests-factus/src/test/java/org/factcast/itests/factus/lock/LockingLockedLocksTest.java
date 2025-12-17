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
package org.factcast.itests.factus.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Factus;
import org.factcast.factus.Handler;
import org.factcast.factus.redis.AbstractRedisManagedProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.RedissonProjectionConfiguration;
import org.factcast.itests.factus.event.versioned.v1.UserCreated;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@SpringBootTest
@ContextConfiguration(
    classes = {TestFactusApplication.class, RedissonProjectionConfiguration.class})
@Slf4j
class LockingLockedLocksTest extends AbstractFactCastIntegrationTest {
  @Autowired Factus factus;
  @Autowired RedissonClient redisson;

  @Nested
  class WhenTriggeringParallelUpdatesOnRedisManagedProjection {

    @Test
    @SneakyThrows
    void theyDoNotRunInParallel() {
      final var p = new ParallelUpdatesAwareProjection(redisson);
      factus.publish(new UserCreated(UUID.randomUUID(), "user"));

      log.info("triggering projection update in a background thread");
      CompletableFuture.runAsync(() -> factus.update(p));

      log.info("waiting for before latch to be counted down");
      p.beforeSleep.await();

      log.info("triggering another update on same projection in the main test thread");
      assertThatCode(() -> factus.update(p)).doesNotThrowAnyException();
    }
  }

  @Nested
  class WhenLockingLockUnderCertainName {
    static final String LOCKNAME = "foo";
    // making sure we definitely have two distinct threads at hand
    final ExecutorService executor1 = Executors.newFixedThreadPool(1);
    final ExecutorService executor2 = Executors.newFixedThreadPool(1);

    @Nested
    class WhenLockingRLock {

      @Test
      void lockingAgainAsRLockWaitsForever() {
        lock1stLockAndMakeSureLocking2ndLockWaitsForever(
            () -> redisson.getLock(LOCKNAME), () -> redisson.getLock(LOCKNAME));
      }

      @Test
      void lockingAgainAsFencedLockWaitsForever() {
        lock1stLockAndMakeSureLocking2ndLockWaitsForever(
            () -> redisson.getLock(LOCKNAME), () -> redisson.getFencedLock(LOCKNAME));
      }
    }

    @Nested
    class WhenLockingFencedLock {
      @Test
      void lockingAgainAsFencedLockWaitsForever() {
        lock1stLockAndMakeSureLocking2ndLockWaitsForever(
            () -> redisson.getFencedLock(LOCKNAME), () -> redisson.getFencedLock(LOCKNAME));
      }
    }

    @SneakyThrows
    void lock1stLockAndMakeSureLocking2ndLockWaitsForever(
        Supplier<RLock> firstLockSupplier, Supplier<RLock> secondLockSupplier) {

      final var lock = firstLockSupplier.get();
      assertThat(lock.isLocked()).isFalse();

      final var afterLock = new CountDownLatch(1);

      CompletableFuture.runAsync(
          () -> {
            lock.lock();
            afterLock.countDown();
          },
          executor1);

      afterLock.await();
      log.info("successfully acquired {}", LockLog.from(lock));

      assertThat(lock.isLocked()).isTrue();

      final var anotherLock = secondLockSupplier.get();
      assertThat(anotherLock.isLocked()).isTrue();

      log.info(
          "trying to acquire lock of type {}, this is expected to time out...",
          LockLog.from(anotherLock));
      final var secondLockAttempt = CompletableFuture.runAsync(anotherLock::lock, executor2);

      // well "forever"...
      Awaitility.await()
          .during(10, TimeUnit.SECONDS)
          .atMost(11, TimeUnit.SECONDS)
          .until(() -> !secondLockAttempt.isDone());

      log.info("it did time out, as expected");
    }
  }

  @ProjectionMetaData(revision = 1)
  static class ParallelUpdatesAwareProjection extends AbstractRedisManagedProjection {

    final CountDownLatch beforeSleep = new CountDownLatch(1);
    final CountDownLatch afterSleep = new CountDownLatch(1);
    final AtomicBoolean running = new AtomicBoolean(false);

    protected ParallelUpdatesAwareProjection(@NonNull RedissonClient redisson) {
      super(redisson);
    }

    @Handler
    @SneakyThrows
    void apply(UserCreated ignored) {
      // switch 'running' to true, only during 1st projection update
      if (running.compareAndSet(false, true)) {
        log.info("counting down 'before' latch");
        beforeSleep.countDown();
        log.info("sleeping for 10 seconds");
        Thread.sleep(10_000);
        log.info("counting down 'after' latch");
        afterSleep.countDown();
      } else if (afterSleep.getCount() > 0) {
        throw new AssertionFailedError("Second projection update should not run in parallel");
      }
    }
  }

  record LockLog(String threadName, String lockType, String lockName, boolean isLocked) {
    static LockLog from(@NonNull RLock lock) {
      return new LockLog(
          Thread.currentThread().getName(),
          lock.getClass().getSimpleName(),
          lock.getName(),
          lock.isLocked());
    }
  }
}
