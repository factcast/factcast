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
package org.factcast.spring.boot.autoconfigure.highlevel;

import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.serializer.binary.BinarySnapshotSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnClass(BinarySnapshotSerializer.class)
@Slf4j
public class BinarySnapshotSerializerAutoConfiguration {
  @Bean
  @Order(100)
  @ConditionalOnMissingBean(SnapshotSerializer.class)
  public SnapshotSerializer binarySnapshotSerializer() {
    return new BinarySnapshotSerializer();
  }
}
