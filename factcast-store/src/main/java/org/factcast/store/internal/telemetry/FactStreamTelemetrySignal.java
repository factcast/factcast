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

import lombok.NonNull;
import org.factcast.core.subscription.SubscriptionRequestTO;

public sealed interface FactStreamTelemetrySignal
    permits FactStreamTelemetrySignal.Connect,
        FactStreamTelemetrySignal.Catchup,
        FactStreamTelemetrySignal.FastForward,
        FactStreamTelemetrySignal.Follow,
        FactStreamTelemetrySignal.Complete,
        FactStreamTelemetrySignal.Close {
  record Connect(@NonNull SubscriptionRequestTO request) implements FactStreamTelemetrySignal {}

  record Catchup(@NonNull SubscriptionRequestTO request) implements FactStreamTelemetrySignal {}

  record FastForward(@NonNull SubscriptionRequestTO request) implements FactStreamTelemetrySignal {}

  record Follow(@NonNull SubscriptionRequestTO request) implements FactStreamTelemetrySignal {}

  record Complete(@NonNull SubscriptionRequestTO request) implements FactStreamTelemetrySignal {}

  record Close(@NonNull SubscriptionRequestTO request) implements FactStreamTelemetrySignal {}
}
