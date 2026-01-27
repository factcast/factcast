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

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.bson.UuidRepresentation;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.factcast.itests.factus.proj.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MongoDbProjectionConfiguration {
  @Bean
  MongoClient mongoDbClient(
      @Value("${mongodb.local.host}") String url, @Value("${mongodb.local.port}") String port) {
    log.info("Creating MongoDbClient with url: {}, port: {}", url, port);
    final var connectionString = "mongodb://" + url + ":" + port;
    CodecRegistry pojoCodecRegistry =
        fromProviders(PojoCodecProvider.builder().automatic(true).build());
    CodecRegistry codecRegistry =
        fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
    return MongoClients.create(
        MongoClientSettings.builder()
            .uuidRepresentation(UuidRepresentation.STANDARD)
            .applyConnectionString(new ConnectionString(connectionString))
            .codecRegistry(codecRegistry)
            .build());
  }

  @Bean
  MongoDatabase mongoDatabase(
      MongoClient mongoDbClient, @Value("${mongodb.local.db.name}") String dbName) {
    return mongoDbClient.getDatabase(dbName);
  }

  @Bean
  MongoDbManagedUserNames mongoDbManagedUserNames(MongoDatabase mongoDB) {
    return new MongoDbManagedUserNames(mongoDB);
  }

  @Bean
  MongoDbSubscribedUserNames MongoDbSubscribedUserNames(MongoDatabase mongoDB) {
    return new MongoDbSubscribedUserNames(mongoDB);
  }
}
