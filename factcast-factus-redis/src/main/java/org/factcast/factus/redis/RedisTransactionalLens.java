package org.factcast.factus.redis;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.redisson.api.TransactionOptions;

@RequiredArgsConstructor
@Slf4j
public class RedisTransactionalLens implements ProjectorLens {

  @NonNull private final RedissonClient redisson;
  @NonNull private final RedisTransactional redisTransactional;

  private final AtomicReference<RTransaction> tx = new AtomicReference<>();
  private final AtomicInteger count = new AtomicInteger();

  @Override
  public Function<Fact, Object> parameterTransformerFor(Class<?> type) {
    if (RTransaction.class.equals(type)) {
      return f ->
          tx.updateAndGet(
              t -> {
                if (t == null) {
                  log.trace("Creating tx, batch size=" + redisTransactional.size());
                  return redisson.createTransaction(TransactionOptions.defaults());
                } else {
                  return t;
                }
              });
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

  private void commit() {
    log.trace("Committing tx, operations=" + count.getAndSet(0));
    tx.get().commit();
    tx.set(null);
  }

  @Override
  public void onCatchup(Projection p) {
    commit();
  }

  private boolean shouldCommit() {
    return count.incrementAndGet() >= redisTransactional.size();
  }
}
