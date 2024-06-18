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

import static java.util.UUID.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.core.snap.SnapshotId;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;

@Slf4j
@RequiredArgsConstructor
public abstract class SnapshotCacheTest extends AbstractFactCastIntegrationTest {

  final SnapshotCache repository;

  @Test
  public void simpleSnapshotRoundtrip() throws Exception {
    SnapshotId id = SnapshotId.of("test", randomUUID());
    // initially empty
    assertThat(repository.getSnapshot(id)).isEmpty();

    // set and retrieve
    repository.setSnapshot(new Snapshot(id, randomUUID(), "foo".getBytes(), false));
    Optional<Snapshot> snapshot = repository.getSnapshot(id);
    assertThat(snapshot).isNotEmpty();
    assertThat(snapshot.get().bytes()).isEqualTo("foo".getBytes());

    // overwrite and retrieve
    repository.setSnapshot(new Snapshot(id, randomUUID(), "bar".getBytes(), false));
    snapshot = repository.getSnapshot(id);
    assertThat(snapshot).isNotEmpty();
    assertThat(snapshot.get().bytes()).isEqualTo("bar".getBytes());

    // clear and make sure, it is cleared
    repository.clearSnapshot(id);
    assertThat(repository.getSnapshot(id)).isEmpty();
  }
}
