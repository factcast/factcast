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

import static org.factcast.factus.redis.tx.TransactionNotificationMessages.COMMIT;
import static org.factcast.factus.redis.tx.TransactionNotificationMessages.ROLLBACK;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.projector.AbstractTransactionalLens;
import org.factcast.factus.redis.AbstractRedisSubscribedProjection;
import org.factcast.factus.redis.RedisProjection;
import org.factcast.factus.redis.tx.RedisTransactional.Defaults;
import org.redisson.api.RTopic;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.redisson.api.TransactionOptions;

@Slf4j
public class RedisTransactionalLens extends AbstractTransactionalLens {

  /**
   * We cannot store the manager itself here, as during creation of the lens, we are on a common
   * thread that creates the lenses for all projections, and they would hence all share the same
   * transaction manager, as the transaction manager is manged via ThreadLocal.
   *
   * <p>Hence, use a supplier here, so we get the right tx manager when we are on the subscription
   * thread.
   */
  private final Supplier<RedissonTxManager> txManagerSupplier;

  private final TransactionOptions opts;
  private final RTopic notificationTopic;

  public RedisTransactionalLens(
      @NonNull RedisProjection p, @NonNull RedissonClient redissonClient) {
    this(p, () -> RedissonTxManager.get(redissonClient), createOpts(p));
  }

  @VisibleForTesting
  RedisTransactionalLens(
      @NonNull RedisProjection p,
      @NonNull Supplier<RedissonTxManager> txManagerSupplier,
      @NonNull TransactionOptions opts) {
    super(p);

    this.txManagerSupplier = txManagerSupplier;
    this.opts = opts;

    if (p.getClass().getAnnotation(EnableRedisTransactionCallbacks.class) != null) {
      // enable callbacks for onCommit and onRollback

      if (!(p instanceof AbstractRedisSubscribedProjection)) {
        throw new IllegalStateException(
            "Cannot use @EnableRedisTransactionCallbacks on projections that are not AbstractRedisSubscribedProjection!");
      }

      AbstractRedisSubscribedProjection sub = (AbstractRedisSubscribedProjection) p;

      this.notificationTopic =
          p.redisson().getTopic(TransactionNotificationMessages.getTopicName(sub));

    } else {
      this.notificationTopic = null;
    }

    bulkSize = Math.max(1, getSize(p));
    flushTimeout = calculateFlushTimeout(opts);
    log.trace(
        "Created {} instance for {} with batchsize={},timeout={}",
        getClass().getSimpleName(),
        p,
        bulkSize,
        flushTimeout);
  }

  @VisibleForTesting
  static TransactionOptions createOpts(@NonNull RedisProjection p) {
    RedisTransactional transactional = p.getClass().getAnnotation(RedisTransactional.class);
    if (transactional == null) {
      throw new IllegalStateException(
          "Projection "
              + p.getClass()
              + " is expected to have an annotation @"
              + RedisTransactional.class.getSimpleName());
    }
    return Defaults.with(transactional);
  }

  @VisibleForTesting
  static int getSize(@NonNull RedisProjection p) {
    RedisTransactional transactional = p.getClass().getAnnotation(RedisTransactional.class);
    if (transactional == null) {
      throw new IllegalStateException(
          "Projection "
              + p.getClass()
              + " is expected to have an annotation @"
              + RedisTransactional.class.getSimpleName());
    }
    return transactional.bulkSize();
  }

  @VisibleForTesting
  static long calculateFlushTimeout(@NonNull TransactionOptions opts) {
    // "best" guess
    long flush = opts.getTimeout() / 10 * 8;
    if (flush < 80) {
      // disable batching altogether as it is too risky
      flush = 0;
    }
    return flush;
  }

  @Override
  public Function<Fact, ?> parameterTransformerFor(@NonNull Class<?> type) {
    if (RTransaction.class.equals(type)) {
      return f -> {
        RedissonTxManager redissonTxManager = txManagerSupplier.get();
        redissonTxManager.options(opts); // it might have been just created
        redissonTxManager.startOrJoin();
        return txManagerSupplier.get().getCurrentTransaction();
      };
    }
    return null;
  }

  @Override
  public void doClear() {
    txManagerSupplier.get().rollback();
    if (notificationTopic != null) {
      notificationTopic.publish(ROLLBACK.code());
    }
  }

  @Override
  public void doFlush() {
    txManagerSupplier.get().commit();
    if (notificationTopic != null) {
      notificationTopic.publish(COMMIT.code());
    }
  }
}
