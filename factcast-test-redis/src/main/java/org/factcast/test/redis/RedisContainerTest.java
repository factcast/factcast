package org.factcast.test.redis;

import org.testcontainers.containers.GenericContainer;

@SuppressWarnings("rawtypes")
public interface RedisContainerTest {
  GenericContainer getRedisContainer();
}
