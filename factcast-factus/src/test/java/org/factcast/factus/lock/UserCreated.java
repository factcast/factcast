package org.factcast.factus.lock;

import java.util.Set;
import java.util.UUID;

import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;

import com.google.common.collect.Sets;

import lombok.Value;

@Value
@Specification(ns = "test")
public class UserCreated implements EventObject {
    UUID aggId;

    String name;

    @Override
    public Set<UUID> aggregateIds() {
        return Sets.newHashSet(aggId);
    }
}
