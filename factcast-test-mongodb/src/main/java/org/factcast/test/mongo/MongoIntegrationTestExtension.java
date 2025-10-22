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
package org.factcast.test.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.factcast.test.FactCastIntegrationTestExecutionListener;
import org.factcast.test.FactCastIntegrationTestExtension;
import org.springframework.test.context.TestContext;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;

@SuppressWarnings({"rawtypes", "resource"})
@Slf4j
public class MongoIntegrationTestExtension implements FactCastIntegrationTestExtension {

  public static final int MONGO_PORT = 27017;
  private final Map<MongoConfig.Config, Containers> executions = new ConcurrentHashMap<>();

  private void startOrReuse(MongoConfig.Config config) {
    final Containers container =
        executions.computeIfAbsent(
            config,
            key -> {
              GenericContainer mongo =
                  new GenericContainer<>("mongo:" + config.mongoVersion())
                      .withCommand("--replSet", "rs0", "--bind_ip_all")
                      .withExposedPorts(MONGO_PORT)
                      .withNetwork(FactCastIntegrationTestExecutionListener._docker_network);
              mongo.start();

              try {
                Container.ExecResult init =
                    mongo.execInContainer(
                        "mongosh",
                        "--quiet",
                        "--eval",
                        "rs.initiate({_id:'rs0', members:[{_id:0, host:'localhost:27017'}]})");
                if (init.getExitCode() != 0 && !init.getStderr().contains("already initialized")) {
                  throw new IllegalStateException("rs.initiate failed: " + init.getStderr());
                }

                Container.ExecResult wait =
                    mongo.execInContainer(
                        "mongosh",
                        "--quiet",
                        "--eval",
                        "while(rs.status().myState!=1){ sleep(200); } ; 'PRIMARY'");
                if (wait.getExitCode() != 0) {
                  throw new IllegalStateException("RS never became PRIMARY: " + wait.getStderr());
                }
              } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
              }

              MongoProxy mongoProxy =
                  new MongoProxy(
                      FactCastIntegrationTestExecutionListener.createProxy(mongo, MONGO_PORT),
                      FactCastIntegrationTestExecutionListener.client());

              return new Containers(
                  mongo,
                  mongoProxy,
                  MongoClients.create(
                      "mongodb://"
                          + mongoProxy.get().getContainerIpAddress()
                          + ":"
                          + mongoProxy.get().getProxyPort()
                          + "/?replicaSet=rs0&directConnection=true"));
            });

    ContainerProxy mongoProxy = container.mongoProxy().get();
    System.setProperty("mongodb.local.host", mongoProxy.getContainerIpAddress());
    System.setProperty("mongodb.local.port", String.valueOf(mongoProxy.getProxyPort()));
  }

  @Override
  public void wipeExternalDataStore(TestContext ctx) {
    final MongoConfig.Config config = discoverConfig(ctx.getTestClass());
    final MongoClient client = executions.get(config).client;

    for (String dbName : client.listDatabaseNames()) {
      if (dbName.equals("admin") || dbName.equals("local") || dbName.equals("config")) {
        continue; // skip internal databases
      }
      MongoDatabase db = client.getDatabase(dbName);
      for (String collName : db.listCollectionNames()) {
        if (collName.contains("system.")) {
          // skip system collections
          continue;
        }
        db.getCollection(collName).drop();
      }
    }
  }

  @Override
  public void injectFields(TestContext ctx) {
    final MongoConfig.Config config = discoverConfig(ctx.getTestClass());
    final Containers containers = executions.get(config);
    FactCastIntegrationTestExtension.inject(ctx.getTestInstance(), containers.container);
  }

  private MongoConfig.Config discoverConfig(Class<?> i) {
    return Optional.ofNullable(i)
        .flatMap(x -> Optional.ofNullable(x.getAnnotation(MongoConfig.class)))
        .map(MongoConfig.Config::from)
        .orElse(MongoConfig.Config.defaults());
  }

  @Value
  static class Containers {
    GenericContainer container;
    MongoProxy mongoProxy;
    MongoClient client;
  }

  @Override
  public void prepareContainers(TestContext ctx) {
    final MongoConfig.Config config = discoverConfig(ctx.getTestClass());
    startOrReuse(config);
  }
}
