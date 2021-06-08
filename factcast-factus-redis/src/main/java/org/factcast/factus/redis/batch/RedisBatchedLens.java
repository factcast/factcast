package org.factcast.factus.redis.batch;

import java.util.function.Function;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.factcast.core.Fact;
import org.factcast.factus.redis.AbstractRedisLens;
import org.factcast.factus.redis.RedisProjection;
import org.redisson.api.RBatch;

@Slf4j
public class RedisBatchedLens extends AbstractRedisLens {

  public RedisBatchedLens(@NonNull RedisProjection p) {
    super(p);

    RedisBatched annotation = p.getClass().getAnnotation(RedisBatched.class);
    val opts = RedisBatched.Defaults.with(annotation);
    // TODO use opts
    batchSize = Math.max(1, annotation.size());
    flushTimeout = 1000 * 60; // one minute fix
    log.debug(
        "Created {} instance for {} with batchsize={},timeout={}",
        getClass().getSimpleName(),
        p,
        batchSize,
        flushTimeout);
  }

  @Override
  public Function<Fact, ?> parameterTransformerFor(Class<?> type) {

    if (RBatch.class.equals(type)) {
      return f -> {
        RedissonBatchManager batch = RedissonBatchManager.get(client);
        batch.startOrJoin();
        RBatch rBatch = batch.getCurrentBatch();
        return rBatch;
      };
    }
    return null;
  }

  @Override
  protected void doClear() {

    RedissonBatchManager bm = RedissonBatchManager.get(client);
    if (bm.inBatch()) {
      bm.discard();
    }
  }

  @Override
  protected void doFlush() {

    // otherwise we can silently commit, not to flush the logs
    RedissonBatchManager bm = RedissonBatchManager.get(client);
    if (bm.inBatch()) {
      bm.execute();
    }
  }
}
