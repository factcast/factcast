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
package org.factcast.itests.factus.client;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.Factus;
import org.factcast.factus.dynamo.DynamoProjectionState;
import org.factcast.factus.dynamo.tx.DynamoTransaction;
import org.factcast.factus.dynamo.tx.DynamoTransactional;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.DynamoProjectionConfiguration;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.proj.TxDynamoManagedUserNames;
import org.factcast.itests.factus.proj.TxDynamoSubscribedUserNames;
import org.factcast.itests.factus.proj.UserNamesDynamoSchema;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import software.amazon.awssdk.core.internal.waiters.ResponseOrException;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;

@SpringBootTest
@ContextConfiguration(classes = {TestFactusApplication.class, DynamoProjectionConfiguration.class})
@Slf4j
public class DynamoTransactionalITest extends AbstractFactCastIntegrationTest {
  @Autowired Factus factus;
  @Autowired DynamoDbClient dynamoDbClient;
  @Autowired DynamoDbEnhancedClient dynamoDbEnhancedClient;
  static final int NUMBER_OF_EVENTS = 10;
  static final String STATE_TABLE_NAME = "DynamoProjectionStateTracking";

  @BeforeEach
  void setupTables() {
    try {
      // Old config with regular client
      //      dynamoDbClient.createTable(
      //          CreateTableRequest.builder()
      //              .tableName("UserNames")
      //              .keySchema(
      //
      // KeySchemaElement.builder().attributeName("key").keyType(KeyType.HASH).build())
      //              .attributeDefinitions(
      //                  AttributeDefinition.builder()
      //                      .attributeName("key")
      //                      .attributeType(ScalarAttributeType.S)
      //                      .build())
      //              .provisionedThroughput(
      //                  ProvisionedThroughput.builder()
      //                      .readCapacityUnits(100L)
      //                      .writeCapacityUnits(100L)
      //                      .build())
      //              .build());

      DynamoDbTable<UserNamesDynamoSchema> userNamesTable =
          dynamoDbEnhancedClient.table(
              "UserNames", TableSchema.fromBean(UserNamesDynamoSchema.class));
      userNamesTable.createTable(
          builder ->
              builder.provisionedThroughput(
                  b -> b.readCapacityUnits(100L).writeCapacityUnits(100L).build()));

      DynamoDbTable<DynamoProjectionState> stateTable =
          dynamoDbEnhancedClient.table(
              STATE_TABLE_NAME, TableSchema.fromImmutableClass(DynamoProjectionState.class));
      stateTable.createTable(
          builder ->
              builder.provisionedThroughput(
                  b -> b.readCapacityUnits(100L).writeCapacityUnits(100L).build()));

      try (DynamoDbWaiter waiter =
          DynamoDbWaiter.builder()
              .client(dynamoDbClient)
              .build()) { // DynamoDbWaiter is Autocloseable
        ResponseOrException<DescribeTableResponse> response =
            waiter
                .waitUntilTableExists(builder -> builder.tableName(STATE_TABLE_NAME).build())
                .matched();

        response
            .response()
            .orElseThrow(() -> new RuntimeException("Customer table was not created."));
        log.info("Customer table was created.");
      }
    } catch (Exception e) {
      log.error("Table creation failed.", e);
    }
  }

  @Nested
  class Managed {
    @BeforeEach
    void setup() {
      var l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
      for (int i = 0; i < NUMBER_OF_EVENTS; i++) {
        l.add(new UserCreated(randomUUID(), getClass().getSimpleName() + ":" + i));
      }
      log.info("publishing {} Events ", NUMBER_OF_EVENTS);
      factus.publish(l);
    }

