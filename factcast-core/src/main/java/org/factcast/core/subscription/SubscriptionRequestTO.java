package org.factcast.core.subscription;

import static com.google.common.base.Preconditions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.factcast.core.util.FactCastJson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * Implementation of {@link SubscriptionRequest}, that is supposed to be used
 * when transfered on the wire to a remote store (for instance via GRPC or REST)
 * 
 * Note that FactSpec.forMark() is silently added to the list of specifications.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
@NoArgsConstructor
@JsonIgnoreProperties
public class SubscriptionRequestTO implements SubscriptionRequest {

    @JsonProperty
    long maxBatchDelayInMs = 0;

    @JsonProperty
    boolean continous;

    @JsonProperty
    boolean ephemeral;

    @JsonProperty
    boolean idOnly = false;

    @JsonProperty
    UUID startingAfter;

    @JsonProperty
    final List<FactSpec> specs = new LinkedList<>(Arrays.asList(FactSpec.forMark()));

    public boolean hasAnyScriptFilters() {
        return specs.stream().anyMatch(s -> s.jsFilterScript() != null);
    }

    public java.util.Optional<UUID> startingAfter() {
        return java.util.Optional.ofNullable(startingAfter);
    }

    // copy constr. from a SR
    public SubscriptionRequestTO(SubscriptionRequest request) {
        maxBatchDelayInMs = request.maxBatchDelayInMs();
        continous = request.continous();
        ephemeral = request.ephemeral();
        startingAfter = request.startingAfter().orElse(null);
        specs.addAll(request.specs());
    }

    public static SubscriptionRequestTO forFacts(SubscriptionRequest request) {
        SubscriptionRequestTO t = new SubscriptionRequestTO(request);
        t.idOnly(false);
        return t;
    }

    public static SubscriptionRequestTO forIds(SubscriptionRequest request) {
        SubscriptionRequestTO t = new SubscriptionRequestTO(request);
        t.idOnly(true);
        return t;
    }

    public void addSpecs(@NonNull List<FactSpec> factSpecs) {
        checkArgument(!factSpecs.isEmpty());
        specs.addAll(factSpecs);
    }

    public SubscriptionRequestTO copy() {
        return FactCastJson.copy(this);
    }

    @Override
    public List<FactSpec> specs() {
        return Collections.unmodifiableList(specs);
    }
}
