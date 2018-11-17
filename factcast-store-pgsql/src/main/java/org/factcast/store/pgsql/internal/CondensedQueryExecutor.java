/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.store.pgsql.internal;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.factcast.store.pgsql.internal.listen.PGListener.FactInsertionEvent;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.Subscribe;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes a given runnable if triggered, but ignores all subsequent triggers
 * for maxDelayInMillis.
 *
 * @author uwe.schaefer@mercateo.com
 */
@Slf4j
@RequiredArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__(@VisibleForTesting))
class CondensedQueryExecutor {

    final long maxDelayInMillis;

    final PGSynchronizedQuery target;

    final Supplier<Boolean> connectionStateSupplier;

    Timer timer = new Timer(CondensedQueryExecutor.class.getSimpleName() + ".timer", true);

    final AtomicBoolean currentlyScheduled = new AtomicBoolean(false);

    public void trigger() {
        if (connectionStateSupplier.get()) {
            if (maxDelayInMillis < 1) {
                runTarget();
            } else if (!currentlyScheduled.getAndSet(true)) {
                timer.schedule(new TimerTask() {

                    @Override
                    public void run() {
                        currentlyScheduled.set(false);
                        try {
                            CondensedQueryExecutor.this.runTarget();
                        } catch (Throwable e) {
                            log.debug("Scheduled query failed, closing: ", e.getMessage());
                            // TODO needed?
                        }
                    }
                }, maxDelayInMillis);
            }
        }
    }

    // called by the EventBus
    @Subscribe
    public void onEvent(FactInsertionEvent ev) {
        trigger();
    }

    protected void runTarget() {
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
