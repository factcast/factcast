/*
 * Copyright © 2017-2026 factcast.org
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

import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import lombok.NonNull;

public class PushbackServerPipeline implements ServerPipeline {
  private final ServerPipeline delegate;
  private final AtomicBoolean isCLosed = new AtomicBoolean(false);

  public PushbackServerPipeline(@Nonnull ServerPipeline chain) {
    this.delegate = chain;
  }

  @Override
  public void process(@NonNull Signal s) throws PipelineAlreadyClosedException {
    if (isCLosed.get()) throw new PipelineAlreadyClosedException();
    delegate.process(s);
  }

  @Override
  public void close() {
    isCLosed.set(true);
    delegate.close();
  }
}
