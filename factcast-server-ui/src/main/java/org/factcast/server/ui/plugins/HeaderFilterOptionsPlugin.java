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
package org.factcast.server.ui.plugins;

import org.factcast.core.Fact;

public class HeaderFilterOptionsPlugin extends JsonViewPlugin {
  @Override
  protected boolean isReady() {
    return true;
  }

  @Override
  protected void doHandle(Fact fact, JsonPayload payload, JsonEntryMetaData jsonEntryMetaData) {
    fact.header()
        .meta()
        .forEach(
            (key, value) -> {
              if (key.startsWith("_")) {
                // not annotating _ts and _ser
                return;
              }
              final var keyPath = "$.meta." + key;
              jsonEntryMetaData.addHeaderMetaFilterOption(keyPath, key, value);
            });
    // todo: how to annotate aggIds properly, set has no defined order
    // but will be converted to a list with order by jackson
    // but assuming iteration order here and of json list will match is pure speculation
    // could in theory also quite safely be done in the FE, after visitor finds "header.aggIds", but
    // then
    // part of the data is no longer controlled by plugin and cannot be disabled anymore.
  }
}
