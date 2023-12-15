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
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.projection.ProjectorContext;

@Slf4j
@UtilityClass
public class TransactionSupport { // helpers
  public static <T extends ProjectorContext, P extends TransactionalProjection<T>>
      void inTransaction(@NonNull P p, @NonNull Consumer<T> o) throws TransactionException {

    T ctx = p.begin();
    try {
      o.accept(ctx);
      p.commit(ctx);
    } catch (Exception e) {
      tryRollback(p, ctx);
      throw new TransactionException(e);
    }
  }

  public static <T extends ProjectorContext, P extends TransactionalProjection<T>> void tryRollback(
      @NonNull P p, @NonNull T ctx) throws TransactionException {
    try {
      p.rollback(ctx);
    } catch (Throwable swallow) {
      log.warn("Swallowed a failure during rollback", swallow);
    }
  }
}
