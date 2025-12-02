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
package org.factcast.itests.factus.client;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.factcast.ExampleSnapshotProjection;
import org.factcast.factus.Factus;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.proj.UserV1;
import org.factcast.spring.boot.autoconfigure.snap.RedissonSnapshotCacheAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.Base64;

import static java.util.UUID.randomUUID;

@Slf4j
@SpringBootTest
@ContextConfiguration(
    classes = {TestFactusApplication.class, RedissonSnapshotCacheAutoConfiguration.class})
public class RedissonSnapshotCachePerformanceTest extends SnapshotCachePerformanceTest {
  @Autowired
  public RedissonSnapshotCachePerformanceTest(SnapshotCache repository) {
    super(repository);
  }
}
