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
package org.factcast.server.ui.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.server.ui.metrics.UiMetrics;

@RequiredArgsConstructor
@Slf4j
public class JsonViewPluginServiceImpl implements JsonViewPluginService {
  private final List<JsonViewPlugin> plugins;
  private final ObjectMapper objectMapper;
  private final JsonUtils jsonUtils;
  private final UiMetrics uiMetrics;

  @Override
  public JsonViewEntry process(@NonNull Fact fact) {
    return uiMetrics.timeFactProcessing(() -> processFact(fact));
  }

  @NonNull
  @SneakyThrows
  JsonViewEntry processFact(@NonNull Fact fact) {
    final JsonPayload payload = jsonUtils.forString(fact.jsonPayload());
    final JsonEntryMetaData metaData = new JsonEntryMetaData();

    plugins.forEach(
        plugin -> {
          try {
            uiMetrics.timePluginExecution(
                plugin.getDisplayName(), () -> plugin.handle(fact, payload, metaData));
          } catch (Exception e) {
            log.warn("Plugin {} failed to handle fact {}", plugin.getDisplayName(), fact.id(), e);
          }
        });

    var content = objectMapper.createObjectNode();
    content.set("header", objectMapper.readTree(fact.jsonHeader()));
    content.set("payload", payload.getPayload());

    return new JsonViewEntry(content, metaData);
  }

  @Override
  public Collection<String> getNonResponsivePlugins() {
    return plugins.stream().filter(p -> !p.isReady()).map(JsonViewPlugin::getDisplayName).toList();
  }
}
