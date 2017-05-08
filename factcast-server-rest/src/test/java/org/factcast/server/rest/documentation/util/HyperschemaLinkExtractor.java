package org.factcast.server.rest.documentation.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.restdocs.hypermedia.Link;
import org.springframework.restdocs.hypermedia.LinkExtractor;
import org.springframework.restdocs.operation.OperationResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercateo.rest.hateoas.client.schema.ClientHyperSchema;

import lombok.SneakyThrows;

public class HyperschemaLinkExtractor implements LinkExtractor {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, List<Link>> extractLinks(OperationResponse response) throws IOException {
        byte[] responseContent = response.getContent();

        ClientHyperSchema hyperSchema = buildSchema(objectMapper.readTree(responseContent));
        Map<String, List<Link>> result = new HashMap<>();
        hyperSchema.getLinks().forEach(l -> {//
            result.put(l.getRel(), Arrays.asList(new Link(l.getRel(), l.getHref().createURI())));
        });
        return result;
    }

    @SneakyThrows
    private ClientHyperSchema buildSchema(JsonNode rawValue) {
        JsonNode schemaElement = rawValue.get("_schema");
        return objectMapper.treeToValue(schemaElement, ClientHyperSchema.class);

    }

}
