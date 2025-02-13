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
package org.factcast.core.subscription;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractSubscription implements Subscription {
  private final List<Runnable> onClose = new LinkedList<>();
  final AtomicBoolean isSubscriptionClosed = new AtomicBoolean(false);

  @Override
  public final Subscription onClose(@NonNull Runnable e) {
    onClose.add(e);
    return this;
  }

  private void tryRun(@NonNull Runnable e) {
    try {
      e.run();
    } catch (Exception ex) {
      log.error("While executing onClose:", ex);
    }
  }

  public abstract void internalClose();

  @Override
  public void close() {
    if (!isSubscriptionClosed.getAndSet(true)) {
      try {
        internalClose();
      } finally {
        onClose.forEach(this::tryRun);
      }
    }
  }
}
