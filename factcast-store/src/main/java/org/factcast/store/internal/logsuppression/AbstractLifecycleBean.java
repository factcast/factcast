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
package org.factcast.store.internal.logsuppression;

import org.springframework.context.SmartLifecycle;

public abstract class AbstractLifecycleBean implements SmartLifecycle {
  private boolean running;

  @Override
  public final synchronized void start() {
    if (!running) {
      onStart();
      running = true;
    }
  }

  @Override
  public final synchronized void stop() {
    if (running) {
      onStop();
      running = false;
    }
  }

  @Override
  public final boolean isRunning() {
    return running;
  }

  protected void onStart() {}

  protected void onStop() {}
}
