package org.factcast.factus;

import static org.assertj.core.api.Assertions.*;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.waiters.WaiterParameters;
import java.util.Optional;
import lombok.SneakyThrows;
import org.factcast.factus.dynamodb.DynamoConstants;
import org.factcast.factus.dynamodb.DynamoOperations;
import org.factcast.factus.dynamodb.DynamoWriterToken;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;

public class DynamoDBPlayground {

  private static final String TABLE_NAME = "myProjection";

  @Container
  static LocalStackContainer localstack =
      new LocalStackContainer().withServices(LocalStackContainer.Service.DYNAMODB);

  static {
    localstack.start();
  }

  @BeforeAll
  public static void setUp() {
    AmazonDynamoDB client = getClient();

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
              .withTableName(DynamoConstants.LOCK_TABLE)
              .withKeySchema(
                  new KeySchemaElement().withKeyType(KeyType.HASH).withAttributeName("_id"))
              .withBillingMode(BillingMode.PAY_PER_REQUEST)
              .withAttributeDefinitions(
                  new AttributeDefinition()
                      .withAttributeName("_id")
                      .withAttributeType(ScalarAttributeType.S)));

      var describeTable = new DescribeTableRequest().withTableName(DynamoConstants.LOCK_TABLE);
      var waitParams = new WaiterParameters<DescribeTableRequest>().withRequest(describeTable);
      client.waiters().tableExists().run(waitParams);
    }
  }

  @SneakyThrows
  @Test
  void theTestWithNoName() {
    AmazonDynamoDB client = getClient();

    Optional<DynamoWriterToken> lock1 = new DynamoOperations(client).lock("l1");
    assertThat(lock1).isNotEmpty();

    Optional<DynamoWriterToken> lock2 = new DynamoOperations(client).lock("l2");
    assertThat(lock2).isNotEmpty();

    Optional<DynamoWriterToken> lock1b = new DynamoOperations(client).lock("l1");
    assertThat(lock1b).isEmpty();

    lock1.get().close();
    // re-acquire
    lock1b = new DynamoOperations(client).lock("l1");
    assertThat(lock1b).isNotEmpty();
  }

  @SneakyThrows
  private static AmazonDynamoDB getClient() {
    AwsClientBuilder.EndpointConfiguration endpointConfiguration =
        localstack.getEndpointConfiguration(Service.DYNAMODB);
    return AmazonDynamoDBClientBuilder.standard()
        .withEndpointConfiguration(endpointConfiguration)
        .withCredentials(new DefaultAWSCredentialsProviderChain())
        .build();
  }
}
