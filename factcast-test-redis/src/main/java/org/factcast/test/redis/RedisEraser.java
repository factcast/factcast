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

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.factcast.test.BetweenTestsEraser;
import org.redisson.Redisson;
import org.redisson.config.Config;

@Slf4j
public class RedisEraser implements BetweenTestsEraser {

  @Override
  public void wipe(Object testInstance) {

    if (testInstance instanceof RedisContainerTest) {

      val redis = ((RedisContainerTest) testInstance).getRedisContainer();

      String url = "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
      log.trace("erasing redis state in between tests for {}", url);

      val config = new Config();
      config.useSingleServer().setAddress(url);
      val client = Redisson.create(config);

      client.getKeys().flushall();
      client.shutdown();

    } else {
      log.trace("Test does not implement RedisContainerTest - skipping");
    }
  }
}
