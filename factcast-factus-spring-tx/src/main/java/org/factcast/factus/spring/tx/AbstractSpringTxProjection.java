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

import javax.annotation.*;
import lombok.NonNull;
import lombok.experimental.Delegate;
import org.factcast.core.*;
import org.factcast.factus.projection.tx.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.*;

abstract class AbstractSpringTxProjection implements SpringTxProjection {

  @Delegate private final TransactionBehavior<TransactionStatus> tx;

  protected AbstractSpringTxProjection(
      @NonNull PlatformTransactionManager platformTransactionManager) {
    this.tx =
        new TransactionBehavior<>(
            new SpringTxAdapter(
                platformTransactionManager, getClass().getAnnotation(SpringTransactional.class)));
  }

  /**
   * For SpringTx it purely depends on which thread calls this method as transactions are thread
   * bound in SpringTX.
   *
   * @return
   */
  @Nullable
  @Override
  public FactStreamPosition factStreamPositionInTransaction() {
    assertInTransaction();
    return factStreamPosition();
  }

  /**
   * For SpringTx it purely depends on which thread calls this method as transactions are thread
   * bound in SpringTX.
   *
   * @return
   */
  @Override
  public void factStreamPositionInTransaction(@NonNull FactStreamPosition factStreamPosition) {
    assertInTransaction();
    factStreamPosition(factStreamPosition);
  }

  public void assertInTransaction() throws TransactionException {
    if (!TransactionSynchronizationManager.isActualTransactionActive())
      throw new TransactionNotRunningException("Transaction is not running");
  }
}
