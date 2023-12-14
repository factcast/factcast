/*
 * Copyright © 2017-2023 factcast.org
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

import java.util.function.Consumer;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.projection.ContextualProjection;
import org.factcast.factus.projection.ProjectorContext;

public interface TransactionAware<T extends ProjectorContext> {
  /**
   * @throws {@link TransactionException}, {@link TransactionAlreadyRunningException}
   */
  T begin() throws TransactionException;

  /**
   * @throws {@link TransactionException}, {@link TransactionNotRunningException}
   */
  void commit(T t) throws TransactionException;

  /**
   * @throws {@link TransactionException}, {@link TransactionNotRunningException}
   */
  void rollback(T t) throws TransactionException;

  // helpers

  static <T extends ProjectorContext, P extends ContextualProjection<T>> void inTransaction(
      @NonNull P p, @NonNull Consumer<T> o) throws TransactionException {

    T ctx = p.begin();
    try {
      o.accept(ctx);
      p.commit(ctx);
    } catch (Exception e) {
      new TryRollback(p, ctx);
      throw new TransactionException(e);
    }
  }

  @Slf4j
  class TryRollback {
    public <P extends ContextualProjection<T>, T extends ProjectorContext> TryRollback(P p, T ctx) {
      try {
        p.rollback(ctx);
      } catch (Throwable swallow) {
        log.warn("Swallowed a failure", swallow);
      }
    }
  }
}
