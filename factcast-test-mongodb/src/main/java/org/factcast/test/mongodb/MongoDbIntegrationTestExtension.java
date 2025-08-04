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
package org.factcast.test.mongodb;

import static org.factcast.test.mongodb.MongoDbConfig.TEST_MONGO_DB_NAME;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.factcast.test.FactCastIntegrationTestExecutionListener;
import org.factcast.test.FactCastIntegrationTestExtension;
import org.springframework.test.context.TestContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings({"rawtypes", "resource"})
@Slf4j
public class MongoDbIntegrationTestExtension implements FactCastIntegrationTestExtension {

  public static final int MONGO_DB_PORT = 27017;
  private final Map<MongoDbConfig.Config, Containers> executions = new ConcurrentHashMap<>();

  private void startOrReuse(MongoDbConfig.Config config) {
    final Containers container =
        executions.computeIfAbsent(
            config,
            key -> {
              MongoDBContainer mongoDbContainer =
                  new MongoDBContainer(DockerImageName.parse("mongo:" + config.mongoDbVersion()))
                      .withExposedPorts(MONGO_DB_PORT);
              mongoDbContainer.start();
              int port = mongoDbContainer.getFirstMappedPort();
              log.info(
                  "MongoDB container started on {}:{}", mongoDbContainer.getReplicaSetUrl(), port);

              MongoDbProxy mongoDbProxy =
                  new MongoDbProxy(
                      FactCastIntegrationTestExecutionListener.createProxy(mongoDbContainer, port),
                      FactCastIntegrationTestExecutionListener.client());
              return new Containers(mongoDbContainer, mongoDbProxy);
            });

    ContainerProxy mongoContainerProxy = container.mongoDbProxy().get();
    log.info(
        "Exposing Proxy for MongoDB on {}:{}",
        mongoContainerProxy.getContainerIpAddress(),
        mongoContainerProxy.getProxyPort());

    GenericContainer mongoContainer = container.container();

    System.setProperty("mongodb.local.host", mongoContainer.getHost());
    System.setProperty("mongodb.local.port", String.valueOf(mongoContainer.getFirstMappedPort()));
    System.setProperty("mongodb.local.db.name", TEST_MONGO_DB_NAME);
  }

  @Override
  public void wipeExternalDataStore(TestContext ctx) {
    log.info("Wiping MongoDB database for test: {}", TEST_MONGO_DB_NAME);
    final MongoClient client =
        ctx.getApplicationContext().getAutowireCapableBeanFactory().getBean(MongoClient.class);

    final MongoDatabase database = client.getDatabase(TEST_MONGO_DB_NAME);
    database.drop();
  }

  @Override
  public void injectFields(TestContext ctx) {
    final MongoDbConfig.Config config = discoverConfig(ctx.getTestClass());
    final Containers containers = executions.get(config);
    FactCastIntegrationTestExtension.inject(ctx.getTestInstance(), containers.mongoDbProxy);
  }

  private MongoDbConfig.Config discoverConfig(Class<?> i) {
    return Optional.ofNullable(i)
        .flatMap(x -> Optional.ofNullable(x.getAnnotation(MongoDbConfig.class)))
        .map(MongoDbConfig.Config::from)
        .orElse(MongoDbConfig.Config.defaults());
  }

  @Value
  static class Containers {
    GenericContainer container;
    MongoDbProxy mongoDbProxy;
  }

  @Override
  public void prepareContainers(TestContext ctx) {
    final MongoDbConfig.Config config = discoverConfig(ctx.getTestClass());
    startOrReuse(config);
  }
}
