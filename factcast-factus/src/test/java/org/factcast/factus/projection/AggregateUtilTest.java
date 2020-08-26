package org.factcast.factus.projection;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class AggregateUtilTest {

    @Test
    void aggregateIdCannotBeOverwritten() {
        Aggregate aggregate = new Aggregate() {
        };

        // this should work
        AggregateUtil.aggregateId(aggregate, UUID.randomUUID());

        // this not
        assertThatThrownBy(() -> AggregateUtil.aggregateId(aggregate, UUID.randomUUID()))
                .hasMessageContaining("aggregateId is already set");
    }
}