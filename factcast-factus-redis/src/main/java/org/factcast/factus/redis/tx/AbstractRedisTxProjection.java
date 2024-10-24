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
import lombok.NonNull;
import lombok.experimental.Delegate;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.projection.tx.OpenTransactionAware;
import org.factcast.factus.projection.tx.TransactionBehavior;
import org.factcast.factus.redis.AbstractRedisProjection;
import org.factcast.factus.redis.FactStreamPositionCodec;
import org.redisson.api.*;

abstract class AbstractRedisTxProjection extends AbstractRedisProjection
    implements OpenTransactionAware<RTransaction> {

  @Delegate private final TransactionBehavior<RTransaction> tx;

  protected AbstractRedisTxProjection(@NonNull RedissonClient redisson) {
    super(redisson);
    this.tx =
        new TransactionBehavior<>(
            new RedisTxAdapter(redisson, getClass().getAnnotation(RedisTransactional.class)));
  }

  @VisibleForTesting
  protected RBucket<FactStreamPosition> stateBucket(@NonNull RTransaction tx) {
    return tx.getBucket(stateBucketName, FactStreamPositionCodec.INSTANCE);
  }

  @Override
  public void transactionalFactStreamPosition(@NonNull FactStreamPosition position) {
    assertInTransaction();
    stateBucket(runningTransaction()).set(position);
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
