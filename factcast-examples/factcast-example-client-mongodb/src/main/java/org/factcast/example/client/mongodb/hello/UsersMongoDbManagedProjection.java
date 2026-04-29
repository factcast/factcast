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
package org.factcast.example.client.mongodb.hello;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.List;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.factcast.example.client.mongodb.hello.events.UserChangedV1;
import org.factcast.example.client.mongodb.hello.events.UserCreatedV1;
import org.factcast.factus.Handler;
import org.factcast.factus.mongodb.AbstractMongoDbManagedProjection;
import org.factcast.factus.serializer.ProjectionMetaData;

@ProjectionMetaData(revision = 1)
@Slf4j
public class UsersMongoDbManagedProjection extends AbstractMongoDbManagedProjection {

  private final MongoCollection<UserSchema> userCollection;

  public UsersMongoDbManagedProjection(@NonNull MongoDatabase mongoDatabase) {

    super(mongoDatabase);

    userCollection =
        mongoDatabase.getCollection(getScopedName().with("users").asString(), UserSchema.class);
  }

  public List<UserSchema> findsAll() {
    return userCollection.find(UserSchema.class).into(new java.util.ArrayList<>());
  }

  public UserSchema findByFirstName(@NonNull String firstName) {
    return userCollection.find(new Document("firstName", firstName), UserSchema.class).first();
  }

  @SneakyThrows
  @Handler
  void apply(UserCreatedV1 e) {
    userCollection.insertOne(
        UserSchema.builder()
            .id(e.aggregateId())
            .displayName(e.lastName() + e.firstName())
            .firstName(e.firstName())
            .lastName(e.lastName())
            .build());

    Thread.sleep(2000); // simulate long processing

    log.info("UserCreated processed");
  }

  @Handler
  void apply(UserChangedV1 e) {
    // Only the last name can be changed.
    userCollection.updateOne(
        new Document("id", e.aggregateId()),
        new Document("lastName", e.lastName()).append("displayName", e.lastName() + e.firstName()));

    log.info("UserChanged processed");
  }
}
