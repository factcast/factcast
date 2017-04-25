package org.factcast.store.pgsql.internal;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.factcast.store.pgsql.internal.PGListener.FactInsertionEvent;

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
 * @author usr
 *
 */
@Slf4j
@RequiredArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__(@VisibleForTesting))
class CondensedExecutor {

    private final long maxDelayInMillis;

    private final Runnable target;

    private final Supplier<Boolean> connectionStateSupplier;

    private Timer timer = new Timer(CondensedExecutor.class.getSimpleName() + ".timer", true);

    private final AtomicBoolean currentlyScheduled = new AtomicBoolean(false);

    public void trigger() {
        if (connectionStateSupplier.get()) {
            if (maxDelayInMillis < 1) {
                runTarget();
            } else if (!currentlyScheduled.getAndSet(true)) {
                timer.schedule(new TimerTask() {

                    @Override
                    public void run() {
                        currentlyScheduled.set(false);
                        CondensedExecutor.this.runTarget();
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
            target.run();
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
