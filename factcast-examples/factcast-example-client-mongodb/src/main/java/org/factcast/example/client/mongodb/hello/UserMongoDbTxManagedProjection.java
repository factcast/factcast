/*
 * Copyright © 2017-2026 factcast.org
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

import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.factcast.example.client.mongodb.hello.events.UserChangedV1;
import org.factcast.example.client.mongodb.hello.events.UserCreatedV1;
import org.factcast.factus.Handler;
import org.factcast.factus.mongodb.tx.AbstractMongoDbTxManagedProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.jspecify.annotations.NonNull;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ProjectionMetaData(revision = 1)
public class UserMongoDbTxManagedProjection extends AbstractMongoDbTxManagedProjection {

  final MongoTemplate mongoTemplate;

  protected UserMongoDbTxManagedProjection(
      @NonNull MongoTransactionManager mongoTransactionManager, @NonNull MongoTemplate template) {
    super(mongoTransactionManager);

    mongoTemplate = template;
  }

  public UserSchema findByAggregateId(@NonNull UUID userId) {
    log.info("Finding User: {}", userId);
    return mongoTemplate.findOne(
        new Query(Criteria.where("id").is(userId)), UserSchema.class, getCollectionName());
  }

  private String getCollectionName() {
    return getScopedName().with("txUsers").asString();
  }

  @Handler
  void apply(UserCreatedV1 e) {
    final UserSchema user =
        UserSchema.builder()
            .id(e.aggregateId())
            .firstName(e.firstName())
            .lastName(e.lastName())
            .displayName(e.lastName() + e.firstName())
            .build();

    mongoTemplate.insert(user, getCollectionName());

    log.info("ManagedTx: UserCreated processed");
  }

  @Handler
  void apply(UserChangedV1 e) {
    // Only the last name can be changed.
    Query query = new Query(Criteria.where("id").is(e.aggregateId()));
    final UserSchema user =
        Optional.ofNullable(mongoTemplate.findOne(query, UserSchema.class, getCollectionName()))
            .orElseThrow(() -> new IllegalStateException("User not found"));

    mongoTemplate.updateFirst(
        query,
        new Update()
            .set("lastName", e.lastName())
            .set("displayName", e.lastName() + user.getFirstName()),
        getCollectionName());

    log.info("ManagedTx: UserChanged processed");
  }
}
