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
import static org.factcast.core.snap.mongo.MongoDbSnapshotCache.*;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Updates;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.bson.Document;
import org.factcast.core.snap.mongo.MongoDbSnapshotCache;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.MongoProjectionConfiguration;
import org.factcast.itests.factus.proj.UserV1;
import org.factcast.spring.boot.autoconfigure.snap.MongoDbSnapshotCacheAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(
    classes = {
      TestFactusApplication.class,
      MongoDbSnapshotCacheAutoConfiguration.class,
      MongoProjectionConfiguration.class
    })
public class MongoSnapshotCacheTest extends SnapshotCacheTest {
  private MongoClient mongoClient;
  private SnapshotCache repository;

  @Autowired
  public MongoSnapshotCacheTest(SnapshotCache repository, MongoClient mongoClient) {
    super(repository);
    this.repository = repository;
    this.mongoClient = mongoClient;
  }

  @Test
  public void testMongoAutoClean() {
    SnapshotIdentifier id = SnapshotIdentifier.of(UserV1.class, randomUUID());
    repository.store(
        id, new SnapshotData("foo".getBytes(), SnapshotSerializerId.of("narf"), randomUUID()));

    Optional<SnapshotData> snapshot = repository.find(id);
    assertThat(snapshot).isNotEmpty();
    assertThat(snapshot.get().serializedProjection()).isEqualTo("foo".getBytes());

    MongoDatabase database = mongoClient.getDatabase("factcast");
    var collection = database.getCollection("factus_snapshot");

    // Mark it as expired 30 days ago
    Document query =
        new Document(PROJECTION_CLASS_FIELD, id.projectionClass().getName())
            .append(AGGREGATE_ID_FIELD, id.aggregateId().toString());
    collection.updateOne(
        query, Updates.set(EXPIRE_AT_FIELD, Instant.now().minus(30, ChronoUnit.DAYS)));

    // Should clean expired
    var repo = (MongoDbSnapshotCache) repository;
    repo.cleanupOldSnapshots();

    snapshot = repository.find(id);
    assertThat(snapshot).isEmpty();
  }
}
