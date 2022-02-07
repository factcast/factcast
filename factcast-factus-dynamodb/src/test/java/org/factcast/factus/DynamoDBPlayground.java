package org.factcast.factus;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.waiters.WaiterParameters;
import lombok.SneakyThrows;
import org.factcast.factus.dynamodb.DynamoConstants;
import org.factcast.factus.dynamodb.DynamoLockItem;
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
    DynamoDBMapper mapper = new DynamoDBMapper(client);

    {
      client.createTable(
          new CreateTableRequest()
              .withTableName(TABLE_NAME)
              .withKeySchema(
                  new KeySchemaElement().withKeyType(KeyType.HASH).withAttributeName("id"))
              .withBillingMode(BillingMode.PAY_PER_REQUEST)
              .withAttributeDefinitions(
                  new AttributeDefinition()
                      .withAttributeName("id")
                      .withAttributeType(ScalarAttributeType.S)));

      var describeTable = new DescribeTableRequest().withTableName(TABLE_NAME);
      var waitParams = new WaiterParameters<DescribeTableRequest>().withRequest(describeTable);
      client.waiters().tableExists().run(waitParams);
    }
    {
      client.createTable(
          mapper
              .generateCreateTableRequest(DynamoLockItem.class)
              .withBillingMode(BillingMode.PAY_PER_REQUEST));

      var describeTable = new DescribeTableRequest().withTableName(DynamoConstants.LOCK_TABLE);
      var waitParams = new WaiterParameters<DescribeTableRequest>().withRequest(describeTable);
      client.waiters().tableExists().run(waitParams);
    }
  }

  @Test
  void theTestWithNoName() {
    AmazonDynamoDB client = getClient();
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

  @SneakyThrows
  private static AmazonDynamoDB getClient1() {
    AmazonDynamoDBClientBuilder standard = AmazonDynamoDBClientBuilder.standard();
    return standard
        .withEndpointConfiguration(
            localstack.getEndpointConfiguration(LocalStackContainer.Service.DYNAMODB))
        .withCredentials(localstack.getDefaultCredentialsProvider())
        .build();
  }
}
