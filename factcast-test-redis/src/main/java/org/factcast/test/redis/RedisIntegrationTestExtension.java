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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.factcast.test.FactCastExtension;
import org.factcast.test.FactCastIntegrationTestExtension;
import org.junit.jupiter.api.extension.*;
import org.redisson.Redisson;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;

@SuppressWarnings("rawtypes")
@Slf4j
public class RedisIntegrationTestExtension implements FactCastIntegrationTestExtension {

  public static final int REDIS_PORT = 6379;
  private final Map<RedisConfig.Config, Containers> executions = new ConcurrentHashMap<>();

  @Override
  public boolean initialize(ExtensionContext ctx) {
    final RedisConfig.Config config = discoverConfig(ctx);
    startOrReuse(config);
    return true;
  }

  private void startOrReuse(RedisConfig.Config config) {
    final Containers container =
        executions.computeIfAbsent(
            config,
            key -> {
              GenericContainer redis =
                  new GenericContainer<>("redis:" + config.redisVersion())
                      .withExposedPorts(REDIS_PORT)
                      .withNetwork(FactCastExtension._docker_network);
              redis.start();

              return new Containers(
                  redis, new RedisProxy(FactCastExtension.proxy(redis, REDIS_PORT)));
            });

    ContainerProxy redisProxy = container.redisProxy().get();
    System.setProperty("spring.redis.host", redisProxy.getContainerIpAddress());
    System.setProperty("spring.redis.port", String.valueOf(redisProxy.getProxyPort()));
  }

  @Override
  public void beforeAll(ExtensionContext ctx) {
    final RedisConfig.Config config = discoverConfig(ctx);
    startOrReuse(config);

    FactCastIntegrationTestExtension.super.beforeAll(ctx);
  }

  @Override
  public void beforeEach(ExtensionContext ctx) {
    final RedisConfig.Config config = discoverConfig(ctx);
    final Containers containers = executions.get(config);

    final var url =
        "redis://" + containers.redis.getHost() + ":" + containers.redis.getMappedPort(REDIS_PORT);
    log.trace("erasing redis state in between tests for {}", url);

    final var clientConfig = new Config().setThreads(1);
    clientConfig.useSingleServer().setAddress(url);

    final var client = Redisson.create(clientConfig);
    client.getKeys().flushdb();
    client.shutdown();

    ctx.getTestInstance()
        .ifPresent(t -> FactCastIntegrationTestExtension.inject(t, containers.redisProxy));
  }

  private RedisConfig.Config discoverConfig(ExtensionContext ctx) {
    return ctx.getTestClass()
        .flatMap(x -> Optional.ofNullable(x.getAnnotation(RedisConfig.class)))
        .map(RedisConfig.Config::from)
        .orElse(RedisConfig.Config.defaults());
  }

  @Value
  static class Containers {
    GenericContainer redis;
    RedisProxy redisProxy;
  }
}
