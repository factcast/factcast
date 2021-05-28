package org.factcast.factus.redis;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.redisson.api.TransactionOptions;

public class RedissonTxManager {

  private final AtomicReference<RTransaction> tx = new AtomicReference<>();
  private final RedissonClient redisson;

  public RedissonTxManager(@NonNull RedissonClient redisson) {
    this.redisson = redisson;
  }

  @Nullable
  public RTransaction get() {
    System.out.println("get() " + tx.get());
    return tx.get();
  }

  protected RTransaction createTransaction() {
    // TODO check for timeout
    RTransaction t = redisson.createTransaction(TransactionOptions.defaults());
    System.out.println("createTransaction() " + t);
    return t;
  }

  public void join(Consumer<RTransaction> block) {
    System.out.println("join() " + tx.get());
    startOrJoin();
    System.out.println("join() after create " + tx.get());
    block.accept(tx.get());
  }

  public <R> R join(Function<RTransaction, R> block) {
    startOrJoin();
    return block.apply(tx.get());
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
    RTransaction t = tx.get();
    System.out.println("entering startorjoin " + t);
    new Exception().printStackTrace();

    if (t == null) {
      t = createTransaction();
      tx.set(t);
      return true;
    } else {
      return false;
    }

    // return tx.getAndUpdate(t -> t == null ? createTransaction() : t) == null;
  }

  public void commit() {
    System.out.println("commit() " + tx.get());
    startOrJoin();
    System.out.println("commit() after create" + tx.get());
    tx.getAndSet(null).commit();
    System.out.println("commit() after commit " + tx.get());
  }

  public void rollback() {
    startOrJoin();
    tx.getAndSet(null).rollback();
  }
}
