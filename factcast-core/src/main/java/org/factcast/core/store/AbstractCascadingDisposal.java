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
package org.factcast.core.store;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractCascadingDisposal implements CascadingDisposal {
  private final Set<WeakReference<Listener>> listeners = new CopyOnWriteArraySet<>();

  public void register(@NonNull Listener s) {
    this.listeners.add(new WeakReference<>(s));
  }

  public void unregister(@NonNull Listener s) {
    this.listeners.removeIf(r -> r.get() == s);
  }

  @Override
  public void disposeAll() {
    listeners.forEach(
        l -> {
          Listener listener = l.get();
          if (listener != null) {
            try {
              listener.onDispose();
            } catch (Exception e) {
              log.debug("While cascading disposal to " + listener.toString() + ":", e);
            }
          }
          // it is copy on write, so this does not break the iteration
          listeners.remove(l);
        });
  }
}
