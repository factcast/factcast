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
import java.util.concurrent.atomic.AtomicReference;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressWarnings("unused")
public class LocalWriteToken {

  private final PseudoLock lock = new PseudoLock();

  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    Duration interval = maxWait.dividedBy(10);
    int seconds = 0;
    while (!maxWait.minusSeconds(seconds).isNegative()) {
      if (lock.tryLock()) {
        return lock::unlock;
      }
      try {

        TimeUnit.SECONDS.sleep(1);
      } catch (InterruptedException e) {
        // nobody cares
      }
      seconds++;
    }
    return null;
  }

  @SneakyThrows
  private static void sleep(Duration interval) {
    Thread.sleep(interval.toMillis());
  }

  public boolean isValid() {
    return lock.isLocked();
  }

  public static class PseudoLock {

    private final AtomicReference<Boolean> mylock = new AtomicReference<>(Boolean.FALSE);

    boolean tryLock() {
      return mylock.compareAndSet(Boolean.FALSE, Boolean.TRUE);
    }

    void unlock() {
      boolean done = mylock.compareAndSet(Boolean.TRUE, Boolean.FALSE);
      if (!done) {
        throw new IllegalStateException("Cannot unlock an unlocked thread");
      }
    }

    boolean isLocked() {
      return mylock.get();
    }
  }
}


