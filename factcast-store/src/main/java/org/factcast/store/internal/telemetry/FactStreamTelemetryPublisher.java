package org.factcast.store.internal.telemetry;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;

@Slf4j
public class FactStreamTelemetryPublisher {

  private final EventBus eventBus;

  public FactStreamTelemetryPublisher() {
    this(new AsyncEventBus(FactStreamTelemetryPublisher.class.getSimpleName(), Executors.newSingleThreadExecutor()));
  }

  public FactStreamTelemetryPublisher(@NonNull EventBus eventBus) {
    this.eventBus = eventBus;
  }

  public void register(@NonNull Object listener) {
    log.debug("Registering listener: {}", listener.getClass().getName());
    eventBus.register(listener);
  }

  public <T extends FactStreamTelemetrySignal> void post(@NonNull T signal) {
    try {
      log.debug("Publishing telemetry signal: {}", signal);
      eventBus.post(signal);
    } catch (Exception e) {
      log.error("Error publishing telemetry signal: {}", signal, e);
    }
  }
}
