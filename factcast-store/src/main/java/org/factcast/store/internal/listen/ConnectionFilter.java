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

public interface ConnectionFilter {
  void onBorrow(Connection connection);

  void onReturn(Connection connection);

  static ConnectionFilter withApplicationName(@NonNull String applicationName) {
    return withProperty("APPLICATION_NAME", applicationName);
  }

  static ConnectionFilter withProperty(@NonNull String property, @NonNull String value) {
    return new PropertyConnectionFilter(property, value) {};
  }

  static ConnectionFilter withBitmapScanDisabled() {
    return new DisableBitmapScanConnectionFilter();
  }

  private static JdbcTemplate jdbc(Connection connection) {
    return new JdbcTemplate(new SingleConnectionDataSource(connection, true));
  }

  @RequiredArgsConstructor
  @EqualsAndHashCode(of = {"property", "value"})
  class PropertyConnectionFilter implements ConnectionFilter {
    final String property;
    final String value;

    String oldValue;

    @Override
    public void onBorrow(Connection connection) {
      oldValue = jdbc(connection).queryForObject("SHOW " + property, String.class);
    }

    @Override
    public void onReturn(Connection connection) {
      jdbc(connection).execute("SET " + property + " " + oldValue);
    }
  }

  @EqualsAndHashCode
  class DisableBitmapScanConnectionFilter implements ConnectionFilter {
    @Override
    public void onBorrow(Connection connection) {
      jdbc(connection).execute("SET enable_bitmapscan=0");
    }

    @Override
    public void onReturn(Connection connection) {
      jdbc(connection).execute("RESET enable_bitmapscan");
    }
  }
}
