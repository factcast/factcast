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
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.Named;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.projection.WriterTokenAware;
import org.factcast.factus.projection.tx.AbstractOpenTransactionAwareProjection;
import org.factcast.factus.projection.tx.TransactionAware;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.redisson.api.*;

abstract class AbstractRedisProjection extends AbstractOpenTransactionAwareProjection<RTransaction>
    implements RedisProjection, TransactionAware, FactStreamPositionAware, WriterTokenAware, Named {
  @Getter protected final RedissonClient redisson;

  private final RLock lock;
  private final String stateBucketName;

  @Getter private final String redisKey;

  protected AbstractRedisProjection(@NonNull RedissonClient redisson) {
    this.redisson = redisson;

    redisKey = getScopedName().asString();
    stateBucketName = redisKey + "_state_tracking";

    // needs to be free from transactions, obviously
    lock = redisson.getLock(redisKey + "_lock");
  }

  @VisibleForTesting
  RBucket<FactStreamPosition> stateBucket(@NonNull RTransaction tx) {
    RBucket<FactStreamPosition> bucket =
        tx.getBucket(stateBucketName, FactStreamPositionCodec.INSTANCE);
    return bucket;
  }

  @VisibleForTesting
  RBucket<FactStreamPosition> stateBucket() {
    return redisson.getBucket(stateBucketName, FactStreamPositionCodec.INSTANCE);
  }

  @Override
  public FactStreamPosition factStreamPosition() {
    if (inTransaction()) {
      return stateBucket(runningTransaction()).get();
    } else {
      return stateBucket().get();
    }
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void factStreamPosition(@Nullable FactStreamPosition position) {
    if (inTransaction()) {
      stateBucket(runningTransaction()).set(position);
    } else {
      stateBucket().set(position);
    }
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    assertNoRunningTransaction();
    try {
      if (lock.tryLock(maxWait.toMillis(), TimeUnit.MILLISECONDS))
        return new RedisWriterToken(redisson, lock);
    } catch (InterruptedException e) {
      // assume lock unsuccessful
    }
    return null;
  }

  @Override
  protected @NonNull RTransaction beginNewTransaction() {
    return redisson().createTransaction(transactionOptions());
  }

  @Override
  protected void commit(@NonNull RTransaction runningTransaction) {
    runningTransaction.commit();
  }

  @Override
  protected void rollback(@NonNull RTransaction runningTransaction) {
    runningTransaction.rollback();
  }

  protected final @NonNull TransactionOptions transactionOptions() {
    RedisTransactional tx = this.getClass().getAnnotation(RedisTransactional.class);
    if (tx != null) return RedisTransactional.Defaults.with(tx);
    else return TransactionOptions.defaults();
  }

  @Override
  public final int maxBatchSizePerTransaction() {
    RedisTransactional tx = this.getClass().getAnnotation(RedisTransactional.class);
    if (tx != null) {
      return tx.bulkSize();
    } else return super.maxBatchSizePerTransaction();
  }
}
