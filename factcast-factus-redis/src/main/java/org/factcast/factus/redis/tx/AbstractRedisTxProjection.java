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
package org.factcast.factus.redis.tx;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.experimental.Delegate;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.Named;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.projection.WriterTokenAware;
import org.factcast.factus.projection.tx.OpenTransactionAware;
import org.factcast.factus.projection.tx.TransactionBehavior;
import org.factcast.factus.redis.FactStreamPositionCodec;
import org.factcast.factus.redis.RedisProjection;
import org.redisson.api.*;

abstract class AbstractRedisTxProjection extends AbstractRedisProjection
    implements OpenTransactionAware<RTransaction>,
        RedisProjection,
        FactStreamPositionAware,
        WriterTokenAware,
        Named {

  @Delegate private final TransactionBehavior<RTransaction> tx;

  protected AbstractRedisTxProjection(@NonNull RedissonClient redisson) {
    super(redisson);
    this.tx =
        new TransactionBehavior<>(
            new RedisTransactionAdapter(
                redisson, getClass().getAnnotation(RedisTransactional.class)));
  }

  @VisibleForTesting
  RBucket<FactStreamPosition> stateBucket(@NonNull RTransaction tx) {
    return tx.getBucket(stateBucketName, FactStreamPositionCodec.INSTANCE);
  }

  @Override
  public FactStreamPosition factStreamPosition() {
    if (inTransaction()) {
      return stateBucket(runningTransaction()).get();
    } else {
      return super.factStreamPosition();
    }
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void factStreamPosition(@Nullable FactStreamPosition position) {
    if (inTransaction()) {
      stateBucket(runningTransaction()).set(position);
    } else {
      super.factStreamPosition(position);
    }
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    assertNoRunningTransaction();
    return super.acquireWriteToken(maxWait);
  }

  @Override
  public AutoCloseable acquireWriteToken() {
    assertNoRunningTransaction();
    return super.acquireWriteToken();
  }
}
