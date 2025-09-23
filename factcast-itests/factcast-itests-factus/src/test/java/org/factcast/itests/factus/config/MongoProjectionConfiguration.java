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

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class MongoProjectionConfiguration {
  @Bean
  @SneakyThrows
  MongoClient mongoDbClient(
      @Value("${mongodb.local.host}") String url, @Value("${mongodb.local.port}") String port) {
    return MongoClients.create(
        "mongodb://" + url + ":" + port + "/?replicaSet=rs0&directConnection=true");
  }
}
