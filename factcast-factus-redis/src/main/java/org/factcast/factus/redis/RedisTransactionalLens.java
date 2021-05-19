package org.factcast.factus.redis;

import io.micrometer.core.lang.Nullable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.redisson.api.RTransaction;

@Slf4j
public class RedisTransactionalLens implements ProjectorLens {

  @NonNull private final RedissonTXManager tx;
  @Nullable private final BatchApply redisTransactional;

  private final AtomicInteger count = new AtomicInteger();
  private int batchSize = 1;

  public RedisTransactionalLens(@NonNull RedissonTXManager tx, BatchApply batchApply) {
    this.tx = tx;
    redisTransactional = batchApply;
    if (batchApply != null) {
      if (batchApply.size() < 1) {
        // TODO resolve default from properties?
        batchSize = 50;
      } else {
        batchSize = batchApply.size();
      }
    }
  }

  @Override
  public Function<Fact, Object> parameterTransformerFor(Class<?> type) {
    if (RTransaction.class.equals(type)) {
      return f -> tx.getOrCreate();
    }
    return null;
  }

  @Override
  public void beforeApply(Projection p, Fact f) {}

  @Override
  public void afterApply(Projection p, Fact f) {
    if (shouldCommit()) {
      // must exist
      commit();
    }
  }

  public void afterExceptionalApply(Projection p, Fact f, RuntimeException e) {
    tx.rollback();
  }

  private void commit() {
    log.trace("Committing tx, number of operations=" + count.getAndSet(0));
    tx.commit();
  }

  @Override
  public void onCatchup(Projection p) {
    commit();
  }

  private boolean shouldCommit() {
    return count.incrementAndGet() >= batchSize;
  }
}
