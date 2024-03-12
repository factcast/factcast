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

import javax.annotation.Nullable;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;

/** used on the server side instead of FactObserver/Subscription */
public interface FactPipeline {
  /**
   * @param fact or null (represents the need to flush)
   */
  void fact(@Nullable Fact fact);

  void info(@NonNull FactStreamInfo info);

  void fastForward(@NonNull FactStreamPosition ffwd);

  void error(@NonNull Throwable err);

  void catchup();

  void complete();
}
