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
package org.factcast.store.internal.snapcache;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.*;
import lombok.NonNull;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.store.test.IntegrationTest;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SuppressWarnings("unchecked")
@ContextConfiguration(classes = {PgTestConfiguration.class})
@ExtendWith(SpringExtension.class)
@IntegrationTest
class SnapshotCacheTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private PgSnapshotCache underTest;

  @Test
  void getSnapshot_empty() {
    // RUN
    Optional<Snapshot> snapshot = underTest.getSnapshot(SnapshotId.of("xxx", UUID.randomUUID()));

    // ASSERT
    assertThat(snapshot).isEmpty();
  }

  @Test
  void getSnapshot_returnsSnapshotAndUpdatesTimestamp() throws InterruptedException {
    // INIT
    // put snapshot
    SnapshotId id = SnapshotId.of("xxx", UUID.randomUUID());
    UUID lastFact = UUID.randomUUID();

    underTest.setSnapshot(new Snapshot(id, lastFact, "foo".getBytes(), false));

    Timestamp firstAccess = getTimestamp(id);

    // RUN
    Optional<Snapshot> snapshot = underTest.getSnapshot(id);

    // ASSERT
    assertThat(snapshot)
        .isNotEmpty()
        .get()
        .extracting(Snapshot::id, Snapshot::lastFact, Snapshot::bytes, Snapshot::compressed)
        .containsExactly(id, lastFact, "foo".getBytes(), false);

    // assert that timestamp got updated
    Timestamp recentAccess = getTimestamp(id);

    assertThat(recentAccess).isAfter(firstAccess);

    Thread.sleep(100);

    // also make sure the timestamp is properly set to the current time, and
    // not some time in the future / past
    Date now = new Date();
    assertThat(recentAccess)
        // should not be newer than current time
        .isBefore(now)
        // but also not older than 5 seconds,
        // assuming test execution is less than 5 seconds ;-)
        .isAfter(minus5seconds(now));
  }

  @NonNull
  private Date minus5seconds(Date now) {
    return new Date(now.getTime() - 1000 * 5);
  }

  @Test
  void setSnapshot_insert() {
    // INIT
    SnapshotId id = SnapshotId.of("xxx", UUID.randomUUID());
    UUID lastFact = UUID.randomUUID();

    Optional<Snapshot> snapshot = underTest.getSnapshot(id);

    assertThat(snapshot).isEmpty();

    // RUN
    // put snapshot
    underTest.setSnapshot(new Snapshot(id, lastFact, "foo".getBytes(), false));

    // ASSERT
    byte[] data = getData(id);

    assertThat(data).isEqualTo("foo".getBytes());
  }

  private byte[] getData(SnapshotId id) {
    return jdbcTemplate.queryForObject(
        "SELECT data FROM snapshot_cache WHERE uuid=? AND cache_key=?",
        byte[].class,
        id.uuid(),
        id.key());
  }

  @Test
  void setSnapshot_update() {
    // INIT
    SnapshotId id = SnapshotId.of("xxx", UUID.randomUUID());
    UUID lastFact = UUID.randomUUID();

    underTest.setSnapshot(new Snapshot(id, lastFact, "foo".getBytes(), false));

    byte[] data = getData(id);

    assertThat(data).isEqualTo("foo".getBytes());

    // RUN
    // put snapshot
    UUID newerFact = UUID.randomUUID();
    underTest.setSnapshot(new Snapshot(id, newerFact, "bar".getBytes(), false));

    // ASSERT
    data = getData(id);

    // check got actually updated
    assertThat(data).isEqualTo("bar".getBytes());
  }

  @Test
  void clearSnapshot() {
    // INIT
    SnapshotId id = SnapshotId.of("xxx", UUID.randomUUID());
    UUID lastFact = UUID.randomUUID();

    underTest.setSnapshot(new Snapshot(id, lastFact, "foo".getBytes(), false));

    byte[] data = getData(id);

    assertThat(data).isEqualTo("foo".getBytes());

    // RUN
    underTest.clearSnapshot(id);

    // ASSERT
    Optional<Snapshot> snapshot = underTest.getSnapshot(id);

    assertThat(snapshot).isEmpty();
  }

  @Test
  void compact() {
    // INIT
    SnapshotId id = SnapshotId.of("xxx", UUID.randomUUID());
    UUID lastFact = UUID.randomUUID();

    underTest.setSnapshot(new Snapshot(id, lastFact, "foo".getBytes(), false));
    Optional<Snapshot> snapshot = underTest.getSnapshot(id);

    assertThat(snapshot).isNotEmpty();

    Timestamp lastAccess = getTimestamp(id);

    // RUN
    underTest.compact(plusOneSecond(lastAccess));

    // ASSERT
    snapshot = underTest.getSnapshot(id);

    assertThat(snapshot).isEmpty();
  }

  @NonNull
  private DateTime plusOneSecond(Date date) {
    return new DateTime(date).plusSeconds(1);
  }

  private Timestamp getTimestamp(SnapshotId id) {
    return jdbcTemplate.queryForObject(
        "SELECT last_access FROM snapshot_cache WHERE uuid=? AND cache_key=?",
        Timestamp.class,
        id.uuid(),
        id.key());
  }
}
