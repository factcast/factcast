package org.factcast.factus.redis.tx;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Function;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.redis.AbstractRedisLens;
import org.factcast.factus.redis.RedisManagedProjection;
import org.factcast.factus.redis.tx.RedisTransactional.Defaults;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.redisson.api.TransactionOptions;

@Slf4j
public class RedisTransactionalLens extends AbstractRedisLens {

  private final RedissonTxManager redissonTxManager;

  public RedisTransactionalLens(@NonNull RedisManagedProjection p, RedissonClient redissonClient) {
    this(p, redissonClient, RedissonTxManager.get(redissonClient), createOpts(p));
  }

  @VisibleForTesting
  RedisTransactionalLens(
      @NonNull RedisManagedProjection p,
      RedissonClient redissonClient,
      RedissonTxManager txman,
      TransactionOptions opts) {
    super(p, redissonClient);

    redissonTxManager = txman;
    txman.options(opts);

    batchSize = Math.max(1, getSize(p));
    flushTimeout = calculateFlushTimeout(opts);
    log.trace(
        "Created {} instance for {} with batchsize={},timeout={}",
        getClass().getSimpleName(),
        p,
        batchSize,
        flushTimeout);
  }

  @VisibleForTesting
  static int getSize(RedisManagedProjection p) {
    RedisTransactional transactional = p.getClass().getAnnotation(RedisTransactional.class);
    if (transactional == null) {
      throw new IllegalStateException(
          "Projection "
              + p.getClass()
              + " is expected to have an annotation @"
              + RedisTransactional.class.getSimpleName());
    }
    return transactional.size();
  }

  @VisibleForTesting
  static TransactionOptions createOpts(RedisManagedProjection p) {
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
  static long calculateFlushTimeout(TransactionOptions opts) {
    // "best" guess
    long flush = opts.getTimeout() / 10 * 8;
    if (flush < 80) {
      // disable batching altogether as it is too risky
      flush = 0;
    }
    return flush;
  }

  @Override
  public Function<Fact, ?> parameterTransformerFor(Class<?> type) {
    if (RTransaction.class.equals(type)) {
      return f -> {
        redissonTxManager.startOrJoin();
        return redissonTxManager.getCurrentTransaction();
      };
    }
    return null;
  }

  @Override
  public void doClear() {
    if (redissonTxManager.inTransaction()) {
      redissonTxManager.rollback();
    }
  }

  @Override
  public void doFlush() {
    // otherwise we can silently commit, not to "flush" the logs
    if (redissonTxManager.inTransaction()) {
      redissonTxManager.commit();
    }
  }
}
