/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.itests.factus.proj;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Handler;
import org.factcast.factus.dynamodb.AbstractDynamoDBManagedProjection;
import org.factcast.factus.dynamodb.DynamoDBTransaction;
import org.factcast.factus.dynamodb.tx.DynamoDBTransactional;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.factus.DynamoBatchingITest;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;

@Slf4j
@ProjectionMetaData(serial = 1)
@DynamoDBTransactional
public class BatchDynamoManagedUserNames extends AbstractDynamoDBManagedProjection {

  public BatchDynamoManagedUserNames(AmazonDynamoDB client) {
    super(client);
  }

  public Map<UUID, String> userNames() {
    Map<UUID, String> ret = new HashMap<>();

    ScanResult scan =
        dynamoDB.scan(new ScanRequest().withTableName(DynamoBatchingITest.TABLE_NAME));
    scan.getItems().stream()
        .forEach(
            item -> {
              ret.put(UUID.fromString(item.get("_id").getS()), item.get("username").getS());
            });

    return ret;
  }

  public int count() {
    return userNames().size();
  }

  public boolean contains(String name) {
    return userNames().containsValue(name);
  }

  public Set<String> names() {
    return new HashSet<>(userNames().values());
  }

  public void clear() {
    userNames().clear();
  }

  // ---- processing:

  @SneakyThrows
  @Handler
  protected void apply(UserCreated created, DynamoDBTransaction tx) {

    Map<String, AttributeValue> item = new HashMap<>();
    item.put("_id", new AttributeValue().withS(created.aggregateId().toString()));
    item.put("username", new AttributeValue().withS(created.userName()));

    tx.add(
        new TransactWriteItem()
            .withPut(new Put().withTableName(DynamoBatchingITest.TABLE_NAME).withItem(item)));
  }

  @Handler
  protected void apply(UserDeleted deleted, DynamoDBTransaction tx) {
    Map<String, AttributeValue> item = new HashMap<>();
    item.put("_id", new AttributeValue().withS(deleted.aggregateId().toString()));

    tx.add(
        new TransactWriteItem()
            .withDelete(new Delete().withTableName(DynamoBatchingITest.TABLE_NAME).withKey(item)));
  }
}
