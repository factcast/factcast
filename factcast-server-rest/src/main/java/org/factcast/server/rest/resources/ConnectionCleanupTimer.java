package org.factcast.server.rest.resources;

import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionCleanupTimer {

    private final Duration delay;

    private final Duration period;

    private final FactsObserver observer;

    private final Timer timer;

    void start() {

        TimerTask task = new TimerTask() {

            @Override
            public void run() {
                if (!observer.isConnectionAlive()) {
                    observer.unsubscribe();
                    timer.cancel();
                    log.debug("unsubscribe and close cleanup timer");
                }

            }
        };
        timer.schedule(task, delay.toMillis(), period.toMillis());

    }

    ConnectionCleanupTimer(@NonNull FactsObserver observer) {
        this(new Timer(true), Duration.ofSeconds(10), Duration.ofSeconds(10), observer);
    }

    @VisibleForTesting
    ConnectionCleanupTimer(@NonNull Timer timer, @NonNull Duration delay, @NonNull Duration period,
            @NonNull FactsObserver observer) {
        this.timer = timer;
        this.delay = delay;
        this.period = period;
        this.observer = observer;
    }

}
