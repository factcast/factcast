package org.factcast.factus.redis.batch;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.redisson.api.RBatch;
import org.redisson.api.RedissonClient;

public class RedissonBatchManager {

  private static final ThreadLocal<Map<RedissonClient, RedissonBatchManager>> holder =
      ThreadLocal.withInitial((Supplier<Map<RedissonClient, RedissonBatchManager>>) HashMap::new);

  public boolean inBatch() {
    return currentBatch != null;
  }

  public static RedissonBatchManager get(RedissonClient c) {
    Map<RedissonClient, RedissonBatchManager> map = getMap();
    return map.computeIfAbsent(c, RedissonBatchManager::new);
  }

  // TODO needed?
  public static void destroy(RedissonClient c) {
    Map<RedissonClient, RedissonBatchManager> map = getMap();
    map.remove(c);
  }

  private static Map<RedissonClient, RedissonBatchManager> getMap() {
    return holder.get();
  }

  // not atomicref needed here as this class is used threadbound anyway
  private RBatch currentBatch;
  private final RedissonClient redisson;

  private RedissonBatchManager(@NonNull RedissonClient redisson) {
    this.redisson = redisson;
  }

  @Nullable
  public RBatch getCurrentBatch() {
    return currentBatch;
  }

  public void join(Consumer<RBatch> block) {
    startOrJoin();
    block.accept(currentBatch);
  }

  // TODO does this make any sense?
  public <R> R join(Function<RBatch, R> block) {
    startOrJoin();
    return block.apply(currentBatch);
  }

  public void joinOrAutoCommit(Consumer<RBatch> block) {
    boolean commit = startOrJoin();
    try {
      join(block);
    } catch (RuntimeException e) {
      commit = false;
      discard();
      throw e;
    } finally {
      if (commit) {
        execute();
      }
    }
  }

  public <R> R joinOrAutoCommit(Function<RBatch, R> block) {
    boolean commit = startOrJoin();
    try {
      return join(block);
    } catch (RuntimeException e) {
      commit = false;
      discard();
      throw e;
    } finally {
      if (commit) {
        execute();
      }
    }
  }

  /** @return true if tx was started, false if there was one running */
  public boolean startOrJoin() {
    if (currentBatch == null) {
      currentBatch = redisson.createBatch();
      return true;
    } else {
      return false;
    }
  }

  public void execute() {
    startOrJoin();
    currentBatch.execute();
    currentBatch = null;
  }

  public void discard() {
    startOrJoin();
    currentBatch.discard();
    currentBatch = null;
  }
}
