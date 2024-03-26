/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.factus.dynamo.tx;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;
import org.factcast.core.FactStreamPosition;
import software.amazon.awssdk.enhanced.dynamodb.MappedTableResource;
import software.amazon.awssdk.enhanced.dynamodb.internal.operations.DefaultOperationContext;
import software.amazon.awssdk.enhanced.dynamodb.internal.operations.DeleteItemOperation;
import software.amazon.awssdk.enhanced.dynamodb.internal.operations.PutItemOperation;
import software.amazon.awssdk.enhanced.dynamodb.internal.operations.UpdateItemOperation;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

@Value
public class DynamoTransaction {

  FactStreamPosition initialFactStreamPosition;

  List<TransactWriteItem> writeItems = new ArrayList<>();

  public void add(TransactWriteItem item) {
    writeItems.add(item);
    if (writeItems.size() > DynamoTransactional.Defaults.maxBulkSize) {
      // todo: exception type?
      throw new IllegalStateException("Max batch size exceeded");
    }
  }

  public <T> void addPutRequest(
      MappedTableResource<T> mappedTableResource, TransactPutItemEnhancedRequest<T> request) {
    PutItemOperation<T> operation = PutItemOperation.create(request);
    add(
        operation.generateTransactWriteItem(
            mappedTableResource.tableSchema(),
            DefaultOperationContext.create(mappedTableResource.tableName()),
            mappedTableResource.mapperExtension()));
  }

  public <T> void addDeleteRequest(
      MappedTableResource<T> mappedTableResource, TransactDeleteItemEnhancedRequest request) {
    DeleteItemOperation<T> operation = DeleteItemOperation.create(request);
    add(
        operation.generateTransactWriteItem(
            mappedTableResource.tableSchema(),
            DefaultOperationContext.create(mappedTableResource.tableName()),
            mappedTableResource.mapperExtension()));
  }

  public <T> void addUpdateRequest(
      MappedTableResource<T> mappedTableResource, TransactUpdateItemEnhancedRequest<T> request) {
    UpdateItemOperation<T> operation = UpdateItemOperation.create(request);
    add(
        operation.generateTransactWriteItem(
            mappedTableResource.tableSchema(),
            DefaultOperationContext.create(mappedTableResource.tableName()),
            mappedTableResource.mapperExtension()));
  }

  public TransactWriteItemsRequest buildTransactionRequest() {
    return TransactWriteItemsRequest.builder().transactItems(writeItems).build();
  }
}
