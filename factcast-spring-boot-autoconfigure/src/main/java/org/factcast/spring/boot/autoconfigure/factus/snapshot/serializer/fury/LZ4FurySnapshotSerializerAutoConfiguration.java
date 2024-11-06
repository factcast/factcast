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

import net.jpountz.lz4.LZ4BlockInputStream;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.serializer.fury.LZ4FurySnapshotSerializer;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;

@ConditionalOnMissingBean(SnapshotSerializer.class)
@ConditionalOnClass({LZ4BlockInputStream.class, SnapshotSerializer.class})
@ConditionalOnProperty(
    prefix = "factcast.factus.snapshot",
    value = "compress",
    matchIfMissing = true)
@AutoConfigureBefore(DefaultFurySnapshotSerializerAutoConfiguration.class)
@AutoConfiguration
public class LZ4FurySnapshotSerializerAutoConfiguration {
  @Bean
  public SnapshotSerializer lz4SnapshotSerializer() {
    return new LZ4FurySnapshotSerializer();
  }
}
