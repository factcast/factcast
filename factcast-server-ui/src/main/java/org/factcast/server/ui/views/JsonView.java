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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import java.util.*;
import org.factcast.server.ui.plugins.JsonViewEntries;
import org.factcast.server.ui.plugins.JsonViewEntry;
import org.factcast.server.ui.utils.NoCoverageReportToBeGenerated;

@Tag("json-view")
@JsModule("./json-view/json-view.ts")
@CssImport("./json-view/json-view.css")
// we have to stick to this version
// until https://github.com/microsoft/monaco-editor/issues/3409 is solved
@NpmPackage(value = "monaco-editor", version = "0.33.0")
@NpmPackage(value = "jsonc-parser", version = "3.2.0")
@NpmPackage(value = "jsonpath-plus", version = "7.2.0")
@NoCoverageReportToBeGenerated
public class JsonView extends Component {

  public void renderFact(JsonViewEntry f) {
    renderFacts(new JsonViewEntries(Collections.singletonList(f)));
  }

  public void renderFacts(JsonViewEntries entries) {
    getElement().callJsFunction("renderJson", entries.json(), entries.meta());
  }
}
