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
package org.factcast.spring.boot.autoconfigure.factus.snapshot.serializer.binary;

import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.serializer.binary.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(BinaryJacksonSnapshotSerializer.class)
@ConditionalOnProperty(
    prefix = "factcast.factus.snapshot",
    value = "compress",
    // PLEASE BE AWARE THAT THIS CONFIG WOULD KILL BACKWARDS COMPATIBILITY
    havingValue = "false")
@Slf4j
@AutoConfigureOrder(-100)
public class UncompressedBinaryJacksonSnapshotSerializerAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(SnapshotSerializer.class)
  public SnapshotSerializer snapshotSerializer(
      BinaryJacksonSnapshotSerializerCustomizer customizer) {
    return new UncompressedBinaryJacksonSnapshotSerializer(customizer);
  }

  @Bean
  @ConditionalOnMissingBean
  public BinaryJacksonSnapshotSerializerCustomizer binarySnapshotSerializerCustomizer() {
    return BinaryJacksonSnapshotSerializerCustomizer.defaultCustomizer();
  }
}
