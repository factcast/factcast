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
