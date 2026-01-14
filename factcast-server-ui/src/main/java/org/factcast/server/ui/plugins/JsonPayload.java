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

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.ParseContext;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@RequiredArgsConstructor
public class JsonPayload {
  private final ParseContext parseContext;
  private final ParseContext pathReturningContext;
  private final String json;
  private DocumentContext documentContext;
  private DocumentContext pathReturningDocumentContext;

  public List<String> findPaths(@NonNull String path) {
    return pathReturningDocumentContext().read(path);
  }

  public List<String> findAnyPaths() {
    return findPaths("$..*");
  }

  public JsonNode get(@NonNull String path) {
    return documentContext().read(path);
  }

  public <T> T read(@NonNull String path, Class<T> clazz) {
    return documentContext().read(path, clazz);
  }

  public void set(@NonNull String path, Object value) {
    documentContext().set(path, value);
    resetPathReturningContext();
  }

  public void add(@NonNull String path, Object value) {
    documentContext().add(path, value);
    resetPathReturningContext();
  }

  public void remove(@NonNull String path) {
    documentContext().delete(path);
    resetPathReturningContext();
  }

  private synchronized DocumentContext documentContext() {
    if (documentContext == null) {
      documentContext = parseContext.parse(json);
    }
    return documentContext;
  }

  private synchronized DocumentContext pathReturningDocumentContext() {
    if (pathReturningDocumentContext == null) {
      pathReturningDocumentContext =
          pathReturningContext.parse(documentContext().json().toString());
    }
    return pathReturningDocumentContext;
  }

  /**
   * Resets the path returning context to force a parse of the json content. This is necessary
   * because the underlying json might change its structure (remove keys, new keys) and plugins
   * further down the road might access non exisiting paths.
   */
  private void resetPathReturningContext() {
    pathReturningDocumentContext = null;
  }

  public JsonNode getPayload() {
    return documentContext().json();
  }
}
