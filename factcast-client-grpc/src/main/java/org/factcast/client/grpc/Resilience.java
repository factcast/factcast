package org.factcast.client.grpc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.factcast.client.grpc.FactCastGrpcClientProperties.ResilienceConfiguration;

@RequiredArgsConstructor
class Resilience {
  private final ResilienceConfiguration config;
  private final List<Long> timestampsOfReconnectionAttempts =
      Collections.synchronizedList(new ArrayList<>());

  boolean attemptsExhausted() {
    int attempts = numberOfAttemptsInWindow();
    return attempts > config.getRetries();
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
    return ClientExceptionHelper.isRetryable(exception) && !attemptsExhausted();
  }

  void sleepForInterval() {
    try {
      Thread.sleep(config.getInterval().toMillis());
    } catch (InterruptedException ignore) {
      Thread.currentThread().interrupt();
    }
  }
}
