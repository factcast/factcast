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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.factcast.store.pgsql.internal.metrics.PGMetricNames;
import org.springframework.stereotype.Component;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PGConnectionTester implements Predicate<Connection> {

    final Counter connectionFailureMetric;

    PGConnectionTester(@NonNull MetricRegistry registry) {
        connectionFailureMetric = registry.counter(new PGMetricNames().connectionFailure());
    }

    @Override
    public boolean test(@Nonnull Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 42");
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            if (resultSet.getInt(1) == 42) {
                log.trace("Connection test passed");
                return true;
            } else {
                log.trace("Connection test failed");
            }
        } catch (SQLException e) {
            log.warn("Connection test failed with exception: {}", e.getMessage());
        }
        connectionFailureMetric.inc();
        return false;
    }
}
