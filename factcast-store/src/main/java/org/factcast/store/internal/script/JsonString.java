/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal.script;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import lombok.*;
import org.factcast.core.util.FactCastJson;

/** is supposed to wrap a json string, just for clarity */
@Value(staticConstructor = "of")
public class JsonString {
  @NonNull String json;

  public static JsonString from(Map<String, Object> data) {
    return new JsonString(FactCastJson.toJsonNode(data).toString());
  }

  @Override
  @NonNull
  public String toString() {
    return json;
  }

  @SneakyThrows
  @NonNull
  public JsonNode toJsonNode() {
    return FactCastJson.readTree(json);
  }
}
