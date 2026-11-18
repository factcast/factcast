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

import com.google.common.annotations.VisibleForTesting;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.*;

@Slf4j
@RequiredArgsConstructor
class PublicationQueue {

  private final PgFactStore pgFactStore;
  private final PgMetrics metrics;
  private final int maxBatchSize;
  private final long flushDelayMillis;
  private final ScheduledExecutorService flushingExecutor;
  private final LongSupplier nanoTime;
  private final AtomicLong ordinalCounter = new AtomicLong(Long.MIN_VALUE);

  PublicationQueue(
      PgFactStore pgFactStore, PgMetrics metrics, int maxBatchSize, long flushDelayMillis) {
    this(
        pgFactStore,
        metrics,
        maxBatchSize,
        flushDelayMillis,
        Executors.newSingleThreadScheduledExecutor());
  }

  PublicationQueue(
      PgFactStore pgFactStore,
      PgMetrics metrics,
      int maxBatchSize,
      long flushDelayMillis,
      ScheduledExecutorService flushingExecutor) {
    this(pgFactStore, metrics, maxBatchSize, flushDelayMillis, flushingExecutor, System::nanoTime);
  }

  record Publication(
      long ordinal,
      List<? extends Fact> facts,
      CompletableFuture<Void> completion,
      long enqueuedAt) {}

  // Non-concurrent ArrayDeque is safe as it is only accessed while synchronized on
  final Queue<Publication> queue = new ArrayDeque<>(4096);

  // All three fields are guarded by queue; active work is excluded from backlog idle time.
  private boolean flushing;
  private boolean backlogWaiting;
  private long backlogSince;

  @VisibleForTesting
  int queueSize() {
    synchronized (queue) {
      return queue.size();
    }
  }

  Future<Void> addAndFlush(List<? extends Fact> toPublish) throws DuplicateFactException {
    CompletableFuture<Void> completion = new CompletableFuture<>();
    AtomicLong serial = new AtomicLong(Long.MAX_VALUE);
    synchronized (queue) {
      // sync makes sure, that the order in the queue is maintained, so that we can early exit
      // flush(ser) based on the ser
      serial.set(ordinalCounter.incrementAndGet());
      long now = nanoTime.getAsLong();
      if (queue.isEmpty() && !flushing) {
        backlogWaiting = true;
        backlogSince = now;
      }
      queue.add(new Publication(serial.get(), toPublish, completion, now));
      // Give nearby publications time to join the batch without blocking the flushing thread.
      // Later arrivals do not postpone this task; their tasks become no-ops if already flushed.
      flushingExecutor.schedule(
          () -> {
            try {
              flush(serial.get());
            } catch (Exception e) {
              log.error(e.getMessage(), e);
            }
          },
          flushDelayMillis,
          TimeUnit.MILLISECONDS);
    }
    return completion;
  }

  /**
   * This is not supposed to be called from somewhere else than the flushingExecutor, which in turn
   * uses a single thread. Otherwise it would not be possible to guarantee that publications no
   * longer in the queue are already flushed to the store.
   *
   * <p>To make this as explicit as possible, we also marked it as synchronized to ensure that only
   * one thread can execute this method at a time.
   *
   * @param ordinal
   */
  @VisibleForTesting
  synchronized void flush(long ordinal) {
    long idleNanos;
    // contention-less sync is said to be "virtually free"
    synchronized (queue) {
      if (queue.isEmpty() || queue.peek().ordinal() > ordinal) {
        // nothing to do for this ordinal, as it already has been flushed by an earlier flush
        return;
      }
      idleNanos = backlogWaiting ? nanoTime.getAsLong() - backlogSince : 0;
      backlogWaiting = false;
      flushing = true;
    }
    metrics.recordDuration(StoreMetrics.OP.PUBLICATION_BACKLOG_IDLE, idleNanos, null);

    try {
      // This is a trade-off between efficiency and latency. The longer the batch gets,
      // the longer it takes for the first publication to be completed.
      // Also the number of conversations open is not infinite as well.
      List<Publication> pubs = new ArrayList<>(maxBatchSize);
      List<Fact> facts = new ArrayList<>(maxBatchSize * 4);

      // collect all facts & futures currently in the queue, regardless if there are more/later
      // publications already
      Publication p;
      while (pubs.size() < maxBatchSize) {
        // trying to keep the lock as short as possible, in order not to block additions to the
        // queue
        synchronized (queue) {
          p = queue.poll();
        }

        if (p != null) {
          metrics.recordDuration(
              StoreMetrics.OP.PUBLICATION_QUEUE_WAIT, nanoTime.getAsLong() - p.enqueuedAt(), null);
          pubs.add(p);
          facts.addAll(p.facts());
        } else break;
      }

      // really should not be empty, but better safe than sorry.
      if (!pubs.isEmpty()) {
        metrics.recordValue(StoreMetrics.VALUE.PUBLICATION_BATCH_ENTRIES, pubs.size());
        metrics.recordValue(StoreMetrics.VALUE.PUBLICATION_BATCH_FACTS, facts.size());
        // try to publish as one
        try {
          pgFactStore.doPublish(facts);
          // since it worked, we can complete all
          pubs.forEach(this::committed);
        } catch (Exception e) {
          metrics.increment(StoreMetrics.EVENT.PUBLICATION_BATCH_FALLBACK, 1);
          log.debug("failed to flush the publication queue in one go. Falling back to iteration.");
          // ok, we need to go one by one then to throw the dup exception in the right
          // place(s)
          //
          // there is no need to log the exception, as it will resurface again below
          pubs.parallelStream()
              .forEach(
                  pub -> {
                    try {
                      pgFactStore.doPublish(pub.facts());
                      committed(pub);
                    } catch (Exception dupe) {
                      pub.completion().completeExceptionally(dupe);
                    }
                  });
        }
      }
    } finally {
      synchronized (queue) {
        flushing = false;
        backlogWaiting = !queue.isEmpty();
        if (backlogWaiting) backlogSince = nanoTime.getAsLong();
      }
    }
  }

  private void committed(Publication pub) {
    metrics.increment(StoreMetrics.EVENT.QUEUED_PUBLICATIONS_COMMITTED, 1);
    metrics.increment(StoreMetrics.EVENT.QUEUED_FACTS_COMMITTED, pub.facts().size());
    pub.completion().complete(null);
  }
}
