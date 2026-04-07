/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.core.subscription;

import java.util.*;
import lombok.NonNull;
import org.factcast.core.spec.FactSpec;

/**
 * Defines a request for Subscription.
 *
 * <p>see {@link FluentSubscriptionRequest}, {@link SubscriptionRequestTO}
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
public interface SubscriptionRequest {

  long maxBatchDelayInMs();

  long keepaliveIntervalInMs();

  boolean streamInfo();

  boolean continuous();

  boolean ephemeral();

  Optional<UUID> startingAfter();

  List<FactSpec> specs();

  String debugInfo();

  String pid();

  static SpecBuilder builder() {
    return FluentSubscriptionRequest.builder();
  }

  // ------------ static factory methods ------------
  // basically shortcuts for creating and followin/catching up a default builder.
  static SpecBuilder follow(@NonNull FactSpec specification) {
    return follow(Collections.singletonList(specification));
  }

  static SpecBuilder follow(@NonNull Collection<FactSpec> specification) {
    return FluentSubscriptionRequest.builder().follow(specification);
  }

  static SpecBuilder catchup(@NonNull FactSpec specification) {
    return catchup(Collections.singletonList(specification));
  }

  static SpecBuilder catchup(@NonNull Collection<FactSpec> specification) {
    return FluentSubscriptionRequest.builder().catchup(specification);
  }

  /**
   * use builder().follow(spec).withMaxBatchDelay(maxBatchDelayInMs) instead
   *
   * @param maxBatchDelayInMs
   * @param specification
   * @return
   */
  @Deprecated
  static SpecBuilder follow(long maxBatchDelayInMs, @NonNull FactSpec specification) {
    return FluentSubscriptionRequest.builder()
        .withMaxBatchDelayInMs(maxBatchDelayInMs)
        .follow(specification);
  }
}
