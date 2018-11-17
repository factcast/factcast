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
package org.factcast.store.inmem;

import org.factcast.core.DefaultFact;
import org.factcast.core.Fact;
import org.factcast.core.util.FactCastJson;

import com.fasterxml.jackson.databind.node.ObjectNode;

class InMemFact extends DefaultFact {

    public InMemFact(long ser, Fact toCopyFrom) {
        super(addSerToHeader(ser, toCopyFrom.jsonHeader()), toCopyFrom.jsonPayload());
    }

    @SuppressWarnings("deprecation")
    public InMemFact() {
    }

    private static String addSerToHeader(long ser, String jsonHeader) {
        ObjectNode json = FactCastJson.toObjectNode(jsonHeader);
        ObjectNode meta = (ObjectNode) json.get("meta");
        if (meta == null) {
            // create a new node
            meta = FactCastJson.newObjectNode();
            json.set("meta", meta);
        }
        // set ser as attribute _ser
        meta.put("_ser", ser);
        return json.toString();
    }
}
