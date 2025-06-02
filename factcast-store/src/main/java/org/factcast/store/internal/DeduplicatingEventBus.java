/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.store.internal;

import com.google.common.eventbus.AsyncEventBus;
import java.util.*;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;
import org.factcast.store.internal.notification.*;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class DeduplicatingEventBus extends AsyncEventBus {

  private static final Object DUMMY = new Object();
  private final Map<String, Object> dedupIdTrail =
      Collections.synchronizedMap(new LRUMap<>(1024, 32));

  public DeduplicatingEventBus(@NotNull String identifier, @NotNull Executor executor) {
    super(identifier, executor);
  }

  @Override
  @SuppressWarnings("java:S6201") // not yet
  public void post(@NotNull Object event) {
    if (event instanceof StoreNotification sn) {

      if (event instanceof FactTruncationNotification) {
        dedupIdTrail.clear();
      }

      // no else here, there might be someone else listening to truncations
      String id = sn.uniqueId();
      if (id != null && dedupIdTrail.put(id, DUMMY) != null) {
        log.trace("Ignoring StoreNotification as duplicate: id={}, notification={}", id, event);
        return; // early exit
      }
    }
    // either not identifiable StoreNotification, or first time
    super.post(event);
  }
}
