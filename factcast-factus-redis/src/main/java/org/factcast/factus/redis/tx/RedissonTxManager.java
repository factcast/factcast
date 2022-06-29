/*
 * Copyright © 2017-2022 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.factus.redis.tx;

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

public class RedissonTxManager {

  private static final ThreadLocal<Map<RedissonClient, RedissonTxManager>> holder =
      ThreadLocal.withInitial((Supplier<Map<RedissonClient, RedissonTxManager>>) HashMap::new);

  public static RedissonTxManager get(@NonNull RedissonClient c) {
    Map<RedissonClient, RedissonTxManager> map = getMap();
    return map.computeIfAbsent(c, RedissonTxManager::new);
  }

  private static Map<RedissonClient, RedissonTxManager> getMap() {
    return holder.get();
  }

  @Setter private TransactionOptions options = RedisTransactional.Defaults.create();

  public boolean inTransaction() {
    return currentTx != null;
  }

  // no atomicref needed here as this class is used threadbound anyway
  private RTransaction currentTx;
  private final RedissonClient redisson;

  RedissonTxManager(@NonNull RedissonClient redisson) {
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

  /** @return true if tx was started, false if there was one running */
  public boolean startOrJoin() {
    if (currentTx == null) {
      currentTx = redisson.createTransaction(options);
      return true;
    } else {
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
