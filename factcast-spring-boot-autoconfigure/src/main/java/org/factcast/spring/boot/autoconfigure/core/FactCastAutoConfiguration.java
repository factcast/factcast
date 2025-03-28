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
package org.factcast.spring.boot.autoconfigure.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import org.factcast.core.FactCast;
import org.factcast.core.store.FactStore;
import org.factcast.core.util.FactCastJson;
import org.factcast.factus.event.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@AutoConfiguration
@ConditionalOnClass(FactCast.class)
@SuppressWarnings("unused")
public class FactCastAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public FactCast factCast(@NonNull FactStore store) {
    return FactCast.from(store);
  }

  @Bean
  @ConditionalOnMissingBean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public EventSerializer eventSerializer(ObjectMapper om) {
    return new DefaultEventSerializer(om);
  }

  @Bean
  @Primary
  @ConditionalOnMissingBean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  // if there is no other one defined
  public ObjectMapper objectMapper() {
    return FactCastJson.mapper();
  }
}
