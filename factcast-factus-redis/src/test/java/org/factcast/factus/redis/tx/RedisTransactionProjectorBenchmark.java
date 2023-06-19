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

import static org.mockito.ArgumentMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.factus.Handler;
import org.factcast.factus.event.DefaultEventSerializer;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.projector.Projector;
import org.factcast.factus.projector.ProjectorImpl;
import org.factcast.factus.redis.AbstractRedisManagedProjection;
import org.factcast.factus.redis.UUIDCodec;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.mockito.Mockito;
import org.openjdk.jmh.annotations.*;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

@SuppressWarnings("unchecked")
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

  private static final TestProjection projection = new TestProjection(mockRedisson);
  private static final TestProjection1 projection1 = new TestProjection1(mockRedisson);
  private static final TestProjection10 projection10 = new TestProjection10(mockRedisson);
  private static final TestProjection50 projection50 = new TestProjection50(mockRedisson);
  private static final TestProjection1000 projection1000 = new TestProjection1000(mockRedisson);

  private static final Projector<TestProjection> projector = new ProjectorImpl<>(ctx, projection);
  private static final Projector<TestProjection10> projector1 =
      new ProjectorImpl<>(ctx, projection1);
  private static final Projector<TestProjection10> projector10 =
      new ProjectorImpl<>(ctx, projection10);
  private static final Projector<TestProjection50> projector50 =
      new ProjectorImpl<>(ctx, projection50);
  private static final Projector<TestProjection50> projector1000 =
      new ProjectorImpl<>(ctx, projection1000);

  private static final RBucket<UUID> nopBucket = Mockito.mock(RBucket.class);
  ;

  static {
    Mockito.when(mockRedisson.getBucket(any(), same(UUIDCodec.INSTANCE)))
        .thenReturn((RBucket) nopBucket);
  }

  @Benchmark
  @BenchmarkMode({Mode.AverageTime})
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Fork(1)
  @Threads(1)
  @Warmup(iterations = 1)
  public void applyBatchNonTransactional() {
    projector.apply(facts);
  }

  @Benchmark
  @BenchmarkMode({Mode.AverageTime})
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Fork(1)
  @Threads(1)
  @Warmup(iterations = 1)
  public void applyBatch1() {
    projector1.apply(facts);
  }

  @Benchmark
  @BenchmarkMode({Mode.AverageTime})
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Fork(1)
  @Threads(1)
  @Warmup(iterations = 1)
  public void applyBatch10() {
    projector10.apply(facts);
  }

  @Benchmark
  @BenchmarkMode({Mode.AverageTime})
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Fork(1)
  @Threads(1)
  @Warmup(iterations = 1)
  public void applyBatch50() {
    projector50.apply(facts);
  }

  @Benchmark
  @BenchmarkMode({Mode.AverageTime})
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Fork(1)
  @Threads(1)
  @Warmup(iterations = 1)
  public void applyBatch1000() {
    projector1000.apply(facts);
  }

  @ProjectionMetaData(name = "peter", serial = 12)
  static class TestProjection extends AbstractRedisManagedProjection {

    protected int count;

    public TestProjection(@NonNull RedissonClient redisson) {
      super(redisson);
    }

    @Handler
    void apply(UserCreated u) {}
  }

  @RedisTransactional(bulkSize = 1)
  @ProjectionMetaData(name = "peter", serial = 12)
  static class TestProjection1 extends TestProjection {
    public TestProjection1(@NonNull RedissonClient redisson) {
      super(redisson);
    }
  }

  @RedisTransactional(bulkSize = 10)
  @ProjectionMetaData(name = "peter", serial = 12)
  static class TestProjection10 extends TestProjection {
    public TestProjection10(@NonNull RedissonClient redisson) {
      super(redisson);
    }
  }

  @RedisTransactional(bulkSize = 50)
  @ProjectionMetaData(name = "peter", serial = 12)
  static class TestProjection50 extends TestProjection {
    public TestProjection50(@NonNull RedissonClient redisson) {
      super(redisson);
    }
  }

  @RedisTransactional(bulkSize = 1000)
  @ProjectionMetaData(name = "peter", serial = 12)
  static class TestProjection1000 extends TestProjection {
    public TestProjection1000(@NonNull RedissonClient redisson) {
      super(redisson);
    }
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }
}
