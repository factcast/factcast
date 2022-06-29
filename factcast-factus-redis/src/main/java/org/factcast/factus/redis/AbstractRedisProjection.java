/*
 * Copyright © 2017-2022 factcast.org
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

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.Named;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.projection.WriterTokenAware;
import org.factcast.factus.redis.batch.RedissonBatchManager;
import org.factcast.factus.redis.tx.RedissonTxManager;
import org.redisson.api.*;

abstract class AbstractRedisProjection
    implements RedisProjection, FactStreamPositionAware, WriterTokenAware, Named {
  @Getter protected final RedissonClient redisson;

  private final RLock lock;
  private final String stateBucketName;

  @Getter private final String redisKey;

  public AbstractRedisProjection(@NonNull RedissonClient redisson) {
    this.redisson = redisson;

    redisKey = getScopedName().asString();
    stateBucketName = redisKey + "_state_tracking";

    // needs to be free from transactions, obviously
    lock = redisson.getLock(redisKey + "_lock");
  }

  @VisibleForTesting
  RBucket<UUID> stateBucket(@NonNull RTransaction tx) {
    return tx.getBucket(stateBucketName, UUIDCodec.INSTANCE);
  }

  @VisibleForTesting
  RBucketAsync<UUID> stateBucket(@NonNull RBatch b) {
    return b.getBucket(stateBucketName, UUIDCodec.INSTANCE);
  }

  @VisibleForTesting
  RBucket<UUID> stateBucket() {
    return redisson.getBucket(stateBucketName, UUIDCodec.INSTANCE);
  }

  @Override
  public UUID factStreamPosition() {
    RedissonTxManager man = RedissonTxManager.get(redisson);
    if (man.inTransaction()) {
      return man.join((Function<RTransaction, UUID>) tx -> stateBucket(tx).get());
    } else {
      return stateBucket().get();
    }
    // note: were not trying to use a bucket from a running batch as it would require to execute the
    // batch to get a result back.
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void factStreamPosition(@NonNull UUID position) {
    RedissonTxManager txMan = RedissonTxManager.get(redisson);
    if (txMan.inTransaction()) {
      txMan.join(
          tx -> {
            stateBucket(txMan.getCurrentTransaction()).set(position);
          });
    } else {
      RedissonBatchManager bman = RedissonBatchManager.get(redisson);
      if (bman.inBatch()) {
        bman.join(
            tx -> {
              stateBucket(bman.getCurrentBatch()).setAsync(position);
            });
      } else {
        stateBucket().set(position);
      }
    }
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    try {
      if (lock.tryLock(maxWait.toMillis(), TimeUnit.MILLISECONDS))
        return new RedisWriterToken(redisson, lock);
    } catch (InterruptedException e) {
      // assume lock unsuccessful
    }
    return null;
  }
}
