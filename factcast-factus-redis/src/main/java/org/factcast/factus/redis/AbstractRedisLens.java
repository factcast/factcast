package org.factcast.factus.redis;

import com.google.common.annotations.VisibleForTesting;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.redisson.api.RedissonClient;

@Slf4j
@Getter
public abstract class AbstractRedisLens implements ProjectorLens {
  final AtomicInteger count = new AtomicInteger();
  final AtomicLong start = new AtomicLong(0);
  protected final Class<? extends Projection> projectionName;

  @Setter protected int bulkSize = 1;
  @Setter protected long flushTimeout = 0;
  protected final RedissonClient client;

  public AbstractRedisLens(RedisProjection projection, RedissonClient redissonClient) {
    projectionName = projection.getClass();
    client = redissonClient;
  }

  @Override
  public void beforeFactProcessing(Fact f) {
    if (bulkSize > 1) {
      long now = System.currentTimeMillis();
      start.getAndUpdate(
          l -> {
            return l > 0 ? l : now;
          });
    }
  }

  @Override
  public void afterFactProcessing(Fact f) {
    count.incrementAndGet();
    if (shouldFlush()) {
      flush();
    }
  }

  @VisibleForTesting
  public boolean shouldFlush() {
    return shouldFlush(false);
  }

  @VisibleForTesting
  public boolean shouldFlush(boolean withinProcessing) {
    int factsProcessed = count.get();
    if (withinProcessing) {
      // +1 because the increment happens AFTER processing
      factsProcessed++;
    }

    boolean bufferFull = factsProcessed >= bulkSize;
    boolean timedOut = timedOut();
    if (timedOut && !withinProcessing) {
      log.trace(
          "Bulk considered timed out. (Bulk age: {}ms, Bulk timeout: {})",
          System.currentTimeMillis() - start.get(),
          flushTimeout);
    }

    return bufferFull || timedOut;
  }

  private boolean timedOut() {
    return (flushTimeout > 0) && (System.currentTimeMillis() - start.get() > flushTimeout);
  }

  @Override
  public void onCatchup(Projection p) {
    if (count.get() > 0) {
      flush();
    }
    // disable bulk applying from here on
    if (isBulkApplying()) {
      log.debug("Disabling bulk application after catchup for {}", projectionName);
      bulkSize = 1;
      flushTimeout = 0;
    }
  }

  @VisibleForTesting
  public boolean isBulkApplying() {
    return bulkSize > 1;
  }

  @Override
  public boolean skipStateUpdate() {
    boolean bulk = isBulkApplying();
    boolean noFlushNecessary = !shouldFlush(true);
    return bulk && noFlushNecessary;
  }

  public void flush() {
    if (bulkSize > 1) {
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

  void increaseCountForTesting() {
    count.incrementAndGet();
  }
}
