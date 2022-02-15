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
package org.factcast.factus.dynamodb;

import com.amazonaws.services.dynamodbv2.model.TransactWriteItem;
import com.amazonaws.services.dynamodbv2.model.TransactWriteItemsRequest;
import com.google.common.collect.Lists;
import java.util.List;
import lombok.NonNull;

public class DynamoDBTransaction {
  private List<TransactWriteItem> items = Lists.newArrayList();

  public void add(@NonNull TransactWriteItem item) {
    checkState();
    this.items.add(item);
  }

  public TransactWriteItemsRequest asTransactWriteItemsRequest() {
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
