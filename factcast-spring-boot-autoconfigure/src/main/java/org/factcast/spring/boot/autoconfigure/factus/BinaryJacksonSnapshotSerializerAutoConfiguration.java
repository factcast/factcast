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
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.serializer.binary.BinaryJacksonSnapshotSerializer;
import org.factcast.factus.serializer.binary.BinaryJacksonSnapshotSerializerCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnClass(BinaryJacksonSnapshotSerializer.class)
@Slf4j
@AutoConfigureOrder(-100)
public class BinaryJacksonSnapshotSerializerAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(SnapshotSerializer.class)
  public SnapshotSerializer snapshotSerializer(
      BinaryJacksonSnapshotSerializerCustomizer customizer) {
    return new BinaryJacksonSnapshotSerializer(customizer);
  }

  @Bean
  @ConditionalOnMissingBean
  public BinaryJacksonSnapshotSerializerCustomizer binarySnapshotSerializerCustomizer() {
    return BinaryJacksonSnapshotSerializerCustomizer.defaultCustomizer();
  }
}
