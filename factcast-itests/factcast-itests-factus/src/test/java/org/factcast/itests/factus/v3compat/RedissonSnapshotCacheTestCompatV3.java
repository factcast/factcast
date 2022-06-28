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
package org.factcast.itests.factus.v3compat;

import org.factcast.core.snap.SnapshotCache;
import org.factcast.itests.factus.RedissonSnapshotCacheTest;
import org.factcast.test.FactcastTestConfig;
import org.springframework.beans.factory.annotation.Autowired;

@FactcastTestConfig(factcastVersion = "0.3.9")
public class RedissonSnapshotCacheTestCompatV3 extends RedissonSnapshotCacheTest {

  @Autowired
  public RedissonSnapshotCacheTestCompatV3(SnapshotCache repository) {
    super(repository);
  }
}
