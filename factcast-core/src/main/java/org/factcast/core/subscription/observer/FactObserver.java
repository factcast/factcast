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
package org.factcast.core.subscription.observer;

import java.util.*;
import lombok.Generated;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.subscription.FactStreamInfo;
import org.slf4j.LoggerFactory;

/**
 * Callback interface to use when subscribing to Facts from FactCast.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@Generated // sneakily skip coverage generation
public interface FactObserver {

  void onNext(@NonNull Fact element);

  default void onFastForward(@NonNull UUID factIdToFfwdTo) {}

  default void onFactStreamInfo(@NonNull FactStreamInfo info) {}

  default void onCatchup() {
    // implement if you are interested in that event
  }

  default void onComplete() {
    // implement if you are interested in that event
  }

  default void onError(@NonNull Throwable exception) {
    LoggerFactory.getLogger(FactObserver.class).warn("Unhandled onError:", exception);
  }

  // since 0.7:

  // overwriting allows for more efficient processing
  default void onNext(@NonNull List<Fact> batch) {
    batch.forEach(this::onNext);
  }
}
