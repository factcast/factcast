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
package org.factcast.test.redis;

import java.util.*;
import java.util.concurrent.*;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.factcast.test.FactCastIntegrationTestExecutionListener;
import org.factcast.test.FactCastIntegrationTestExtension;
import org.redisson.api.RedissonClient;
import org.springframework.test.context.TestContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;

@SuppressWarnings({"rawtypes", "resource"})
@Slf4j
public class RedisIntegrationTestExtension implements FactCastIntegrationTestExtension {

  public static final int REDIS_PORT = 6379;
  private final Map<RedisConfig.Config, Containers> executions = new ConcurrentHashMap<>();

  private void startOrReuse(RedisConfig.Config config) {
    final Containers container =
        executions.computeIfAbsent(
            config,
            key -> {
              GenericContainer redis =
                  new GenericContainer<>("redis:" + config.redisVersion())
                      .withExposedPorts(REDIS_PORT)
                      .withNetwork(FactCastIntegrationTestExecutionListener._docker_network);
              redis.start();

              return new Containers(
                  redis,
                  new RedisProxy(
                      FactCastIntegrationTestExecutionListener.createProxy(redis, REDIS_PORT),
                      FactCastIntegrationTestExecutionListener.client()));
            });

    ContainerProxy redisProxy = container.redisProxy().get();
    System.setProperty("spring.data.redis.host", redisProxy.getContainerIpAddress());
    System.setProperty("spring.data.redis.port", String.valueOf(redisProxy.getProxyPort()));
  }

  @Override
  public void wipeExternalDataStore(TestContext ctx) {
    ctx.getApplicationContext()
        .getAutowireCapableBeanFactory()
        .getBean(RedissonClient.class)
        .getKeys()
        .flushall();
  }

  @Override
  public void injectFields(TestContext ctx) {
    final RedisConfig.Config config = discoverConfig(ctx.getTestClass());
    final Containers containers = executions.get(config);
    FactCastIntegrationTestExtension.inject(ctx.getTestInstance(), containers.redisProxy);
  }

  private RedisConfig.Config discoverConfig(Class<?> i) {
    return Optional.ofNullable(i)
        .flatMap(x -> Optional.ofNullable(x.getAnnotation(RedisConfig.class)))
        .map(RedisConfig.Config::from)
        .orElse(RedisConfig.Config.defaults());
  }

  @Value
  static class Containers {
    GenericContainer redis;
    RedisProxy redisProxy;
  }

  @Override
  public void prepareContainers(TestContext ctx) {
    final RedisConfig.Config config = discoverConfig(ctx.getTestClass());
    startOrReuse(config);
  }
}
