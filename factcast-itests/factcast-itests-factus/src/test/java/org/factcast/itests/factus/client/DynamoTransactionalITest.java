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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.Map;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.Factus;
import org.factcast.factus.dynamo.tx.DynamoTransaction;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.projection.ScopedName;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.DynamoProjectionConfiguration;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.proj.TxDynamoManagedUserNames;
import org.factcast.itests.factus.proj.TxDynamoSubscribedUserNames;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

@SpringBootTest
@ContextConfiguration(classes = {TestFactusApplication.class, DynamoProjectionConfiguration.class})
@Slf4j
public class DynamoTransactionalITest extends AbstractFactCastIntegrationTest {
  @Autowired Factus factus;
  @Autowired DynamoDbClient dynamoDbClient;
  final int NUMBER_OF_EVENTS = 10;

  @BeforeEach
  void setupTables() {
    try {
      dynamoDbClient.createTable(CreateTableRequest.builder().tableName("UserNames").build());
      dynamoDbClient.createTable(
          CreateTableRequest.builder().tableName("DynamoProjectionStateTracking").build());
    } catch (Exception e) {
      // TODO: rethink where to create the tables so that we don't need to recreate
      log.warn("This will probably fail the second time.", e);
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

      // Create State table

      String stateName = "Hi";
      dynamoDbClient.createTable(CreateTableRequest.builder().tableName(stateName).build());

      // Create individual projection tables
      dynamoDbClient.createTable(
          CreateTableRequest.builder()
              .tableName(
                  ScopedName.fromProjectionMetaData(TxDynamoManagedUserNamesSize3.class).asString())
              .build());
    }

    @SneakyThrows
    @Test
    void bulkAppliesInTransaction3() {
      TxDynamoManagedUserNamesSize3 p = new TxDynamoManagedUserNamesSize3(dynamoDbClient);
      factus.update(p);

      assertThat(p.userNames()).hasSize(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(4); // expected at 3,6,9,10
    }

    @SneakyThrows
    @Test
    void bulkAppliesInTransaction2() {
      TxDynamoManagedUserNamesSize2 p = new TxDynamoManagedUserNamesSize2(dynamoDbClient);
      factus.update(p);

      assertThat(p.userNames()).hasSize(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(5); // expected at 2,4,6,8,10
    }

    @SneakyThrows
    @Test
    void bulkAppliesInTransactionTimeout() {
      TxDynamoManagedUserNamesTimeout p = new TxDynamoManagedUserNamesTimeout(dynamoDbClient);
      assertThatThrownBy(() -> factus.update(p)).isInstanceOf(StatusRuntimeException.class);

      // factStreamPosition was called once, inside the transaction, but its effect
      // will have been rolled back as commit() fails with a timeout
      assertThat(p.stateModifications()).isOne();
      // therefore the FSP must be unset
      Assertions.assertThat(p.factStreamPosition()).isNull();
    }

    @SneakyThrows
    @Test
    void rollsBack() {
      TxDynamoManagedUserNamesSizeBlowAt7Th p =
          new TxDynamoManagedUserNamesSizeBlowAt7Th(dynamoDbClient);

      assertThat(p.userNames()).isEmpty();

      try {
        factus.update(p);
      } catch (Throwable expected) {
        // ignore
      }

      // only first bulk (size = 5) should be executed
      assertThat(p.userNames()).hasSize(6); // 1-5 + 6
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

      assertThat(p.userNames()).hasSize(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(4); // expected at 3,6,9,10
    }

    @SneakyThrows
    @Test
    void bulkAppliesInTransaction2() {
      TxDynamoSubscribedUserNamesSize2 p = new TxDynamoSubscribedUserNamesSize2(dynamoDbClient);
      factus.subscribeAndBlock(p).awaitCatchup();

      assertThat(p.userNames()).hasSize(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(5); // expected at 2,4,6,8,10
    }

    @SneakyThrows
    @Test
    void bulkAppliesInTransactionTimeout() {
      TxDynamoSubscribedUserNamesTimeout p = new TxDynamoSubscribedUserNamesTimeout(dynamoDbClient);
      Assertions.assertThatThrownBy(
              () -> {
                factus.subscribeAndBlock(p).awaitCatchup();
              })
          .isInstanceOf(StatusRuntimeException.class);

      assertThat(p.userNames()).isEmpty();

      // factStreamPosition was called once, inside the transaction, but its effect
      // will have been rolled back as commit() fails with a timeout
      assertThat(p.stateModifications()).isOne();
      // therefore the FSP must be unset
      Assertions.assertThat(p.factStreamPosition()).isNull();
    }

    @SneakyThrows
    @Test
    void rollsBack() {
      TxDynamoSubscribedUserNamesSizeBlowAt7Th p =
          new TxDynamoSubscribedUserNamesSizeBlowAt7Th(dynamoDbClient);

      assertThat(p.userNames()).isEmpty();

      try {
        factus.subscribeAndBlock(p).awaitCatchup();
      } catch (Throwable expected) {
        // ignore
      }

      // first bulk (size = 5) should be applied successfully
      // second bulk (size = 5) should have the first fact applied (retry after error))
      assertThat(p.userNames()).hasSize(7); // [0,6]
      assertThat(p.stateModifications()).isEqualTo(2); // 0-4 and 5-6
    }
  }

  static class TrackingTxDynamoManagedUserNames extends TxDynamoManagedUserNames {
    public TrackingTxDynamoManagedUserNames(DynamoDbClient client) {
      super(client);
    }

    @Getter int stateModifications = 0;

    @Override
    public void factStreamPosition(FactStreamPosition factStreamPosition) {
      stateModifications++;
      super.factStreamPosition(factStreamPosition);
    }
  }

  static class TrackingTxDynamoSubscribedUserNames extends TxDynamoSubscribedUserNames {
    public TrackingTxDynamoSubscribedUserNames(DynamoDbClient redisson) {
      super(redisson);
    }

    @Getter int stateModifications = 0;

    @Override
    public void factStreamPosition(FactStreamPosition factStreamPosition) {
      System.out.println(factStreamPosition);
      stateModifications++;
      super.factStreamPosition(factStreamPosition);
    }
  }

  @ProjectionMetaData(revision = 1)
  @RedisTransactional(bulkSize = 2)
  static class TxDynamoManagedUserNamesSize2 extends TrackingTxDynamoManagedUserNames {
    public TxDynamoManagedUserNamesSize2(DynamoDbClient redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(revision = 1)
  @RedisTransactional(bulkSize = 3)
  static class TxDynamoManagedUserNamesSize3 extends TrackingTxDynamoManagedUserNames {
    public TxDynamoManagedUserNamesSize3(DynamoDbClient redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(revision = 1)
  @RedisTransactional(timeout = 50, bulkSize = 100)
  static class TxDynamoManagedUserNamesTimeout extends TrackingTxDynamoManagedUserNames {
    public TxDynamoManagedUserNamesTimeout(DynamoDbClient redisson) {
      super(redisson);
    }

    @Override
    @SneakyThrows
    protected void apply(UserCreated created, DynamoTransaction tx) {
      tx.add(
          TransactWriteItem.builder()
              .put(
                  Put.builder()
                      .tableName(projectionKey())
                      .item(
                          Map.of(
                              "key",
                              AttributeValue.fromS(created.aggregateId().toString()),
                              "value",
                              AttributeValue.fromS(created.userName())))
                      .build())
              .build());

      Thread.sleep(100);
    }

    @Override
    protected void commit(@NonNull DynamoTransaction runningTransaction) {
      super.commit(runningTransaction);
    }
  }

  @ProjectionMetaData(revision = 1)
  @RedisTransactional(bulkSize = 2)
  static class TxDynamoSubscribedUserNamesSize2 extends TrackingTxDynamoSubscribedUserNames {
    public TxDynamoSubscribedUserNamesSize2(DynamoDbClient redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(revision = 1)
  @RedisTransactional(bulkSize = 3)
  static class TxDynamoSubscribedUserNamesSize3 extends TrackingTxDynamoSubscribedUserNames {
    public TxDynamoSubscribedUserNamesSize3(DynamoDbClient redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(revision = 1)
  @RedisTransactional(timeout = 150, bulkSize = 100)
  static class TxDynamoSubscribedUserNamesTimeout extends TrackingTxDynamoSubscribedUserNames {
    public TxDynamoSubscribedUserNamesTimeout(DynamoDbClient redisson) {
      super(redisson);
    }

    @Override
    @SneakyThrows
    protected void apply(UserCreated created, DynamoTransaction tx) {
      //      RMap<UUID, String> userNames = tx.getMap(projectionKey(), codec);
      //      userNames.put(created.aggregateId(), created.userName());

      tx.add(
          TransactWriteItem.builder()
              .put(
                  Put.builder()
                      .tableName(projectionKey())
                      .item(
                          Map.of(
                              "key",
                              AttributeValue.fromS(created.aggregateId().toString()),
                              "value",
                              AttributeValue.fromS(created.userName())))
                      .build())
              .build());

      Thread.sleep(100);
    }
  }

  @ProjectionMetaData(revision = 1)
  @RedisTransactional(bulkSize = 5)
  static class TxDynamoManagedUserNamesSizeBlowAt7Th extends TrackingTxDynamoManagedUserNames {
    private int count;

    public TxDynamoManagedUserNamesSizeBlowAt7Th(DynamoDbClient redisson) {
      super(redisson);
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
  @RedisTransactional(bulkSize = 5)
  static class TxDynamoSubscribedUserNamesSizeBlowAt7Th
      extends TrackingTxDynamoSubscribedUserNames {
    private int count;

    public TxDynamoSubscribedUserNamesSizeBlowAt7Th(DynamoDbClient redisson) {
      super(redisson);
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
