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
package org.factcast.store.pgsql.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.store.FactStore;
import org.factcast.store.pgsql.internal.StoreMetrics.OP;
import org.factcast.store.test.AbstractFactStoreTest;
import org.factcast.store.test.IntegrationTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = {PgTestConfiguration.class})
@Sql(scripts = "/test_schema.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
public class PgFactStoreTest extends AbstractFactStoreTest {

  @Autowired FactStore fs;

  @Autowired PgMetrics metrics;

  @Override
  protected FactStore createStoreToTest() {
    return fs;
  }

  @Test
  void testGetSnapshotMetered() {
    Optional<Snapshot> snapshot = store.getSnapshot(new SnapshotId("xxx", UUID.randomUUID()));
    assertThat(snapshot).isEmpty();

    verify(metrics).time(same(OP.GET_SNAPSHOT), any(Supplier.class));
  }

  @Test
  void testClearSnapshotMetered() {
    SnapshotId id = new SnapshotId("xxx", UUID.randomUUID());
    store.clearSnapshot(id);
    verify(metrics).time(same(OP.CLEAR_SNAPSHOT), any(Runnable.class));
  }

  @Test
  void testSetSnapshotMetered() {
    SnapshotId id = new SnapshotId("xxx", UUID.randomUUID());
    Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);
    store.setSnapshot(snap);

    verify(metrics).time(same(OP.SET_SNAPSHOT), any(Runnable.class));
  }
}