    @SneakyThrows
    @Test
    void bulkAppliesInTransaction3() {
      TxDynamoManagedUserNamesSize3 p = new TxDynamoManagedUserNamesSize3(dynamoDbClient);
      factus.update(p);

      assertThat(p.count()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(4); // expected at 3,6,9,10
    }

    @SneakyThrows
    @Test
    void bulkAppliesInTransaction2() {
      TxDynamoManagedUserNamesSize2 p = new TxDynamoManagedUserNamesSize2(dynamoDbClient);
      factus.update(p);

      assertThat(p.count()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(5); // expected at 2,4,6,8,10
    }

    // TODO : does this really test rollback behaviour?
    @SneakyThrows
    @Test
    void rollsBack() {
      TxDynamoManagedUserNamesSizeBlowAt7th p =
          new TxDynamoManagedUserNamesSizeBlowAt7th(dynamoDbClient);

      assertThat(p.count()).isEqualTo(0);

      try {
        factus.update(p);
      } catch (Throwable expected) {
        // ignore
      }

      // only first bulk (size = 5) should be executed
      assertThat(p.count()).isEqualTo(6); // 1-5 + 6
      assertThat(p.stateModifications()).isEqualTo(2); // 5,6
    }
  }

  @Nested
  class Subscribed {
    @BeforeEach
    void setup() {
      var l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
      for (int i = 0; i < NUMBER_OF_EVENTS; i++) {
        l.add(new UserCreated(randomUUID(), getClass().getSimpleName() + ":" + i));
      }
      log.info("publishing {} Events ", NUMBER_OF_EVENTS);
      factus.publish(l);
    }

    @SneakyThrows
    @Test
    void bulkAppliesInTransaction3() {
      TxDynamoSubscribedUserNamesSize3 p = new TxDynamoSubscribedUserNamesSize3(dynamoDbClient);
      factus.subscribeAndBlock(p).awaitCatchup();

      assertThat(p.count()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(4); // expected at 3,6,9,10
    }

    @SneakyThrows
    @Test
    void bulkAppliesInTransaction2() {
      TxDynamoSubscribedUserNamesSize2 p = new TxDynamoSubscribedUserNamesSize2(dynamoDbClient);
      factus.subscribeAndBlock(p).awaitCatchup();

      assertThat(p.count()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(5); // expected at 2,4,6,8,10
    }

    @SneakyThrows
    @Test
    void rollsBack() {
      // batch size = 5; throws while applying the 7th event.
      TxDynamoSubscribedUserNamesSizeBlowAt7Th p =
          new TxDynamoSubscribedUserNamesSizeBlowAt7Th(dynamoDbClient);

      assertThat(p.count()).isEqualTo(0);

      try {
        factus.subscribeAndBlock(p).awaitCatchup();
      } catch (Throwable expected) {
        // ignore
      }

      // first bulk (size = 5) should be applied successfully
      // second bulk (size = 5) should have the first fact applied (retry after error))
      assertThat(p.count()).isEqualTo(7); // [0,6]
      assertThat(p.stateModifications()).isEqualTo(2); // 0-4 and 5-6
    }
  }

  @Getter
  static class TrackingTxDynamoManagedUserNames extends TxDynamoManagedUserNames {
    public TrackingTxDynamoManagedUserNames(DynamoDbClient client) {
      super(client);
    }

    int stateModifications = 0;

    @Override
    public void factStreamPosition(FactStreamPosition factStreamPosition) {
      stateModifications++;
      super.factStreamPosition(factStreamPosition);
    }
  }

  @Getter
  static class TrackingTxDynamoSubscribedUserNames extends TxDynamoSubscribedUserNames {
    public TrackingTxDynamoSubscribedUserNames(DynamoDbClient dynamoDbClient) {
      super(dynamoDbClient);
    }

    int stateModifications = 0;

    @Override
    public void factStreamPosition(FactStreamPosition factStreamPosition) {
      System.out.println(factStreamPosition);
      stateModifications++;
      super.factStreamPosition(factStreamPosition);
    }
  }

  @ProjectionMetaData(revision = 1)
  @DynamoTransactional(bulkSize = 2)
  static class TxDynamoManagedUserNamesSize2 extends TrackingTxDynamoManagedUserNames {
    public TxDynamoManagedUserNamesSize2(DynamoDbClient dynamoDbClient) {
      super(dynamoDbClient);
    }
  }

  @ProjectionMetaData(revision = 1)
  @DynamoTransactional(bulkSize = 3)
  static class TxDynamoManagedUserNamesSize3 extends TrackingTxDynamoManagedUserNames {
    public TxDynamoManagedUserNamesSize3(DynamoDbClient dynamoDbClient) {
      super(dynamoDbClient);
    }
  }

  @ProjectionMetaData(revision = 1)
  @DynamoTransactional(bulkSize = 2)
  static class TxDynamoSubscribedUserNamesSize2 extends TrackingTxDynamoSubscribedUserNames {
    public TxDynamoSubscribedUserNamesSize2(DynamoDbClient dynamoDbClient) {
      super(dynamoDbClient);
    }
  }

  @ProjectionMetaData(revision = 1)
  @DynamoTransactional(bulkSize = 3)
  static class TxDynamoSubscribedUserNamesSize3 extends TrackingTxDynamoSubscribedUserNames {
    public TxDynamoSubscribedUserNamesSize3(DynamoDbClient dynamoDbClient) {
      super(dynamoDbClient);
    }
  }

  @ProjectionMetaData(revision = 1)
  @DynamoTransactional(bulkSize = 5)
  static class TxDynamoManagedUserNamesSizeBlowAt7th extends TrackingTxDynamoManagedUserNames {
    private int count;

    public TxDynamoManagedUserNamesSizeBlowAt7th(DynamoDbClient dynamoDbClient) {
      super(dynamoDbClient);
    }

    @Override
    protected void apply(UserCreated created, DynamoTransaction tx) {
      if (++count == 7) { // blow the second bulk
        throw new IllegalStateException("Bad luck");
      }
      super.apply(created, tx);
    }
  }

  @ProjectionMetaData(revision = 1)
  @DynamoTransactional(bulkSize = 5)
  static class TxDynamoSubscribedUserNamesSizeBlowAt7Th
      extends TrackingTxDynamoSubscribedUserNames {
    private int count;

    public TxDynamoSubscribedUserNamesSizeBlowAt7Th(DynamoDbClient dynamoDbClient) {
      super(dynamoDbClient);
    }

    @Override
    protected void apply(UserCreated created, DynamoTransaction tx) {
      if (count++ == 7) { // blow the second bulk
        throw new IllegalStateException("Bad luck");
      }
      super.apply(created, tx);
    }
  }
}
