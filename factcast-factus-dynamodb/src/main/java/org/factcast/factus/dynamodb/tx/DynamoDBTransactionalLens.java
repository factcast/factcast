/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.factus.dynamodb.tx;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.google.common.annotations.VisibleForTesting;
import java.util.function.Function;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.dynamodb.DynamoDBProjection;
import org.factcast.factus.dynamodb.DynamoDBTransaction;
import org.factcast.factus.projector.AbstractTransactionalLens;

@Slf4j
public class DynamoDBTransactionalLens extends AbstractTransactionalLens {

  private final DynamoDBTxManager dynamoTxManager;

  public DynamoDBTransactionalLens(@NonNull DynamoDBProjection p, @NonNull AmazonDynamoDB client) {
    super(p);
    this.dynamoTxManager = DynamoDBTxManager.get(client);
    bulkSize = getSize(p);
    flushTimeout = getTimeout(p);
    log.trace(
        "Created {} instance for {} with batchsize={},timeout={}",
        getClass().getSimpleName(),
        p,
        bulkSize,
        flushTimeout);
  }

  @VisibleForTesting
  static int getSize(@NonNull DynamoDBProjection p) {
    return getDynamoTransactionalAnnotation(p).bulkSize();
  }

  @VisibleForTesting
  static long getTimeout(@NonNull DynamoDBProjection p) {
    return getDynamoTransactionalAnnotation(p).timeout();
  }

  private static DynamoDBTransactional getDynamoTransactionalAnnotation(DynamoDBProjection p) {
    DynamoDBTransactional transactional = p.getClass().getAnnotation(DynamoDBTransactional.class);
    if (transactional == null) {
      throw new IllegalStateException(
          "Projection "
              + p.getClass()
              + " is expected to have an annotation @"
              + DynamoDBTransactional.class.getSimpleName());
    }
    return transactional;
  }

  @Override
  public Function<Fact, ?> parameterTransformerFor(@NonNull Class<?> type) {
    if (DynamoDBTransaction.class.equals(type)) {
      return f -> {
        dynamoTxManager.startOrJoin();
        return dynamoTxManager.getCurrentTransaction();
      };
    }
    return null;
  }

  @Override
  public void doClear() {
    dynamoTxManager.rollback();
  }

  @Override
  public void doFlush() {
    dynamoTxManager.commit();
  }
}
