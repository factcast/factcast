package org.factcast.factus.dynamodb.tx;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.google.common.annotations.VisibleForTesting;
import java.util.function.Function;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.dynamodb.DynamoProjection;
import org.factcast.factus.dynamodb.tx.DynamoTransactional.Defaults;
import org.factcast.factus.projector.AbstractTransactionalLens;
import org.redisson.api.RTransaction;
import org.redisson.api.TransactionOptions;

@Slf4j
public class DynamoTransactionalLens extends AbstractTransactionalLens {

  private final DynamoTxManager redissonTxManager;

  public DynamoTransactionalLens(
      @NonNull DynamoProjection p, @NonNull AmazonDynamoDBClient client) {
    this(p, DynamoTxManager.get(client), createOpts(p));
  }

  @VisibleForTesting
  DynamoTransactionalLens(
      @NonNull DynamoProjection p,
      @NonNull DynamoTxManager txman,
      @NonNull TransactionOptions opts) {
    super(p);

    redissonTxManager = txman;
    txman.options(opts);

    bulkSize = Math.max(1, getSize(p));
    flushTimeout = calculateFlushTimeout(opts);
    log.trace(
        "Created {} instance for {} with batchsize={},timeout={}",
        getClass().getSimpleName(),
        p,
        bulkSize,
        flushTimeout);
  }

  @VisibleForTesting
  static int getSize(@NonNull DynamoProjection p) {
    DynamoTransactional transactional = p.getClass().getAnnotation(DynamoTransactional.class);
    if (transactional == null) {
      throw new IllegalStateException(
          "Projection "
              + p.getClass()
              + " is expected to have an annotation @"
              + DynamoTransactional.class.getSimpleName());
    }
    return transactional.bulkSize();
  }

  @VisibleForTesting
  static TransactionOptions createOpts(@NonNull DynamoProjection p) {
    DynamoTransactional transactional = p.getClass().getAnnotation(DynamoTransactional.class);
    if (transactional == null) {
      throw new IllegalStateException(
          "Projection "
              + p.getClass()
              + " is expected to have an annotation @"
              + DynamoTransactional.class.getSimpleName());
    }
    return Defaults.with(transactional);
  }

  @VisibleForTesting
  static long calculateFlushTimeout(@NonNull TransactionOptions opts) {
    // "best" guess
    long flush = opts.getTimeout() / 10 * 8;
    if (flush < 80) {
      // disable batching altogether as it is too risky
      flush = 0;
    }
    return flush;
  }

  @Override
  public Function<Fact, ?> parameterTransformerFor(@NonNull Class<?> type) {
    if (RTransaction.class.equals(type)) {
      return f -> {
        redissonTxManager.startOrJoin();
        return redissonTxManager.getCurrentTransaction();
      };
    }
    return null;
  }

  @Override
  public void doClear() {
    redissonTxManager.rollback();
  }

  @Override
  public void doFlush() {
    redissonTxManager.commit();
  }
}
