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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import lombok.NonNull;

public class JsonUtils {
  private final ParseContext pathReturningContext;
  private final ParseContext parseContext;
  private final ObjectMapper objectMapper;

  public JsonUtils(@NonNull ObjectMapper objectMapper) {
    this.parseContext =
        JsonPath.using(
            Configuration.builder()
                .jsonProvider(new JacksonJsonNodeJsonProvider(objectMapper))
                .mappingProvider(new JacksonMappingProvider(objectMapper))
                .options(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL)
                .build());

    this.pathReturningContext =
        JsonPath.using(
            Configuration.builder()
                .options(Option.AS_PATH_LIST, Option.ALWAYS_RETURN_LIST, Option.SUPPRESS_EXCEPTIONS)
                .build());

    this.objectMapper = objectMapper;
  }

  public JsonPayload forString(@NonNull String json) {
    return new JsonPayload(parseContext, pathReturningContext, json);
  }

  public ObjectNode createObjectNode() {
    return objectMapper.createObjectNode();
  }

  public ArrayNode createArrayNode() {
    return objectMapper.createArrayNode();
  }
}
