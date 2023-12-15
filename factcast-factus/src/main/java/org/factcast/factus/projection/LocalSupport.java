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
package org.factcast.factus.projection;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.projection.tx.TransactionException;
import org.factcast.factus.projection.tx.TransactionalProjection;

@UtilityClass
public class LocalSupport {

  @Slf4j
  @SuppressWarnings({"unused", "java:S2142"})
  static class WriteToken {

    private final ReentrantLock lock = new ReentrantLock(true);

    public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
      try {
        if (lock.tryLock(maxWait.toMillis(), TimeUnit.MILLISECONDS)) {
          return lock::unlock;
        }
      } catch (InterruptedException e) {
        log.warn("while trying to acquire write token", e);
      }
      return null;
    }

    public boolean isValid() {
      return lock.isLocked();
    }
  }

  static class Transaction implements TransactionalProjection<LocalProjectorContext> {

    private UUID pos = null;

    @Override
    @NonNull
    public LocalProjectorContext begin() throws TransactionException {
      return new LocalProjectorContext(); // TODO maybe reuse?
    }

    @Override
    public void commit(@NonNull LocalProjectorContext ctx) throws TransactionException {
      // nothing to do
    }

    @Override
    public void rollback(@NonNull LocalProjectorContext ctx) throws TransactionException {
      throw new UnsupportedOperationException("Cannot rollback from LocalTransaction");
    }

    @Override
    public void factStreamPosition(@NonNull UUID position, @NonNull LocalProjectorContext ctx) {
      pos = position;
    }

    @Override
    public @Nullable UUID factStreamPosition() {
      return pos;
    }
  }

  public static class LocalProjectorContext implements ProjectorContext {}
}
