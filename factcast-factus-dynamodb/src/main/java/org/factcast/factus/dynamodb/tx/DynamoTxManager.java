package org.factcast.factus.dynamodb.tx;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.factcast.factus.dynamodb.DynamoDBTransactionItems;

public class DynamoTxManager {

  private static final ThreadLocal<Map<AmazonDynamoDBClient, DynamoTxManager>> holder =
      ThreadLocal.withInitial((Supplier<Map<AmazonDynamoDBClient, DynamoTxManager>>) HashMap::new);

  public static DynamoTxManager get(@NonNull AmazonDynamoDBClient c) {
    Map<AmazonDynamoDBClient, DynamoTxManager> map = getMap();
    return map.computeIfAbsent(c, DynamoTxManager::new);
  }

  private static Map<AmazonDynamoDBClient, DynamoTxManager> getMap() {
    return holder.get();
  }

  public boolean inTransaction() {
    return currentTx != null;
  }

  // no atomicref needed here as this class is used threadbound anyway
  private DynamoDBTransactionItems currentTx;
  private final AmazonDynamoDBClient client;

  DynamoTxManager(@NonNull AmazonDynamoDBClient redisson) {
    this.client = redisson;
  }

  @Nullable
  public DynamoDBTransactionItems getCurrentTransaction() {
    return currentTx;
  }

  public void join(Consumer<DynamoDBTransactionItems> block) {
    startOrJoin();
    block.accept(currentTx);
  }

  public <R> R join(Function<DynamoDBTransactionItems, R> block) {
    startOrJoin();
    return block.apply(currentTx);
  }

  /** @return true if tx was started, false if there was one running */
  public boolean startOrJoin() {
    if (currentTx == null) {
      currentTx = new DynamoDBTransactionItems();
      return true;
    } else {
      return false;
    }
  }

  public void commit() {
    if (currentTx != null) {
      try {
        // we don't check fro the response.
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
