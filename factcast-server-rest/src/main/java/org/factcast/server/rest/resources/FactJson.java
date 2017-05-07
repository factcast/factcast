package org.factcast.server.rest.resources;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.mercateo.common.rest.schemagen.IgnoreInRestSchema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Value;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FactJson {
    @Value
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {

        @JsonProperty
        @NonNull
        @NotNull
        final UUID id;

        @JsonProperty
        @NonNull
        @NotNull
        final String ns;

        @JsonProperty
        final String type;

        @JsonProperty
        final Set<UUID> aggId;

        @JsonProperty
        final Map<String, String> meta = new HashMap<>();

        @JsonAnySetter
        @IgnoreInRestSchema
        final Map<String, Object> anyOther = new HashMap<>();

        @JsonAnyGetter
        public Map<String, Object> anyOther() {
            return anyOther;
        }
    }

    @JsonProperty
    @NotNull
    @NonNull
    @Valid
    Header header;

    @JsonProperty
    @NotNull
    @NonNull
    JsonNode payload;
}
