package org.factcast.core.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedList;

import org.junit.jupiter.api.Test;

public class IntermediatePublishResultTest {
    @Test
    public void testNullContracts() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            new IntermediatePublishResult(null);
        });

        assertThrows(NullPointerException.class, () -> {
            new IntermediatePublishResult(new LinkedList<>()).andThen(null);
        });
    }

    @Test
    public void testAndThen() throws Exception {
        IntermediatePublishResult uut = new IntermediatePublishResult(new LinkedList<>()).andThen(
                () -> {
                });
        assertThat(uut.andThen()).isPresent();
    }
}
