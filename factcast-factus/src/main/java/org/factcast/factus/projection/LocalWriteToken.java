/*
 * Copyright © 2017-2020 factcast.org
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressWarnings("unused")
class LocalWriteToken {

  private final ReentrantLock lock = new ReentrantLock(true);

  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    try {
      if (lock.tryLock(maxWait.toMillis(), TimeUnit.MILLISECONDS)) {
        return lock::unlock;
      }
    } catch (InterruptedException e) {
      log.warn("while trying to aquire write token", e);
    }
    return null;
  }
}
