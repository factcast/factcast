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
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import java.util.*;
import java.util.stream.IntStream;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.FactHeader;
import org.factcast.core.util.FactCastJson;

@Tag("json-view")
@JsModule("./json-view/json-view.ts")
@CssImport("./json-view/json-view.css")
// we have to stick to this version
// until https://github.com/microsoft/monaco-editor/issues/3409 is solved
@NpmPackage(value = "monaco-editor", version = "0.33.0")
@NpmPackage(value = "@humanwhocodes/momoa", version = "3.0.0")
public class JsonView extends Component {

  public void renderFact(Fact f) {
    renderFact(
        f,
        new FactMetaData(
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap()));

    //		new FactMetaData(Map.of("id", List.of("123"), "meta._ts", List.of("1.1.1970 oder so")),
    //				Map.of("foo[0].bar", List.of("Hallo Uwe", "Alles klar?"), "lastName", List.of("foo")),
    // Map.of(),
    //				Map.of("lastName", List.of("This is the last name of a user.")))
  }

  public void renderFact(Fact f, FactMetaData metaData) {
    final var annotationMap = new HashMap<String, String>();
    final var hoverMap = new HashMap<String, Collection<String>>();

    metaData.headerAnnotations.forEach(
        (k, v) -> annotationMap.put("header." + k, String.join(", ", v)));
    metaData.payloadAnnotations.forEach(
        (k, v) -> annotationMap.put("payload." + k, String.join(", ", v)));

    metaData.headerHoverContent.forEach((k, v) -> hoverMap.put("header." + k, v));
    metaData.payloadHoverContent.forEach((k, v) -> hoverMap.put("payload." + k, v));

    getElement()
        .callJsFunction(
            "renderJson",
            FactCastJson.writeValueAsPrettyString(toFactJson(f)),
            FactCastJson.writeValueAsString(buildMetaDataMap(annotationMap, hoverMap)));
  }

  public void renderFacts(List<Fact> f) {
    renderFacts(f, Collections.emptyList());

    //    new FactMetaData(
    //        Map.of("id", List.of("123"), "meta._ts", List.of("1.1.1970 oder so")),
    //        Map.of("foo[0].bar", List.of("Hallo Uwe", "Alles klar?")),
    //        Map.of(),
    //        Map.of("lastName", List.of("This is the last name of a user."))))
  }

  public void renderFacts(List<Fact> f, List<FactMetaData> annotations) {
    final var annotationMap = new HashMap<String, String>();
    final var hoverMap = new HashMap<String, Collection<String>>();

    IntStream.range(0, annotations.size())
        .forEach(
            i -> {
              final var a = annotations.get(i);
              a.headerAnnotations.forEach(
                  (k, v) -> annotationMap.put("[" + i + "].header." + k, String.join(", ", v)));
              a.payloadAnnotations.forEach(
                  (k, v) -> annotationMap.put("[" + i + "].payload." + k, String.join(", ", v)));
              a.headerHoverContent.forEach((k, v) -> hoverMap.put("[" + i + "].header." + k, v));
              a.payloadHoverContent.forEach((k, v) -> hoverMap.put("[" + i + "].payload." + k, v));
            });

    getElement()
        .callJsFunction(
            "renderJson",
            FactCastJson.writeValueAsPrettyString(f.stream().map(this::toFactJson).toList()),
            FactCastJson.writeValueAsString(buildMetaDataMap(annotationMap, hoverMap)));
  }

  @NonNull
  private static Map<String, HashMap<String, ?>> buildMetaDataMap(
      HashMap<String, String> annotationMap, HashMap<String, Collection<String>> hoverMap) {
    return Map.of("annotations", annotationMap, "hoverContent", hoverMap);
  }

  private FactJson toFactJson(Fact f) {
    return new FactJson(f.header(), FactCastJson.toObjectNode(f.jsonPayload()));
  }

  public record FactJson(FactHeader header, ObjectNode payload) {}

  public record FactMetaData(
      Map<String, Collection<String>> headerAnnotations,
      Map<String, Collection<String>> payloadAnnotations,
      Map<String, Collection<String>> headerHoverContent,
      Map<String, Collection<String>> payloadHoverContent) {}
}
