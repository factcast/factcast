package org.factcast.test.redis;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

import org.factcast.test.FactCastIntegrationTestExtension;
import org.junit.jupiter.api.extension.*;
import org.redisson.Redisson;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

@SuppressWarnings("rawtypes")
@Slf4j
public class RedisIntegrationTestExtension implements FactCastIntegrationTestExtension {

  private final Map<RedisConfig.Config, GenericContainer> executions = new ConcurrentHashMap<>();

  @Override
  public boolean initialize(ExtensionContext ctx) {
    final RedisConfig.Config config = discoverConfig(ctx);

    startOrReuse(config);

    return true;
  }

  private void startOrReuse(RedisConfig.Config config) {
    final GenericContainer container =
        executions.computeIfAbsent(
            config,
            key -> {
              GenericContainer redis =
                  new GenericContainer<>("redis:" + config.redisVersion()).withExposedPorts(6379);
              redis.start();

              return redis;
            });

    System.setProperty("spring.redis.host", container.getHost());
    System.setProperty("spring.redis.port", String.valueOf(container.getMappedPort(6379)));
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
    final GenericContainer container = executions.get(config);

    final var url = "redis://" + container.getHost() + ":" + container.getMappedPort(6379);
    log.trace("erasing redis state in between tests for {}", url);

    final var clientConfig = new Config().setThreads(1);
    clientConfig.useSingleServer().setAddress(url);

    final var client = Redisson.create(clientConfig);
    client.getKeys().flushdb();
    client.shutdown();
  }

  private RedisConfig.Config discoverConfig(ExtensionContext ctx) {
    return ctx.getTestClass()
        .flatMap(x -> Optional.ofNullable(x.getAnnotation(RedisConfig.class)))
        .map(RedisConfig.Config::from)
        .orElse(RedisConfig.Config.defaults());
  }
}
