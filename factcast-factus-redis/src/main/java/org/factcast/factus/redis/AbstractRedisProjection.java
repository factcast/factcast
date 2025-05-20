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
import org.factcast.factus.projection.WriterToken;
import org.redisson.api.*;

@SuppressWarnings("java:S2142")
public abstract class AbstractRedisProjection implements RedisProjection {
  @Getter protected final RedissonClient redisson;

  protected final RFencedLock lock;
  protected final String stateBucketName;

  @Getter protected final String redisKey;

  protected AbstractRedisProjection(@NonNull RedissonClient redisson) {
    this.redisson = redisson;

    redisKey = getScopedName().asString();
    stateBucketName = redisKey + "_state_tracking";

    // needs to be free from transactions, obviously
    lock = redisson.getFencedLock(redisKey + "_lock");
  }

  @VisibleForTesting
  protected RBucket<FactStreamPosition> stateBucket() {
    return redisson.getBucket(stateBucketName, FactStreamPositionCodec.INSTANCE);
  }

  @Override
  public FactStreamPosition factStreamPosition() {
    return stateBucket().get();
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void factStreamPosition(@Nullable FactStreamPosition position) {
    stateBucket().set(position);
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    try {
      if (lock.tryLock(maxWait.toMillis(), TimeUnit.MILLISECONDS)) {
        return new RedisWriterToken(lock);
      }
    } catch (InterruptedException e) {
      // assume lock unsuccessful
    }
    return null;
  }
}
