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
import java.util.concurrent.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressWarnings("unused")
public class LocalWriteToken {

  private final Semaphore lock = new Semaphore(1);

  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    long end = System.currentTimeMillis() + maxWait.toMillis();
    int seconds = 0;

    do {
      try {
        if (lock.tryAcquire(maxWait.toMillis(), TimeUnit.MILLISECONDS)) {
          return lock::release;
        }
      } catch (InterruptedException e) {
        // nobody cares
      }
    } while (System.currentTimeMillis() < end);
    return null;
  }

  public boolean isValid() {
    return lock.availablePermits() == 0;
  }
}
