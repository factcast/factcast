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
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

@Value
public class DynamoTransaction {

  FactStreamPosition initialFactStreamPosition;

  List<TransactWriteItem> writeItems = new ArrayList<>();

  static int batchSize;

  public void add(TransactWriteItem item) {
    writeItems.add(item);
    if (writeItems.size() > batchSize) {
      // todo: exception type?
      throw new IllegalStateException("Max batch size exceeded");
    }
  }

  public TransactWriteItemsRequest buildTransactionRequest() {
    return TransactWriteItemsRequest.builder()
        .overrideConfiguration(AwsRequestOverrideConfiguration.builder().build())
        .build();
  }
}
