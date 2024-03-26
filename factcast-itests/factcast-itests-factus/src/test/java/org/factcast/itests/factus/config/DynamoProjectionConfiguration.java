/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.itests.factus.config;

import java.net.URI;
import org.factcast.itests.factus.proj.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoProjectionConfiguration {
  @Bean
  DynamoManagedUserNames DynamoManagedUserNames(DynamoDbClient client) {
    return new DynamoManagedUserNames(client);
  }

  @Bean
  TxDynamoManagedUserNames txDynamoManagedUserNames(DynamoDbClient client) {
    return new TxDynamoManagedUserNames(client);
  }

  @Bean
  TxDynamoSubscribedUserNames txDynamoSubscribedUserNames(DynamoDbClient client) {
    return new TxDynamoSubscribedUserNames(client);
  }

  @Bean
  DynamoDbClient dynamoDbClient(
      @Value("${dynamodb.local.host}") String url, @Value("${dynamodb.local.port}") String port) {
    return DynamoDbClient.builder()
        .endpointOverride(URI.create("http://" + url + ":" + port))
        .build();
  }

  // TODO make sure if we can remove the regular client above or pass it as an argument below.
  @Bean
  DynamoDbEnhancedClient dynamoDbEnhancedClient(
      @Value("${dynamodb.local.host}") String url, @Value("${dynamodb.local.port}") String port) {
    return DynamoDbEnhancedClient.builder()
        .dynamoDbClient(
            DynamoDbClient.builder()
                .endpointOverride(URI.create("http://" + url + ":" + port))
                .build())
        .build();
  }
}
