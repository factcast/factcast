package org.factcast.itests.docexample.factus.event;

import lombok.Getter;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Getter
@Specification(ns = "user", type = "UserAdded", version = 1)
public class UserAdded implements EventObject {

    private UUID userId;
    private String email;

    // used by Jackson deserializer
    protected UserAdded(){}

    public static UserAdded of(UUID userId, String email) {
        UserAdded fact = new UserAdded();
        fact.userId = userId;
        fact.email = email;
        return fact;
    }

    @Override
    public Set<UUID> aggregateIds() {
        return Collections.emptySet();
    }
}
