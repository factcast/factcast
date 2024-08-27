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
package org.factcast.itests.factus.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.snap.jdbc.JdbcSnapshotCache;
import org.factcast.core.snap.jdbc.JdbcSnapshotProperties;
import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.itests.TestFactusApplication;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@SpringBootTest
@ContextConfiguration(classes = TestFactusApplication.class)
@RequiredArgsConstructor
public class JdbcSnapshotCacheCleanupITest extends AbstractFactCastIntegrationTest {
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @SneakyThrows
  void testCleanup() {
    JdbcSnapshotProperties properties = new JdbcSnapshotProperties();

    jdbcTemplate.execute(
        "CREATE TABLE factcast_snapshots(key VARCHAR(512), uuid VARCHAR(36), last_fact_id VARCHAR(36), "
            + "bytes BYTEA, compressed boolean, last_accessed TIMESTAMP, PRIMARY KEY (key, uuid))");
    jdbcTemplate.execute(
        "CREATE INDEX factcast_snapshots_idx_last_accessed ON factcast_snapshots(last_accessed);");

    Snapshot snap1 =
        new Snapshot(
            SnapshotId.of("key1", UUID.randomUUID()),
            UUID.randomUUID(),
            new byte[] {1, 2, 3},
            false);
    Snapshot snap2 =
        new Snapshot(
            SnapshotId.of("key2", UUID.randomUUID()),
            UUID.randomUUID(),
            new byte[] {1, 2, 3},
            false);
    Snapshot snap3 =
        new Snapshot(
            SnapshotId.of("key3", UUID.randomUUID()),
            UUID.randomUUID(),
            new byte[] {1, 2, 3},
            false);

    // Stale
    insertSnapshot(
        snap1,
        Timestamp.from(
            Instant.now().minus(properties.getDeleteSnapshotStaleForDays() + 5, ChronoUnit.DAYS)));

    // Non Stale
    insertSnapshot(
        snap2,
        Timestamp.from(
            Instant.now().minus(properties.getDeleteSnapshotStaleForDays() - 1, ChronoUnit.DAYS)));

    // Stale
    insertSnapshot(
        snap3,
        Timestamp.from(
            Instant.now().minus(properties.getDeleteSnapshotStaleForDays() + 1, ChronoUnit.DAYS)));

    List<Map<String, Object>> snapshots = getSnapshots();
    assertThat(snapshots).hasSize(3);

    // On start the cleanup should be triggered
    SnapshotCache snapshotCache = new JdbcSnapshotCache(properties, jdbcTemplate.getDataSource());
    // Wait for the cleanup to finish
    Awaitility.await().until(() -> getSnapshots().size() == 1);

    snapshots = getSnapshots();
    assertThat(snapshots.get(0).get("key")).isEqualTo(snap2.id().key());
    assertThat(snapshots.get(0).get("uuid")).isEqualTo(snap2.id().uuid().toString());
  }

  private List<Map<String, Object>> getSnapshots() {
    return jdbcTemplate.queryForList("SELECT * FROM factcast_snapshots");
  }

  private void insertSnapshot(Snapshot snapshot, Timestamp lastAccessed) {
    jdbcTemplate.update(
        "INSERT INTO factcast_snapshots VALUES (?, ?, ?, ?, ?, ?)",
        snapshot.id().key(),
        snapshot.id().uuid().toString(),
        snapshot.lastFact().toString(),
        snapshot.bytes(),
        snapshot.compressed(),
        lastAccessed);
  }
}
