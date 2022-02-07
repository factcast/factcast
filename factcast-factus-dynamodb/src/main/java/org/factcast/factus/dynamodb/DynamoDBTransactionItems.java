package org.factcast.factus.dynamodb;

import com.amazonaws.services.dynamodbv2.model.TransactWriteItem;
import com.amazonaws.services.dynamodbv2.model.TransactWriteItemsRequest;
import com.google.common.collect.Lists;
import java.util.List;
import lombok.NonNull;

public class DynamoDBTransactionItems {
  private final List<TransactWriteItem> items = Lists.newArrayList();

  public final void add(@NonNull TransactWriteItem item) {
    this.items.add(item);
  }

  public final TransactWriteItemsRequest asTransactWriteItemsRequest() {
    return new TransactWriteItemsRequest().withTransactItems(items);
  }

  public void rollback() {
    items.clear();
  }
}
