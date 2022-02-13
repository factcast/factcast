package org.factcast.itests.factus;

import static java.util.UUID.*;
import static org.assertj.core.api.Assertions.*;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.waiters.WaiterParameters;
import java.util.ArrayList;
import java.util.UUID;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Factus;
import org.factcast.factus.dynamodb.DynamoDBConstants;
import org.factcast.factus.dynamodb.DynamoDBTransaction;
import org.factcast.factus.dynamodb.tx.DynamoDBTransactional;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.proj.BatchDynamoManagedUserNames;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.dynamodb.DynamoDBTestUtil;
import org.junit.jupiter.api.*;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = {Application.class})
@EnableAutoConfiguration(
    exclude = {DataSourceAutoConfiguration.class, RedissonAutoConfiguration.class})
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
public class DynamoBatchingITest extends AbstractFactCastIntegrationTest {

  @Autowired Factus factus;
  private static AmazonDynamoDB client;
  final int NUMBER_OF_EVENTS = 10;
  public static final String TABLE_NAME = "dyn-usernames";




  @BeforeAll
  public static void setUp() {
    client = DynamoDBTestUtil.getClient();

    {
      client.createTable(
          new CreateTableRequest()
              .withTableName(TABLE_NAME)
              .withKeySchema(
                  new KeySchemaElement().withKeyType(KeyType.HASH).withAttributeName("_id"))
              .withBillingMode(BillingMode.PAY_PER_REQUEST)
              .withAttributeDefinitions(
                  new AttributeDefinition()
                      .withAttributeName("_id")
                      .withAttributeType(ScalarAttributeType.S)));

      var describeTable = new DescribeTableRequest().withTableName(TABLE_NAME);
      var waitParams = new WaiterParameters<DescribeTableRequest>().withRequest(describeTable);
      client.waiters().tableExists().run(waitParams);
    }
    {
      client.createTable(
          new CreateTableRequest()
              .withTableName(DynamoDBConstants.LOCK_TABLE)
              .withKeySchema(
                  new KeySchemaElement().withKeyType(KeyType.HASH).withAttributeName("_id"))
              .withBillingMode(BillingMode.PAY_PER_REQUEST)
              .withAttributeDefinitions(
                  new AttributeDefinition()
                      .withAttributeName("_id")
                      .withAttributeType(ScalarAttributeType.S)));

      var describeTable = new DescribeTableRequest().withTableName(DynamoDBConstants.LOCK_TABLE);
      var waitParams = new WaiterParameters<DescribeTableRequest>().withRequest(describeTable);
      client.waiters().tableExists().run(waitParams);
    }
  }

  @Nested
  class Managed {

    @BeforeEach
    public void setup() {

      var l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
      for (int i = 0; i < NUMBER_OF_EVENTS; i++) {
        l.add(new UserCreated(randomUUID(), "" + i));
      }
      log.info("publishing {} Events ", NUMBER_OF_EVENTS);
      factus.publish(l);
    }

    @SneakyThrows
    @Test
    void bulkAppliesInBatch3() {
      var p = new BatchDynamoManagedUserNamesSize3(client);
      factus.update(p);

      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(4); // expected at 3,6,9,10
    }

    @SneakyThrows
    @Test
    void bulkAppliesInBatch2() {
      var p = new BatchDynamoManagedUserNamesSize2(client);
      factus.update(p);

      assertThat(p.userNames().size()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(5); // expected at 2,4,6,8,10
    }

    @SneakyThrows
    @Test
    void discardsFaultyBulk() {
      BatchDynamoManagedUserNamesSizeBlowAt7th p =
          new BatchDynamoManagedUserNamesSizeBlowAt7th(client);

      assertThat(p.userNames()).isEmpty();

      try {
        factus.update(p);
      } catch (Throwable expected) {
        // ignore
      }

      // only first bulk (size = 5) should be executed
      assertThat(p.userNames()).hasSize(5);
      assertThat(p.stateModifications()).isEqualTo(1);
    }
  }

  static class TrackingBatchDynamoManagedUserNames extends BatchDynamoManagedUserNames {
    public TrackingBatchDynamoManagedUserNames(AmazonDynamoDB redisson) {
      super(redisson);
    }

    @Getter int stateModifications = 0;

    @Override
    public void factStreamPosition(@NonNull UUID factStreamPosition) {
      stateModifications++;
      super.factStreamPosition(factStreamPosition);
    }
  }

  @ProjectionMetaData(serial = 1)
  @DynamoDBTransactional(bulkSize = 2)
  static class BatchDynamoManagedUserNamesSize2 extends TrackingBatchDynamoManagedUserNames {
    public BatchDynamoManagedUserNamesSize2(AmazonDynamoDB redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(serial = 1)
  @DynamoDBTransactional(bulkSize = 3)
  static class BatchDynamoManagedUserNamesSize3 extends TrackingBatchDynamoManagedUserNames {
    public BatchDynamoManagedUserNamesSize3(AmazonDynamoDB redisson) {
      super(redisson);
    }
  }

  @ProjectionMetaData(serial = 1)
  @DynamoDBTransactional(bulkSize = 5)
  static class BatchDynamoManagedUserNamesSizeBlowAt7th
      extends TrackingBatchDynamoManagedUserNames {
    private int count;

    public BatchDynamoManagedUserNamesSizeBlowAt7th(AmazonDynamoDB redisson) {
      super(redisson);
    }

    @Override
    protected void apply(UserCreated created, DynamoDBTransaction tx) {
      if (count++ == 8) { // blow the second bulk
        throw new IllegalStateException("Bad luck");
      }
      super.apply(created, tx);
    }
  }
}
