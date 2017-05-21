package org.factcast.store.pgsql.internal.listen;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

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

    final @NonNull Supplier<PgConnection> ds;

    final @NonNull EventBus eventBus;

    final @NonNull Predicate<Connection> pgConnectionTester;

    final AtomicBoolean running = new AtomicBoolean(true);

    Thread listenerThread;

    private int blockingWaitTimeInMillis = 1000 * 60;

    private void listen() {
        log.trace("Starting instance Listener");

        CountDownLatch l = new CountDownLatch(1);

        listenerThread = new Thread(() -> {
            while (running.get()) {
                // make sure, we did not miss anything while reconnecting
                postEvent("scheduled-poll");

                try (PgConnection pc = ds.get()) {
                    try (PreparedStatement ps = pc.prepareStatement(PGConstants.LISTEN_SQL);) {
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
                            log.warn("Connection is failing test");
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                            }
                            break;
                        }
                    }

                } catch (SQLException e) {
                    if (e.getMessage().contains("administrator command")) {
                        log.warn("While waiting for Notifications", e);
                    } else {
                        log.warn("While waiting for Notifications", e);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e1) {
                        }
                    }
                }
            }

        }, "PG Instance Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
        try {
            l.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
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
    public void afterPropertiesSet() throws Exception {
        listen();
    }

    @Override
    public void destroy() throws Exception {
        this.running.set(false);
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
    }

}
