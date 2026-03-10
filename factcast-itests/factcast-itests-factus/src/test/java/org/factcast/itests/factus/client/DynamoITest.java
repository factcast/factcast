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

import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.altindag.log.LogCaptor;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.Factus;
import org.factcast.factus.FactusImpl;
import org.factcast.factus.dynamo.DynamoProjectionState;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.DynamoProjectionConfiguration;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.factcast.itests.factus.proj.DynamoManagedUserNames;
import org.factcast.itests.factus.proj.DynamoSubscribedUserNames;
import org.factcast.itests.factus.proj.DynamoUserNamesSchema;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.shaded.org.awaitility.Awaitility;
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
class DynamoITest extends AbstractFactCastIntegrationTest {
  @Autowired Factus factus;
  @Autowired DynamoDbClient dynamoDbClient;
  @Autowired DynamoDbEnhancedClient dynamoDbEnhancedClient;
  static final int NUMBER_OF_EVENTS = 10;
  static final String STATE_TABLE_NAME = "DynamoProjectionStateTracking";

  @BeforeEach
  void setupTables() {
    try {
      DynamoDbTable<DynamoUserNamesSchema> userNamesTable =
          dynamoDbEnhancedClient.table(
              "UserNames", TableSchema.fromBean(DynamoUserNamesSchema.class));
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
    void happyPath() {
      ManagedUserNames p = new ManagedUserNames(dynamoDbClient);
      factus.update(p);

      assertThat(p.count()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(10);
    }

    @SneakyThrows
    @Test
    void projectionStoppedAtException() {
      DynamoManagedUserNamesSizeBlowAt7th p =
          new DynamoManagedUserNamesSizeBlowAt7th(dynamoDbClient);

      assertThat(p.count()).isZero();

      try {
        factus.update(p);
      } catch (Throwable expected) {
        // ignore
      }

      assertThat(p.count()).isEqualTo(6);
      assertThat(p.stateModifications()).isEqualTo(6);
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
    void happyPath() {
      SubscribedUserNames p = new SubscribedUserNames(dynamoDbClient);
      factus.subscribeAndBlock(p).awaitCatchup().close();

      assertThat(p.count()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(10);
    }

    @SneakyThrows
    @Test
    void projectionStoppedAtException() {
      // throws while applying the 7th event.
      DynamoSubscribedUserNamesSizeBlowAt7Th p =
          new DynamoSubscribedUserNamesSizeBlowAt7Th(dynamoDbClient);

      assertThat(p.count()).isZero();

      try {
        factus.subscribeAndBlock(p).awaitCatchup().close();
      } catch (Throwable expected) {
        // ignore
      }

      assertThat(p.count()).isEqualTo(6);
      assertThat(p.stateModifications()).isEqualTo(6);
    }

    @Test
    void testTokenReleaseAfterTooManyFailures() throws Exception {
      var subscribedUserNames =
          new TxDynamoSubscribedUserNamesTokenExposedAndThrowsError(dynamoDbClient);

      factus.publish(new UserDeleted(UUID.randomUUID()));

      // The projection doesnt have a token yet
      assertThat(subscribedUserNames.token()).isNull();
      try (var logCaptor = LogCaptor.forClass(FactusImpl.class)) {
        logCaptor.setLogLevelToTrace();
        factus.subscribe(subscribedUserNames);

        // The projection acquiered a token and
        subscribedUserNames.latch.await();
        Awaitility.await()
            .until(
                () ->
                    !logCaptor.getTraceLogs().isEmpty()
                        && logCaptor
                            .getTraceLogs()
                            .get(0)
                            .contains(
                                "Closing AutoCloseable for class class org.factcast.factus.dynamo.DynamoWriterToken"));
      }

      Awaitility.await()
          .until(
              () -> subscribedUserNames.token() != null && !subscribedUserNames.token().isValid());
    }
  }

  @Getter
  static class TrackingDynamoManagedUserNames extends DynamoManagedUserNames {
    public TrackingDynamoManagedUserNames(DynamoDbClient client) {
      super(client);
    }

    int stateModifications;

    @Override
    public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {
      stateModifications++;
      super.factStreamPosition(factStreamPosition);
    }
  }

  @Getter
  static class TrackingDynamoSubscribedUserNames extends DynamoSubscribedUserNames {
    public TrackingDynamoSubscribedUserNames(DynamoDbClient dynamoDbClient) {
      super(dynamoDbClient);
    }

    int stateModifications;

    @Override
    public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {
      System.out.println(factStreamPosition);
      stateModifications++;
      super.factStreamPosition(factStreamPosition);
    }
  }

  @ProjectionMetaData(revision = 1)
  static class ManagedUserNames extends TrackingDynamoManagedUserNames {
    public ManagedUserNames(DynamoDbClient dynamoDbClient) {
      super(dynamoDbClient);
    }
  }

  @ProjectionMetaData(revision = 1)
  static class SubscribedUserNames extends TrackingDynamoSubscribedUserNames {
    public SubscribedUserNames(DynamoDbClient dynamoDbClient) {
      super(dynamoDbClient);
    }
  }

  @ProjectionMetaData(revision = 1)
  static class DynamoManagedUserNamesSizeBlowAt7th extends TrackingDynamoManagedUserNames {
    private int count;

    public DynamoManagedUserNamesSizeBlowAt7th(DynamoDbClient dynamoDbClient) {
      super(dynamoDbClient);
    }

    @Override
    public void apply(UserCreated created) {
      if (++count == 7) { // blow the second bulk
        throw new IllegalStateException("Bad luck");
      }
      super.apply(created);
    }
  }

  @Getter
  @ProjectionMetaData(revision = 1)
  static class TxDynamoSubscribedUserNamesTokenExposedAndThrowsError
      extends TrackingDynamoSubscribedUserNames {
    private final CountDownLatch latch = new CountDownLatch(1);
    private WriterToken token;

    public TxDynamoSubscribedUserNamesTokenExposedAndThrowsError(DynamoDbClient dynamoDbClient) {
      super(dynamoDbClient);
    }

    @Override
    public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
      token = super.acquireWriteToken(maxWait);
      latch.countDown();
      return token;
    }

    @Override
    public void apply(UserDeleted created) {
      throw new IllegalArgumentException("user should be in map but wasnt");
    }
  }

  @ProjectionMetaData(revision = 1)
  static class DynamoSubscribedUserNamesSizeBlowAt7Th extends TrackingDynamoSubscribedUserNames {
    private int count;

    public DynamoSubscribedUserNamesSizeBlowAt7Th(DynamoDbClient dynamoDbClient) {
      super(dynamoDbClient);
    }

    @Override
    public void apply(UserCreated created) {
      if (++count == 7) { // blow the second bulk
        throw new IllegalStateException("Bad luck");
      }
      super.apply(created);
    }
  }
}
