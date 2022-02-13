package org.factcast.factus.dynamodb;

import com.amazonaws.services.dynamodbv2.model.TransactWriteItem;
import com.amazonaws.services.dynamodbv2.model.TransactWriteItemsRequest;
import com.google.common.collect.Lists;
import java.util.List;
import lombok.NonNull;

public class DynamoDBTransaction {
  private List<TransactWriteItem> items = Lists.newArrayList();

  public final void add(@NonNull TransactWriteItem item) {
    checkState();
    this.items.add(item);
  }

  public final TransactWriteItemsRequest asTransactWriteItemsRequest() {
    checkState();
    return new TransactWriteItemsRequest().withTransactItems(items);
  }

  public void rollback() {
    items = null;
  }

  private void checkState() {
    if (items == null)
      throw new IllegalStateException(getClass().getSimpleName() + " already rolled back.");
  }
}
