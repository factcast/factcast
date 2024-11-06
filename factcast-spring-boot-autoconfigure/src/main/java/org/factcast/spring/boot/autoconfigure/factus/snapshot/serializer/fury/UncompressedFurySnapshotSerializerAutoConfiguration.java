/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.spring.boot.autoconfigure.factus.snapshot.serializer.fury;

import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.serializer.fury.FurySnapshotSerializer;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;

@ConditionalOnMissingBean(SnapshotSerializer.class)
@ConditionalOnClass({SnapshotSerializer.class,FurySnapshotSerializer.class})
@ConditionalOnProperty(
    prefix = "factcast.factus.snapshot",
    value = "compress",
    havingValue = "false",
    matchIfMissing = false)
@AutoConfigureAfter({
  LZ4FurySnapshotSerializerAutoConfiguration.class,
  SnappyFurySnapshotSerializerAutoConfiguration.class
})
@AutoConfiguration
public class UncompressedFurySnapshotSerializerAutoConfiguration {
  @Bean
  public SnapshotSerializer uncompressedSnapshotSerializer() {
    return new FurySnapshotSerializer();
  }
}
