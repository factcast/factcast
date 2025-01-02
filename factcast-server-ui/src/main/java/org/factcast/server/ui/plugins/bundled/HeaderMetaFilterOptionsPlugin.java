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

import org.factcast.core.Fact;
import org.factcast.server.ui.plugins.*;

public class HeaderMetaFilterOptionsPlugin extends JsonViewPlugin {
  @Override
  protected void doHandle(Fact fact, JsonPayload payload, JsonEntryMetaData jsonEntryMetaData) {
    fact.header()
        .meta()
        // TODO: needs adaptation regarding multiple values.
        .forEachDistinctKey(
            (key, value) -> {
              if (key.startsWith("_")) {
                // not annotating _ts and _ser
                return;
              }
              final var keyPath = "$.meta." + key;
              String valueToUse = value.iterator().next(); // TODO iterate
              jsonEntryMetaData.addHeaderMetaFilterOption(keyPath, key, valueToUse);
            });
  }

  @Override
  protected boolean isReady() {
    return true;
  }
}
