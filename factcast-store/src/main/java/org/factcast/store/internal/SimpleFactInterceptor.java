package org.factcast.store.internal;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.FactTransformers;
import org.factcast.core.subscription.transformation.TransformationRequest;
import org.factcast.store.internal.filter.FactFilter;

import lombok.NonNull;

public class SimpleFactInterceptor extends AbstractFactInterceptor {
  private final FactTransformerService service;
  private final FactTransformers transformers;
  private final FactFilter filter;
  private final SubscriptionImpl targetSubscription;

  public SimpleFactInterceptor(
      @NonNull FactTransformerService service,
      @NonNull FactTransformers transformers,
      @NonNull FactFilter filter,
      @NonNull SubscriptionImpl targetSubscription,
      @NonNull PgMetrics metrics) {
    super(metrics);
    this.service = service;
    this.transformers = transformers;
    this.filter = filter;
    this.targetSubscription = targetSubscription;
  }

  public void accept(@NonNull Fact f) {
    if (filter.test(f)) {

      TransformationRequest transformationRequest = transformers.prepareTransformation(f);

      if (transformationRequest == null) {
        targetSubscription.notifyElement(f);
      } else {
        Fact transformed = service.transform(transformationRequest);
        targetSubscription.notifyElement(transformed);
      }

      increaseNotifyMetric(1);
    }
  }
}
