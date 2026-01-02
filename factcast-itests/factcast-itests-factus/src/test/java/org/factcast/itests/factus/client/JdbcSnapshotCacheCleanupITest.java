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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.factcast.core.snap.jdbc.JdbcSnapshotCache;
import org.factcast.core.snap.jdbc.JdbcSnapshotProperties;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;
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
        """
                    CREATE TABLE IF NOT EXISTS factcast_snapshot(projection_class VARCHAR(512), aggregate_id VARCHAR(36) NULL, last_fact_id VARCHAR(36),
                                      bytes BYTEA, snapshot_serializer_id VARCHAR(128), PRIMARY KEY (projection_class, aggregate_id));
                    """);
    jdbcTemplate.execute(
        """
                          CREATE TABLE IF NOT EXISTS factcast_snapshot_lastaccessed(projection_class VARCHAR(512), aggregate_id VARCHAR(36) NULL,
                          last_accessed TIMESTAMP WITH TIME ZONE DEFAULT now(), PRIMARY KEY (projection_class, aggregate_id));
                          CREATE INDEX IF NOT EXISTS my_snapshot_table_index ON factcast_snapshot_lastaccessed(last_accessed);
                          """);

    SnapshotSerializerId serializerId = SnapshotSerializerId.of("serializer");

    SnapshotIdentifier id1 =
        SnapshotIdentifier.of(TestAggregateProjection.class, UUID.randomUUID());
    SnapshotData snap1 = new SnapshotData(new byte[] {1, 2, 3}, serializerId, UUID.randomUUID());
    SnapshotIdentifier id2 =
        SnapshotIdentifier.of(TestAggregateProjection.class, UUID.randomUUID());
    SnapshotData snap2 = new SnapshotData(new byte[] {1, 2, 3}, serializerId, UUID.randomUUID());
    SnapshotIdentifier id3 =
        SnapshotIdentifier.of(TestAggregateProjection.class, UUID.randomUUID());
    SnapshotData snap3 = new SnapshotData(new byte[] {1, 2, 3}, serializerId, UUID.randomUUID());

    // Stale
    insertSnapshot(
        id1,
        snap1,
        Timestamp.valueOf(
            LocalDate.now()
                .minusDays(properties.getDeleteSnapshotStaleForDays() + 5)
                .atStartOfDay()));

    // Non Stale
    insertSnapshot(
        id2,
        snap2,
        Timestamp.valueOf(
            LocalDate.now()
                .minusDays(properties.getDeleteSnapshotStaleForDays() - 1)
                .atStartOfDay()));

    // Stale
    insertSnapshot(
        id3,
        snap3,
        Timestamp.valueOf(
            LocalDate.now()
                .minusDays(properties.getDeleteSnapshotStaleForDays() + 1)
                .atStartOfDay()));

    List<Map<String, Object>> snapshots = getSnapshots();
    assertThat(snapshots).hasSize(3);

    // On start the cleanup should be triggered
    SnapshotCache snapshotCache = new JdbcSnapshotCache(properties, jdbcTemplate.getDataSource());
    // Wait for the cleanup to finish
    Awaitility.await().until(() -> getSnapshots().size() == 1);

    snapshots = getSnapshots();
    assertThat(snapshots.get(0).get("projection_class")).isEqualTo(id2.projectionClass().getName());
    assertThat(snapshots.get(0).get("aggregate_id")).isEqualTo(id2.aggregateId().toString());
  }

  private List<Map<String, Object>> getSnapshots() {
    return jdbcTemplate.queryForList("SELECT * FROM factcast_snapshot");
  }

  private void insertSnapshot(
      SnapshotIdentifier id, SnapshotData snapshot, Timestamp lastAccessed) {
    jdbcTemplate.update(
        "INSERT INTO factcast_snapshot VALUES (?, ?, ?, ?, ?)",
        id.projectionClass().getName(),
        id.aggregateId().toString(),
        snapshot.lastFactId().toString(),
        snapshot.serializedProjection(),
        snapshot.snapshotSerializerId().name());

    jdbcTemplate.update(
        "INSERT INTO factcast_snapshot_lastaccessed VALUES (?, ?, ?)",
        id.projectionClass().getName(),
        id.aggregateId().toString(),
        lastAccessed);
  }

  static class TestAggregateProjection extends Aggregate {}
}
