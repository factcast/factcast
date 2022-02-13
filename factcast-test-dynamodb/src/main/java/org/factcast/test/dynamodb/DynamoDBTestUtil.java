package org.factcast.test.dynamodb;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import lombok.SneakyThrows;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;

public class DynamoDBTestUtil {

  @SneakyThrows
  public static AmazonDynamoDB getClient(LocalStackContainer localstack) {
    AwsClientBuilder.EndpointConfiguration endpointConfiguration =
        localstack.getEndpointConfiguration(Service.DYNAMODB);
    return AmazonDynamoDBClientBuilder.standard()
        .withEndpointConfiguration(endpointConfiguration)
        .withCredentials(new DefaultAWSCredentialsProviderChain())
        .build();
  }

  @SneakyThrows
  public static AmazonDynamoDB getClient() {
    return getClient(DynamoDBIntegrationTestExtension.localstack);
  }
}
