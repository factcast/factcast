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

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
class SpringTxManager {
  @NonNull private final PlatformTransactionManager transactionManager;
  @NonNull private final TransactionDefinition definition;

  @VisibleForTesting protected TransactionStatus currentTx;

  public void startOrJoin() {
    if (currentTx == null) {
      currentTx = transactionManager.getTransaction(definition);
    }
  }

  public void commit() {
    if (currentTx != null) {
      try {
        transactionManager.commit(currentTx);
      } finally {
        currentTx = null;
      }
    } else {
      log.warn("Trying to commit when no Transaction is in scope");
    }
  }

  public void rollback() {
    if (currentTx != null) {
      try {
        transactionManager.rollback(currentTx);
      } finally {
        currentTx = null;
      }
    } else {
      log.warn("Trying to rollback when no Transaction is in scope");
    }
  }

  public boolean isRunning(){return currentTx!=null;}
}
