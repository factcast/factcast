package org.factcast.itests.factus;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.factcast.factus.Handler;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;
import org.factcast.factus.projection.SubscribedProjection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

public class JpaSubscribedUserNames implements SubscribedProjection {

    @Getter
    private Set<String> names = new HashSet<>();

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Specification(ns = "subscribed_username")
    public static class UserCreated implements EventObject {

        UUID aggregateId;

        String name;

        @Override
        public Set<UUID> aggregateIds() {
            return Collections.singleton(aggregateId);
        }
    }

    @Handler
    public void userCreated(JpaSubscribedUserNames.UserCreated userCreated) {
        names.add(userCreated.name);
    }

    @Getter(onMethod_ = @Override)
    private UUID state;

    @Override
    public void state(@NonNull UUID state) {
        this.state = state;
    }

    @Override
    public AutoCloseable acquireWriteToken(@NonNull Duration maxWait) {
        return () -> {
        };
    }
}
