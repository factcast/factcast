package org.factcast.server.rest.resources;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Defines a Specification of facts to match for a subscription.
 * 
 * @author usr
 *
 */
@Data
public class FactSpecParam {
    @JsonProperty
    private String ns;

    @JsonProperty
    private String type = null;

    @JsonProperty
    private UUID aggId = null;

    @JsonProperty
    private final Map<String, String> meta = new HashMap<>();
}
