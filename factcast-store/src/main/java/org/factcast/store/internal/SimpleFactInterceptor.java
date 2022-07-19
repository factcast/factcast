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
