/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.server.ui.metrics;

import java.util.function.Supplier;
import lombok.NonNull;

public interface UiMetrics {
  /** The time it takes to execute the specified plugin for one fact. */
  void timePluginExecution(@NonNull String pluginDisplayName, @NonNull Runnable r);

  /** The overall processing time of one fact with all the plugins */
  <T> T timeFactProcessing(@NonNull Supplier<T> r);

  class NOP implements UiMetrics {

    @Override
    public void timePluginExecution(@NonNull String pluginDisplayName, @NonNull Runnable r) {
      r.run();
    }

    @Override
    public <T> T timeFactProcessing(@NonNull Supplier<T> r) {
      return r.get();
    }
  }
}
