/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.factus.mongodb;

import java.time.Duration;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.*;
import org.factcast.factus.projection.WriterToken;

// TODO consider making this a general ShedlockWriterToken implementation
@Slf4j
public class MongoDbWriterToken implements WriterToken {
  private @NonNull SimpleLock lock;
  private LockProvider lockProvider;
  private LockConfiguration lockConfiguration;

  protected MongoDbWriterToken(
      @NonNull SimpleLock lock,
      @NonNull LockProvider lockProvider,
      LockConfiguration lockConfiguration) {
    this.lockProvider = lockProvider;
    this.lock = lock;
    this.lockConfiguration = lockConfiguration;
  }

  // TODO: test if this behaves like expected or if multiple threads fight over the same lock
  /**
   * Attempts to extend the lock, which will only succeed of the lock is either free or held by the
   * instance.
   */
  @Override
  public boolean isValid() {
    try {
      Optional<SimpleLock> extendedLock =
          lock.extend(Duration.ofSeconds(60), Duration.ofSeconds(1));
      if (extendedLock.isPresent()) {
        this.lock = extendedLock.get();
        return true;
      }
    } catch (IllegalStateException e) {
      log.debug("Failed to extend lock, it is no longer valid: {}", e.getMessage());
    }
    return false;
  }

  @Override
  public void close() {
    try {
      lock.unlock();
    } catch (IllegalStateException e) {
      log.warn("Failed to unlock, it is no longer valid: {}", e.getMessage());
    }
  }
}
