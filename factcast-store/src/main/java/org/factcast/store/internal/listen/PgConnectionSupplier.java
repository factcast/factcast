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
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.postgresql.jdbc.PgConnection;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Slf4j
public class PgConnectionSupplier {

  @NonNull @VisibleForTesting protected final org.apache.tomcat.jdbc.pool.DataSource ds;
  @NonNull @VisibleForTesting protected final Properties props;

  public static final String APPLICATION_NAME = "ApplicationName";
  private final String applicationName;

  public PgConnectionSupplier(DataSource dataSource) {
    if (org.apache.tomcat.jdbc.pool.DataSource.class.isAssignableFrom(dataSource.getClass())) {
      ds = (org.apache.tomcat.jdbc.pool.DataSource) dataSource;
      props = buildPgConnectionProperties(ds);
      applicationName = Optional.ofNullable(props.getProperty(APPLICATION_NAME)).orElse("factcast");
    } else {
      throw new IllegalArgumentException(
          "expected "
              + org.apache.tomcat.jdbc.pool.DataSource.class.getName()
              + " , but got "
              + dataSource.getClass().getName());
    }
  }

  public PgConnection getUnpooledConnection(@NonNull String clientId) throws SQLException {
    Properties connectionProps = new Properties();
    connectionProps.putAll(props);
    connectionProps.setProperty(APPLICATION_NAME, applicationName + "|" + clientId);
    return getConnectionFromDriverManager(connectionProps);
  }

  @SneakyThrows
  @SuppressWarnings("java:S2077")
  public Connection getPooledConnection(String clientId) {
    Connection c = ds.getConnection();
    setConnectionApplicationName(clientId, c);
    return c;
  }

  @SuppressWarnings("java:S2077")
  private void setConnectionApplicationName(String clientId, Connection c) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement("SET application_name='" + applicationName + "|" + clientId + "'"); ) {
      ps.execute();
    }
  }

  @SuppressWarnings("resource")
  private PgConnection getConnectionFromDriverManager(Properties props) throws SQLException {
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

  @VisibleForTesting
  @SuppressWarnings("java:S3776") // any alternative makes it harder to read
  Properties buildPgConnectionProperties(org.apache.tomcat.jdbc.pool.DataSource ds) {
    Properties dbp = new Properties();
    PoolConfiguration poolProperties = ds.getPoolProperties();
    if (poolProperties != null) {
      String username = poolProperties.getUsername();
      if (username != null) {
        dbp.setProperty("user", username);
      }
      String password = poolProperties.getPassword();
      if (password != null) {
        dbp.setProperty("password", password);
      }

      Properties connectionProperties = poolProperties.getDbProperties();

      if (connectionProperties != null) {
        try {
          // restore all custom properties
          connectionProperties.forEach(
              (k, v) -> {
                if ((String) v != null) {
                  dbp.setProperty((String) k, (String) v);
                }
              });

          // the sockettimeout is explicitly set to 0 due to the long lifetime of the connection for
          // NOTIFY/LISTEN and CURSOR usage reasons.
          String socketTimeout = connectionProperties.getProperty("socketTimeout");
          if (socketTimeout != null && !"0".equals(socketTimeout)) {
            log.info("Supressed JDBC SocketTimeout parameter for long running connections");
          }
          dbp.setProperty("socketTimeout", "0");

          // disable statement caching
          dbp.setProperty("preparedStatementCacheQueries", "0");
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException(
              "illegal connectionProperties: " + connectionProperties);
        }
      }
    }
    return dbp;
  }

  @SneakyThrows
  @SuppressWarnings("java:S2077")
  public SingleConnectionDataSource getPooledAsSingleDataSource(ConnectionModifier... modifiers) {
    return getPooledAsSingleDataSource(
        modifiers != null ? Arrays.asList(modifiers) : Collections.emptyList());
  }

  @SneakyThrows
  @SuppressWarnings("java:S2077")
  public SingleConnectionDataSource getPooledAsSingleDataSource(
      @NonNull List<ConnectionModifier> filterList) {
    return new ModifiedSingleConnectionDataSource(ds.getConnection(), filterList);
  }

  static class ModifiedSingleConnectionDataSource extends SingleConnectionDataSource {
    private final Connection c;
    private final List<ConnectionModifier> filterList;

    public ModifiedSingleConnectionDataSource(
        @NonNull Connection c, @NonNull List<ConnectionModifier> filterList) {
      super(c, true);
      this.c = c;
      this.filterList = filterList;

      filterList.forEach(f -> f.afterBorrow(c));
    }

    @Override
    public void destroy() {
      var rev = new ArrayList<>(filterList);
      Collections.reverse(rev);
      rev.forEach(f -> f.beforeReturn(c));
      super.destroy();
    }

    public boolean isWrapperFor(Class<?> iface) throws java.sql.SQLException {
      // TODO Auto-generated method stub
      return iface != null && iface.isAssignableFrom(this.getClass());
    }

    public <T> T unwrap(Class<T> iface) throws java.sql.SQLException {
      // TODO Auto-generated method stub
      try {
        if (iface != null && iface.isAssignableFrom(this.getClass())) {
          return (T) this;
        }
        throw new java.sql.SQLException("Auto-generated unwrap failed; Revisit implementation");
      } catch (Exception e) {
        throw new java.sql.SQLException(e);
      }
    }

    public java.util.logging.Logger getParentLogger() {
      // TODO Auto-generated method stub
      return null;
    }
  }
}
