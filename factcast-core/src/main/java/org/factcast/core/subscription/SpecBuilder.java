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
import javax.annotation.Nullable;
import lombok.NonNull;
import org.factcast.core.spec.FactSpec;

public interface SpecBuilder {

  @NonNull
  SpecBuilder or(@NonNull FactSpec specification);

  @NonNull
  SubscriptionRequest from(@NonNull UUID id);

  @NonNull
  SubscriptionRequest fromNullable(@Nullable UUID id);

  @NonNull
  SubscriptionRequest fromScratch();

  @NonNull
  SubscriptionRequest fromNowOn();

  /**
   * Start at the server-selected publication-safe horizon, then resume from a usable UUID on
   * reconnect. The observer must handle fast-forward callbacks if it needs to save this boundary.
   * The boundary is available only when fast-forward is negotiated and the horizon has a UUID.
   * Without a UUID, reconnect selects now again; it does not replay history. This client-only
   * policy does not guarantee that buffered facts have been processed when delivery returns.
   */
  @NonNull
  default SubscriptionRequest fromNowOnAndResume() {
    SubscriptionRequestTO request = SubscriptionRequestTO.from(fromNowOn());
    request.stateAwareEphemeral(true);
    return request;
  }

  // mutators
  @NonNull
  SpecBuilder follow(@NonNull Collection<FactSpec> specification);

  @NonNull
  default SpecBuilder follow(@NonNull FactSpec specification) {
    return follow(Collections.singletonList(specification));
  }

  @NonNull
  SpecBuilder catchup(@NonNull Collection<FactSpec> specification);

  @NonNull
  default SpecBuilder catchup(@NonNull FactSpec specification) {
    return catchup(Collections.singletonList(specification));
  }

  @NonNull
  SpecBuilder withKeepaliveIntervalInMs(long msec);

  @NonNull
  SpecBuilder withMaxBatchDelayInMs(long msec);

  @NonNull
  SpecBuilder withDebugHintFrom(@NonNull Class<?> clazz);
}
