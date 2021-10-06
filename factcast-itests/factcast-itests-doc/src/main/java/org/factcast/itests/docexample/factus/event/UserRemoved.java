package org.factcast.itests.docexample.factus.event;

import lombok.Getter;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Getter
@Specification(ns = "user", type = "UserRemoved", version = 1)
public class UserRemoved implements EventObject {

    private UUID userId;

    // used by Jackson deserializer
    protected UserRemoved(){}

    public static UserRemoved of(UUID userID) {
        UserRemoved fact = new UserRemoved();
        fact.userId = userID;
        return fact;
    }

    @Override
    public Set<UUID> aggregateIds() {
        return Collections.emptySet();
    }
}
