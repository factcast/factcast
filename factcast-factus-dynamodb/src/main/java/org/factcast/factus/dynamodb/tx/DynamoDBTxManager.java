package org.factcast.factus.dynamodb.tx;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.factcast.factus.dynamodb.DynamoDBTransaction;

public class DynamoDBTxManager {
  private static final ThreadLocal<DynamoDBTxManager> holder = new ThreadLocal<>();

  public static DynamoDBTxManager get(@NonNull AmazonDynamoDB client) {
    return Optional.ofNullable(holder.get())
        .orElseGet(
            () -> {
              DynamoDBTxManager instance = new DynamoDBTxManager(client);
              holder.set(instance);
              return instance;
            });
  }

  public boolean inTransaction() {
    return currentTx != null;
  }

  // no atomicref needed here as this class is used threadbound anyway
  private DynamoDBTransaction currentTx;
  private final AmazonDynamoDB client;

  DynamoDBTxManager(@NonNull AmazonDynamoDB client) {
    this.client = client;
  }

  @Nullable
  public DynamoDBTransaction getCurrentTransaction() {
    return currentTx;
  }

  public void join(Consumer<DynamoDBTransaction> block) {
    startOrJoin();
    block.accept(currentTx);
  }

  public <R> R join(Function<DynamoDBTransaction, R> block) {
    startOrJoin();
    return block.apply(currentTx);
  }

  /** @return true if tx was started, false if there was one running */
  public boolean startOrJoin() {
    if (currentTx == null) {
      currentTx = createNewTransaction();
      return true;
    } else {
      return false;
    }
  }

  @VisibleForTesting
  DynamoDBTransaction createNewTransaction() {
    return new DynamoDBTransaction();
  }

  public void commit() {
    if (currentTx != null) {
      try {
        // we don't check for the response.
        client.transactWriteItems(currentTx.asTransactWriteItemsRequest());
      } finally {
        currentTx = null;
      }
    }
  }

  public void rollback() {
    if (currentTx != null) {
      try {
        currentTx.rollback();
      } finally {
        currentTx = null;
      }
    }
  }
}
