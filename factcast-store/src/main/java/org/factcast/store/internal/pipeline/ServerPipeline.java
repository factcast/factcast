/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.store.internal.pipeline;

import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;

/**
 * used on the server side instead of FactObserver/Subscription
 *
 * <p>Note, that pipelines *MUST* maintain the order of signals.
 */
public interface ServerPipeline {

  void process(@NonNull Signal s);

  // convenience

  default void error(@NonNull Throwable err) {
    process(new Signal.ErrorSignal(err));
  }

  default void fastForward(@NonNull FactStreamPosition ffwd) {
    process(new Signal.FastForwardSignal(ffwd));
  }

  default void info(@NonNull FactStreamInfo info) {
    process(new Signal.FactStreamInfoSignal(info));
  }

  default void catchup() {
    process(new Signal.CatchupSignal());
  }

  default void complete() {
    process(new Signal.CompleteSignal());
  }

  default void flush() {
    process(new Signal.FlushSignal());
  }

  default void fact(@NonNull Fact f) {
    process(new Signal.FactSignal(f));
  }
}
