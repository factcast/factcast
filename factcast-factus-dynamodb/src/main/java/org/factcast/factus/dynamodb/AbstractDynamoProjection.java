package org.factcast.factus.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.factus.dynamodb.tx.DynamoTxManager;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.Named;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.projection.WriterTokenAware;
import org.factcast.factus.redis.batch.RedissonBatchManager;

abstract class AbstractDynamoProjection
    implements DynamoProjection, FactStreamPositionAware, WriterTokenAware, Named {
  @Getter protected final AmazonDynamoDBClient client;

  private final RLock lock;
  private final String stateBucketName;

  @Getter private final String redisKey;

  public AbstractDynamoProjection(@NonNull RedissonClient redisson) {
    this.client = redisson;

    redisKey = getScopedName().asString();
    stateBucketName = redisKey + "_state_tracking";

    // needs to be free from transactions, obviously
    lock = redisson.getLock(redisKey + "_lock");
  }

  @VisibleForTesting
  RBucket<UUID> stateBucket(@NonNull RTransaction tx) {
    return tx.getBucket(stateBucketName, UUIDCodec.INSTANCE);
  }

  @VisibleForTesting
  RBucketAsync<UUID> stateBucket(@NonNull RBatch b) {
    return b.getBucket(stateBucketName, UUIDCodec.INSTANCE);
  }

  @VisibleForTesting
  RBucket<UUID> stateBucket() {
    return client.getBucket(stateBucketName, UUIDCodec.INSTANCE);
  }

  @Override
  public UUID factStreamPosition() {
    DynamoTxManager man = DynamoTxManager.get(client);
    if (man.inTransaction()) {
      return man.join((Function<RTransaction, UUID>) tx -> stateBucket(tx).get());
    } else {
      return stateBucket().get();
    }
    // note: were not trying to use a bucket from a running batch as it would require to execute the
    // batch to get a result back.
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void factStreamPosition(@NonNull UUID position) {
    DynamoTxManager txMan = DynamoTxManager.get(client);
    if (txMan.inTransaction()) {
      txMan.join(
          tx -> {
            stateBucket(txMan.getCurrentTransaction()).set(position);
          });
    } else {
      RedissonBatchManager bman = RedissonBatchManager.get(client);
      if (bman.inBatch()) {
        bman.join(
            tx -> {
              stateBucket(bman.getCurrentBatch()).setAsync(position);
            });
      } else {
        stateBucket().set(position);
      }
    }
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    lock.lock();
    return new DynamoWriterToken(client, lock);
  }
}
