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
package org.factcast.store.internal.listen;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class TracingConnection implements Connection {

  @Delegate private final Connection delegate;

  private boolean bitmapScanOff = false;

  @Override
  public Statement createStatement() throws SQLException {
    return new TracingStatement(delegate.createStatement());
  }

  @Override
  public void close() throws SQLException {
    log.debug("Checking connection state before closing. Bitmap scan off: {}", bitmapScanOff);
    if (bitmapScanOff) {
      log.error("Connection is closed while bitmap scan is still disabled.");
    }
    delegate.close();
  }

  @RequiredArgsConstructor
  class TracingStatement implements Statement {
    private final String setBitmapScanOff = "SET enable_bitmapscan='off'";
    private final String reset = "RESET enable_bitmapscan";

    @Delegate private final Statement delegate;

    @Override
    public boolean execute(String sql) throws SQLException {
      if (sql.equals(setBitmapScanOff)) {
        bitmapScanOff = true;
      } else if (sql.equals(reset)) {
        bitmapScanOff = false;
      }
      return delegate.execute(sql);
    }
  }
}
