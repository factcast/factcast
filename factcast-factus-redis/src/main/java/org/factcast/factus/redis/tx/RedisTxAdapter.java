/*
 * Copyright © 2017-2024 factcast.org
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

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.factus.projection.tx.TransactionAdapter;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.redisson.api.TransactionOptions;

@RequiredArgsConstructor
public class RedisTxAdapter implements TransactionAdapter<RTransaction> {
  @NonNull private final RedissonClient client;
  @Nullable private final RedisTransactional annotation;

  @Override
  @NonNull
  public RTransaction beginNewTransaction() {
    return client.createTransaction(transactionOptions());
  }

  @VisibleForTesting
  @NonNull
  public TransactionOptions transactionOptions() {
    if (annotation != null) {
      return RedisTransactional.Defaults.with(annotation);
    } else {
      return TransactionOptions.defaults();
    }
  }

  @Override
  public void commit(@NonNull RTransaction runningTransaction) {
    runningTransaction.commit();
  }

  @Override
  public void rollback(@NonNull RTransaction runningTransaction) {
    runningTransaction.rollback();
  }

  @Override
  public int maxBatchSizePerTransaction() {
    if (annotation != null) {
      return annotation.bulkSize();
    } else {
      return RedisTransactional.DEFAULT_BULK_SIZE;
    }
  }
}
