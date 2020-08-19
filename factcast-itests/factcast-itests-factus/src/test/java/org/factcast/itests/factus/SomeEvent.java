package org.factcast.itests.factus;

import java.util.Set;
import java.util.UUID;

import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;

import com.google.common.collect.Sets;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Specification(ns = "anotherTest")
public class SomeEvent implements EventObject {
    UUID aggregateId;

    String userName;

    @Override
    public Set<UUID> aggregateIds() {
        return Sets.newHashSet(aggregateId);
    }
}
