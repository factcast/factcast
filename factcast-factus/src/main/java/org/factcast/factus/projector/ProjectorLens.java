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
package org.factcast.factus.projector;

import java.util.function.*;

import org.factcast.core.Fact;
import org.factcast.factus.projection.Projection;

/**
 * Instance created from a ProjectorPlugin to modify the behaviour of the projector. The lifecycle
 * is bound to the subscription.
 */
public interface ProjectorLens {
  /**
   * provide a function to call in order to create/lookup/provide an instance for the parameters of
   * the given type.
   *
   * @param type
   * @return null if type is not handled by this lens.
   */
  default Function<Fact, ?> parameterTransformerFor(Class<?> type) {
    return f -> null;
  }

  default void beforeFactProcessing(Fact f) {}

  /**
   * called after the handler method and the subsequent setting of the state terminated without
   * exceptions
   *
   * @param f
   */
  default void afterFactProcessing(Fact f) {}

  /**
   * cleanup call. the lens is NOT supposed to throw the given throwable or any exception on its
   * own.
   *
   * @param f
   */
  default void afterFactProcessingFailed(Fact f, Throwable justForInformation) {}

  /**
   * let the lens know of the change of phases (used for altering behavior between phases, for
   * instance on batching)
   *
   * @param p
   */
  default void onCatchup(Projection p) {}

  default boolean skipStateUpdate() {
    return false;
  }

  void onCancel();
}
