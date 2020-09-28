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
package org.factcast.store.pgsql.internal.snapcache;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.joda.time.DateTime;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class PgSnapshotCache {
  private static final String SELECT_SNAPSHOT =
      "SELECT factid,data,compressed FROM snapshot_cache WHERE uuid=? AND cache_key=?";

  private static final String UPSERT_SNAPSHOT =
      "INSERT INTO snapshot_cache(uuid,cache_key,factid,data,compressed) VALUES (?,?,?,?,?) ON CONFLICT (uuid,cache_key) DO UPDATE set factid=?, data=?, compressed=?";

  private static final String CLEAR_SNAPSHOT =
      "DELETE FROM snapshot_cache WHERE uuid=? AND cache_key=?";

  private static final String TOUCH_SNAPSHOT_ACCESSTIME =
      "UPDATE snapshot_cache set last_access=now() WHERE uuid=? AND cache_key=?";

  final JdbcTemplate jdbcTemplate;

  public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {

    jdbcTemplate.update(TOUCH_SNAPSHOT_ACCESSTIME, new Object[] {id.uuid(), id.key()});
    return jdbcTemplate
        .query(
            SELECT_SNAPSHOT, new Object[] {id.uuid(), id.key()}, this::extractSnapshotFromResultSet)
        .stream()
        .findFirst()
        .map(
            snapData ->
                new Snapshot(id, snapData.factId(), snapData.bytes(), snapData.compressed()));
  }

  public void setSnapshot(@NonNull Snapshot snap) {
    jdbcTemplate.update(
        UPSERT_SNAPSHOT,
        snap.id().uuid(),
        snap.id().key(),
        snap.lastFact(),
        snap.bytes(),
        snap.compressed(),
        snap.lastFact(),
        snap.bytes(),
        snap.compressed());
  }

  public void clearSnapshot(@NonNull SnapshotId id) {
    jdbcTemplate.update(CLEAR_SNAPSHOT, id.uuid(), id.key());
  }

  private PgSnapshotData extractSnapshotFromResultSet(
      ResultSet resultSet, @SuppressWarnings("unused") int rowNum) throws SQLException {
    return new PgSnapshotData(
        UUID.fromString(resultSet.getString(1)), resultSet.getBytes(2), resultSet.getBoolean(3));
  }

  public void compact(@NonNull DateTime thresholdDate) {
    jdbcTemplate.update("DELETE FROM snapshot_cache WHERE last_access < ?", thresholdDate.toDate());
  }
}
