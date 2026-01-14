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

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import java.util.*;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.util.NoCoverageReportToBeGenerated;
import org.factcast.server.ui.plugins.JsonViewEntries;
import org.factcast.server.ui.plugins.JsonViewEntry;
import tools.jackson.databind.ObjectMapper;

@Tag("json-view")
@JsModule("./json-view/json-view.ts")
@CssImport("./json-view/json-view.css")
// we have to stick to this version
// until https://github.com/microsoft/monaco-editor/issues/3409 is solved
@NpmPackage(value = "monaco-editor", version = "0.33.0")
@NpmPackage(value = "jsonc-parser", version = "3.2.0")
@NpmPackage(value = "jsonpath-plus", version = "7.2.0")
@NoCoverageReportToBeGenerated
@SuppressWarnings("java:S1948")
@RequiredArgsConstructor
@Slf4j
public class JsonView extends Component {

  private final Consumer<QuickFilterOptions> filterApplier;
  private final ObjectMapper om = new ObjectMapper();

  public JsonView() {
    this(null);
  }

  public void renderFact(JsonViewEntry f, int conditionCount) {
    renderFacts(new JsonViewEntries(Collections.singletonList(f)), conditionCount);
  }

  public void renderFacts(JsonViewEntries entries, int conditionCount) {
    var enableQuickFiltering = filterApplier != null && conditionCount > 0;
    getElement()
        .callJsFunction(
            "renderJson",
            entries.json(),
            entries.meta(),
            enableQuickFiltering, // enable quick filters,
            conditionCount); // number of filter conditions, needed for quick filtering correct
    // condition
  }

  public void clear() {
    getElement().callJsFunction("clear");
  }

  @ClientCallable
  @SneakyThrows
  public void updateFilters(String filterOptions) {
    log.info("Updating filters with {}", filterOptions);
    filterApplier.accept(om.readValue(filterOptions, QuickFilterOptions.class));
  }

  public record QuickFilterOptions(UUID aggregateId, MetaFilterOption meta, int affectedCriteria) {}

  public record MetaFilterOption(String key, String value) {}
}
