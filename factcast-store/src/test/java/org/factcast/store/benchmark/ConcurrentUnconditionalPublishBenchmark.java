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
package org.factcast.store.benchmark;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.ThreadParams;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 1, time = 60)
@Measurement(iterations = 2, time = 60)
@Fork(
    value = 1,
    jvmArgs = {"-Xms1g", "-Xmx1g"})
@Threads(256)
public class ConcurrentUnconditionalPublishBenchmark {
  @State(Scope.Benchmark)
  public static class StoreState {
    @Param({"1", "100"})
    public int batchSize;

    private final LongAdder acknowledgedEvents = new LongAdder();
    private BenchmarkDatabase database;
    private FactStore store;

    @Setup(Level.Trial)
    public void start(BenchmarkParams parameters) throws Exception {
      database = new BenchmarkDatabase(batchSize, parameters.getThreads());
      store = database.start();
    }

    @TearDown(Level.Trial)
    public void stop() throws Exception {
      if (database != null) {
        try (BenchmarkDatabase fixture = database) {
          fixture.verifyAndReport(acknowledgedEvents.sum());
        }
      }
    }
  }

  @State(Scope.Thread)
  public static class Publisher {
    private BenchmarkFacts generator;
    private List<Fact> facts;

    @Setup(Level.Trial)
    public void start(ThreadParams thread) {
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        throw new IllegalStateException("Publishers must not run inside a caller transaction");
      }
      generator = new BenchmarkFacts(thread.getThreadIndex());
    }

    @Setup(Level.Invocation)
    public void prepare(StoreState state) {
      // Generate and serialize fresh facts outside the timed publish call, without reusing IDs.
      facts = generator.nextBatch(state.batchSize);
    }
  }

  @Benchmark
  public void publish(StoreState state, Publisher publisher) {
    state.store.publish(publisher.facts);
    // Count acknowledgements only after the synchronous call has successfully committed.
    state.acknowledgedEvents.add(state.batchSize);
  }
}
