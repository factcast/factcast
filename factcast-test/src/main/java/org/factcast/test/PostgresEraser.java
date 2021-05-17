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
package org.factcast.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.testcontainers.containers.PostgreSQLContainer;

@UtilityClass
public class PostgresEraser {
  @SneakyThrows
  static void wipeAllFactCastDataDataFromPostgres(PostgreSQLContainer<?> pg) {
    String url = pg.getJdbcUrl();

    Properties p = new Properties();
    p.put("user", pg.getUsername());
    p.put("password", pg.getPassword());

    try (Connection con = DriverManager.getConnection(url, p);
        Statement st = con.createStatement()) {
      st.execute("TRUNCATE fact");
      st.execute("TRUNCATE tokenstore");
      st.execute("TRUNCATE transformationcache");
      st.execute("TRUNCATE snapshot_cache");
    }
  }
}
