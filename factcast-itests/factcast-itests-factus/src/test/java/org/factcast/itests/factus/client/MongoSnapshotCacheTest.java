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
import static org.factcast.core.snap.mongo.MongoDbSnapshotCache.METADATA_EXPIRE_AT_FIELD;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Updates;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.factcast.core.snap.mongo.MongoDbSnapshotCache;
import org.factcast.factus.projection.ScopedName;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.MongoClientConfiguration;
import org.factcast.itests.factus.proj.UserV1;
import org.factcast.spring.boot.autoconfigure.snap.MongoDbSnapshotCacheAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@Slf4j
@SpringBootTest
@ContextConfiguration(
    classes = {
      TestFactusApplication.class,
      MongoDbSnapshotCacheAutoConfiguration.class,
            MongoClientConfiguration.class
    })
public class MongoSnapshotCacheTest extends SnapshotCacheTest {
  private final MongoClient mongoClient;
  private final SnapshotCache repository;

  @Autowired
  public MongoSnapshotCacheTest(SnapshotCache repository, MongoClient mongoClient) {
    super(repository);
    this.repository = repository;
    this.mongoClient = mongoClient;
  }

  @SneakyThrows
  @Test
  public void testMongoAutoClean() {
    SnapshotIdentifier id = SnapshotIdentifier.of(UserV1.class, randomUUID());
    repository.store(
        id, new SnapshotData("foo".getBytes(), SnapshotSerializerId.of("narf"), randomUUID()));

    Optional<SnapshotData> snapshot = repository.find(id);
    assertThat(snapshot).isNotEmpty();
    assertThat(snapshot.get().serializedProjection()).isEqualTo("foo".getBytes());

    Thread.sleep(100); // small wait to assure the completable future in find() is done

    // Mark it as expired 30 days ago
    MongoDatabase database = mongoClient.getDatabase("factcast");
    var collection = database.getCollection("factus_snapshots.files");

    String identifier =
        ScopedName.fromProjectionMetaData(id.projectionClass())
            .with(Optional.ofNullable(id.aggregateId()).map(UUID::toString).orElse("snapshot"))
            .asString();
    Document query = new Document("filename", identifier);

    collection.updateOne(
        query, Updates.set(METADATA_EXPIRE_AT_FIELD, Instant.now().minus(30, ChronoUnit.DAYS)));

    // Should clean expired
    var repo = (MongoDbSnapshotCache) repository;
    repo.cleanupOldSnapshots();

    snapshot = repository.find(id);
    assertThat(snapshot).isEmpty();
  }
}
