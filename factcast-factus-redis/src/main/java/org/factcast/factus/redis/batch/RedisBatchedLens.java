package org.factcast.factus.redis.batch;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.function.Function;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.projector.AbstractTransactionalLens;
import org.factcast.factus.redis.RedisProjection;
import org.redisson.api.BatchOptions;
import org.redisson.api.RBatch;
import org.redisson.api.RedissonClient;

@Slf4j
public class RedisBatchedLens extends AbstractTransactionalLens {

  private final RedissonBatchManager batchMan;

  public RedisBatchedLens(@NonNull RedisProjection p, RedissonClient redissonClient) {

    this(p, RedissonBatchManager.get(redissonClient), createOpts(p));
  }

  RedisBatchedLens(@NonNull RedisProjection p, RedissonBatchManager man, BatchOptions opts) {
    super(p);

    batchMan = man;
    man.options(opts);

    bulkSize = Math.max(1, getSize(p));
    flushTimeout = Duration.ofSeconds(30).toMillis();
    log.trace(
        "Created {} instance for {} with batchsize={},timeout={}",
        getClass().getSimpleName(),
        p,
        bulkSize,
        flushTimeout);
  }

  @VisibleForTesting
  static int getSize(@NonNull RedisProjection p) {
    RedisBatched annotation = p.getClass().getAnnotation(RedisBatched.class);
    if (annotation == null) {
      throw new IllegalStateException(
          "Projection "
              + p.getClass()
              + " is expected to have an annotation @"
              + RedisBatched.class.getSimpleName());
    }
    return annotation.size();
  }

  @VisibleForTesting
  static BatchOptions createOpts(@NonNull RedisProjection p) {
    RedisBatched annotation = p.getClass().getAnnotation(RedisBatched.class);
    if (annotation == null) {
      throw new IllegalStateException(
          "Projection "
              + p.getClass()
              + " is expected to have an annotation @"
              + RedisBatched.class.getSimpleName());
    }
    return RedisBatched.Defaults.with(annotation);
  }

  @Override
  public Function<Fact, ?> parameterTransformerFor(@NonNull Class<?> type) {
    if (RBatch.class.equals(type)) {
      return f -> {
        batchMan.startOrJoin();
        RBatch rBatch = batchMan.getCurrentBatch();
        return rBatch;
      };
    }
    return null;
  }

  @Override
  protected void doClear() {
    if (batchMan.inBatch()) {
      batchMan.discard();
    }
  }

  @Override
  protected void doFlush() {
    // otherwise we can silently commit, not to flush the logs
    if (batchMan.inBatch()) {
      batchMan.execute();
    }
  }
}
