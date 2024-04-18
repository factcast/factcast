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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.factus.projection.Projection;

public abstract class AbstractTransactionAwareProjection<T>
    implements TransactionAware, Projection {

  @Getter(AccessLevel.PROTECTED)
  private T runningTransaction;

  protected AbstractTransactionAwareProjection() {}

  @Override
  public final void begin() throws TransactionException {
    assertNoRunningTransaction();
    try {
      runningTransaction = beginNewTransaction();
    } catch (Exception e) {
      throw new TransactionException(e);
    }
  }

  @Override
  public final void commit() throws TransactionException {
    assertInTransaction();
    try {
      commit(runningTransaction);
    } catch (Exception e) {
      throw new TransactionException(e);
    } finally {
      runningTransaction = null;
    }
  }

  @Override
  public final void rollback() throws TransactionException {
    assertInTransaction();
    try {
      rollback(runningTransaction);
    } catch (Exception e) {
      throw new TransactionException(e);
    } finally {
      runningTransaction = null;
    }
  }

  protected final void assertNoRunningTransaction() throws TransactionException {
    if (this.runningTransaction != null)
      throw new TransactionAlreadyRunningException("Transaction already running");
  }

  protected final void assertInTransaction() throws TransactionException {
    if (this.runningTransaction == null)
      throw new TransactionNotRunningException("Transaction is not running");
  }

  protected final boolean inTransaction() {
    return runningTransaction != null;
  }

  protected abstract @NonNull T beginNewTransaction();

  protected abstract void rollback(@NonNull T runningTransaction);

  protected abstract void commit(@NonNull T runningTransaction);
}
