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
package org.factcast.factus.projection.tx;

import lombok.*;

@RequiredArgsConstructor
public class TransactionBehavior<T> {
  @NonNull private final TransactionAdapter<T> adapter;

  @Getter
  @Setter(AccessLevel.PRIVATE)
  private T runningTransaction;

  public void begin() throws TransactionException {
    assertNoRunningTransaction();
    try {
      runningTransaction(adapter.beginNewTransaction());
    } catch (Exception e) {
      throw new TransactionException(e);
    }
  }

  public void commit() throws TransactionException {
    assertInTransaction();
    try {
      adapter.commit(runningTransaction());
    } catch (Exception e) {
      throw new TransactionException(e);
    } finally {
      runningTransaction(null);
    }
  }

  public void rollback() throws TransactionException {
    assertInTransaction();
    try {
      adapter.rollback(runningTransaction());
    } catch (Exception e) {
      throw new TransactionException(e);
    } finally {
      runningTransaction(null);
    }
  }

  public void assertNoRunningTransaction() throws TransactionException {
    if (this.runningTransaction() != null) {
      throw new TransactionAlreadyRunningException("Transaction already running");
    }
  }

  public void assertInTransaction() throws TransactionException {
    if (this.runningTransaction() == null) {
      throw new TransactionNotRunningException("Transaction is not running");
    }
  }

  public boolean inTransaction() {
    return runningTransaction() != null;
  }

  public int maxBatchSizePerTransaction() {
    return adapter.maxBatchSizePerTransaction();
  }
}
