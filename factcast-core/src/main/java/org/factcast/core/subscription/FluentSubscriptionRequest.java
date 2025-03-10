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

import java.util.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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

  boolean streamInfo;

  boolean continuous;

  UUID startingAfter;

  final List<FactSpec> specs = new LinkedList<>();

  final String debugInfo;

  String pid;

  FluentSubscriptionRequest() {
    debugInfo = createDebugInfo();
    streamInfo = true;
  }

  private String createDebugInfo() {
    StackTraceElement stackTraceElement = new Exception().getStackTrace()[3];
    return UUID.randomUUID()
        + " ("
        + stackTraceElement
            .getClassName()
            .substring(stackTraceElement.getClassName().lastIndexOf(".") + 1)
        + "."
        + stackTraceElement.getMethodName()
        + ":"
        + stackTraceElement.getLineNumber()
        + ")";
  }

  @RequiredArgsConstructor
  public static class Builder implements SpecBuilder {

    private final FluentSubscriptionRequest toBuild;

    @Override
    public SpecBuilder or(@NonNull FactSpec specification) {
      toBuild.specs.add(specification);
      return this;
    }

    @Override
    public SubscriptionRequest fromScratch() {
      return toBuild;
    }

    @Override
    public SubscriptionRequest fromNowOn() {
      toBuild.ephemeral = true;
      return toBuild;
    }

    @Override
    public SubscriptionRequest from(@NonNull UUID id) {
      toBuild.startingAfter = id;
      return toBuild;
    }

    @Override
    public SubscriptionRequest fromNullable(UUID id) {
      toBuild.startingAfter = id;
      return toBuild;
    }

    public SpecBuilder follow(@NonNull FactSpec specification) {
      or(specification);
      toBuild.continuous = true;
      return this;
    }

    public SpecBuilder catchup(@NonNull FactSpec specification) {
      or(specification);
      toBuild.continuous = false;
      return this;
    }

    public SpecBuilder catchup(Collection<FactSpec> specification) {
      if (specification.isEmpty()) {
        throw new IllegalArgumentException("At least one FactSpec is needed for a subscription");
      }
      specification.forEach(this::catchup);
      return this;
    }

    @Override
    public SpecBuilder withMaxBatchDelayInMs(long msec) {
      if (msec < 10) {
        throw new IllegalArgumentException("The minimum maxBatchDelayInMs is 10msec");
      }

      toBuild.maxBatchDelayInMs = msec;
      return this;
    }

    @Override
    public SpecBuilder withKeepaliveIntervalInMs(long msec) {
      if (msec > 0 && msec < 3000) {
        throw new IllegalArgumentException(
            "The minimum keepaliveIntervalInMs is 3000ms. To disable keepalive, set it to 0.");
      }

      toBuild.keepaliveIntervalInMs = msec;
      return this;
    }

    public SpecBuilder follow(Collection<FactSpec> specification) {
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
