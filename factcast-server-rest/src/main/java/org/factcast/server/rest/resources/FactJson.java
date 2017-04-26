package org.factcast.server.rest.resources;

import javax.validation.constraints.NotNull;

import org.factcast.core.DefaultFact;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FactJson {
    @JsonProperty
    @NotNull
    DefaultFact.Header header;

    @JsonProperty
    @NotNull
    JsonNode payLoad;
}
