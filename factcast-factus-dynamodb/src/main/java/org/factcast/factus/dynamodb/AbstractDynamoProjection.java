package org.factcast.factus.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import java.time.Duration;
import java.util.UUID;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.factus.dynamodb.tx.DynamoTxManager;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.Named;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.projection.WriterTokenAware;

abstract class AbstractDynamoProjection
    implements DynamoProjection, FactStreamPositionAware, WriterTokenAware, Named {
  @Getter protected final AmazonDynamoDBClient client;

  // TODO private final RLock lock;
  private final String stateBucketName;
  private final String lockBucketName;

  @Getter private final String scopedName;

  public AbstractDynamoProjection(@NonNull AmazonDynamoDBClient amazonDynamoDBClient) {
    this.client = amazonDynamoDBClient;

    scopedName = getScopedName().asString();
    stateBucketName = scopedName + "_state_tracking";
    lockBucketName = scopedName + "_lock";

    // needs to be free from transactions, obviously
    // lock = amazonDynamoDBClient.getLock(redisKey + "_lock");
  }

  // please note, it only reflects to WRITTEN position, thus does not include those changes of a
  // running transaction
  @Override
  public UUID factStreamPosition() {

    // TODO fetch from dynamo
    return new UUID(1, 1);
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void factStreamPosition(@NonNull UUID position) {
    DynamoTxManager txMan = DynamoTxManager.get(client);
    if (txMan.inTransaction()) {

      throw new IllegalStateException("TODO must be called only on commit?");

    } else {
      // TODO write to dynamo

    }
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    // TODO
    /* lock.lock();
    return new DynamoWriterToken(client, lock);*/
    return new DynamoWriterToken(client, lockBucketName);
  }
}
