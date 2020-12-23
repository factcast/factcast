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
package org.factcast.itests.factus.proj;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import lombok.val;
import org.factcast.factus.projection.WriterToken;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

public class RedisWriterToken implements WriterToken {
  private final @NonNull RLock lock;
  private final Timer timer = new Timer();
  private final AtomicBoolean liveness;

  public RedisWriterToken(@NonNull RedissonClient redisson, @NonNull RLock lock) {
    this.lock = lock;
    liveness = new AtomicBoolean(lock.isLocked());
    val watchDogTimeout = redisson.getConfig().getLockWatchdogTimeout();
    TimerTask timerTask =
        new TimerTask() {
          @Override
          public void run() {
            liveness.set(lock.isLocked());
          }
        };
    timer.scheduleAtFixedRate(timerTask, 0, (long) (watchDogTimeout / 1.5));
  }

  @Override
  public void close() throws Exception {
    lock.unlock();
    timer.cancel();
  }

  @Override
  public boolean isValid() {
    return liveness.get();
  }
}
