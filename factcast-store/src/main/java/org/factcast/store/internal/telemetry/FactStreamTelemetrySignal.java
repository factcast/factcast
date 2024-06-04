package org.factcast.store.internal.telemetry;

import lombok.NonNull;
import org.factcast.core.subscription.SubscriptionRequestTO;

public sealed interface FactStreamTelemetrySignal permits
    FactStreamTelemetrySignal.Connect,
    FactStreamTelemetrySignal.Catchup,
    FactStreamTelemetrySignal.FastForward,
    FactStreamTelemetrySignal.Follow,
    FactStreamTelemetrySignal.Complete,
    FactStreamTelemetrySignal.Close
{
  record Connect(@NonNull SubscriptionRequestTO request) implements FactStreamTelemetrySignal { }
  record Catchup(@NonNull SubscriptionRequestTO request) implements FactStreamTelemetrySignal { }
  record FastForward(@NonNull SubscriptionRequestTO request) implements FactStreamTelemetrySignal { }
  record Follow(@NonNull SubscriptionRequestTO request) implements FactStreamTelemetrySignal { }
  record Complete(@NonNull SubscriptionRequestTO request) implements FactStreamTelemetrySignal { }
  record Close(@NonNull SubscriptionRequestTO request) implements FactStreamTelemetrySignal { }
}
