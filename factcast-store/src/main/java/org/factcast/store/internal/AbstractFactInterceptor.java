package org.factcast.store.internal;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractFactInterceptor implements FactInterceptor {
  @NonNull final PgMetrics metrics;

  protected void increaseNotifyMetric(int count) {
    metrics.counter(StoreMetrics.EVENT.CATCHUP_FACT).increment(count);
  }
}
