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
package org.factcast.server.ui.views;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import java.util.*;
import java.util.stream.IntStream;
import org.factcast.core.util.FactCastJson;
import org.factcast.server.ui.plugins.JsonViewEntry;

@Tag("json-view")
@JsModule("./json-view/json-view.ts")
@CssImport("./json-view/json-view.css")
// we have to stick to this version
// until https://github.com/microsoft/monaco-editor/issues/3409 is solved
@NpmPackage(value = "monaco-editor", version = "0.33.0")
@NpmPackage(value = "jsonc-parser", version = "3.2.0")
@NpmPackage(value = "jsonpath-plus", version = "7.2.0")
public class JsonView extends Component {

  public void renderFact(JsonViewEntry f) {
    getElement()
        .callJsFunction(
            "renderJson",
            FactCastJson.writeValueAsPrettyString(f.fact()),
            FactCastJson.writeValueAsString(f.metaData()));
  }

  public void renderFacts(List<JsonViewEntry> f) {
    getElement()
        .callJsFunction(
            "renderJson",
            FactCastJson.writeValueAsPrettyString(f.stream().map(JsonViewEntry::fact).toList()),
            FactCastJson.writeValueAsString(new JsonViewEntriesMetaData(f)));
  }

  static class JsonViewEntriesMetaData {
    @JsonProperty private final Map<String, Collection<String>> annotations = new HashMap<>();

    @JsonProperty private final Map<String, Collection<String>> hoverContent = new HashMap<>();

    public JsonViewEntriesMetaData(List<JsonViewEntry> entries) {
      IntStream.range(0, entries.size())
          .forEach(
              i -> {
                final var a = entries.get(i);
                a.metaData()
                    .annotations()
                    .forEach((k, v) -> annotations.put("$.[" + i + "]." + k, v));
                a.metaData()
                    .hoverContent()
                    .forEach((k, v) -> hoverContent.put("$.[" + i + "]." + k, v));
              });
    }
  }
}
