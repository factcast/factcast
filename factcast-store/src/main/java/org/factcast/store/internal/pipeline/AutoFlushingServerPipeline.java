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

import com.google.common.base.Preconditions;
import java.util.*;
import lombok.*;

public class AutoFlushingServerPipeline extends AbstractServerPipeline {
  public static final long AUTOFLUSH_CHECK_INTERVAL = 2000;
  private final long autoflushDelayMs;
  private final Timer timer = new Timer();
  private long lastFlush = 0;

  public AutoFlushingServerPipeline(@NonNull ServerPipeline parent, long autoFlushDelayMs) {
    super(parent);
    Preconditions.checkArgument(
        autoFlushDelayMs >= AUTOFLUSH_CHECK_INTERVAL,
        "autoFlushDelayMs must be >=" + AUTOFLUSH_CHECK_INTERVAL);
    this.autoflushDelayMs = autoFlushDelayMs;
    timer.scheduleAtFixedRate(new FlushTask(), 0, AUTOFLUSH_CHECK_INTERVAL);
  }

  @Synchronized
  @Override
  public synchronized void process(@NonNull Signal s) {
    parent.process(s);

    if (s instanceof Signal.FlushSignal) {
      this.lastFlush = now(); // record the time
    }
  }

  private long now() {
    return System.currentTimeMillis();
  }

  class FlushTask extends TimerTask {
    @Override
    public void run() {
      autoFlush();
    }
  }

  void autoFlush() {
    if (now() - lastFlush > autoflushDelayMs) {
      // we only inject an extra flush, if the last flush (injected or regular) was some time ago..
      //
      // when flushing an empty pipeline, no harm is done
      process(Signal.flush());
    }
  }

  @Override
  public void close() {
    timer.cancel();
    super.close();
  }
}
