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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.factcast.test.FactCastIntegrationTestExecutionListener;
import org.factcast.test.FactCastIntegrationTestExtension;
import org.springframework.test.context.TestContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;

@SuppressWarnings({"rawtypes", "resource"})
@Slf4j
public class DynamoIntegrationTestExtension implements FactCastIntegrationTestExtension {

  public static final int DYNAMO_PORT = 6380;
  private final Map<DynamoConfig.Config, Containers> executions = new ConcurrentHashMap<>();

  private void startOrReuse(DynamoConfig.Config config) {
    final Containers container =
        executions.computeIfAbsent(
            config,
            key -> {
              GenericContainer dynamo =
                  new GenericContainer<>("amazon/dynamodb-local:" + config.redisVersion())
                      .withExposedPorts(DYNAMO_PORT)
                      .withNetwork(FactCastIntegrationTestExecutionListener._docker_network);
              dynamo.start();

              return new Containers(
                  dynamo,
                  new DynamoProxy(
                      FactCastIntegrationTestExecutionListener.createProxy(dynamo, DYNAMO_PORT),
                      FactCastIntegrationTestExecutionListener.client()));
            });

    ContainerProxy redisProxy = container.dynamoProxy().get();
    System.setProperty("spring.data.redis.host", redisProxy.getContainerIpAddress());
    System.setProperty("spring.data.redis.port", String.valueOf(redisProxy.getProxyPort()));
  }

  @Override
  public void wipeExternalDataStore(TestContext ctx) {
    //TODO How to wipe out dynamo?
//    ctx.getApplicationContext()
//        .getAutowireCapableBeanFactory()
//        .getBean(RedissonClient.class)
//        .getKeys()
//        .flushall();
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
    GenericContainer redis;
    DynamoProxy dynamoProxy;
  }

  @Override
  public void prepareContainers(TestContext ctx) {
    final DynamoConfig.Config config = discoverConfig(ctx.getTestClass());
    startOrReuse(config);
  }
}
