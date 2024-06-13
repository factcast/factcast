/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.factus.redis.tx;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.factus.Handler;
import org.factcast.factus.event.DefaultEventSerializer;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.projector.Projector;
import org.factcast.factus.projector.ProjectorImpl;
import org.factcast.factus.redis.FactStreamPositionCodec;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.mockito.Mockito;
import org.openjdk.jmh.annotations.*;
import org.redisson.api.RBucket;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;

@SuppressWarnings({"unchecked", "deprecation"})
public class RedisTransactionProjectorBenchmark {
  private static final EventSerializer ctx = new DefaultEventSerializer(new ObjectMapper());
  private static final Fact f =
      Fact.builder()
          .ns("test")
          .type("UserCreated")
          .version(1)
          .build("{\"aggId\":\"2fe71a21-455c-486f-90ab-d14a966b5636\", \"name\":\"peter\"}");

  private static final List<Fact> facts = Collections.nCopies(1000, f);
  private static final RedissonClient mockRedisson = Mockito.mock(RedissonClient.class);
  private static final RTransaction tx = Mockito.mock(RTransaction.class);

  @SuppressWarnings("rawtypes")
  private static final RBucket nopBucket = Mockito.mock(RBucket.class);

  private static final TestProjection50 projection50 = new TestProjection50(mockRedisson);
  private static final TestProjection projection = new TestProjection(mockRedisson);

  private static final Projector<TestProjection50> projector50 =
      new ProjectorImpl<>(projection50, ctx);

  private static final Projector<TestProjection> projector = new ProjectorImpl<>(projection, ctx);

  static {
    Mockito.when(mockRedisson.createTransaction(any())).thenReturn(tx);
    Mockito.when(mockRedisson.getBucket(any(), same(FactStreamPositionCodec.INSTANCE)))
        .thenReturn(nopBucket);
    Mockito.when(tx.getBucket(any(), same(FactStreamPositionCodec.INSTANCE))).thenReturn(nopBucket);
  }

  @Benchmark
  @BenchmarkMode({Mode.AverageTime})
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Fork(1)
  @Threads(1)
  @Warmup(iterations = 1)
  public void applyBatch() {
    projector50.apply(facts);
  }

  @Benchmark
  @BenchmarkMode({Mode.AverageTime})
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Fork(1)
  @Threads(1)
  @Warmup(iterations = 1)
  public void applyBatchDefaultSize() {
    projector.apply(facts);
  }

  @ProjectionMetaData(name = "peter", revision = 12)
  static class TestProjection extends AbstractRedisTxManagedProjection {

    protected int count;

    public TestProjection(@NonNull RedissonClient redisson) {
      super(redisson);
    }

    @Handler
    void apply(UserCreated u) {}
  }

  @ProjectionMetaData(name = "peter", revision = 12)
  @RedisTransactional(bulkSize = 50)
  static class TestProjection50 extends TestProjection {
    public TestProjection50(@NonNull RedissonClient redisson) {
      super(redisson);
    }
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
    new RedisTransactionProjectorBenchmark().applyBatchDefaultSize();
  }
}
