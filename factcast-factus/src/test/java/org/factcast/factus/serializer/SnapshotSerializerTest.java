package org.factcast.factus.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import org.factcast.factus.projection.SnapshotProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.Data;

@ExtendWith(MockitoExtension.class)
class SnapshotSerializerTest {

    private final SnapshotSerializer underTest = new SnapshotSerializer.DefaultSnapshotSerializer();

    @Test
    void testRoundtrip() {
        // RUN
        SimpleSnapshotProjection initialProjection = new SimpleSnapshotProjection();
        initialProjection.val("Hello");

        byte[] bytes = underTest.serialize(initialProjection);
        SimpleSnapshotProjection projection = underTest
                .deserialize(SimpleSnapshotProjection.class, bytes);

        // ASSERT
        assertThat(projection.val())
                .isEqualTo("Hello");
    }

    @Data
    static class SimpleSnapshotProjection implements SnapshotProjection {
        String val;
    }

}