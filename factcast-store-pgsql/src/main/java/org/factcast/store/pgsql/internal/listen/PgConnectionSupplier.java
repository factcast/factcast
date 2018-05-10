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

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import javax.inject.Inject;
import javax.sql.DataSource;

import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.postgresql.jdbc.PgConnection;
import org.springframework.stereotype.Component;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgConnectionSupplier {

    @NonNull
    private final org.apache.tomcat.jdbc.pool.DataSource ds;

    @Inject
    PgConnectionSupplier(@NonNull DataSource dataSource) {

        if (dataSource instanceof org.apache.tomcat.jdbc.pool.DataSource) {
            this.ds = (org.apache.tomcat.jdbc.pool.DataSource) dataSource;
        } else {
            throw new IllegalStateException("expected "
                    + org.apache.tomcat.jdbc.pool.DataSource.class.getName() + " , but got "
                    + dataSource.getClass().getName());
        }
    }

    public PgConnection get() throws SQLException {
        try {
            return (PgConnection) DriverManager.getDriver(ds.getUrl())
                    .connect(ds.getUrl(), buildCredentialProperties(ds));
        } catch (SQLException e) {
            final String msg = "Cannot acquire Connection from DriverManager: " + ds.getUrl();
            log.error(msg, e);
            throw e;
        }
    }

    @VisibleForTesting
    Properties buildCredentialProperties(org.apache.tomcat.jdbc.pool.DataSource ds) {

        Properties dbp = new Properties();
        final PoolConfiguration poolProperties = ds.getPoolProperties();
        if (poolProperties != null) {
            final String user = poolProperties.getUsername();
            if (user != null) {
                dbp.setProperty("user", user);
            }
            final String pwd = poolProperties.getPassword();
            if (pwd != null) {
                dbp.setProperty("password", pwd);
            }
        }

        return dbp;
    }

}
