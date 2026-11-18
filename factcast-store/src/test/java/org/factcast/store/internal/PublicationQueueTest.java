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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.Assertions;
import org.factcast.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicationQueueTest {

  @Mock private PgFactStore pgFactStore;

  @Mock private Fact fact;

  @Mock private PgMetrics metrics;

  private PublicationQueue underTest;

  @BeforeEach
  void setUp() {
    underTest = new PublicationQueue(pgFactStore, metrics, 500, 20);
  }

  @Test
  void measuresBacklogWaitingWithoutCountingActiveFlushesOrEmptyQueueTime() throws Exception {
    var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    var clock = new AtomicLong(100);
    var scheduler = mock(ScheduledExecutorService.class);
    underTest =
        new PublicationQueue(pgFactStore, new PgMetrics(registry), 1, 0, scheduler, clock::get);
    var first = underTest.addAndFlush(List.of(fact, fact));
    clock.set(110);
    var second = underTest.addAndFlush(List.of(fact));
    doAnswer(
            inv -> {
              clock.addAndGet(100);
              return null;
            })
        .when(pgFactStore)
        .doPublish(anyList());

    clock.set(120);
    underTest.flush(Long.MAX_VALUE); // 20ns idle, then 100ns active work; one entry remains.
    clock.set(230);
    underTest.flush(Long.MAX_VALUE); // Only the 10ns since finishing the previous flush is idle.
    clock.set(1000);
    underTest.flush(Long.MAX_VALUE); // Empty flush must not produce a sample.
    var third = underTest.addAndFlush(List.of(fact));
    underTest.flush(Long.MAX_VALUE); // Include a zero idle sample, excluding the empty interval.
    first.get();
    second.get();
    third.get();

    var idle =
        registry
            .get(StoreMetrics.DURATION_METRIC_NAME)
            .tag("operation", "publicationBacklogIdle")
            .timer();
    assertThat(idle.count()).isEqualTo(3);
    assertThat(idle.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(30);
    var wait =
        registry
            .get(StoreMetrics.DURATION_METRIC_NAME)
            .tag("operation", "publicationQueueWait")
            .timer();
    assertThat(wait.count()).isEqualTo(3);
    assertThat(wait.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(140);
    var entries =
        registry
            .get(StoreMetrics.METER_METRIC_NAME)
            .tag("operation", "publicationBatchEntries")
            .summary();
    assertThat(entries.count()).isEqualTo(3);
    assertThat(entries.totalAmount()).isEqualTo(3);
    assertThat(
            registry
                .get(StoreMetrics.METER_METRIC_NAME)
                .tag("operation", "publicationBatchFacts")
                .summary()
                .totalAmount())
        .isEqualTo(4);
    assertThat(
            registry
                .get(StoreMetrics.METER_METRIC_NAME)
                .tag("operation", "queuedFactsCommitted")
                .counter()
                .count())
        .isEqualTo(4);
  }

  @Test
  void countsOnlyCommittedPublicationsAfterBatchFallback() throws Exception {
    var scheduler = mock(ScheduledExecutorService.class);
    underTest = new PublicationQueue(pgFactStore, metrics, 500, 0, scheduler);
    Fact duplicate = mock(Fact.class);
    doAnswer(
            inv -> {
              List<Fact> facts = inv.getArgument(0);
              if (facts.contains(duplicate)) throw new DuplicateFactException("duplicate");
              return null;
            })
        .when(pgFactStore)
        .doPublish(anyList());
    var good = underTest.addAndFlush(List.of(fact, fact));
    var bad = underTest.addAndFlush(List.of(duplicate));

    underTest.flush(Long.MAX_VALUE);
    good.get();
    assertThrows(ExecutionException.class, bad::get);
    verify(metrics).recordValue(StoreMetrics.VALUE.PUBLICATION_BATCH_ENTRIES, 2);
    verify(metrics).recordValue(StoreMetrics.VALUE.PUBLICATION_BATCH_FACTS, 3);
    verify(metrics).increment(StoreMetrics.EVENT.PUBLICATION_BATCH_FALLBACK, 1);
    verify(metrics).increment(StoreMetrics.EVENT.QUEUED_PUBLICATIONS_COMMITTED, 1);
    verify(metrics).increment(StoreMetrics.EVENT.QUEUED_FACTS_COMMITTED, 2);
    verify(metrics, never()).increment(StoreMetrics.EVENT.QUEUED_FACTS_COMMITTED, 1);
  }

  @Test
  void waitsTwentyMillisecondsBeforeFlushingAnIsolatedPublication() throws Exception {
    AtomicLong publishingStarted = new AtomicLong();
    doAnswer(
            invocation -> {
              publishingStarted.set(System.nanoTime());
              return null;
            })
        .when(pgFactStore)
        .doPublish(anyList());

    long enqueued = System.nanoTime();
    underTest.addAndFlush(List.of(fact)).get(5, TimeUnit.SECONDS);

    assertThat(publishingStarted.get() - enqueued)
        .isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(20));
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 20, 50})
  void publicationsQueuedBeforeTheFirstDeadlineShareOneFlush(long delayMillis) throws Exception {
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    underTest = new PublicationQueue(pgFactStore, metrics, 500, delayMillis, scheduler);
    Fact secondFact = mock(Fact.class);
    Fact thirdFact = mock(Fact.class);

    Future<Void> first = underTest.addAndFlush(List.of(fact));
    Future<Void> second = underTest.addAndFlush(List.of(secondFact));
    Future<Void> third = underTest.addAndFlush(List.of(thirdFact));

    assertThat(first.isDone()).isFalse();
    assertThat(second.isDone()).isFalse();
    assertThat(third.isDone()).isFalse();
    verifyNoInteractions(pgFactStore);
    ArgumentCaptor<Runnable> scheduled = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler, times(3))
        .schedule(scheduled.capture(), eq(delayMillis), eq(TimeUnit.MILLISECONDS));

    // Advancing to the first deadline is enough; later arrivals never reset that deadline.
    scheduled.getAllValues().get(0).run();
    first.get(1, TimeUnit.SECONDS);
    second.get(1, TimeUnit.SECONDS);
    third.get(1, TimeUnit.SECONDS);
    scheduled.getAllValues().get(1).run();
    scheduled.getAllValues().get(2).run();

    verify(pgFactStore).doPublish(List.of(fact, secondFact, thirdFact));
    verifyNoMoreInteractions(pgFactStore);
  }

  @Test
  void entriesBeyondTheBatchLimitStillFlushAtTheirScheduledDeadline() throws Exception {
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    underTest = new PublicationQueue(pgFactStore, metrics, 2, 20, scheduler);
    Fact secondFact = mock(Fact.class);
    Fact thirdFact = mock(Fact.class);
    Future<Void> first = underTest.addAndFlush(List.of(fact));
    Future<Void> second = underTest.addAndFlush(List.of(secondFact));
    Future<Void> third = underTest.addAndFlush(List.of(thirdFact));
    ArgumentCaptor<Runnable> scheduled = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler, times(3)).schedule(scheduled.capture(), eq(20L), eq(TimeUnit.MILLISECONDS));

    scheduled.getAllValues().get(0).run();
    first.get(1, TimeUnit.SECONDS);
    second.get(1, TimeUnit.SECONDS);
    assertThat(third.isDone()).isFalse();
    scheduled.getAllValues().get(1).run();
    assertThat(third.isDone()).isFalse();
    scheduled.getAllValues().get(2).run();
    third.get(1, TimeUnit.SECONDS);

    verify(pgFactStore).doPublish(List.of(fact, secondFact));
    verify(pgFactStore).doPublish(List.of(thirdFact));
    verifyNoMoreInteractions(pgFactStore);
  }

  @Test
  void addAndFlush_should_complete_when_batchPublish_succeeds() throws Exception {
    // Arrange
    List<Fact> facts = Collections.singletonList(fact);

    // Act
    Future<Void> future = underTest.addAndFlush(facts);

    // Assert
    future.get(); // Should not throw
    verify(pgFactStore, timeout(1000)).doPublish(facts);
  }

  @Test
  void publicationDoesNotCompleteBeforeBatchPublishReturns() throws Exception {
    CountDownLatch publishing = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              publishing.countDown();
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              return null;
            })
        .when(pgFactStore)
        .doPublish(anyList());

    Future<Void> publication = underTest.addAndFlush(List.of(fact));
    try {
      assertThat(publishing.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(publication.isDone()).isFalse();
    } finally {
      release.countDown();
    }
    publication.get(5, TimeUnit.SECONDS);
  }

  @Test
  void addAndFlush_should_retry_individually_when_batchPublish_fails() throws Exception {
    // Arrange
    Fact fact1 = mock(Fact.class);
    Fact fact2 = mock(Fact.class);
    List<Fact> facts = List.of(fact1, fact2);

    // Simulate batch failure for the full list
    AtomicBoolean failed = new AtomicBoolean(false);
    doAnswer(
            invocation -> {
              if (!failed.getAndSet(true)) {
                throw new RuntimeException("Batch failure");
              }
              return null;
            })
        .when(pgFactStore)
        .doPublish(anyList());

    // Act
    Future<Void> future = underTest.addAndFlush(facts);

    // Assert
    // Wait for completion (it should complete now that retries succeed)
    future.get();

    verify(pgFactStore, times(2)).doPublish(facts); // Initial failure + retry
  }

  @Test
  void addAndFlush_should_handle_concurrent_lists_with_duplicates() throws Exception {

    CountDownLatch cdl = new CountDownLatch(1);

    // should not make any difference, but being cautious
    PublicationQueue q = spy(new PublicationQueue(pgFactStore, metrics, 100, 20));

    doAnswer(
            i -> {
              cdl.await();
              return i.callRealMethod();
            })
        .when(q)
        .flush(anyLong());

    // Arrange
    Fact fact1 = mock(Fact.class);
    Fact fact2 = mock(Fact.class);
    Fact fact3 = mock(Fact.class);

    List<Fact> facts1 = List.of(fact1);
    List<Fact> facts2 = List.of(fact2);
    List<Fact> facts3 = List.of(fact3);

    List<Fact> allFacts = List.of(fact1, fact2, fact3);

    // Simulate batch failure for combined list and dup failure for individual lists
    doAnswer(
            invocation -> {
              List<Fact> facts = invocation.getArgument(0);
              if (facts.size() == 3) {
                throw new DuplicateFactException("Batch failure");
              }
              if (facts == facts1 || facts == facts3) {
                throw new DuplicateFactException("Duplicate fact");
              }
              return null;
            })
        .when(pgFactStore)
        .doPublish(anyList());

    // make sure, everything is quiet
    assertThat(cdl.await(50, TimeUnit.MILLISECONDS)).isFalse();

    // Act
    Future<Future<Void>> future1 = CompletableFuture.supplyAsync(() -> q.addAndFlush(facts1));
    Future<Future<Void>> future2 = CompletableFuture.supplyAsync(() -> q.addAndFlush(facts2));
    Future<Future<Void>> future3 = CompletableFuture.supplyAsync(() -> q.addAndFlush(facts3));

    // make sure, everything is quiet
    assertThat(cdl.await(50, TimeUnit.MILLISECONDS)).isFalse();

    while (q.queueSize() < 3) {
      Thread.sleep(10);
    }

    cdl.countDown();

    // Assert
    ExecutionException executionException1 =
        assertThrows(ExecutionException.class, () -> future1.get().get());
    Assertions.assertThat(executionException1.getCause())
        .isInstanceOf(DuplicateFactException.class);

    assertDoesNotThrow(() -> future2.get().get());

    ExecutionException executionException3 =
        assertThrows(ExecutionException.class, () -> future3.get().get());
    Assertions.assertThat(executionException3.getCause())
        .isInstanceOf(DuplicateFactException.class);

    // this one failed:
    verify(pgFactStore, times(1)).doPublish(argThat(l -> l.containsAll(allFacts)));
    // so those need to happen
    verify(pgFactStore, times(1)).doPublish(eq(facts1));
    verify(pgFactStore, times(1)).doPublish(eq(facts2));
    verify(pgFactStore, times(1)).doPublish(eq(facts3));
  }
}
