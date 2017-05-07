package org.factcast.example.grpc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.factcast.core.Fact;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
class SmokeTestFact implements Fact {
    @JsonProperty
    UUID id = UUID.randomUUID();

    @JsonProperty
    Set<UUID> aggId;

    @JsonProperty
    String type;

    @JsonProperty
    String ns = "smoke";

    String jsonPayload = "{}";

    @JsonProperty
    Map<String, String> meta = new HashMap<>();

    @Override
    public String meta(String key) {
        return meta.get(key);
    }

    public SmokeTestFact meta(String key, String value) {
        meta.put(key, value);
        return this;
    }

    @Override
    @SneakyThrows
    public String jsonHeader() {
        return new ObjectMapper().writeValueAsString(this);
    }

    public SmokeTestFact aggId(@NonNull UUID aggId, UUID... otherAggIds) {
        this.aggId.add(aggId);
        if (otherAggIds != null) {
            this.aggId.addAll(Arrays.asList(otherAggIds));
        }
        return this;
    }
}
