/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.client.grpc.cli.util;

import org.factcast.client.grpc.cli.util.Parser.Options;
import org.factcast.core.Fact;
import org.factcast.core.util.FactCastJson;

import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FactRenderer {

    final Options options;

    private static final String TAB = "\t";

    private static final String CR = "\n";

    public String render(Fact f) {
        return "Fact: id=" + f.id() + CR + TAB + "header: " + renderJson(f.jsonHeader()).replaceAll(
                CR, CR + TAB + TAB) + CR + TAB + "payload: " + renderJson(f.jsonPayload())
                        .replaceAll(CR, CR + TAB + TAB) + CR + CR;
    }

    private String renderJson(String jsonString) {
        if (options.pretty()) {
            ObjectNode objectNode = FactCastJson.toObjectNode(jsonString);
            return FactCastJson.writeValueAsPrettyString(objectNode);
        } else
            return jsonString;
    }
}
