/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.core.snap.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.TimerTask;
import javax.sql.DataSource;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class StaleSnapshotsTimerTask extends TimerTask {

  @NonNull private final DataSource dataSource;
  @NonNull private final int staleForDays;
  @NonNull private final String statement;

  public StaleSnapshotsTimerTask(
      @NonNull DataSource dataSource,
      // sanitized by the properties class
      @NonNull String tableName,
      @NonNull int staleForDays) {
    this.dataSource = dataSource;
    this.statement = "DELETE FROM " + tableName + " WHERE last_accessed < ?";
    this.staleForDays = staleForDays;
  }

  @Override
  public void run() {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement prepared = connection.prepareStatement(this.statement)) {
      prepared.setTimestamp(
          1, Timestamp.valueOf(LocalDate.now().atStartOfDay().minusDays(staleForDays)));
      prepared.executeUpdate();
    } catch (Exception e) {
      log.error("Failed to delete old snapshots", e);
    }
  }
}
