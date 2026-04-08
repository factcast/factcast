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

import static org.factcast.core.util.StackTraceCallerHelper.createDebugInfo;

import java.util.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.factcast.core.spec.FactSpec;

/**
 * SubscriptionRequest intended to be used by clients for convenience.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@FieldDefaults(level = AccessLevel.PROTECTED)
@Getter
class FluentSubscriptionRequest implements SubscriptionRequest {

  boolean ephemeral;

  long maxBatchDelayInMs = SubscriptionRequestTO.DEFAULT_MAX_BATCH_DELAY_IN_MS;

  long keepaliveIntervalInMs;

  /**
   * no way for current versions to not want a streamInfo from the server, but removing it would
   * break compatibility with older clients (that do not support FSInfo Signals.
   */
  final boolean streamInfo = true;

  boolean continuous;

  UUID startingAfter;

  final List<FactSpec> specs = new LinkedList<>();

  String debugInfo;

  String pid;

  private FluentSubscriptionRequest() {
    debugInfo = createDebugInfo();
  }

  public static SpecBuilder builder() {
    return new Builder();
  }

  public static class Builder implements SpecBuilder {

    private final FluentSubscriptionRequest toBuild = new FluentSubscriptionRequest();

    @Override
    public @NonNull SpecBuilder or(@NonNull FactSpec specification) {
      toBuild.specs.add(specification);
      return this;
    }

    @Override
    public @NonNull SubscriptionRequest fromScratch() {
      return toBuild;
    }

    @Override
    public @NonNull SubscriptionRequest fromNowOn() {
      toBuild.ephemeral = true;
      return toBuild;
    }

    @Override
    public @NonNull SubscriptionRequest from(@NonNull UUID id) {
      return fromNullable(id);
    }

    @Override
    public @NonNull SubscriptionRequest fromNullable(UUID id) {
      toBuild.startingAfter = id;
      return toBuild;
    }

    @Override
    public @NonNull SpecBuilder follow(@NonNull FactSpec specification) {
      or(specification);
      toBuild.continuous = true;
      return this;
    }

    @Override
    public @NonNull SpecBuilder catchup(@NonNull FactSpec specification) {
      or(specification);
      toBuild.continuous = false;
      return this;
    }

    public @NonNull SpecBuilder catchup(Collection<FactSpec> specification) {
      if (specification.isEmpty()) {
        throw new IllegalArgumentException("At least one FactSpec is needed for a subscription");
      }
      specification.forEach(this::catchup);
      return this;
    }

    @Override
    public @NonNull SpecBuilder withMaxBatchDelayInMs(long msec) {
      if (msec < 10) {
        throw new IllegalArgumentException("The minimum maxBatchDelayInMs is 10msec");
      }

      toBuild.maxBatchDelayInMs = msec;
      return this;
    }

    @Override
    public @NonNull SpecBuilder withDebugHintFrom(Class<?> clazz) {
      toBuild.debugInfo += " | " + clazz.getName();
      return this;
    }

    @Override
    public @NonNull SpecBuilder withKeepaliveIntervalInMs(long msec) {
      if (msec > 0 && msec < 3000) {
        throw new IllegalArgumentException(
            "The minimum keepaliveIntervalInMs is 3000ms. To disable keepalive, set it to 0.");
      }

      toBuild.keepaliveIntervalInMs = msec;
      return this;
    }

    public @NonNull SpecBuilder follow(Collection<FactSpec> specification) {
      if (specification.isEmpty()) {
        throw new IllegalArgumentException("At least one FactSpec is needed for a subscription");
      }
      specification.forEach(this::follow);
      return this;
    }
  }

  @Override
  public Optional<UUID> startingAfter() {
    return Optional.ofNullable(startingAfter);
  }

  @Override
  public String toString() {
    return debugInfo;
  }
}
