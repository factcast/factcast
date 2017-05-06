package org.factcast.core.subscription;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.factcast.core.spec.FactSpec;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * SubscriptionRequest intented to be used by clients for convenience.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@FieldDefaults(level = AccessLevel.PROTECTED)
@Getter
class FluentSubscriptionRequest implements SubscriptionRequest {

    boolean ephemeral = false;

    boolean marks = true;

    long maxBatchDelayInMs = 0;

    boolean continous;

    UUID startingAfter;

    List<FactSpec> specs = new LinkedList<>();

    boolean idOnly = false;

    String debugInfo;

    FluentSubscriptionRequest() {
        debugInfo = createDebugInfo();
    }

    private String createDebugInfo() {
        StackTraceElement stackTraceElement = new Exception().getStackTrace()[3];
        return UUID.randomUUID() + " (" + stackTraceElement.getClassName().substring(
                stackTraceElement.getClassName().lastIndexOf(".") + 1) + "." + stackTraceElement
                        .getMethodName() + ":" + stackTraceElement.getLineNumber() + ")";
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

        SpecBuilder follow(FactSpec specification) {
            or(specification);
            toBuild.continous = true;
            return this;
        }

        SpecBuilder catchup(FactSpec specification) {
            or(specification);
            toBuild.continous = false;
            return this;
        }

        @Override
        public SpecBuilder skipMarks() {
            toBuild.marks = false;
            return this;
        }
    }

    public java.util.Optional<UUID> startingAfter() {
        return java.util.Optional.ofNullable(startingAfter);
    }

    public interface SpecBuilder {
        SpecBuilder or(@NonNull FactSpec specification);

        SpecBuilder skipMarks();

        SubscriptionRequest from(@NonNull UUID id);

        SubscriptionRequest fromScratch();

        SubscriptionRequest fromNowOn();

    }

    @Override
    public String toString() {
        return debugInfo;
    }
}
