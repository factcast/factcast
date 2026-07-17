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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.*;

@Slf4j
@RequiredArgsConstructor
class UnconditionalPublishQueue {

  private final PgFactStore pgFactStore;
  private final ExecutorService flushingExecutor = Executors.newSingleThreadExecutor();
  private final AtomicLong serialCounter = new AtomicLong(Long.MIN_VALUE);

  record Publication(long serial, List<? extends Fact> facts, CompletableFuture<Void> completion) {}

  // TODO can we dare unbounded deque here? If we use BlockingQueue instead, we'd need to rethink
  // locking
  final Queue<Publication> queue = new ArrayDeque<>(4096);

  Future<Void> addAndFlush(List<? extends Fact> toPublish) throws DuplicateFactException {
    CompletableFuture<Void> completion = new CompletableFuture<>();
    AtomicLong serial = new AtomicLong(Long.MAX_VALUE);
    // sync makes sure, that the order in the queue is maintained, so that we can early exit
    // flush(ser) based on the ser
    synchronized (queue) {
      serial.set(serialCounter.incrementAndGet());
      queue.add(new Publication(serial.get(), toPublish, completion));
    }
    flushingExecutor.submit(
        () -> {
          try {
            flush(serial.get());
          } catch (Exception e) {
            log.error(e.getMessage(), e);
          }
        });
    return completion;
  }

  void flush(long ser) {
    if ((!queue.isEmpty()) && (queue.peek().serial() <= ser)) {
      // collect all facts & futures

      // This is a trade-off between efficiency and latency. The longer the batch gets,
      // the longer it takes for the first publication to be completed.
      // Also the number of conversations open is not infinite as well.
      //
      // TODO make configurable?
      int maxTransactionsToCombine = 500;
      List<Publication> pubs = new ArrayList<>(maxTransactionsToCombine);
      List<Fact> facts = new ArrayList<>(maxTransactionsToCombine);

      // contention-less sync is said to be "virtually free"
      synchronized (queue) {
        Publication p;
        while ((pubs.size() < maxTransactionsToCombine) && (p = queue.poll()) != null) {
          pubs.add(p);
          facts.addAll(p.facts());
        }
      }

      // could still be empty due to concurrent access
      if (!pubs.isEmpty()) {
        // try to publish as one
        try {
          pgFactStore.batchPublish(facts);
          // since it worked, we can complete all
          pubs.forEach(pub -> pub.completion().complete(null));
        } catch (Exception e) {
          // ok, we need to go one by one then in order to throw the dup exception in the right
          // place(s)
          //
          // there is no need to log the exception, as it will resurface again below
          pubs.parallelStream()
              .forEach(
                  pub -> {
                    try {
                      pgFactStore.batchPublish(pub.facts());
                      pub.completion().complete(null);
                    } catch (Exception dupe) {
                      pub.completion().completeExceptionally(dupe);
                    }
                  });
        }
      }
    }
  }
}
