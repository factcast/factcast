/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.core.subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.factcast.core.spec.FactSpec;
import org.factcast.core.util.FactCastJson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Implementation of {@link SubscriptionRequest}, that is supposed to be used
 * when transfered on the wire to a remote store (for instance via GRPC or REST)
 *
 * Note that FactSpec.forMark() is silently added to the list of specifications,
 * if marks is true.
 *
 * @author uwe.schaefer@mercateo.com
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@JsonIgnoreProperties
public class SubscriptionRequestTO implements SubscriptionRequest {

    @JsonProperty
    long maxBatchDelayInMs = 0;

    @JsonProperty
    boolean marks;

    @JsonProperty
    boolean continuous;

    @JsonProperty
    boolean ephemeral;

    @JsonProperty
    boolean idOnly = false;

    @JsonProperty
    UUID startingAfter;

    @JsonProperty
    String debugInfo;

    @JsonProperty
    final List<FactSpec> specs = new LinkedList<>();

    public SubscriptionRequestTO() {
    }

    public boolean hasAnyScriptFilters() {
        return specs.stream().anyMatch(s -> s.jsFilterScript() != null);
    }

    public java.util.Optional<UUID> startingAfter() {
        return java.util.Optional.ofNullable(startingAfter);
    }

    // copy constr. from a SR
    public SubscriptionRequestTO(SubscriptionRequest request) {
        maxBatchDelayInMs = request.maxBatchDelayInMs();
        continuous = request.continuous();
        ephemeral = request.ephemeral();
        startingAfter = request.startingAfter().orElse(null);
        debugInfo = request.debugInfo();
        specs.addAll(request.specs());
        marks = request.marks();
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

    private void checkArgument(boolean b) {
        if (!b)
            throw new IllegalArgumentException();
    }

    @Override
    public List<FactSpec> specs() {
        ArrayList<FactSpec> l = new ArrayList<>(specs);
        if (marks) {
            l.add(0, FactSpec.forMark());
        }
        return Collections.unmodifiableList(l);
    }

    public String dump() {
        return FactCastJson.writeValueAsString(this);
    }

    @Override
    public String toString() {
        return debugInfo;
    }
}
