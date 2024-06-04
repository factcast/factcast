/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.store.internal.telemetry;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import java.util.concurrent.Executors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FactStreamTelemetryPublisher {

  private final EventBus eventBus;

  public FactStreamTelemetryPublisher() {
    this(
        new AsyncEventBus(
            FactStreamTelemetryPublisher.class.getSimpleName(),
            Executors.newSingleThreadExecutor()));
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
