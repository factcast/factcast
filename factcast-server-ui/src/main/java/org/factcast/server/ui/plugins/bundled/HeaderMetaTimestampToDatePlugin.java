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
package org.factcast.server.ui.plugins.bundled;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.factcast.core.Fact;
import org.factcast.server.ui.plugins.JsonEntryMetaData;
import org.factcast.server.ui.plugins.JsonPayload;
import org.factcast.server.ui.plugins.JsonViewPlugin;

public class HeaderMetaTimestampToDatePlugin extends JsonViewPlugin {
  @Override
  public void doHandle(Fact fact, JsonPayload payload, JsonEntryMetaData jsonEntryMetaData) {
    Optional.ofNullable(fact.header().timestamp())
        .ifPresent(
            ts ->
                jsonEntryMetaData.annotateHeader(
                    "$.meta._ts",
                    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toString()));
  }

  @Override
  public boolean isReady() {
    return true;
  }
}
