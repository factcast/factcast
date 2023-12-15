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

import java.util.UUID;
import lombok.NonNull;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.ProjectorContext;

/**
 * context can be null (default for vm-local projections)
 *
 * @param <T>
 */
public interface TransactionalProjection<T extends ProjectorContext>
    extends FactStreamPositionAware {
  /**
   * @throws {@link TransactionException}, {@link TransactionAlreadyRunningException}
   */
  @NonNull
  T begin() throws TransactionException;

  /**
   * @throws {@link TransactionException}, {@link TransactionNotRunningException}
   */
  void commit(@NonNull T ctx) throws TransactionException;

  /**
   * @throws {@link TransactionException}, {@link TransactionNotRunningException}
   */
  void rollback(@NonNull T ctx) throws TransactionException;

  void factStreamPosition(@NonNull UUID position, @NonNull T ctx);
}
