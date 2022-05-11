/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.registry.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import com.networknt.schema.SpecVersion.VersionFlag;
import lombok.NonNull;
import okhttp3.OkHttpClient;

public class ValidationConstants {

  public static final ObjectMapper JACKSON = new ObjectMapper();

  public static final OkHttpClient OK_HTTP = new OkHttpClient();

  public static final String HTTPHEADER_IF_MODIFIED_SINCE = "If-Modified-Since";

  public static final String HTTPHEADER_E_TAG = "ETag";

  public static final String HTTPHEADER_LAST_MODIFIED = "Last-Modified";

  public static final int HTTP_OK = 200;

  public static final int HTTP_NOT_MODIFIED = 304;

  public static JsonSchema fromJsonNode(@NonNull JsonNode jsonNode) {
    VersionFlag versionFlag;
    try {
      versionFlag = SpecVersionDetector.detect(jsonNode);
    } catch (JsonSchemaException e) {
      // TODO make configurable?
      versionFlag = VersionFlag.V7;
    }
    JsonSchemaFactory factory = JsonSchemaFactory.getInstance(versionFlag);
    SchemaValidatorsConfig cfg = new SchemaValidatorsConfig();
    cfg.setEcma262Validator(true);
    return factory.getSchema(jsonNode, cfg);
  }
}
