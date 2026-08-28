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

import java.util.*;
import java.util.concurrent.atomic.*;
import javax.annotation.Nonnull;
import lombok.NonNull;
import org.factcast.store.internal.catchup.CatchupDataSource;

public class PushbackServerPipeline {
  private final ServerPipeline delegate;
  private boolean isClosed = false;
  private Set<CatchupDataSource> onCloseListeners = Collections.synchronizedSet(new HashSet<>());

  public PushbackServerPipeline(@Nonnull ServerPipeline chain) {
    this.delegate = chain;
  }

  public synchronized void process(@NonNull Signal s) throws PipelineAlreadyClosedException {
    if (isClosed) throw new PipelineAlreadyClosedException();
    delegate.process(s);
  }

  public synchronized void close() {
    isClosed = true;
    delegate.close();
    // we don't close the datasource, because it will be done in the catchup process.
    onCloseListeners.forEach(CatchupDataSource::cancel);
  }

  // these two methods are use to communicate a close to a datasource in use. Note that due to
  // offloading, there might be two separate DS valid at a time.
  public synchronized void register(@NonNull CatchupDataSource ds)
      throws PipelineAlreadyClosedException {

    if (isClosed) throw new PipelineAlreadyClosedException();
    else if (!onCloseListeners.add(ds))
      throw new IllegalStateException("DS was already registered. This is a weird bug.");
  }

  public synchronized void unregister(@NonNull CatchupDataSource ds) {
    if (!onCloseListeners.remove(ds))
      throw new IllegalStateException("DS was not registered. This is a bug.");
  }

  public synchronized boolean isClosed() {
    return isClosed;
  }
}
