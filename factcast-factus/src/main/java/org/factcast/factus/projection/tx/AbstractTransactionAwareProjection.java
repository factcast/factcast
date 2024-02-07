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

import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projection.parameter.HandlerParameterContributor;
import org.factcast.factus.projection.parameter.HandlerParameterProvider;

// TODO move to factus module
public abstract class AbstractTransactionAwareProjection<T>
    implements TransactionAware, Projection, HandlerParameterContributor {

  private final @NonNull Class<T> typeOfTransaction;
  private T runningTransaction;

  protected AbstractTransactionAwareProjection(Class<T> typeOfTransaction) {
    this.typeOfTransaction = typeOfTransaction;
  }

  @Override
  public final void begin() throws TransactionException {
    assertNotRunning();
    runningTransaction = beginNewTransaction();
  }

  @Override
  public final void commit() throws TransactionException {
    assertRunning();
    try {
      commit(runningTransaction);
    } finally {
      runningTransaction = null;
    }
  }

  @Override
  public final void rollback() throws TransactionException {
    assertRunning();
    try {
      rollback(runningTransaction);
    } finally {
      runningTransaction = null;
    }
  }

  protected final void assertNotRunning() throws TransactionException {
    if (this.runningTransaction != null)
      throw new TransactionAlreadyRunningException("Transaction already running");
  }

  protected final void assertRunning() throws TransactionException {
    if (this.runningTransaction == null)
      throw new TransactionNotRunningException("Transaction is not running");
  }

  @Nullable
  public final HandlerParameterProvider providerFor(
      @NonNull Class<?> type, @NonNull Set<Annotation> annotations) {
    if (typeOfTransaction.equals(type)) {
      return f -> {
        assertRunning();
        return runningTransaction;
      };
    } else return null;
  }

  protected abstract @NonNull T beginNewTransaction();

  protected abstract void rollback(@NonNull T runningTransaction);

  protected abstract void commit(@NonNull T runningTransaction);
}
