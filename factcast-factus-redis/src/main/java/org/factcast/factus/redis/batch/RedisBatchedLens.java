package org.factcast.factus.redis.batch;

import java.util.function.Function;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.redis.AbstractRedisLens;
import org.factcast.factus.redis.RedisProjection;
import org.factcast.factus.redis.batch.RedisBatched.Defaults;
import org.redisson.api.BatchOptions;
import org.redisson.api.RBatch;
import org.redisson.api.RedissonClient;

@Slf4j
public class RedisBatchedLens extends AbstractRedisLens {

  private final BatchOptions opts;
  private final RedissonBatchManager batchMan;

  public RedisBatchedLens(@NonNull RedisProjection p, RedissonClient redissonClient) {
    super(p, redissonClient);

    RedisBatched batched = p.getClass().getAnnotation(RedisBatched.class);
    opts = Defaults.with(batched);
    batchMan = RedissonBatchManager.get(p.redisson()).options(opts);

    batchSize = Math.max(1, batched.size());
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
    RedissonBatchManager man = RedissonBatchManager.get(client);
    man.options(opts);
    if (RBatch.class.equals(type)) {
      return f -> {
        RedissonBatchManager batch = man;
        batch.startOrJoin();
        RBatch rBatch = batch.getCurrentBatch();
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
