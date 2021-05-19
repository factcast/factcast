package org.factcast.factus.redis;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.NonNull;
import lombok.val;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.redisson.api.TransactionOptions;

public class RedissonTXManager {

  private final AtomicReference<RTransaction> tx = new AtomicReference<>();
  private final RedissonClient redisson;

  public RedissonTXManager(@NonNull RedissonClient redisson) {
    this.redisson = redisson;
  }

  public RTransaction getOrCreate() {
    startOrJoin();
    return tx.get();
  }

  private RTransaction createTransaction() {
    return redisson.createTransaction(TransactionOptions.defaults());
  }

  public void join(Consumer<RTransaction> block) {
    block.accept(getOrCreate());
  }

  public <R> R join(Function<RTransaction, R> block) {
    return block.apply(getOrCreate());
  }

  public void joinOrAutoCommit(Consumer<RTransaction> block) {
    val commit = startOrJoin();
    try {
      join(block);
    } catch (RuntimeException e) {
      rollback();
      throw e;
    } finally {
      if (tx.get() != null && commit) {
        commit();
      }
    }
  }

  public <R> R joinOrAutoCommit(Function<RTransaction, R> block) {
    val commit = startOrJoin();
    try {
      return join(block);
    } catch (RuntimeException e) {
      rollback();
      throw e;
    } finally {
      if (tx.get() != null && commit) {
        commit();
      }
    }
  }

  /** @return true if tx was started, false if there was one running */
  private boolean startOrJoin() {
    return tx.getAndUpdate(t -> t == null ? createTransaction() : t) == null;
  }

  public void commit() {
    startOrJoin();
    tx.getAndSet(null).commit();
  }

  public void rollback() {
    startOrJoin();
    tx.getAndSet(null).rollback();
  }
}
