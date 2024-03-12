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
package org.factcast.store.internal.pipeline;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;

@RequiredArgsConstructor
public abstract class AbstractFactPipeline implements FactPipeline {
  @NonNull protected final FactPipeline parent;

  @Override
  public void error(@NonNull Throwable err) {
    parent.error(err);
  }

  @Override
  public void fastForward(FactStreamPosition ffwd) {
    parent.fastForward(ffwd);
  }

  @Override
  public void info(@NonNull FactStreamInfo info) {
    parent.info(info);
  }

  @Override
  public void catchup() {
    parent.catchup();
  }

  @Override
  public void complete() {
    parent.complete();
  }
}
