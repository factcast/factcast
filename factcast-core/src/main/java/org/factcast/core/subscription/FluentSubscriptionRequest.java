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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
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

  private static final List<String> INTERNAL_PACKAGE_PREFIXES =
      Lists.newArrayList(
          "org.factcast.",
          "java.",
          "javax.",
          "jdk.",
          "sun.",
          "com.sun.",
          "org.springframework.",
          "com.google.common.");

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
    StackTraceElement caller = findCallerFrame(new Exception().getStackTrace());
    return UUID.randomUUID()
        + " ("
        + caller.getClassName().substring(caller.getClassName().lastIndexOf(".") + 1)
        + "."
        + caller.getMethodName()
        + ":"
        + caller.getLineNumber()
        + ")";
  }

  @VisibleForTesting
  static StackTraceElement findCallerFrame(StackTraceElement[] stack) {
    for (StackTraceElement frame : stack) {
      if (isExternalFrame(frame)) {
        return frame;
      }
    }
    // fallback: use original hardcoded index behavior
    return stack.length > 3 ? stack[3] : stack[stack.length - 1];
  }

  private static boolean isExternalFrame(StackTraceElement frame) {
    String className = frame.getClassName();
    for (String prefix : INTERNAL_PACKAGE_PREFIXES) {
      if (className.startsWith(prefix)) {
        return false;
      }
    }
    return true;
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
