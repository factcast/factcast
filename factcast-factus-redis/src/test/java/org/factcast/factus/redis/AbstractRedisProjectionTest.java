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
package org.factcast.factus.redis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.TestFactStreamPosition;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class AbstractRedisProjectionTest {
  private static final String STATE_BUCKET_NAME = "STATE_BUCKET_NAME";
  private static final String REDIS_KEY = "REDIS_KEY";
  @Mock private RedissonClient redisson;
  @Mock private RLock lock;
  @Mock private RTransaction runningTransaction;
  private TestRedisProjection underTest;

  @Nested
  class WhenStatingBucket {
    @Mock private @NonNull RTransaction tx;

    @BeforeEach
    void setup() {
      underTest = new TestRedisProjection(redisson);
    }

    @Test
    void delegatesToTx() {
      RBucket b = mock(RBucket.class);
      when(tx.getBucket(any(), same(FactStreamPositionCodec.INSTANCE))).thenReturn(b);
      Assertions.assertThat(underTest.stateBucket(tx)).isSameAs(b);
    }

    @Test
    void delegatesToRedisson() {
      RBucket b = mock(RBucket.class);
      when(redisson.getBucket(any(), same(FactStreamPositionCodec.INSTANCE))).thenReturn(b);
      Assertions.assertThat(underTest.stateBucket()).isSameAs(b);
    }
  }

  @Nested
  class WhenFactingStreamPosition {
    @Mock private @NonNull RTransaction tx;

    @BeforeEach
    void setup() {
      underTest = new TestRedisProjection(redisson);
    }

    @Test
    void delegatesToTx() {
      FactStreamPosition fsp = TestFactStreamPosition.random();
      when(redisson.createTransaction(any())).thenReturn(tx);

      RBucket b = mock(RBucket.class);
      when(tx.getBucket(any(), same(FactStreamPositionCodec.INSTANCE))).thenReturn(b);

      underTest.begin();
      underTest.factStreamPosition(fsp);

      verify(b).set(fsp);
    }
  }

  @Nested
  class WhenAcquiringWriteToken {
    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenBeginingNewTransaction {
    @Mock private @NonNull RTransaction tx;

    @BeforeEach
    void setup() {
      underTest = new TestRedisProjection(redisson);
    }

    @Test
    void delegatesToTx() {
      FactStreamPosition fsp = TestFactStreamPosition.random();
      when(redisson.createTransaction(any())).thenReturn(tx);

      underTest.begin();
      Assertions.assertThat(underTest.runningTransaction()).isSameAs(tx);
    }
  }

  @Nested
  class WhenCommitting {
    @Mock private @NonNull RTransaction tx;

    @BeforeEach
    void setup() {
      underTest = new TestRedisProjection(redisson);
    }

    @Test
    void delegatesToTx() {
      FactStreamPosition fsp = TestFactStreamPosition.random();
      when(redisson.createTransaction(any())).thenReturn(tx);

      underTest.begin();
      underTest.commit();
      verify(tx).commit();
      Assertions.assertThat(underTest.runningTransaction()).isNull();
    }
  }

  @Nested
  class WhenRollbacking {
    @Mock private @NonNull RTransaction tx;

    @BeforeEach
    void setup() {
      underTest = new TestRedisProjection(redisson);
    }

    @Test
    void delegatesToTx() {
      FactStreamPosition fsp = TestFactStreamPosition.random();
      when(redisson.createTransaction(any())).thenReturn(tx);

      underTest.begin();
      underTest.rollback();
      verify(tx).rollback();
      Assertions.assertThat(underTest.runningTransaction()).isNull();
    }
  }

  @Nested
  class WhenTransactioningOptions {

    @BeforeEach
    void setup() {
      underTest = new TestRedisProjection(redisson);
    }

    @Test
    void readsAnnotation() {
      Assertions.assertThat(underTest.transactionOptions().getTimeout()).isEqualTo(98);
    }
  }

  @Nested
  class WhenMaxingBatchSizePerTransaction {

    @BeforeEach
    void setup() {
      underTest = new TestRedisProjection(redisson);
    }

    @Test
    void readsAnnotation() {
      Assertions.assertThat(underTest.maxBatchSizePerTransaction()).isEqualTo(23);
    }
  }
}

@ProjectionMetaData(revision = 1)
@RedisTransactional(bulkSize = 23, timeout = 98)
class TestRedisProjection extends AbstractRedisProjection {
  protected TestRedisProjection(@NonNull RedissonClient redisson) {
    super(redisson);
  }
}
