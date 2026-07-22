/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store;

import java.sql.*;
import javax.annotation.Nonnull;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

public class OffloadDataSource extends DelegatingDataSource {

  public OffloadDataSource(@Nonnull DataSource delegate) {
    super(delegate);
  }

  @Override
  public Connection getConnection() throws SQLException {
    Connection connection = super.getConnection();
    // not a guarantee, better use separate RO user
    connection.setReadOnly(true);
    return connection;
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    Connection connection = super.getConnection(username, password);
    // not a guarantee, better use separate RO user
    connection.setReadOnly(true);
    return connection;
  }
}
