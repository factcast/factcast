package org.factcast.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.factcast.core.util.FactCastJson;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Test0Fact implements Fact {

    @JsonProperty
    UUID id = UUID.randomUUID();

    @JsonProperty
    Set<UUID> aggIds = new LinkedHashSet<>();

    @JsonProperty
    String type;

    @JsonProperty
    String ns = "default";

    String jsonPayload = "{}";

    @JsonProperty
    Map<String, String> meta = new HashMap<>();

    @Override
    public String meta(String key) {
        return meta.get(key);
    }

    public Test0Fact meta(String key, String value) {
        meta.put(key, value);
        return this;
    }

    @Override
    @SneakyThrows
    public String jsonHeader() {
        return FactCastJson.writeValueAsString(this);
    }

    public Test0Fact aggId(@NonNull UUID aggId, UUID... otherAggIds) {
        this.aggIds.add(aggId);
        if (otherAggIds != null) {
            this.aggIds.addAll(Arrays.asList(otherAggIds));
        }
        return this;
    }
}
