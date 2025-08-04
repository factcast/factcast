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

  /**
   * Checks if the lock is held and returns true if successful, and false if the lock cannot be
   * obtained.
   */
  @Override
  public boolean isValid() {
    return lockProvider.lock(lockConfiguration).isPresent();
    //        if (lock.isEmpty()) {
    //            log.debug("Lock is no longer valid, it has been released or cannot be obtained.");
    //            return false;
    //        }
    //
    //      return true;
    //    } catch (IllegalStateException e) {
    //      log.debug("Failed to assert lock, it is no longer valid: {}", e.getMessage());
    //    }
    //    return false;
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
