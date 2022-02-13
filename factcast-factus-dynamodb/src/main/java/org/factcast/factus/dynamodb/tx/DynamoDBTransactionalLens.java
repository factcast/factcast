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
