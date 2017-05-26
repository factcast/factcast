package org.factcast.server.rest.resources;

import javax.ws.rs.WebApplicationException;

import org.factcast.core.Fact;
import org.factcast.core.util.FactCastJson;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Component
public class FactTransformer {

    private final ObjectMapper objectMapper;

    public FactJson toJson(Fact f) {
        try {
            JsonNode payLoad = objectMapper.readTree(f.jsonPayload());
            return new FactJson(FactCastJson.readValue(
                    org.factcast.server.rest.resources.FactJson.Header.class, f.jsonHeader()),
                    payLoad);
        } catch (Exception e) {
            throw new WebApplicationException(e.getMessage(), 500);
        }
    }
}
