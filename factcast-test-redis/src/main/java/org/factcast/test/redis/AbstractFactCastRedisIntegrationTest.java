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
package org.factcast.test.redis;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.FactCastExtension;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("rawtypes")
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith({FactCastExtension.class})
@Slf4j
public class AbstractFactCastRedisIntegrationTest extends AbstractFactCastIntegrationTest
    implements RedisContainerTest {

  @SuppressWarnings("rawtypes")
  protected static final GenericContainer _redis =
      new GenericContainer<>("redis:5.0.9-alpine").withExposedPorts(6379);

  static {
    init();
  }

  @SuppressWarnings("rawtypes")
  @SneakyThrows
  protected static void init() {
    _redis.start();
    Runtime.getRuntime().addShutdownHook(new Thread(_redis::stop));
  }

  @BeforeAll
  public static void configureRedis() throws InterruptedException {
    System.setProperty("spring.redis.host", _redis.getHost());
    System.setProperty("spring.redis.port", String.valueOf(_redis.getMappedPort(6379)));
  }

  @Override
  public GenericContainer getRedisContainer() {
    return _redis;
  }
}
