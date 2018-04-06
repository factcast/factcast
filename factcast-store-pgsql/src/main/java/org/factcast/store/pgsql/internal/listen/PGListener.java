package org.factcast.store.pgsql.internal.listen;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.factcast.store.pgsql.internal.PGConstants;
import org.postgresql.PGNotification;
import org.postgresql.jdbc.PgConnection;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import com.google.common.eventbus.EventBus;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens (sql LISTEN command) to a channel on Postgresql and passes a trigger
 * on an EventBus.
 * 
 * This trigger then is supposed to "encourage" active subscriptions to query
 * for new Facts from PG.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PGListener implements InitializingBean, DisposableBean {

    final @NonNull PgConnectionSupplier pgConnectionSupplier;

    final @NonNull EventBus eventBus;

    final @NonNull Predicate<Connection> pgConnectionTester;

    final AtomicBoolean running = new AtomicBoolean(true);

    private Thread listenerThread;

    private int blockingWaitTimeInMillis = 1000 * 60;

    private void listen() {
        log.trace("Starting instance Listener");

        CountDownLatch l = new CountDownLatch(1);

        listenerThread = new Thread(() -> {
            while (running.get()) {
                // make sure, we did not miss anything while reconnecting
                postEvent("scheduled-poll");

                try (PgConnection pc = pgConnectionSupplier.get()) {
                    try (PreparedStatement ps = pc.prepareStatement(PGConstants.LISTEN_SQL)) {
                        log.trace("Running LISTEN command");
                        ps.execute();
                    }

                    while (running.get()) {

                        if (pgConnectionTester.test(pc)) {

                            log.trace("Waiting for notifications for {}ms",
                                    blockingWaitTimeInMillis);

                            l.countDown();
                            PGNotification[] notifications = pc.getNotifications(
                                    blockingWaitTimeInMillis);

                            if (notifications != null && notifications.length > 0) {
                                final String name = notifications[0].getName();
                                log.trace("notifying consumers for '{}'", name);
                                postEvent(name);
                            } else {
                                log.trace("No notifications yet. Looping.");
                            }
                        } else {
                            log("Connection is failing test", null);
                            sleepUnlessTest(1000);
                            break;
                        }
                    }

                } catch (SQLException e) {

                    log("While waiting for Notifications", e);
                    sleepUnlessTest(1000);

                }
            }

        }, "PG Instance Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
        listenerThread.setUncaughtExceptionHandler((t, e) -> log.error("thread " + t
                + " encountered an unhandled exception", e));
        try {
            l.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
    }

    private void sleepUnlessTest(int i) {
        try {
            Thread.sleep(inJunitTest() ? Math.min(50, i) : i);
        } catch (InterruptedException e) {
        }
    }

    private void log(String msg, SQLException e) {
        if (inJunitTest()) {
            log.trace(msg, e);
        } else {
            log.warn(msg, e);
        }
    }

    private boolean inJunitTest() {
        return Package.getPackage("org.junit") != null;
    }

    private void postEvent(final String name) {
        if (running.get()) {
            eventBus.post(new FactInsertionEvent(name));
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class FactInsertionEvent {
        @SuppressWarnings("unused")
        final String name;
    }

    @Override
    public void afterPropertiesSet() {
        listen();
    }

    @Override
    public void destroy() {
        this.running.set(false);
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
    }

}
