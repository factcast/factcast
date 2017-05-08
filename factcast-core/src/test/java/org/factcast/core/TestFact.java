package org.factcast.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.factcast.core.util.FactCastJson;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;

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
public class TestFact implements Fact {
    @JsonProperty
    UUID id = UUID.randomUUID();

    @JsonProperty
    Set<UUID> aggIds = Sets.newLinkedHashSet();

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

    public TestFact meta(String key, String value) {
        meta.put(key, value);
        return this;
    }

    @Override
    @SneakyThrows
    public String jsonHeader() {
        return FactCastJson.writeValueAsString(this);
    }

    public TestFact aggId(@NonNull UUID aggId, UUID... otherAggIds) {
        this.aggIds.add(aggId);
        if (otherAggIds != null) {
            this.aggIds.addAll(Arrays.asList(otherAggIds));
        }
        return this;
    }
}
