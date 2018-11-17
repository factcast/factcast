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
 * <p>
 * This trigger then is supposed to "encourage" active subscriptions to query
 * for new Facts from PG.
 *
 * @author uwe.schaefer@mercateo.com
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PGListener implements InitializingBean, DisposableBean {

    @NonNull
    final PgConnectionSupplier pgConnectionSupplier;

    @NonNull
    final EventBus eventBus;

    @NonNull
    final Predicate<Connection> pgConnectionTester;

    private final AtomicBoolean running = new AtomicBoolean(true);

    private Thread listenerThread;

    private final int blockingWaitTimeInMillis = 1000 * 60;

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
                            throw new SQLException("Connection is failing test");
                        }
                    }
                } catch (SQLException e) {
                    log.warn("While waiting for Notifications", e);
                    sleep();
                }
            }
        }, "PG Instance Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
        listenerThread.setUncaughtExceptionHandler((t, e) -> log.error("thread " + t
                + " encountered an unhandled exception", e));
        try {
            l.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    private void sleep() {
        try {
            Thread.sleep(1000);
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
