package org.factcast.test.redis;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.factcast.test.FactCastIntegrationTestExtension;
import org.junit.jupiter.api.extension.*;
import org.redisson.Redisson;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

@SuppressWarnings("rawtypes")
@Slf4j
public class RedisIntegrationTestExtension implements FactCastIntegrationTestExtension {

  private GenericContainer _redis;
  private Config config;
  private String url;

  @Override
  public boolean initialize(Map<String, GenericContainer<?>> containers) {

    if (!containers.containsKey("factcast")) {
      return false;
    }

    _redis = new GenericContainer<>("redis:5.0.9-alpine").withExposedPorts(6379);
    _redis.start();
    Runtime.getRuntime().addShutdownHook(new Thread(_redis::stop));
    System.setProperty("spring.redis.host", _redis.getHost());
    System.setProperty("spring.redis.port", String.valueOf(_redis.getMappedPort(6379)));
    url = "redis://" + _redis.getHost() + ":" + _redis.getMappedPort(6379);
    config = new Config().setThreads(1);
    config.useSingleServer().setAddress(url);
    return true;
  }

  @Override
  public void beforeEach(ExtensionContext ctx) {
    log.trace("erasing redis state in between tests for {}", url);
    val client = Redisson.create(config);
    client.getKeys().flushdb();
    client.shutdown();
  }
}
