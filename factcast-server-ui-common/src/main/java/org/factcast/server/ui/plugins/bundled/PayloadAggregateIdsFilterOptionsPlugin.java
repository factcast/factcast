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
package org.factcast.server.ui.plugins.bundled;

import java.util.UUID;
import org.factcast.core.Fact;
import org.factcast.server.ui.plugins.JsonEntryMetaData;
import org.factcast.server.ui.plugins.JsonPayload;
import org.factcast.server.ui.plugins.JsonViewPlugin;

public class PayloadAggregateIdsFilterOptionsPlugin extends JsonViewPlugin {

  @Override
  protected void doHandle(Fact fact, JsonPayload payload, JsonEntryMetaData jsonEntryMetaData) {
    final var idPaths =
        payload.findPaths("$..*").stream().filter(p -> p.toLowerCase().endsWith("id']")).toList();

    idPaths.forEach(
        p -> {
          try {
            final var uuid = payload.read(p, UUID.class);
            jsonEntryMetaData.addPayloadAggregateIdFilterOption(p, uuid);
          } catch (Exception e) {
            // maybe not a uuid, silently ignore
          }
        });
  }

  @Override
  protected boolean isReady() {
    return true;
  }
}
