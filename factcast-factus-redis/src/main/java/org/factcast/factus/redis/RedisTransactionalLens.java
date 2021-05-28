package org.factcast.factus.redis;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.factcast.core.Fact;
import org.factcast.factus.projection.BatchApply;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.redisson.api.RTransaction;

@Slf4j
public class RedisTransactionalLens implements ProjectorLens {

  @NonNull private final RedissonTxManager tx;

  private final AtomicInteger count = new AtomicInteger();
  private final AtomicLong start = new AtomicLong(0);
  private final Class<? extends Projection> projectionName;
  private int batchSize = 1;
  private long timeout = 0;

  public RedisTransactionalLens(@NonNull RedissonTxManager tx, Projection p) {
    this.tx = tx;
    val annotation = p.getClass().getAnnotation(BatchApply.class);
    if (annotation != null) {
      batchSize = Math.max(1, annotation.size());
      timeout = Math.max(100, Math.min(20000, annotation.timeoutInMs()));
    }
    projectionName = p.getClass();

    log.debug(
        "Created {} instance for {} with batchsize={},timeout={}",
        getClass().getSimpleName(),
        p,
        batchSize,
        timeout);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Function<Fact, ?> parameterTransformerFor(Class<?> type) {
    if (RTransaction.class.equals(type)) {
      tx.startOrJoin();
      return f -> {
        System.out.println("generated rtx as " + tx.get());
        return tx.get();
      };
    }
    return null;
  }

  @Override
  public void beforeFactProcessing(Fact f) {
    tx.startOrJoin();
    if (batchSize > 1) {
      start.getAndUpdate(l -> l > 0 ? l : System.currentTimeMillis());
    }
  }

  @Override
  public void afterFactProcessing(Fact f) {
    count.incrementAndGet();
    if (shouldCommit()) {
      commit();
    }
  }

  private boolean shouldCommit() {
    return count.get() >= batchSize
        || ((timeout > 0) && (System.currentTimeMillis() - start.get() > timeout));
  }

  @Override
  public void afterFactProcessingFailed(Fact f, Throwable justForInformation) {

    start.set(0);
    int rolledBack = count.getAndSet(0);

    log.warn(
        "Rolling back transaction on {} with number of facts processed={} for fact {} due to ",
        projectionName,
        rolledBack,
        f,
        justForInformation);

    tx.rollback();
  }

  private void commit() {
    if (batchSize > 1) {
      start.set(0);
      int processed = count.getAndSet(0);
      log.trace("Committing tx on {}, number of facts processed={}", projectionName, processed);
    }

    // otherwise we can silently commit, not to flush the logs
    tx.commit();
  }

  @Override
  public void onCatchup(Projection p) {
    commit();
    // disable batching from here on
    if (isBatching()) {
      log.debug("Disabling batching after catchup for {}", projectionName);
      batchSize = 1;
      timeout = 0;
    }
  }

  private boolean isBatching() {
    return batchSize > 1;
  }

  @Override
  public boolean skipStateUpdate() {
    return isBatching() && !shouldCommit();
  }
}
