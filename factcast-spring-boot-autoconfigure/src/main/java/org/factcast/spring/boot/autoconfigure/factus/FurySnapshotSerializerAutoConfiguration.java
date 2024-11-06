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
package org.factcast.spring.boot.autoconfigure.factus;

import lombok.extern.slf4j.Slf4j;
import net.jpountz.lz4.LZ4BlockInputStream;
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorInputStream;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.serializer.fury.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(FurySnapshotSerializer.class)
@ConditionalOnMissingBean(SnapshotSerializer.class)
@Slf4j
@AutoConfigureOrder(-100)
public class FurySnapshotSerializerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(SnapshotSerializer.class)
  public SnapshotSerializer defaultUncompressedSnapshotSerializer() {
    return new FurySnapshotSerializer();
  }

  @ConditionalOnMissingBean(SnapshotSerializer.class)
  @AutoConfigureOrder(-100)
  @Bean
  @ConditionalOnProperty(
      prefix = "factcast.factus.snapshot",
      value = "compress",
      havingValue = "false")
  public SnapshotSerializer explicitlyUncompressedSnapshotSerializer() {
    return new FurySnapshotSerializer();
  }

  @AutoConfigureOrder(-110) // prefer over snappy
  @Bean
  @ConditionalOnMissingBean(SnapshotSerializer.class)
  @ConditionalOnClass(LZ4BlockInputStream.class)
  @ConditionalOnProperty(
      prefix = "factcast.factus.snapshot",
      value = "compress",
      matchIfMissing = true)
  public SnapshotSerializer lz4SnapshotSerializer() {
    return new LZ4FurySnapshotSerializer();
  }

  @AutoConfigureOrder(-105)
  @Bean
  @ConditionalOnMissingBean(SnapshotSerializer.class)
  @ConditionalOnClass(FramedSnappyCompressorInputStream.class)
  @ConditionalOnProperty(
      prefix = "factcast.factus.snapshot",
      value = "compress",
      matchIfMissing = true)
  public SnapshotSerializer snappySnapshotSerializer() {
    return new SnappyFurySnapshotSerializer();
  }
}
