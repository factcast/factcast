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
package org.factcast.store.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.lock.FactTableWriteLock;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(
    classes = {
      PgTestConfiguration.class,
      ConditionalPublicationOrderingIntegrationTest.LockConfiguration.class
    })
@TestPropertySource(properties = "factcast.store.publish-batch.enabled=true")
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@IntegrationTest
class ConditionalPublicationOrderingIntegrationTest {

  @Autowired FactStore store;
  @Autowired ControlledWriteLock lock;
  @Autowired StoreConfigurationProperties properties;

  @BeforeEach
  void batchingIsEnabled() {
    assertThat(properties.getPublishBatch().isEnabled()).isTrue();
  }

  @Test
  void conditionalPublicationCanOvertakeAnUncommittedUnconditionalPublication() throws Exception {
    var token = store.stateFor(List.of(FactSpec.ns("ordering")));
    Fact unconditional = Fact.builder().ns("ordering").buildWithoutPayload();
    Fact conditional = Fact.builder().ns("ordering").buildWithoutPayload();
    var gate = lock.pauseNextSharedLock(false);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> pending = executor.submit(() -> store.publish(List.of(unconditional)));
      try {
        assertThat(gate.paused().await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(pending.isDone()).isFalse();

        // The queued writer has not acquired the shared lock, so the conditional writer may
        // commit first. Invocation order imposes no ordering on these overlapping calls.
        Future<Boolean> published =
            executor.submit(
                () -> store.publishIfUnchanged(List.of(conditional), Optional.of(token)));
        assertThat(published.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(pending.isDone()).isFalse();
        assertThat(store.fetchById(conditional.id())).isPresent();
        assertThat(store.fetchById(unconditional.id())).isEmpty();
      } finally {
        gate.release().countDown();
        pending.get(10, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(store.serialOf(conditional.id()).orElseThrow())
        .isLessThan(store.serialOf(unconditional.id()).orElseThrow());
  }

  @Test
  void completedUnconditionalPublicationInvalidatesTheToken() {
    var token = store.stateFor(List.of(FactSpec.ns("ordering")));
    Fact unconditional = Fact.builder().ns("ordering").buildWithoutPayload();
    Fact conditional = Fact.builder().ns("ordering").buildWithoutPayload();

    // Successful return from the batched publish implies that its transaction has committed.
    store.publish(List.of(unconditional));

    assertThat(store.publishIfUnchanged(List.of(conditional), Optional.of(token))).isFalse();
    assertThat(store.fetchById(unconditional.id())).isPresent();
    assertThat(store.fetchById(conditional.id())).isEmpty();
  }

  @Test
  void writerHoldingTheSharedLockMustCommitBeforeTokenValidation() throws Exception {
    var token = store.stateFor(List.of(FactSpec.ns("ordering")));
    Fact unconditional = Fact.builder().ns("ordering").buildWithoutPayload();
    Fact conditional = Fact.builder().ns("ordering").buildWithoutPayload();
    var gate = lock.pauseNextSharedLock(true);
    var exclusiveAttempted = new CountDownLatch(1);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> pending = executor.submit(() -> store.publish(List.of(unconditional)));
      try {
        assertThat(gate.paused().await(5, TimeUnit.SECONDS)).isTrue();
        Future<Boolean> published =
            executor.submit(
                () -> {
                  lock.observeExclusiveLockOnCurrentThread(exclusiveAttempted);
                  return store.publishIfUnchanged(List.of(conditional), Optional.of(token));
                });

        // The real PostgreSQL shared lock prevents validation until the writer commits,
        // even though its publish call has not returned yet.
        assertThat(exclusiveAttempted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(pending.isDone()).isFalse();
        assertThat(published.isDone()).isFalse();
        gate.release().countDown();
        pending.get(10, TimeUnit.SECONDS);

        assertThat(published.get(5, TimeUnit.SECONDS)).isFalse();
        assertThat(store.fetchById(unconditional.id())).isPresent();
        assertThat(store.fetchById(conditional.id())).isEmpty();
      } finally {
        gate.release().countDown();
        pending.get(10, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class LockConfiguration {
    @Bean
    @Primary
    ControlledWriteLock controlledWriteLock(
        @Qualifier("factTableWriteLock") FactTableWriteLock delegate) {
      return new ControlledWriteLock(delegate);
    }
  }

  // Only scheduling is controlled here; all database locking is performed by the real bean.
  static class ControlledWriteLock implements FactTableWriteLock {
    private final FactTableWriteLock delegate;
    private final AtomicReference<Gate> nextSharedLock = new AtomicReference<>();
    private final ThreadLocal<CountDownLatch> nextExclusiveLock = new ThreadLocal<>();

    ControlledWriteLock(FactTableWriteLock delegate) {
      this.delegate = delegate;
    }

    Gate pauseNextSharedLock(boolean acquireBeforePausing) {
      var gate = new Gate(acquireBeforePausing, new CountDownLatch(1), new CountDownLatch(1));
      nextSharedLock.set(gate);
      return gate;
    }

    void observeExclusiveLockOnCurrentThread(CountDownLatch attempted) {
      nextExclusiveLock.set(attempted);
    }

    @Override
    public void acquireSharedTXLock() {
      Gate gate = nextSharedLock.getAndSet(null);
      if (gate == null) {
        delegate.acquireSharedTXLock();
        return;
      }
      if (gate.acquireBeforePausing()) delegate.acquireSharedTXLock();
      gate.paused().countDown();
      try {
        if (!gate.release().await(15, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Publication was not released by the test");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
      if (!gate.acquireBeforePausing()) delegate.acquireSharedTXLock();
    }

    @Override
    public void acquireExclusiveTXLock() {
      CountDownLatch attempted = nextExclusiveLock.get();
      if (attempted != null) {
        nextExclusiveLock.remove();
        attempted.countDown();
      }
      delegate.acquireExclusiveTXLock();
    }

    @Override
    public boolean isExclusiveTXLockHeld() {
      return delegate.isExclusiveTXLockHeld();
    }

    record Gate(boolean acquireBeforePausing, CountDownLatch paused, CountDownLatch release) {}
  }
}
