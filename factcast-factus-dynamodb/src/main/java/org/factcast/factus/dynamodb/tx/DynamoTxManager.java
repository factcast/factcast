package org.factcast.factus.dynamodb.tx;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.Setter;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.redisson.api.TransactionOptions;

public class DynamoTxManager {

  private static final ThreadLocal<Map<RedissonClient, DynamoTxManager>> holder =
          ThreadLocal.withInitial((Supplier<Map<RedissonClient, DynamoTxManager>>) HashMap::new);

  public static DynamoTxManager get(@NonNull RedissonClient c) {
    Map<RedissonClient, DynamoTxManager> map = getMap();
    return map.computeIfAbsent(c, DynamoTxManager::new);
  }

  private static Map<RedissonClient, DynamoTxManager> getMap() {
    return holder.get();
  }

  @Setter
  private TransactionOptions options = DynamoTransactional.Defaults.create();

  public boolean inTransaction() {
    return currentTx != null;
  }

  // no atomicref needed here as this class is used threadbound anyway
  private RTransaction currentTx;
  private final RedissonClient redisson;

  DynamoTxManager(@NonNull RedissonClient redisson) {
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

  /**
   * @return true if tx was started, false if there was one running
   */
  public boolean startOrJoin() {
    if (currentTx == null) {
      currentTx = redisson.createTransaction(options);
      return true;
    }
    else {
      return false;
    }
  }

  public void commit() {
    if (currentTx != null) {
      try {
        currentTx.commit();
      } finally {
        currentTx = null;
      }
    }
  }

  public void rollback() {
    if (currentTx != null) {
      try {
        currentTx.rollback();
      } finally {
        currentTx = null;
      }
    }
  }
}
