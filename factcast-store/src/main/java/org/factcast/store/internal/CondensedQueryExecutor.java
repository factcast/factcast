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
import java.util.concurrent.atomic.*;
import java.util.function.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.spec.FactSpec;
import org.factcast.store.internal.listen.PgListener.FactInsertionSignal;

/**
 * Executes a given runnable if triggered, but ignores all subsequent triggers for maxDelayInMillis.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@SuppressWarnings("UnstableApiUsage")
@Slf4j
class CondensedQueryExecutor {

  private final long maxDelayInMillis;

  private final PgSynchronizedQuery target;

  private final Supplier<Boolean> connectionStateSupplier;
  private final Set<String> interests;

  private Timer timer = new Timer(CondensedQueryExecutor.class.getSimpleName() + ".timer", true);

  private final AtomicBoolean currentlyScheduled = new AtomicBoolean(false);

  @VisibleForTesting
  CondensedQueryExecutor(
      long maxDelayInMillis,
      @NonNull PgSynchronizedQuery target,
      @NonNull Supplier<Boolean> connectionStateSupplier,
      @NonNull List<FactSpec> specs,
      @NonNull Timer timer) {
    this.maxDelayInMillis = maxDelayInMillis;
    this.target = target;
    this.connectionStateSupplier = connectionStateSupplier;
    this.timer = timer;
    interests = extractInterests(specs);
  }

  public CondensedQueryExecutor(
      long maxDelayInMillis,
      @NonNull PgSynchronizedQuery target,
      @NonNull Supplier<Boolean> connectionStateSupplier,
      @NonNull List<FactSpec> specs) {
    this.maxDelayInMillis = maxDelayInMillis;
    this.target = target;
    this.connectionStateSupplier = connectionStateSupplier;
    interests = extractInterests(specs);
  }

  private Set<String> extractInterests(List<FactSpec> specs) {
    HashSet<String> set = new HashSet<>();
    specs.forEach(
        s -> {
          if (s.type() == null) {
            set.add(s.ns());
          } else {
            set.add(s.ns() + ":" + s.type());
          }
        });
    return set;
  }

  public void trigger() {
    if (connectionStateSupplier.get()) {
      if (maxDelayInMillis < 1) {
        runTarget();
      } else if (!currentlyScheduled.getAndSet(true)) {
        timer.schedule(
            new TimerTask() {

              @Override
              public void run() {
                currentlyScheduled.set(false);
                try {
                  runTarget();
                } catch (Throwable e) {
                  log.error("Scheduled query failed, closing: {}", e.getMessage());
                }
              }
            },
            maxDelayInMillis);
      }
    }
  }

  // called by the EventBus
  @Subscribe
  public void onEvent(FactInsertionSignal ev) {

    String ns = ev.ns();
    String type = ev.type();

    // only trigger if necessary
    if (mightMatch(ns, type)) {
      trigger();
    }
  }

  @VisibleForTesting
  boolean mightMatch(String ns, String type) {
    return ns == null
        ||
        // listens to this exact type
        interests.contains(ns + ":" + type)
        ||
        // listens to the whole namespace
        interests.contains(ns)
        ||
        // is a catchall
        interests.contains("*");
  }

  @SuppressWarnings("WeakerAccess")
  protected synchronized void runTarget() {
    try {
      target.run(false);
    } catch (Throwable e) {
      log.error("cannot run Target: ", e);
    }
  }

  public void cancel() {
    currentlyScheduled.set(true);
    timer.cancel();
    timer.purge();
    // make sure, the final run did not flip again
    currentlyScheduled.set(true);
  }
}
