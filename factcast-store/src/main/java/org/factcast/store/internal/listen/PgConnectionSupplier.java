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
package org.factcast.store.internal.listen;

import com.google.common.annotations.VisibleForTesting;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.postgresql.jdbc.PgConnection;

@Slf4j
public class PgConnectionSupplier {

  @NonNull @VisibleForTesting protected final org.apache.tomcat.jdbc.pool.DataSource ds;
  @NonNull @VisibleForTesting protected final Properties props;

  public PgConnectionSupplier(DataSource dataSource) {
    if (org.apache.tomcat.jdbc.pool.DataSource.class.isAssignableFrom(dataSource.getClass())) {
      ds = (org.apache.tomcat.jdbc.pool.DataSource) dataSource;
      props = buildPgConnectionProperties(ds);
    } else {
      throw new IllegalArgumentException(
          "expected "
              + org.apache.tomcat.jdbc.pool.DataSource.class.getName()
              + " , but got "
              + dataSource.getClass().getName());
    }
  }

  @SuppressWarnings("resource")
  public PgConnection get() throws SQLException {
    try {
      return DriverManager.getDriver(ds.getUrl())
          .connect(ds.getUrl(), props)
          .unwrap(PgConnection.class);
    } catch (SQLException e) {
      String msg = "Cannot acquire Connection from DriverManager: " + ds.getUrl();
      log.error(msg, e);
      throw e;
    }
  }

  private void setProperty(Properties dbp, String propertyName, String value) {
    if (value != null) {
      dbp.setProperty(propertyName, value);
    }
  }

  @VisibleForTesting
  Properties buildPgConnectionProperties(org.apache.tomcat.jdbc.pool.DataSource ds) {
    Properties dbp = new Properties();
    PoolConfiguration poolProperties = ds.getPoolProperties();
    if (poolProperties != null) {
      setProperty(dbp, "user", poolProperties.getUsername());
      setProperty(dbp, "password", poolProperties.getPassword());

      Properties connectionProperties = poolProperties.getDbProperties();

      if (connectionProperties != null) {
        try {
          // restore all custom properties
          connectionProperties.forEach((k, v) -> setProperty(dbp, (String) k, (String) v));

          // the sockettimeout is explicitly set to 0 due to the long lifetime of the connection for
          // NOTIFY/LISTEN and CURSOR usage reasons.
          String socketTimeout = connectionProperties.getProperty("socketTimeout");
          if (socketTimeout != null && !"0".equals(socketTimeout)) {
            log.info("Supressed JDBC SocketTimeout parameter for long running connections");
          }

          setProperty(dbp, "socketTimeout", "0");

          // disable statement caching
          setProperty(dbp, "preparedStatementCacheQueries", "0");
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException(
              "illegal connectionProperties: " + connectionProperties);
        }
      }
    }
    return dbp;
  }
}
