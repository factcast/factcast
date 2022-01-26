package org.factcast.factus;


import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient;
import com.amazonaws.services.dynamodbv2.CreateDynamoDBTableOptions;
import java.net.URI;
import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

public class DynamoDBPlayground {

  private static final String TABLE_NAME = "myProjection";
  private static final String POS_NAME = "myProjection_position";

  @Container
  static LocalStackContainer localstack =
          new LocalStackContainer().withServices(LocalStackContainer.Service.DYNAMODB);

  static {
    localstack.start();
  }

  @BeforeAll
  public static void setUp() {
    DynamoDbClient client = getClient();
    client.createTable(
            CreateTableRequest.builder()
                    .tableName(TABLE_NAME)
                    .keySchema(
                            KeySchemaElement.builder().keyType(KeyType.HASH).
                                    attributeName("id").build())
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build())
                    .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
                    .build());

    DescribeTableRequest main = DescribeTableRequest.builder().tableName(TABLE_NAME).build();

    client.waiter().waitUntilTableExists(main);

    client.createTable(
            software.amazon.awssdk.services.dynamodb.model.CreateTableRequest.builder()
                    .tableName(POS_NAME)
                    .keySchema(software.amazon.awssdk.services.dynamodb.model.KeySchemaElement.builder().keyType(software.amazon.awssdk.services.dynamodb.model.KeyType.HASH).attributeName("id").build())
                    .attributeDefinitions(software.amazon.awssdk.services.dynamodb.model.AttributeDefinition.builder().attributeName("id").attributeType(software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType.S).build())
                    .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(10L).build())
                    .build());

    DescribeTableRequest pos = DescribeTableRequest.builder().tableName(POS_NAME).build();

    client.waiter().waitUntilTableExists(pos);

    AmazonDynamoDBLockClient.createLockTableInDynamoDB(
            CreateDynamoDBTableOptions.builder(
                            getClient1(),
                            new com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput(10L, 10L),
                            "lockTable")
                    .build());

    DescribeTableRequest lock = DescribeTableRequest.builder().tableName("lockTable").build();

    client.waiter().waitUntilTableExists(lock);


  }

  @Test
  void theTestWithNoName() {
    DynamoDbClient client = getClient();
  }

  @SneakyThrows
  private static DynamoDbClient getClient() {
    com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration endpointConfiguration = localstack.getEndpointConfiguration(Service.DYNAMODB);
    return DynamoDbClient.builder()
            .endpointOverride(new URI(localstack.getEndpointConfiguration(Service.DYNAMODB).getServiceEndpoint()))
            .credentialsProvider(new AwsCredentialsProviderAdapter(localstack.getDefaultCredentialsProvider()))
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

  private static class AwsCredentialsProviderAdapter implements AwsCredentialsProvider {
    private final AWSCredentials credentials;

    public AwsCredentialsProviderAdapter(AWSCredentialsProvider provider) {
      this.credentials = provider.getCredentials();
    }

    @Override
    public AwsCredentials resolveCredentials() {
      return AwsBasicCredentials.create(credentials.getAWSAccessKeyId(), credentials.getAWSSecretKey());
    }
  }
}
