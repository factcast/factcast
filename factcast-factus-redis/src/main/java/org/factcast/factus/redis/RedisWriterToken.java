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
package org.factcast.factus.redis;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import jakarta.annotation.Nullable;
import java.time.Duration;
import java.util.concurrent.atomic.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.projection.WriterToken;
import org.redisson.api.*;

@Slf4j
public class RedisWriterToken implements WriterToken {
  private static final long CHECK_INTERVAL = Duration.ofSeconds(15).toMillis();
  private final @NonNull RFencedLock lock;

  @Getter(AccessLevel.PROTECTED)
  @VisibleForTesting
  private @Nullable AtomicLong liveness;

  @Getter(AccessLevel.PROTECTED)
  @VisibleForTesting
  private final Long token;

  public RedisWriterToken(@NonNull RFencedLock lock) {
    Preconditions.checkArgument(lock.isLocked());
    this.lock = lock;
    this.token = lock.getToken();
    liveness = new AtomicLong(System.currentTimeMillis());
  }

  @Override
  public void close() {
    if (lockedAndOwned()) {
      lock.forceUnlock();
    }
    liveness = null;
  }

  private boolean lockedAndOwned() {
    return lock.isLocked() && lock.getToken().equals(token);
  }

  @Override
  public boolean isValid() {
    if (alreadyClosed()) {
      return false; // it'll never come back
    }
    long lastCheck = liveness.get();
    if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL) {
      return true;
    } else {
      // recheck
      if (lockedAndOwned()) {
        liveness.set(System.currentTimeMillis());
        return true;
      } else {
        close();
        return false;
      }
    }
  }

  boolean alreadyClosed() {
    return liveness == null;
  }
}
