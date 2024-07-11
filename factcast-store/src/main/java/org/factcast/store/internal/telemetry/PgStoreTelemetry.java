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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.internal.PgMetrics;

@Slf4j
public final class PgStoreTelemetry {

  private final EventBus eventBus;

  public PgStoreTelemetry(@NonNull PgMetrics metrics) {
    // needs to be a singleThread executor to ensure that the order of events is respected,
    // singleThreadExec however is not observable by micrometer
    this(metrics.monitor(Executors.newFixedThreadPool(1), "telemetry"));
  }

  @VisibleForTesting
  PgStoreTelemetry(@NonNull ExecutorService executorService) {
    this(new AsyncEventBus(PgStoreTelemetry.class.getSimpleName(), executorService));
  }

  @VisibleForTesting
  PgStoreTelemetry(@NonNull EventBus eventBus) {
    this.eventBus = eventBus;
  }

  public void register(@NonNull Object listener) {
    log.debug("Registering listener: {}", listener.getClass().getName());
    eventBus.register(listener);
  }

  public void onConnect(@NonNull SubscriptionRequestTO request) {
    eventBus.post(new Connect(request));
  }

  public void onCatchup(@NonNull SubscriptionRequestTO request) {
    eventBus.post(new Catchup(request));
  }

  public void onFollow(@NonNull SubscriptionRequestTO request) {
    eventBus.post(new Follow(request));
  }

  public void onComplete(@NonNull SubscriptionRequestTO request) {
    eventBus.post(new Complete(request));
  }

  public void onClose(@NonNull SubscriptionRequestTO request) {
    eventBus.post(new Close(request));
  }

  public record Connect(@NonNull SubscriptionRequestTO request) {}

  public record Catchup(@NonNull SubscriptionRequestTO request) {}

  public record Follow(@NonNull SubscriptionRequestTO request) {}

  public record Complete(@NonNull SubscriptionRequestTO request) {}

  public record Close(@NonNull SubscriptionRequestTO request) {}
}
