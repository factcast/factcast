package org.factcast.factus.redis.batch;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.Setter;
import org.redisson.api.BatchOptions;
import org.redisson.api.RBatch;
import org.redisson.api.RedissonClient;

public class RedissonBatchManager {

  private static final ThreadLocal<Map<RedissonClient, RedissonBatchManager>> holder =
      ThreadLocal.withInitial((Supplier<Map<RedissonClient, RedissonBatchManager>>) HashMap::new);

  private static Map<RedissonClient, RedissonBatchManager> getMap() {
    return holder.get();
  }

  public static RedissonBatchManager get(@NonNull RedissonClient client) {
    Map<RedissonClient, RedissonBatchManager> map = getMap();
    return map.computeIfAbsent(client, RedissonBatchManager::new);
  }

  @Setter private BatchOptions options;

  // no atomicref needed here as this class is used threadbound anyway
  private RBatch currentBatch;
  private final RedissonClient redisson;

  RedissonBatchManager(@NonNull RedissonClient redisson) {
    this.redisson = redisson;
  }

  public boolean inBatch() {
    return currentBatch != null;
  }

  @Nullable
  public RBatch getCurrentBatch() {
    return currentBatch;
  }

  public void join(Consumer<RBatch> block) {
    startOrJoin();
    block.accept(currentBatch);
  }

  public <R> R join(Function<RBatch, R> block) {
    startOrJoin();
    return block.apply(currentBatch);
  }

  /** @return true if tx was started, false if there was one running */
  public boolean startOrJoin() {
    if (currentBatch == null) {
      currentBatch = redisson.createBatch(options);
      return true;
    } else {
      return false;
    }
  }

  public void execute() {
    if (currentBatch != null) {
      currentBatch.execute();
      currentBatch = null;
    }
  }

  public void discard() {
    if (currentBatch != null) {
      currentBatch.discard();
      currentBatch = null;
    }
  }
}
