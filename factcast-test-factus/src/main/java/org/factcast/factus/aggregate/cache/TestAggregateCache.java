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
package org.factcast.factus.aggregate.cache;

import java.time.Duration;
import java.util.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Factus;
import org.factcast.factus.projection.*;
import org.factcast.factus.projector.FactSpecProvider;

@Slf4j
public class TestAggregateCache<A extends Aggregate> extends AbstractAggregateCache<A> {

  private final List<UUID> trail = Collections.synchronizedList(new ArrayList<>());

  public TestAggregateCache(@NonNull Factus factus, @NonNull FactSpecProvider factSpecProvider) {
    super(factus, factSpecProvider);
  }

  public void clearTrail() {
    trail.clear();
  }

  @Override
  protected void invalidate(UUID uuid) {
    super.invalidate(uuid);
    synchronized (trail) {
      trail.add(uuid);
      trail.notifyAll();
    }
  }

  public void waitForInvalidationOf(UUID uuid, Duration timeout)
      throws InvalidationTimeoutException {

    long started = System.currentTimeMillis();

    while (true) {
      long timeLeft = timeout.toMillis() - (System.currentTimeMillis() - started);
      if (trail.contains(uuid)) {
        return;
      }
      if (timeLeft <= 0) {
        throw new InvalidationTimeoutException();
      }

      synchronized (trail) {
        try {
          synchronized (trail) {
            trail.wait(timeLeft);
          }
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  public void waitForInvalidationOf(UUID id) {
    waitForInvalidationOf(id, Duration.ofSeconds(10));
  }

  static class InvalidationTimeoutException extends RuntimeException {}
}
