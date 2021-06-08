package org.factcast.factus.redis;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.redisson.api.RedissonClient;

@Slf4j
public abstract class AbstractRedisLens implements ProjectorLens {
  private final AtomicInteger count = new AtomicInteger();
  private final AtomicLong start = new AtomicLong(0);
  protected final Class<? extends Projection> projectionName;
  protected int batchSize = 1;
  protected long flushTimeout = 0;
  protected final RedissonClient client;

  public AbstractRedisLens(RedisProjection projection) {
    projectionName = projection.getClass();
    client = projection.redisson();
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
        || ((flushTimeout > 0) && (System.currentTimeMillis() - start.get() > flushTimeout));
  }

  @Override
  public void onCatchup(Projection p) {
    flush();
    // disable batching from here on
    if (isBatching()) {
      log.debug("Disabling batching after catchup for {}", projectionName);
      batchSize = 1;
      flushTimeout = 0;
    }
  }

  private boolean isBatching() {
    return batchSize > 1;
  }

  @Override
  public boolean skipStateUpdate() {
    return isBatching() && !shouldFlush();
  }

  void flush() {

    if (batchSize > 1) {
      start.set(0);
      int processed = count.getAndSet(0);
      log.trace("Flushing on {}, number of facts processed={}", projectionName, processed);
    }
    doFlush();
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

    doClear();
  }

  protected abstract void doClear();

  protected abstract void doFlush();
}
