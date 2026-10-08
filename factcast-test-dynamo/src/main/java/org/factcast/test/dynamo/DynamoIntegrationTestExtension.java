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
package org.factcast.test.dynamo;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.factcast.test.FactCastIntegrationTestExecutionListener;
import org.factcast.test.FactCastIntegrationTestExtension;
import org.springframework.test.context.TestContext;
import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@SuppressWarnings({"rawtypes", "resource"})
@Slf4j
public class DynamoIntegrationTestExtension implements FactCastIntegrationTestExtension {

  public static final int DYNAMO_PORT = 8000;
  private final Map<DynamoConfig.Config, Containers> executions = new ConcurrentHashMap<>();

  private void startOrReuse(DynamoConfig.Config config) {
    final Containers container =
        executions.computeIfAbsent(
            config,
            key -> {
              GenericContainer dynamo =
                  new GenericContainer<>("amazon/dynamodb-local:" + config.dynamoVersion())
                      .withExposedPorts(DYNAMO_PORT)
                      .withNetwork(FactCastIntegrationTestExecutionListener._docker_network);
              dynamo.start();

              DynamoProxy dynamoProxy =
                  new DynamoProxy(
                      FactCastIntegrationTestExecutionListener.createProxy(
                          "dynamo", dynamo, DYNAMO_PORT),
                      FactCastIntegrationTestExecutionListener.client());
              return new Containers(
                  dynamo,
                  dynamoProxy,
                  DynamoDbClient.builder()
                      .region(Region.EU_CENTRAL_1)
                      .credentialsProvider(
                          StaticCredentialsProvider.create(
                              AwsBasicCredentials.create(
                                  "AKIAIOSFODNN7EXAMPLE",
                                  "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")))
                      .endpointOverride(
                          URI.create(
                              "http://"
                                  + dynamoProxy.get().host()
                                  + ":"
                                  + dynamoProxy.get().port()))
                      .build());
            });

    FactCastIntegrationTestExecutionListener.ProxiedEndpoint dynamoProxy =
        container.dynamoProxy().get();
    System.setProperty("dynamodb.local.host", dynamoProxy.host());
    System.setProperty("dynamodb.local.port", String.valueOf(dynamoProxy.port()));
  }

  @Override
  public void wipeExternalDataStore(TestContext ctx) {
    final DynamoConfig.Config config = discoverConfig(ctx.getTestClass());
    final DynamoDbClient client = executions.get(config).client;

    List<String> tables = client.listTables().tableNames();
    // Delete all tables
    tables.forEach(t -> client.deleteTable(DeleteTableRequest.builder().tableName(t).build()));
  }

  @Override
  public void injectFields(TestContext ctx) {
    final DynamoConfig.Config config = discoverConfig(ctx.getTestClass());
    final Containers containers = executions.get(config);
    FactCastIntegrationTestExtension.inject(ctx.getTestInstance(), containers.dynamoProxy);
  }

  private DynamoConfig.Config discoverConfig(Class<?> i) {
    return Optional.ofNullable(i)
        .flatMap(x -> Optional.ofNullable(x.getAnnotation(DynamoConfig.class)))
        .map(DynamoConfig.Config::from)
        .orElse(DynamoConfig.Config.defaults());
  }

  @Value
  static class Containers {
    GenericContainer container;
    DynamoProxy dynamoProxy;
    DynamoDbClient client;
  }

  @Override
  public void prepareContainers(TestContext ctx) {
    final DynamoConfig.Config config = discoverConfig(ctx.getTestClass());
    startOrReuse(config);
  }
}
