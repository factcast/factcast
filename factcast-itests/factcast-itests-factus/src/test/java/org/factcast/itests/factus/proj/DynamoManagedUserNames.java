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

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.dynamo.AbstractDynamoManagedProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Slf4j
@ProjectionMetaData(revision = 1)
public class DynamoManagedUserNames extends AbstractDynamoManagedProjection implements DynamoUserNames {

  private final DynamoDbTable<DynamoUserNamesSchema> userNames;

  public DynamoManagedUserNames(DynamoDbClient dynamoDbClient) {
    super(dynamoDbClient, "DynamoProjectionStateTracking");
    this.userNames =
            enhancedClient.table("UserNames", TableSchema.fromBean(DynamoUserNamesSchema.class));
  }

  @Override
  public DynamoDbTable<DynamoUserNamesSchema> userNames() {
    return userNames;
  }
}
