package org.factcast.factus.redis;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.redisson.api.TransactionOptions;

public class RedissonTxManager {

  private static final ThreadLocal<Map<RedissonClient, RedissonTxManager>> holder =
      ThreadLocal.withInitial((Supplier<Map<RedissonClient, RedissonTxManager>>) HashMap::new);

  public static RedissonTxManager get(RedissonClient c) {
    Map<RedissonClient, RedissonTxManager> map = getMap();
    return map.computeIfAbsent(c, RedissonTxManager::new);
  }

  public boolean inTransaction() {
    return currentTx != null;
  }

  // TODO needed?
  public static void destroy(RedissonClient c) {
    Map<RedissonClient, RedissonTxManager> map = getMap();
    map.remove(c);
  }

  private static Map<RedissonClient, RedissonTxManager> getMap() {
    return holder.get();
  }

  // not atomicref needed here as this class is used threadbound anyway
  private RTransaction currentTx;
  private final RedissonClient redisson;

  private RedissonTxManager(@NonNull RedissonClient redisson) {
    this.redisson = redisson;
  }

  @Nullable
  public RTransaction getCurrentTransaction() {
    return currentTx;
  }

  public void join(Consumer<RTransaction> block) {
    startOrJoin();
    block.accept(currentTx);
  }

  public <R> R join(Function<RTransaction, R> block) {
    startOrJoin();
    return block.apply(currentTx);
  }

  public void joinOrAutoCommit(Consumer<RTransaction> block) {
    boolean commit = startOrJoin();
    try {
      join(block);
    } catch (RuntimeException e) {
      commit = false;
      rollback();
      throw e;
    } finally {
      if (commit) {
        commit();
      }
    }
  }

  public <R> R joinOrAutoCommit(Function<RTransaction, R> block) {
    boolean commit = startOrJoin();
    try {
      return join(block);
    } catch (RuntimeException e) {
      commit = false;
      rollback();
      throw e;
    } finally {
      if (commit) {
        commit();
      }
    }
  }

  /** @return true if tx was started, false if there was one running */
  public boolean startOrJoin() {
    if (currentTx == null) {
      currentTx =
          redisson.createTransaction(TransactionOptions.defaults().timeout(30, TimeUnit.SECONDS));
      return true;
    } else {
      return false;
    }
  }

  public void commit() {
    startOrJoin();
    currentTx.commit();
    currentTx = null;
  }

  public void rollback() {
    startOrJoin();
    currentTx.rollback();
    currentTx = null;
  }
}
