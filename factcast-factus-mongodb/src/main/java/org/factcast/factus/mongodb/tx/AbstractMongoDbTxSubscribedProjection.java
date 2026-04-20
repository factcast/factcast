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
import org.factcast.factus.mongodb.MongoDbWriterTokenManager;
import org.factcast.factus.projection.SubscribedProjection;
import org.jspecify.annotations.NonNull;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;

public abstract class AbstractMongoDbTxSubscribedProjection extends AbstractMongoDbTxProjection
    implements SubscribedProjection {

  protected AbstractMongoDbTxSubscribedProjection(
      @NonNull MongoTransactionManager mongoTransactionManager,
      @NonNull MongoTemplate mongoTemplate) {
    super(mongoTransactionManager, mongoTemplate);
  }

  @VisibleForTesting
  protected AbstractMongoDbTxSubscribedProjection(
      @NonNull MongoTransactionManager mongoTransactionManager,
      @NonNull MongoTemplate mongoTemplate,
      @NonNull MongoDbWriterTokenManager lockSupport) {
    super(mongoTransactionManager, mongoTemplate, lockSupport);
  }
}
