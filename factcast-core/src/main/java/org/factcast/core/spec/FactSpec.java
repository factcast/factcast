package org.factcast.core.spec;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.factcast.core.MarkFact;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * Defines a Specification of facts to match for a subscription.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@Getter
@Setter
public class FactSpec {

    @NonNull
    @JsonProperty
    final String ns;

    @JsonProperty
    String type = null;

    @JsonProperty
    UUID aggId = null;

    @JsonProperty
    String jsFilterScript = null;

    @NonNull
    @JsonProperty
    final Map<String, String> meta = new HashMap<>();

    public FactSpec meta(@NonNull String k, @NonNull String v) {
        meta.put(k, v);
        return this;
    }

    public static FactSpec forMark() {
        return FactSpec.ns(MarkFact.MARK_NS).type(MarkFact.MARK_TYPE);
    }

    public static FactSpec ns(String ns) {
        return new FactSpec(ns);
    }

    public FactSpec(@NonNull @JsonProperty("ns") String ns) {
        super();
        this.ns = ns;
    }
}
