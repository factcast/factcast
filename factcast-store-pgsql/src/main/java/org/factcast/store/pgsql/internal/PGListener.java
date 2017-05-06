package org.factcast.store.pgsql.internal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.annotation.Scheduled;

import com.google.common.base.Supplier;
import com.google.common.eventbus.EventBus;
import com.impossibl.postgres.api.jdbc.PGConnection;

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
class PGListener implements InitializingBean, DisposableBean {

    static final String LISTEN_SQL = "LISTEN " + PGConstants.CHANNEL_NAME;

    final Supplier<PGConnection> connectionSupplier;

    final @NonNull EventBus eventBus;

    final @NonNull Predicate<Connection> connectionTester;

    PGConnection connection = null;

    @Scheduled(fixedRate = 10000)
    public synchronized void check() {
        if (!connectionTester.test(connection)) {
            log.warn("Reconnecting");
            Connection oldConnection = connection;
            CompletableFuture.runAsync(() -> {
                try {
                    oldConnection.close();
                } catch (Throwable e) {
                    // silently swallow, connection is probably gone anyway
                }
            });

            listen();
        }
    }

    private synchronized void listen() {

        try {
            this.connection = connectionSupplier.get();
            connection.addNotificationListener(this.getClass().getSimpleName(),
                    PGConstants.CHANNEL_NAME, (pid, name, msg) -> {
                        log.trace("Recieved event from pg name={} message={}", name, msg);
                        eventBus.post(new FactInsertionEvent(name));

                    });
            try (PreparedStatement statement = connection.prepareStatement(LISTEN_SQL);) {
                statement.execute();
            }

        } catch (Throwable e) {
            log.error("Unable to retrieve jdbc Connection: ", e);
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
    public synchronized void destroy() throws Exception {
        if (connection != null) {
            try {
                connection.removeNotificationListener(this.getClass().getSimpleName());
                connection.close();
            } catch (SQLException e) {
                log.warn("During shutdown: ", e);
            }
        }
        connection = null;
    }

}
