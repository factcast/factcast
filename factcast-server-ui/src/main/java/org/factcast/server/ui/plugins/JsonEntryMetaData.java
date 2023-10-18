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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;

/** meta-data for one fact (json entry) */
@Getter(AccessLevel.PROTECTED)
public class JsonEntryMetaData {
  private final Map<String, Collection<String>> annotations = new HashMap<>();

  private final Map<String, Collection<String>> hoverContent = new HashMap<>();

  public void annotateHeader(@NonNull String path, @NonNull String value) {
    var p = "header." + path;
    var l = annotations.getOrDefault(p, new ArrayList<>());
    l.add(value);
    annotations.put(p, l);
  }

  public void annotatePayload(@NonNull String path, @NonNull String value) {
    var p = "payload." + path;
    var l = annotations.getOrDefault(p, new ArrayList<>());
    l.add(value);
    annotations.put(p, l);
  }

  public void addHeaderHoverContent(@NonNull String path, @NonNull String value) {
    var p = "header." + path;
    var l = hoverContent.getOrDefault(p, new ArrayList<>());
    l.add(value);
    hoverContent.put(p, l);
  }

  public void addPayloadHoverContent(@NonNull String path, @NonNull String value) {
    var p = "payload." + path;
    var l = hoverContent.getOrDefault(p, new ArrayList<>());
    l.add(value);
    hoverContent.put(p, l);
  }
}
