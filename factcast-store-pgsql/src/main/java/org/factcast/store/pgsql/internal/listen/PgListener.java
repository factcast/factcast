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
package org.factcast.store.pgsql.internal.listen;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.factcast.store.pgsql.internal.PgConstants;
import org.postgresql.PGNotification;
import org.postgresql.jdbc.PgConnection;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import com.google.common.eventbus.EventBus;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens (sql LISTEN command) to a channel on Postgresql and passes a trigger
 * on an EventBus.
 * <p>
 * This trigger then is supposed to "encourage" active subscriptions to query
 * for new Facts from PG.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@SuppressWarnings("UnstableApiUsage")
@Slf4j
@RequiredArgsConstructor
public class PgListener implements InitializingBean, DisposableBean {

    private static final int MAX_ALLOWED_NOTIFICATION_LATENCY_IN_MILLIS = 200;

    @NonNull
    final PgConnectionSupplier pgConnectionSupplier;

    @NonNull
    final EventBus eventBus;

    @NonNull
    final Predicate<Connection> pgConnectionTester;

    private final AtomicBoolean running = new AtomicBoolean(true);

    private Thread listenerThread;

    private final int blockingWaitTimeInMillis = 1000 * 15;

    private void listen() {
        log.trace("Starting instance Listener");
        CountDownLatch l = new CountDownLatch(1);
        listenerThread = new Thread(() -> {
            while (running.get()) {

                // new connection
                try (PgConnection pc = pgConnectionSupplier.get()) {

                    try (PreparedStatement ps = pc.prepareStatement(PgConstants.LISTEN_SQL)) {
                        ps.execute();
                    }
                    try (PreparedStatement ps = pc.prepareStatement(
                            PgConstants.LISTEN_ROUNDTRIP_CHANNEL_SQL)) {
                        ps.execute();
                    }
                    l.countDown();

                    // make sure, we did not miss anything while
                    // reconnecting,
                    postEvent("scheduled-poll");

                    while (running.get()) {

                        // listen to the real thing or pings
                        PGNotification[] notifications = pc.getNotifications(
                                blockingWaitTimeInMillis);
                        if (notifications == null) {
                            notifications = sendProbeAndWaitForEcho(pc);
                        }

                        if (Arrays.stream(notifications)
                                .anyMatch(n -> PgConstants.CHANNEL_NAME.equals(n.getName()))) {
                            log.trace("notifying consumers for '{}'", PgConstants.CHANNEL_NAME);
                            postEvent(PgConstants.CHANNEL_NAME);
                        } else {
                            log.trace("No notifications yet. Looping.");
                        }

                    }
                } catch (SQLException e) {
                    log.warn("While waiting for Notifications", e);
                    sleep();
                }
            }
        }, "PG Instance Listener");
        listenerThread.setDaemon(true);
        listenerThread.setUncaughtExceptionHandler(
                (t, e) -> log.error("thread " + t + " encountered an unhandled exception", e));
        listenerThread.start();
        try {
            log.info("Waiting to establish postgres listener (max 15sec.)");
            boolean await = l.await(15, TimeUnit.SECONDS);
            log.info("postgres listener " + (await ? "" : "not ") + "established");
        } catch (InterruptedException ignored) {
        }
    }

    private PGNotification[] sendProbeAndWaitForEcho(PgConnection connection) throws SQLException {
        connection.prepareCall(PgConstants.NOTIFY_ROUNDTRIP).execute();
        PGNotification[] notifications = connection.getNotifications(
                MAX_ALLOWED_NOTIFICATION_LATENCY_IN_MILLIS);
        if (notifications == null) {
            // missed the notifications from the DB, something is fishy
            // here....
            throw new SQLException("Missed roundtrip notification from channel '"
                    + PgConstants.ROUNDTRIP_CHANNEL_NAME + "'");
        } else {
            return notifications;
        }
    }

    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignore) {
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
