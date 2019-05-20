/*
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.postgresql.PGNotification;
import org.postgresql.jdbc.PgConnection;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PgConnectionTester implements Predicate<Connection> {
    private static int MAX_ALLOWED_NOTIFICATION_LATENCY_IN_MILLIS = 150;

    @Override
    public boolean test(@Nonnull Connection connection) {
        return testSelectStatement(connection) && testNotificationRoundTrip(connection);
    }

    @VisibleForTesting
    boolean testNotificationRoundTrip(Connection connection) {
        try {
            connection.prepareCall("LISTEN alive").execute();
            connection.prepareCall("NOTIFY alive").execute();

            PgConnection pc = (PgConnection) connection;
            PGNotification[] notifications = pc.getNotifications(
                    MAX_ALLOWED_NOTIFICATION_LATENCY_IN_MILLIS);
            if ((notifications == null) || (notifications.length == 0)) {
                // missed the notifications from the DB, something is fishy
                // here....
                throw new SQLException("Missed notification from channel 'alive'");
            } else {
                return true;
            }

        } catch (SQLException e) {
            log.warn("Connection test (Notification) failed with exception: {}", e.getMessage());
        } finally {
            try {
                connection.prepareCall("UNLISTEN alive").execute();
            } catch (Exception ignore) {
                // we do not care anymore
            }
        }
        return false;

    }

    @VisibleForTesting
    boolean testSelectStatement(Connection connection) {
        try (PreparedStatement statement = prepareStatement(connection, "SELECT 42");
                ResultSet resultSet = statement.executeQuery()) {

            resultSet.next();
            if (resultSet.getInt(1) == 42) {
                log.trace("Connection test passed (Select)");
                return true;
            } else {
                log.warn("Connection test failed (Select)");
            }
        } catch (SQLException e) {
            log.warn("Connection test (Select) failed with exception: {}", e.getMessage());
        }
        return false;
    }

    private PreparedStatement prepareStatement(Connection connection, String string)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(string);
        statement.setQueryTimeout(1);
        return statement;
    }
}
