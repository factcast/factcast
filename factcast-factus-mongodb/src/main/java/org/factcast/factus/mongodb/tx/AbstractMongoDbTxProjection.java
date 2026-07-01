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
package org.factcast.factus.mongodb.tx;

import com.google.common.annotations.VisibleForTesting;
import com.mongodb.client.MongoDatabase;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.mongodb.MongoDbProjection;
import org.factcast.factus.mongodb.MongoDbWriterTokenManager;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.spring.tx.*;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@SuppressWarnings("java:S1133")
public abstract class AbstractMongoDbTxProjection extends AbstractSpringTxProjection
    implements MongoDbProjection {
  private final MongoTemplate template;
  private final String projectionKey;
  private MongoDbWriterTokenManager lockSupport;

  protected AbstractMongoDbTxProjection(
      @NonNull MongoTransactionManager mongoTransactionManager,
      @NonNull MongoTemplate mongoTemplate) {
    super(mongoTransactionManager);
    this.template = mongoTemplate;
    this.projectionKey = this.getScopedName().asString();
  }

  @Override
  public @NonNull MongoDatabase mongoDb() {
    return template.getDb();
  }

  @Override
  public FactStreamPosition factStreamPosition() {
    return Optional.ofNullable(template.findOne(projectionStateByKeyQuery(), State.class))
        .map(state -> FactStreamPosition.of(state.lastFactId, state.lastFactSerial))
        .orElse(null);
  }

  private @NonNull Query projectionStateByKeyQuery() {
    return new Query(Criteria.where(PROJECTION_CLASS_FIELD).is(projectionKey));
  }

  @Override
  public void factStreamPosition(@lombok.NonNull FactStreamPosition position) {
    UUID factId = position.factId();
    if (factId != null) {
      template.upsert(
          projectionStateByKeyQuery(),
          new Update()
              .set(LAST_FACT_ID_FIELD, factId)
              .set(LAST_FACT_SERIAL_FIELD, position.serial()),
          State.class);
    }
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    return lockSupport().acquireWriteToken(maxWait);
  }

  @VisibleForTesting
  protected MongoDbWriterTokenManager lockSupport() {
    if (lockSupport == null) {
      lockSupport = MongoDbWriterTokenManager.create(template.getDb(), projectionKey);
    }
    return lockSupport;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Document(collection = STATE_COLLECTION_NAME)
  protected static class State {
    String projectionKey;
    UUID lastFactId;
    long lastFactSerial;
  }
}
