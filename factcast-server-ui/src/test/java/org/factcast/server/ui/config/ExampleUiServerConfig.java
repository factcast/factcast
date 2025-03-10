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
package org.factcast.server.ui.config;

import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.server.ui.plugins.JsonEntryMetaData;
import org.factcast.server.ui.plugins.JsonPayload;
import org.factcast.server.ui.plugins.JsonViewPlugin;
import org.factcast.store.internal.script.JSEngineFactory;
import org.factcast.store.internal.script.graaljs.GraalJSEngineFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExampleUiServerConfig {
  @Bean
  public JSEngineFactory jsEngineFactory() {
    return new GraalJSEngineFactory();
  }

  @Bean
  public JsonViewPlugin testPlugin() {
    return new JsonViewPlugin() {
      @Override
      public void doHandle(Fact fact, JsonPayload payload, JsonEntryMetaData jsonEntryMetaData) {
        final var paths = payload.findPaths("$..firstName");

        paths.forEach(
            p -> {
              final var name = payload.read(p, String.class);

              jsonEntryMetaData.annotatePayload(p, "Name: " + name);
              jsonEntryMetaData.addPayloadHoverContent(p, "J. Edgar Hoover: " + name);
            });
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public @NonNull String getDisplayName() {
        return "TestPlugin";
      }
    };
  }

  @Bean
  public JsonViewPlugin hoverOnlyPlugin() {
    return new JsonViewPlugin() {
      @Override
      public void doHandle(Fact fact, JsonPayload payload, JsonEntryMetaData jsonEntryMetaData) {
        final var paths = payload.findPaths("$..lastName");

        paths.forEach(
            p -> {
              final var name = payload.read(p, String.class);
              jsonEntryMetaData.addPayloadHoverContent(p, "J. Edgar Hoover: " + name);
            });
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public @NonNull String getDisplayName() {
        return "HoverOnlyPlugin";
      }
    };
  }

  @Bean
  public JsonViewPlugin hoverIdsPlugin() {
    return new JsonViewPlugin() {
      @Override
      public void doHandle(Fact fact, JsonPayload payload, JsonEntryMetaData jsonEntryMetaData) {
        final var idPaths =
            payload.findPaths("$..*").stream()
                .filter(p -> p.toLowerCase().endsWith("id']"))
                .toList();

        idPaths.forEach(
            p -> jsonEntryMetaData.addPayloadHoverContent(p, "This is most likely an ID."));
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public @NonNull String getDisplayName() {
        return "HoverIdsPlugin";
      }
    };
  }
}
