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
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.factcast.example.client.mongodb.hello.events.UserChangedV1;
import org.factcast.example.client.mongodb.hello.events.UserCreatedV1;
import org.factcast.factus.Handler;
import org.factcast.factus.mongodb.AbstractMongoDbSubscribedProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ProjectionMetaData(revision = 1)
public class MongoDbSubscribedProjection extends AbstractMongoDbSubscribedProjection {

  private final MongoCollection<UserSchema> userTable;

  public MongoDbSubscribedProjection(@NonNull MongoDatabase mongoDatabase) {

    super(mongoDatabase);

    userTable = mongoDatabase.getCollection("usersSubscribed", UserSchema.class);
  }

  public UserSchema findByFirstName(@NonNull String firstName) {
    return userTable.find(new Document("firstName", firstName), UserSchema.class).first();
  }

  @Handler
  void apply(UserCreatedV1 e) {
    userTable.insertOne(
        UserSchema.builder()
            .displayName(e.lastName() + e.firstName())
            .firstName(e.firstName())
            .lastName(e.lastName())
            .build());

    log.info("Subscribed: UserCreated processed");
  }

  @Handler
  void apply(UserChangedV1 e) {
    // Only the last name can be changed.
    userTable.replaceOne(
        new Document("firstName", e.firstName()),
        UserSchema.builder()
            .firstName(e.firstName())
            .displayName(e.lastName() + e.firstName())
            .lastName(e.lastName())
            .build());

    log.info("Subscribed: UserChanged processed");
  }
}
