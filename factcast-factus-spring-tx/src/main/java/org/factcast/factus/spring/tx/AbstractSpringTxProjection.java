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

import lombok.NonNull;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.Named;
import org.factcast.factus.projection.WriterTokenAware;
import org.factcast.factus.projection.tx.AbstractTransactionAwareProjection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

abstract class AbstractSpringTxProjection
    extends AbstractTransactionAwareProjection<TransactionStatus>
    implements SpringTxProjection, FactStreamPositionAware, WriterTokenAware, Named {
  private final PlatformTransactionManager platformTransactionManager;

  protected AbstractSpringTxProjection(
      @NonNull PlatformTransactionManager platformTransactionManager) {
    this.platformTransactionManager = platformTransactionManager;
  }

  @Override
  public PlatformTransactionManager platformTransactionManager() {
    return platformTransactionManager;
  }

  @Override
  protected @NonNull TransactionStatus beginNewTransaction() {
    return platformTransactionManager().getTransaction(transactionOptions());
  }

  @Override
  protected void rollback(@NonNull TransactionStatus runningTransaction) {
    platformTransactionManager().rollback(runningTransaction);
  }

  @Override
  protected void commit(@NonNull TransactionStatus runningTransaction) {
    platformTransactionManager().commit(runningTransaction);
  }

  protected final @NonNull TransactionDefinition transactionOptions() {
    SpringTransactional tx = getClass().getAnnotation(SpringTransactional.class);
    if (tx != null) return SpringTransactional.Defaults.with(tx);
    else return SpringTransactional.Defaults.create();
  }

  @Override
  public final int maxBatchSizePerTransaction() {
    SpringTransactional tx = getClass().getAnnotation(SpringTransactional.class);
    if (tx == null || tx.bulkSize() < 1) {
      return super.maxBatchSizePerTransaction();
    } else return tx.bulkSize();
  }
}
