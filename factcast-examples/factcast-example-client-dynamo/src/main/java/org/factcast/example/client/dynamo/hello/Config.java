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
package org.factcast.example.client.dynamo.hello;

import static org.awaitility.Awaitility.await;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.dynamo.DynamoProjectionState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

@Slf4j
@Configuration
public class Config {

  @Bean
  DynamoDbClient dynamoDbClient() {
    return DynamoDbClient.builder().endpointOverride(URI.create("http://localhost:8000")).build();
  }

  @Bean
  DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
    DynamoDbEnhancedClient c = DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
    createTable(c);
    await().until(() -> client.listTables().tableNames().contains("users"));
    return c;
  }

  private void createTable(DynamoDbEnhancedClient c) {

    log.info("Creating tables");

    DynamoDbTable<DynamoProjectionState> stateTable =
        c.table("stateTable", TableSchema.fromImmutableClass(DynamoProjectionState.class));
    try {
      stateTable.describeTable();
    } catch (ResourceNotFoundException e) {
      stateTable.createTable();
      log.info("Created state table");
    }

    DynamoDbTable<UserSchema> userTable =
        c.table("users", TableSchema.fromImmutableClass(UserSchema.class));
    try {
      userTable.describeTable();
    } catch (ResourceNotFoundException e) {
      userTable.createTable();
      log.info("Created user table");
    }
  }
}
