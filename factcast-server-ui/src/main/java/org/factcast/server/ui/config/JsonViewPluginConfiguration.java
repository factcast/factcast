/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.server.ui.config;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.factcast.server.ui.metrics.UiMetrics;
import org.factcast.server.ui.plugins.*;
import org.factcast.server.ui.plugins.bundled.HeaderMetaFilterOptionsPlugin;
import org.factcast.server.ui.plugins.bundled.HeaderMetaTimestampToDatePlugin;
import org.factcast.server.ui.plugins.bundled.PayloadAggregateIdsFilterOptionsPlugin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonViewPluginConfiguration {
  @Bean
  @ConditionalOnMissingBean
  public JsonViewPluginObjectMapperCustomizer jsonViewPluginObjectMapperCustomizer() {
    return om -> {};
  }

  @Bean
  @ConditionalOnMissingBean
  public HeaderMetaFilterOptionsPlugin headerMetaFilterOptionsPlugin() {
    return new HeaderMetaFilterOptionsPlugin();
  }

  @Bean
  @ConditionalOnMissingBean
  public PayloadAggregateIdsFilterOptionsPlugin payloadAggregateIdsFilterOptionsPlugin() {
    return new PayloadAggregateIdsFilterOptionsPlugin();
  }

  @Bean
  @ConditionalOnMissingBean
  public HeaderMetaTimestampToDatePlugin headerMetaTimestampToDatePlugin() {
    return new HeaderMetaTimestampToDatePlugin();
  }

  @Bean
  @ConditionalOnMissingBean
  public JsonUtils jsonUtils(JsonViewPluginObjectMapperCustomizer customizer) {
    return new JsonUtils(createObjectMapper(customizer));
  }

  @Bean
  @ConditionalOnMissingBean
  public JsonViewPluginService jsonViewPluginService(
      Optional<List<JsonViewPlugin>> plugins,
      JsonViewPluginObjectMapperCustomizer customizer,
      JsonUtils jsonUtils,
      UiMetrics uiMetrics) {
    return new JsonViewPluginServiceImpl(
        plugins.orElse(List.of()), createObjectMapper(customizer), jsonUtils, uiMetrics);
  }

  private static ObjectMapper createObjectMapper(JsonViewPluginObjectMapperCustomizer customizer) {
    final var om = new ObjectMapper();

    customizer.customize(om);

    return om;
  }

  @Bean
  @ConditionalOnMissingBean
  public TokenStreamFactory jsonFactory(ObjectMapper objectMapper) {
    // TODO SB4 was: return new TokenStreamFactory(objectMapper);
    return new JsonFactory(objectMapper);
  }
}
