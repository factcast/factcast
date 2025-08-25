/*
 * Copyright © 2017-2025 factcast.org
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

import java.sql.Connection;
import lombok.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

public interface ConnectionModifier {

  void afterBorrow(Connection connection);

  void beforeReturn(Connection connection);

  static ConnectionModifier withApplicationName(@NonNull String applicationName) {
    return withProperty("application_name", applicationName);
  }

  static ConnectionModifier withProperty(@NonNull String property, @NonNull String value) {
    return new Property(property, value) {};
  }

  static ConnectionModifier withBitmapScanDisabled() {
    return new DisableBitmapScan();
  }

  static ConnectionModifier withAutoCommitDisabled() {
    return new DisableAutoCommit();
  }

  private static JdbcTemplate jdbc(Connection connection) {
    return new JdbcTemplate(new SingleConnectionDataSource(connection, true));
  }

  @RequiredArgsConstructor
  @EqualsAndHashCode(of = {"property", "value"})
  @SuppressWarnings("java:S2077")
  public class Property implements ConnectionModifier {
    final String propertyName;
    final String value;

    String oldValue;

    @Override
    public void afterBorrow(Connection connection) {
      JdbcTemplate jdbc = jdbc(connection);
      oldValue = jdbc.queryForObject("SHOW " + propertyName, String.class);
      jdbc.execute("SET " + propertyName + "='" + value + "'");
    }

    @Override
    public void beforeReturn(Connection connection) {
      jdbc(connection).execute("SET " + propertyName + "='" + oldValue + "'");
    }
  }

  @EqualsAndHashCode
  class DisableAutoCommit implements ConnectionModifier {
    @SneakyThrows
    @Override
    public void afterBorrow(Connection connection) {
      connection.setAutoCommit(false);
    }

    @SneakyThrows
    @Override
    public void beforeReturn(Connection connection) {
      connection.setAutoCommit(true);
    }
  }

  @EqualsAndHashCode
  class DisableBitmapScan implements ConnectionModifier {
    @Override
    public void afterBorrow(Connection connection) {
      jdbc(connection).execute("SET enable_bitmapscan='off'");
    }

    @Override
    public void beforeReturn(Connection connection) {
      jdbc(connection).execute("RESET enable_bitmapscan");
    }
  }
}
