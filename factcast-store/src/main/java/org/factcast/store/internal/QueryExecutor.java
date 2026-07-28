/*
 * Copyright © 2017-2020 factcast.org
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.Subscribe;
import java.util.*;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.spec.FactSpec;
import org.factcast.store.internal.notification.FactInsertionNotification;

/**
 * Executes a given runnable if triggered, but ignores all subsequent triggers for maxDelayInMillis.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@Slf4j
class QueryExecutor {

  private final PgSynchronizedQuery target;
  private final Supplier<Boolean> connectionStateSupplier;
  private final Set<String> interests;

  public QueryExecutor(
      @NonNull PgSynchronizedQuery target,
      @NonNull Supplier<Boolean> connectionStateSupplier,
      @NonNull List<FactSpec> specs) {
    this.target = target;
    this.connectionStateSupplier = connectionStateSupplier;
    interests = extractInterests(specs);
  }

  private Set<String> extractInterests(List<FactSpec> specs) {
    HashSet<String> set = new HashSet<>();
    specs.forEach(
        s -> {
          if (s.type() == null || "*".equals(s.type())) {
            set.add(s.ns());
          } else {
            set.add(s.ns() + ":" + s.type());
          }
        });
    return set;
  }

  public void trigger() {
    if (Boolean.TRUE.equals(connectionStateSupplier.get())) {
      runTarget();
    }
  }

  // called by the EventBus
  @Subscribe
  public void onEvent(FactInsertionNotification ev) {
    // only trigger if necessary
    if (mightMatch(ev.ns(), ev.type())) {
      trigger();
    }
  }

  @VisibleForTesting
  boolean mightMatch(String ns, String type) {
    return ns == null
        || // listens to this exact type
        interests.contains(ns + ":" + type)
        || // listens to the whole namespace
        interests.contains(ns)
        || // is a catchall
        interests.contains("*")
        || // listens to the type in any namespace
        interests.contains("*:" + type);
  }

  @SuppressWarnings("WeakerAccess")
  protected synchronized void runTarget() {
    try {
      target.run(false);
    } catch (Exception e) {
      log.error("cannot run Target: ", e);
    }
  }

  public void cancel() {
    // hook for cleaning up, no longer needed
  }
}
