package org.factcast.server.rest.resources;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * Defines a Specification of facts to match for a subscription.
 */
@Getter
public class FactSpecParam {
    @Setter
    @NonNull
    @JsonProperty
    private String ns;

    @Setter
    @JsonProperty
    private String type = null;

    @Setter
    @JsonProperty
    private UUID aggId = null;

    @JsonProperty
    private final Map<String, String> meta = new HashMap<>();

    // TODO JAR: where is the jsFilterScript !?
}
