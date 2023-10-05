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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.factcast.core.Fact;
import org.factcast.core.FactHeader;
import org.factcast.core.util.FactCastJson;

@Tag("json-view")
@JsModule("./json-view/json-view.ts")
@CssImport("./json-view/json-view.css")
@NpmPackage(value = "monaco-editor", version = "0.43.0")
@NpmPackage(value = "@humanwhocodes/momoa", version = "3.0.0")
public class JsonView extends Component {

  public void renderFact(Fact f) {
    renderFact(
        f,
        new Annotations(
            Map.of("id", "123", "meta._ts", "1.1.1970 oder so"),
            Map.of("foo[0].bar", "Hallo Uwe")));
  }

  public void renderFact(Fact f, Annotations annotations) {
    final var finalMap = new HashMap<String, String>();

    annotations.headerAnnotations.forEach((k, v) -> finalMap.put("header." + k, v));
    annotations.payloadAnnotations.forEach((k, v) -> finalMap.put("payload." + k, v));

    getElement()
        .callJsFunction(
            "renderJson",
            FactCastJson.writeValueAsPrettyString(toFactJson(f)),
            FactCastJson.writeValueAsString(finalMap));
  }

  public void renderFacts(List<Fact> f) {
    renderFacts(
        f,
        List.of(
            new Annotations(
                Map.of("id", "123", "meta._ts", "1.1.1970 oder so"),
                Map.of("foo[0].bar", "Hallo Uwe"))));
  }

  public void renderFacts(List<Fact> f, List<Annotations> annotations) {
    final var finalMap = new HashMap<String, String>();

    IntStream.range(0, annotations.size())
        .forEach(
            i -> {
              final var a = annotations.get(i);
              a.headerAnnotations.forEach((k, v) -> finalMap.put("[" + i + "].header." + k, v));
              a.payloadAnnotations.forEach((k, v) -> finalMap.put("[" + i + "].payload." + k, v));
            });

    getElement()
        .callJsFunction(
            "renderJson",
            FactCastJson.writeValueAsPrettyString(f.stream().map(this::toFactJson).toList()),
            FactCastJson.writeValueAsString(finalMap));
  }

  private FactJson toFactJson(Fact f) {
    return new FactJson(f.header(), FactCastJson.toObjectNode(f.jsonPayload()));
  }

  public record FactJson(FactHeader header, ObjectNode payload) {}

  public record Annotations(
      Map<String, String> headerAnnotations, Map<String, String> payloadAnnotations) {}
}
