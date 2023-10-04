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
import com.hilerio.ace.AceEditor;
import com.hilerio.ace.AceMode;
import com.hilerio.ace.AceTheme;
import java.util.Collection;
import org.factcast.core.Fact;
import org.factcast.core.FactHeader;
import org.factcast.core.util.FactCastJson;

public class JsonView extends AceEditor {
  public JsonView() {
    super();

    setTheme(AceTheme.github);
    setMode(AceMode.json);
    setReadOnly(true);
    setShowGutter(true);
    setDisplayIndentGuides(true);
  }

  public void setFact(Fact f) {
    setValue(FactCastJson.writeValueAsPrettyString(this.toFactJson(f)));
  }

  public void setFacts(Collection<Fact> f) {
    setValue(FactCastJson.writeValueAsPrettyString(f.stream().map(this::toFactJson).toList()));
  }

  private FactJson toFactJson(Fact f) {
    return new FactJson(f.header(), FactCastJson.toObjectNode(f.jsonPayload()));
  }

  record FactJson(FactHeader header, ObjectNode payload) {}
}
