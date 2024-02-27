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

import org.factcast.itests.factus.proj.*;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoProjectionConfiguration {
  @Bean
  DynamoManagedUserNames redissonManagedUserNames(DynamoDbClient client) {
    return new DynamoManagedUserNames(client);
  }

  @Bean
  TxDynamoManagedUserNames txRedissonManagedUserNames(DynamoDbClient client) {
    return new TxDynamoManagedUserNames(client);
  }

  @Bean
  TxDynamoSubscribedUserNames txRedissonSubscribedUserNames(DynamoDbClient client) {
    return new TxDynamoSubscribedUserNames(client);
  }

  @Bean
  DynamoDbClient dynamoDbClient() {
    //TODO get it from DynamoIntegrationTestExtension
    return null;
  }
}
