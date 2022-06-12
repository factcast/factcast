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
package org.factcast.itests.factus;

import org.factcast.core.snap.SnapshotCache;
import org.factcast.spring.boot.autoconfigure.core.RedissonSnapshotCacheAutoConfiguration;
import org.junit.jupiter.api.*;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@EnableAutoConfiguration(
    exclude = {
      RedissonSnapshotCacheAutoConfiguration.class,
      RedissonAutoConfiguration.class,
      RedisAutoConfiguration.class,
      DataSourceAutoConfiguration.class
    })
@ContextConfiguration(classes = Application.class)
@Tag("integration")
public class FactCastSnapshotCacheTest extends SnapshotCacheTest {
  @Autowired
  public FactCastSnapshotCacheTest(SnapshotCache repository) {
    super(repository);
  }
}
