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

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;

@Slf4j
@RequiredArgsConstructor
public abstract class SnapshotCacheTest extends AbstractFactCastIntegrationTest {

  final SnapshotCache repository;

  @Test
  public void simpleSnapshotRoundtrip() {
    SnapshotIdentifier id = SnapshotIdentifier.of(Aggregate.class, randomUUID());
    // initially empty
    assertThat(repository.find(id)).isEmpty();

    // set and retrieve
    repository.store(
        id, new SnapshotData("foo".getBytes(), SnapshotSerializerId.of("narf"), randomUUID()));
    Optional<SnapshotData> snapshot = repository.find(id);
    assertThat(snapshot).isNotEmpty();
    assertThat(snapshot.get().serializedProjection()).isEqualTo("foo".getBytes());

    // overwrite and retrieve
    repository.store(
        id, new SnapshotData("bar".getBytes(), SnapshotSerializerId.of("narf"), randomUUID()));
    snapshot = repository.find(id);
    assertThat(snapshot).isNotEmpty();
    assertThat(snapshot.get().serializedProjection()).isEqualTo("bar".getBytes());

    // clear and make sure, it is cleared
    repository.remove(id);
    assertThat(repository.find(id)).isEmpty();
  }
}
