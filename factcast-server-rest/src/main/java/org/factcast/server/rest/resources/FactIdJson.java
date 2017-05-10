package org.factcast.server.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.NonNull;
import lombok.Value;

@Value
public class FactIdJson {

    @NonNull
    @JsonProperty
    private String id;
}
