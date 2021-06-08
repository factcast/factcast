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
import org.redisson.api.RBatch;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;

@Slf4j
public class RedisTransactionalLens implements ProjectorLens {

  private final AtomicInteger count = new AtomicInteger();
  private final AtomicLong start = new AtomicLong(0);
  private final Class<? extends Projection> projectionName;
  private int batchSize = 1;
  private long timeout = 0;
  private final RedissonClient client;

  RedisTransactionalLens(@NonNull RedisProjection p) {
    client = p.redisson();

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

  @Override
  public Function<Fact, ?> parameterTransformerFor(Class<?> type) {
    if (RTransaction.class.equals(type)) {
      return f -> {
        RedissonTxManager tx = RedissonTxManager.get(client);

        if (RedissonBatchManager.get(client).inBatch()) {
          throw new IllegalStateException(
              "You cannot mix Transactional and Batching behavior on projections. Offending: "
                  + projectionName);
        }

        if (!tx.inTransaction()) {
          tx.startOrJoin();
        }

        RTransaction curr = tx.getCurrentTransaction();
        log.debug("pulling RTransaction pararmeter: " + curr);
        return curr;
      };
    }

    if (RBatch.class.equals(type)) {
      return f -> {
        RedissonBatchManager batch = RedissonBatchManager.get(client);
        if (RedissonTxManager.get(client).inTransaction()) {
          throw new IllegalStateException(
              "You cannot mix Transactional and Batching behavior on projections. Offending: "
                  + projectionName);
        }

        if (!batch.inBatch()) {
          batch.startOrJoin();
        }

        RBatch rBatch = batch.getCurrentBatch();
        log.debug("pulling RBatch pararmeter " + rBatch);
        return rBatch;
      };
    }
    return null;
  }

  @Override
  public void beforeFactProcessing(Fact f) {
    if (batchSize > 1) {
      start.getAndUpdate(l -> l > 0 ? l : System.currentTimeMillis());
    }
  }

  @Override
  public void afterFactProcessing(Fact f) {
    count.incrementAndGet();
    if (shouldFlush()) {
      flush();
    }
  }

  private boolean shouldFlush() {
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

    RedissonTxManager tx = RedissonTxManager.get(client);
    if (tx.inTransaction()) {
      RedissonTxManager.get(client).rollback();
    }

    RedissonBatchManager bm = RedissonBatchManager.get(client);
    if (bm.inBatch()) {
      bm.discard();
    }
  }

  private void flush() {

    if (batchSize > 1) {
      start.set(0);
      int processed = count.getAndSet(0);
      log.trace("Flushing on {}, number of facts processed={}", projectionName, processed);
    }

    // otherwise we can silently commit, not to flush the logs
    RedissonTxManager tx = RedissonTxManager.get(client);
    if (tx.inTransaction()) {
      log.trace("commiting tx");
      RedissonTxManager.get(client).commit();
    }

    RedissonBatchManager bm = RedissonBatchManager.get(client);
    if (bm.inBatch()) {
      log.trace("executing batch");
      bm.execute();
    }
  }

  @Override
  public void onCatchup(Projection p) {
    flush();
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
    return isBatching() && !shouldFlush();
  }
}
