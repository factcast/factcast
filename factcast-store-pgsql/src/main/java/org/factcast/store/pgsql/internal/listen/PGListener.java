package org.factcast.store.pgsql.internal.listen;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.factcast.store.pgsql.PGConfigurationProperties;
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

    final @NonNull DataSource ds;

    final @NonNull EventBus eventBus;

    final @NonNull PGConfigurationProperties props;

    final AtomicBoolean running = new AtomicBoolean(true);

    Thread listenerThread;

    @SuppressWarnings("resource")
    private void listen() {
        log.trace("Starting instance Listener");

        listenerThread = new Thread(() -> {
            while (running.get()) {
                try (Connection c = ds.getConnection();) {
                    try (PreparedStatement ps = c.prepareStatement(PGConstants.LISTEN_SQL);) {
                        log.trace("Running LISTEN command");
                        ps.execute();
                    }

                    try (Connection actual = ((javax.sql.PooledConnection) c).getConnection();) {
                        PgConnection pc = (PgConnection) actual;
                        log.trace("Waiting for notifications for {}ms", props
                                .getNotificationWaitTimeInMillis());

                        PGNotification[] notifications = pc.getNotifications((int) props
                                .getNotificationWaitTimeInMillis());
                        if (!running.get()) {
                            return;
                        }

                        if (notifications != null && notifications.length > 0) {
                            final String name = notifications[0].getName();
                            log.trace("notifying consumers for '{}'", name);
                            postEvent(name);
                        } else {
                            log.trace("No notifications yet. Looping.");
                            postEvent("scheduled-poll");
                        }
                    }
                } catch (SQLException e) {
                    log.warn("While waiting for Notifications", e);
                }
            }

        }, "PG Instance Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
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
        listenerThread.interrupt();
    }

}
