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
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.serializer.binary.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(CompressedBinaryJacksonSnapshotSerializer.class)
@Slf4j
@AutoConfigureOrder(-100)
public class BinaryJacksonSnapshotSerializerAutoConfiguration {
  @Bean
  @ConditionalOnClass(LZ4BlockOutputStream.class)
  @ConditionalOnMissingBean(SnapshotSerializer.class)
  @ConditionalOnProperty(
      prefix = "factcast.factus.snapshot",
      value = "compress",
      matchIfMissing = true)
  public SnapshotSerializer lz4SnapshotSerializer(
      BinaryJacksonSnapshotSerializerCustomizer customizer) {
    return new CompressedBinaryJacksonSnapshotSerializer(customizer);
  }

  @Bean
  @ConditionalOnMissingClass(LZ4BlockOutputStream.class)
  @ConditionalOnMissingBean(SnapshotSerializer.class)
  @ConditionalOnProperty(
      prefix = "factcast.factus.snapshot",
      value = "compress",
      matchIfMissing = true)
  public SnapshotSerializer lz4SnapshotSerializer(
      BinaryJacksonSnapshotSerializerCustomizer customizer) {
    return new CompressedBinaryJacksonSnapshotSerializer(customizer);
  }

  @Bean
  @ConditionalOnMissingBean(SnapshotSerializer.class)
  @ConditionalOnProperty(
      prefix = "factcast.factus.snapshot",
      value = "compress",
      havingValue = "false")
  public SnapshotSerializer uncompressedSnapshotSerializer(
      BinaryJacksonSnapshotSerializerCustomizer customizer) {
    return new UncompressedBinaryJacksonSnapshotSerializer(customizer);
  }

  @Bean
  @ConditionalOnMissingBean
  public BinaryJacksonSnapshotSerializerCustomizer binarySnapshotSerializerCustomizer() {
    return BinaryJacksonSnapshotSerializerCustomizer.defaultCustomizer();
  }
}
