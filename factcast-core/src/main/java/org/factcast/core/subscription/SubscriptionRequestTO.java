/*
 * Copyright © 2017-2020 factcast.org
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.management.ManagementFactory;
import java.util.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.util.FactCastJson;

/**
 * Implementation of {@link SubscriptionRequest}, that is supposed to be used when transferred over
 * the wire to a remote store (for instance via GRPC or REST)
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public class SubscriptionRequestTO implements SubscriptionRequest {

  public static final long DEFAULT_MAX_BATCH_DELAY_IN_MS = 10;

  private static final String PID = ManagementFactory.getRuntimeMXBean().getName();

  @JsonProperty long maxBatchDelayInMs = DEFAULT_MAX_BATCH_DELAY_IN_MS;

  @JsonProperty long keepaliveIntervalInMs = 0;

  @JsonProperty boolean streamInfo = false; // defaults to false if not set (backwards comp.)

  @JsonProperty boolean continuous;

  @JsonProperty boolean ephemeral;

  @JsonProperty UUID startingAfter;

  @JsonProperty String debugInfo;

  @JsonProperty final List<FactSpec> specs = new LinkedList<>();

  @JsonProperty String pid;

  public boolean hasAnyScriptFilters() {
    return specs.stream().anyMatch(s -> s.jsFilterScript() != null);
  }

  @Override
  public java.util.Optional<UUID> startingAfter() {
    return java.util.Optional.ofNullable(startingAfter);
  }

  // copy constr. from a SR
  public SubscriptionRequestTO(SubscriptionRequest request) {
    maxBatchDelayInMs = request.maxBatchDelayInMs();
    keepaliveIntervalInMs = request.keepaliveIntervalInMs();
    streamInfo = request.streamInfo();
    continuous = request.continuous();
    ephemeral = request.ephemeral();
    startingAfter = request.startingAfter().orElse(null);
    debugInfo = request.debugInfo();
    specs.addAll(request.specs());
    pid = PID;
  }

  // TODO now that forIDs is gone, maybe rename?
  public static SubscriptionRequestTO forFacts(SubscriptionRequest request) {
    return new SubscriptionRequestTO(request);
  }

  public void addSpecs(@NonNull List<FactSpec> factSpecs) {
    checkArgument(!factSpecs.isEmpty());
    specs.addAll(factSpecs);
  }

  private void checkArgument(boolean b) {
    if (!b) {
      throw new IllegalArgumentException();
    }
  }

  @Override
  public List<FactSpec> specs() {
    ArrayList<FactSpec> l = new ArrayList<>(specs);
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
