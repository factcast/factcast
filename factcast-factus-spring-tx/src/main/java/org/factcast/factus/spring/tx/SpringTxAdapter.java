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
package org.factcast.factus.spring.tx;

import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.factcast.factus.projection.tx.TransactionAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

public class SpringTxAdapter implements TransactionAdapter<TransactionStatus> {

  private final PlatformTransactionManager platformTransactionManager;
  @Nullable private final SpringTransactional annotation;

  public SpringTxAdapter(
      @NonNull PlatformTransactionManager platformTransactionManager,
      @Nullable SpringTransactional annotation) {
    this.platformTransactionManager = platformTransactionManager;
    this.annotation = annotation;
  }

  @Override
  public @NonNull TransactionStatus beginNewTransaction() {
    return platformTransactionManager.getTransaction(transactionOptions());
  }

  @Override
  public void rollback(@NonNull TransactionStatus runningTransaction) {
    platformTransactionManager.rollback(runningTransaction);
  }

  @Override
  public void commit(@NonNull TransactionStatus runningTransaction) {
    platformTransactionManager.commit(runningTransaction);
  }

  protected @NonNull TransactionDefinition transactionOptions() {
    if (annotation != null) {
      return SpringTransactional.Defaults.with(annotation);
    } else {
      return SpringTransactional.Defaults.create();
    }
  }

  @Override
  public int maxBatchSizePerTransaction() {
    if (annotation == null || annotation.bulkSize() < 1) {
      return SpringTransactional.DEFAULT_BULK_SIZE;
    } else {
      return annotation.bulkSize();
    }
  }
}
