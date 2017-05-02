package org.factcast.store.pgsql.internal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.impossibl.postgres.api.jdbc.PGConnection;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Used to test if a connection is still alive.
 * 
 * Even though CPools provide this already, this one is intended to used with
 * the cone {@link PGConnection}, that listens to changes on the fact table and
 * thus should not be reused in a CPool.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */

@Slf4j

class ConnectionTester implements Predicate<Connection> {

    final Counter connectionFailureMetric;

    ConnectionTester(@NonNull MetricRegistry registry) {
        connectionFailureMetric = registry.counter(new PGMetricNames().connectionFailure());
    }

    @Override
    public boolean test(@Nonnull Connection connection) {
        if (connection != null) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT 42");
                    ResultSet resultSet = statement.executeQuery();) {
                resultSet.next();
                if (resultSet.getInt(1) == 42) {
                    log.trace("Connection test passed");
                    return true;
                } else {
                    log.trace("Connection test failed");
                }
            } catch (SQLException e) {
                log.warn("Connection test failed with exception", e);
            }
        }
        connectionFailureMetric.inc();
        return false;
    }
}
