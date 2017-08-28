package org.factcast.store.inmem;

import org.factcast.core.DefaultFact;
import org.factcast.core.Fact;
import org.factcast.core.util.FactCastJson;

import com.fasterxml.jackson.databind.node.ObjectNode;

class InMemFact extends DefaultFact {

    public InMemFact(long ser, Fact toCopyFrom) {
        super(addSerToHeader(ser, toCopyFrom.jsonHeader()), toCopyFrom.jsonPayload());
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
