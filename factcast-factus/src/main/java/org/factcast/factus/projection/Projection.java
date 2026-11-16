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
package org.factcast.factus.projection;

import java.util.Collection;
import lombok.NonNull;
import org.factcast.core.spec.FactSpec;
import org.slf4j.LoggerFactory;

public interface Projection extends ProgressAware {
  default @NonNull Collection<FactSpec> postprocess(
      @NonNull Collection<FactSpec> specsAsDiscovered) {
    return specsAsDiscovered;
  }

  default void onCatchup() {
    // implement if you are interested in that event
  }

  default void onComplete() {
    // implement if you are interested in that event
  }

  default void onError(@NonNull Throwable exception) {
    LoggerFactory.getLogger(getClass()).warn("Unhandled onError:", exception);
  }

  /**
   * Selects the starting point only when no saved progress is available. Saved checkpoints and
   * snapshots always take precedence, so changing this policy does not reset a projection.
   *
   * @return the nonnull initialization policy
   */
  default @NonNull InitialPosition initialPosition() {
    return InitialPosition.FROM_SCRATCH;
  }

  enum InitialPosition {
    /** Replay all matching history when no saved progress exists. */
    FROM_SCRATCH,
    /**
     * Consume after the approximate current latest serial without saving an initial checkpoint.
     * Without saved progress, each new initialization selects now again and can skip intervening
     * events.
     */
    FROM_NOW
  }
}
