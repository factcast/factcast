package org.factcast.factus.spring.tx;

import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

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
}
