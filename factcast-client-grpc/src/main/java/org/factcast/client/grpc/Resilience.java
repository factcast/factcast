/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.client.grpc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.client.grpc.FactCastGrpcClientProperties.ResilienceConfiguration;

@Slf4j
@RequiredArgsConstructor
class Resilience {
  @NonNull private final ResilienceConfiguration config;
  private final List<Long> timestampsOfReconnectionAttempts =
      Collections.synchronizedList(new ArrayList<>());

  boolean attemptsExhausted() {
    int attempts = numberOfAttemptsInWindow();
    return attempts >= config.getAttempts();
  }

  int numberOfAttemptsInWindow() {
    long now = System.currentTimeMillis();
    // remove all older reconnection attempts
    timestampsOfReconnectionAttempts.removeIf(t -> now - t > config.getWindow().toMillis());
    return timestampsOfReconnectionAttempts.size();
  }

  void registerAttempt() {
    timestampsOfReconnectionAttempts.add(System.currentTimeMillis());
  }

  boolean shouldRetry(Throwable exception) {
    if (config.isEnabled()) {

      boolean exhausted = attemptsExhausted();
      if (exhausted) {
        log.warn(
            "Allowed attempts in window are exhausted! Attempts: {}, allowed: {}.",
            numberOfAttemptsInWindow(),
            config.getAttempts());
        return false;
      }

      boolean retryable = ClientExceptionHelper.isRetryable(exception);
      if (retryable) {
        log.debug("Exception of type {} is considered retryable.", exception.getClass(), exception);
        return true;
      } else {
        log.debug(
            "Exception of type {} is not considered retryable.", exception.getClass(), exception);
      }
    } else {
      log.trace("Resilience is disabled.");
    }
    return false;
  }

  void sleepForInterval() {
    try {
      Thread.sleep(config.getInterval().toMillis());
    } catch (InterruptedException ignore) {
      Thread.currentThread().interrupt();
    }
  }
}
